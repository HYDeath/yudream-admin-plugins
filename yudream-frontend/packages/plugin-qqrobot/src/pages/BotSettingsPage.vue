<script setup lang="ts">
import {
  YdButton,
  YdInput,
  YdLoadingOverlay,
  YdPageHeader,
  YdSwitch,
  YdTextarea,
  useYdToast,
} from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { computed, onMounted, ref } from 'vue'
import { createQqRobotApi } from '../api/qqrobot-api'
import type { BotConnectionState, QqIntegrationSettings } from '../types'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const api = createQqRobotApi(props.sdk)
const toast = useYdToast()
const loading = ref(true)
const saving = ref(false)
const state = ref<BotConnectionState | null>(null)
const integrations = ref<QqIntegrationSettings>({
  registrationReviewEnabled: true,
  requireApprovedForGroup: false,
  registrationUrl: '/platform/plugins/registration-review',
  providerAvailable: false,
})
const activeAnchor = ref('connections')
const draft = ref({
  replyEnabled: true,
  replyToFriendAdd: true,
  replyToGroupAdd: true,
  friendAddMessage: '',
  groupAddMessage: '',
})

const anchors = [
  { id: 'connections', label: '消息连接' },
  { id: 'replies', label: '欢迎语' },
  { id: 'binding', label: '接入流程' },
  { id: 'integrations', label: '插件联动' },
]

const connections = computed(() => state.value?.connections ?? [])
const enabled = computed(() => connections.value.length > 0)
const stateLabel = computed(() => {
  if (!state.value) return '加载中'
  if (state.value.state === 'unavailable') return 'QQ 消息平台未启用'
  if (!connections.value.length) return '尚未配置连接'
  return `${connections.value.length} 个连接在线`
})

async function load() {
  loading.value = true
  try {
    state.value = await api.botConnections()
    integrations.value = await api.integrationSettings()
    draft.value = {
      replyEnabled: state.value.replyEnabled,
      replyToFriendAdd: state.value.replyToFriendAdd,
      replyToGroupAdd: state.value.replyToGroupAdd,
      friendAddMessage: state.value.friendAddMessage,
      groupAddMessage: state.value.groupAddMessage,
    }
  } catch (error) {
    toast.error(String((error as Error)?.message ?? '机器人接入状态加载失败'))
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    // 凭证不在这里：只把回复策略写回插件设置。
    await api.saveBotSettings({
      enabled: draft.value.replyEnabled,
      appId: '',
      secretRef: '',
      webhookPath: '',
      apiBase: '',
      sandbox: false,
      replyToFriendAdd: draft.value.replyToFriendAdd,
      replyToGroupAdd: draft.value.replyToGroupAdd,
      friendAddMessage: draft.value.friendAddMessage,
      groupAddMessage: draft.value.groupAddMessage,
      verifySignature: true,
      secretConfigured: true,
      publicKeyConfigured: true,
    })
    integrations.value = await api.saveIntegrationSettings(integrations.value)
    toast.success('回复策略已保存')
    await load()
  } catch (error) {
    toast.error(String((error as Error)?.message ?? '保存失败'))
  } finally {
    saving.value = false
  }
}

function jump(id: string) {
  activeAnchor.value = id
  document.getElementById(`qqrobot-anchor-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function protocolLabel(item: { official?: boolean, protocol?: string, platform?: string }) {
  if (item.official || item.protocol === 'official') return '官方 QQ'
  if (item.protocol === 'milky') return 'Milky'
  return item.platform || '未知协议'
}

onMounted(load)
</script>

<template>
  <div class="qqrobot-page">
    <YdPageHeader title="机器人设置" description="登录连接由平台「QQ 消息平台」统一管理，这里只配置回复策略">
      <template #actions>
        <YdButton variant="outline" :loading="loading" @click="load">刷新状态</YdButton>
        <YdButton :loading="saving" @click="save">保存</YdButton>
      </template>
    </YdPageHeader>
    <div class="qqrobot-body">
      <YdLoadingOverlay :loading="loading">
        <div class="qqrobot-doc">
          <nav class="qqrobot-doc-nav">
            <button
              v-for="item in anchors"
              :key="item.id"
              type="button"
              class="qqrobot-doc-link"
              :class="{ on: activeAnchor === item.id }"
              @click="jump(item.id)"
            >{{ item.label }}</button>
          </nav>

          <div class="qqrobot-doc-body">
            <div class="qqrobot-status-strip" :class="{ on: enabled }">
              <span class="qqrobot-status-dot" />
              <strong>{{ stateLabel }}</strong>
              <span class="qqrobot-muted">{{ state?.capabilityName ?? 'QQ 消息平台' }}</span>
              <a v-if="state" class="qqrobot-status-link" :href="state.managePath">管理连接</a>
            </div>

            <section id="qqrobot-anchor-connections" class="qqrobot-doc-section">
              <header>
                <h3>消息连接</h3>
                <p>
                  QQ 号登录、AppID / AppSecret、Webhook 与沙盒都配置在平台的「QQ 消息平台」里，
                  本插件不再重复保存同一套凭证，避免两边互相覆盖。
                </p>
              </header>

              <div v-if="state?.state === 'unavailable'" class="qqrobot-blocked">
                <strong>QQ 消息平台能力未启用</strong>
                <p>{{ state.message || '请先在「平台 → 能力目录 → QQ 消息平台」启用该能力。' }}</p>
                <a :href="state.managePath">去启用</a>
              </div>

              <div v-else-if="!connections.length" class="qqrobot-blocked">
                <strong>还没有可用的消息连接</strong>
                <p>在「QQ 消息平台」新建连接：官方机器人填 AppID / AppSecret，Milky 填地址与 Access Token。</p>
                <a :href="state?.managePath ?? '/platform/milky'">去新建连接</a>
              </div>

              <div v-else class="qqrobot-conn-list">
                <div v-for="item in connections" :key="item.id" class="qqrobot-conn-row">
                  <span class="qqrobot-conn-dot" />
                  <div class="qqrobot-conn-main">
                    <strong>{{ item.name }}</strong>
                    <code>{{ item.id }}</code>
                  </div>
                  <span class="qqrobot-chip">{{ protocolLabel(item) }}</span>
                  <span class="qqrobot-chip on">已启用</span>
                </div>
              </div>

              <div class="qqrobot-switch-list">
                <label class="qqrobot-switch-row">
                  <span><strong>启用机器人与指令回复</strong><small>关闭后收到的事件一律忽略，适合维护或切换账号时暂停</small></span>
                  <YdSwitch v-model="draft.replyEnabled" />
                </label>
              </div>
            </section>

            <section id="qqrobot-anchor-replies" class="qqrobot-doc-section">
              <header>
                <h3>欢迎语</h3>
                <p>好友添加与机器人入群时自动发送一次；留空则不回复</p>
              </header>
              <div class="qqrobot-switch-list">
                <label class="qqrobot-switch-row">
                  <span><strong>好友添加自动回复</strong><small>对方添加机器人好友后立刻私聊欢迎语</small></span>
                  <YdSwitch v-model="draft.replyToFriendAdd" />
                </label>
                <label class="qqrobot-switch-row">
                  <span><strong>入群自动回复</strong><small>机器人被拉进群后发送一次欢迎语</small></span>
                  <YdSwitch v-model="draft.replyToGroupAdd" />
                </label>
              </div>
              <div class="qqrobot-grid">
                <label class="qqrobot-field qqrobot-wide">
                  <span>好友添加欢迎语</span>
                  <YdTextarea v-model="draft.friendAddMessage" :rows="3" />
                </label>
                <label class="qqrobot-field qqrobot-wide">
                  <span>入群欢迎语</span>
                  <YdTextarea v-model="draft.groupAddMessage" :rows="3" />
                </label>
              </div>
            </section>

            <section id="qqrobot-anchor-binding" class="qqrobot-doc-section">
              <header>
                <h3>接入流程</h3>
                <p>从登录 QQ 到玩家完成绑定，一共三步</p>
              </header>
              <ol class="qqrobot-steps">
                <li>
                  <strong>在平台创建消息连接</strong>
                  <span>
                    官方机器人：QQ 开放平台建应用 → 拿 AppID / AppSecret → 在「QQ 消息平台」新建官方连接，
                    Webhook 用 <code>/api/public/qqbot/{连接ID}/webhook</code>。
                    Milky：部署 Milky 端后填地址与 Access Token，扫码登录 QQ。
                  </span>
                </li>
                <li>
                  <strong>启用连接并测试</strong>
                  <span>在连接上点「启用」，再用「测试」确认能收发消息；这里的状态条会同步显示连接数。</span>
                </li>
                <li>
                  <strong>玩家绑定游戏账号</strong>
                  <span>玩家在 QQ 里发「绑定 游戏ID」拿验证码，进服后在聊天栏输入验证码即可完成双向绑定。</span>
                </li>
              </ol>
            </section>

            <section id="qqrobot-anchor-integrations" class="qqrobot-doc-section">
              <header>
                <h3>插件联动</h3>
                <p>按需启用注册审核联动；未安装对应插件时不会阻断 QQ 机器人。</p>
              </header>
              <div class="qqrobot-status-strip" :class="{ on: integrations.providerAvailable }">
                <span class="qqrobot-status-dot" />
                <strong>{{ integrations.providerAvailable ? '已检测到注册审核插件' : '未检测到注册审核插件' }}</strong>
              </div>
              <div class="qqrobot-switch-list">
                <label class="qqrobot-switch-row">
                  <span><strong>启用注册审核查询</strong><small>QQ 私聊或群聊发送「注册」时返回当前审核状态</small></span>
                  <YdSwitch v-model="integrations.registrationReviewEnabled" />
                </label>
                <label class="qqrobot-switch-row">
                  <span><strong>群聊要求审核通过</strong><small>开启后，群内其它指令需先通过注册审核；绑定和「注册」仍可用</small></span>
                  <YdSwitch v-model="integrations.requireApprovedForGroup" :disabled="!integrations.registrationReviewEnabled" />
                </label>
              </div>
              <label class="qqrobot-field qqrobot-wide">
                <span>注册审核入口</span>
                <YdInput v-model="integrations.registrationUrl" placeholder="/platform/plugins/registration-review" />
              </label>
            </section>
          </div>
        </div>
      </YdLoadingOverlay>
    </div>
  </div>
</template>
