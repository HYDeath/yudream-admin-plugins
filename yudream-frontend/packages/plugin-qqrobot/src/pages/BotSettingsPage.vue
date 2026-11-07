<script setup lang="ts">
import {
  YdButton,
  YdCopyButton,
  YdInput,
  YdLoadingOverlay,
  YdPageHeader,
  YdSelect,
  YdSettingsLayout,
  YdSettingsSection,
  YdSwitch,
  YdTextarea,
  useYdToast,
} from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { computed, onMounted, ref } from 'vue'
import { createQqRobotApi } from '../api/qqrobot-api'
import { emptyBotSettings, type QqBotSettings, type QqBotTestResult } from '../types'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const api = createQqRobotApi(props.sdk)
const toast = useYdToast()
const loading = ref(true)
const saving = ref(false)
const testing = ref(false)
const draft = ref<QqBotSettings>(emptyBotSettings())
const appSecret = ref('')
const webhookPublicKey = ref('')
const testResult = ref<QqBotTestResult | null>(null)
const section = ref('access')

const sections = [
  { label: '基础接入', value: 'access', icon: 'i-ri:robot-2-line' },
  { label: '回调与密钥', value: 'webhook', icon: 'i-ri:shield-keyhole-line' },
  { label: '事件回复', value: 'events', icon: 'i-ri:chat-smile-2-line' },
]

const environmentOptions = [
  { label: '沙箱环境', value: 'sandbox' },
  { label: '正式环境', value: 'production' },
]

const callbackUrl = computed(() => {
  const path = draft.value.webhookPath || ''
  const origin = globalThis.location?.origin ?? ''
  return path.startsWith('http') ? path : `${origin}${path}`
})

const secretHint = computed(() => draft.value.secretConfigured
  ? '已保存，留空表示不修改'
  : '尚未配置，请填写开放平台的 AppSecret')

async function load() {
  loading.value = true
  try {
    draft.value = await api.botSettings()
    appSecret.value = ''
    webhookPublicKey.value = ''
  }
  catch (error) {
    toast.error(String((error as Error)?.message ?? '加载机器人设置失败'))
  }
  finally {
    loading.value = false
  }
}

function setEnvironment(value: string) {
  draft.value.sandbox = value === 'sandbox'
  draft.value.apiBase = draft.value.sandbox
    ? 'https://sandbox.api.sgroup.qq.com'
    : 'https://api.sgroup.qq.com'
}

async function save() {
  saving.value = true
  try {
    const payload = {
      ...draft.value,
      appSecret: appSecret.value.trim(),
      webhookPublicKey: webhookPublicKey.value.trim(),
    }
    draft.value = await api.saveBotSettings(payload)
    appSecret.value = ''
    webhookPublicKey.value = ''
    toast.success('机器人设置已保存')
  }
  catch (error) {
    toast.error(String((error as Error)?.message ?? '保存失败'))
  }
  finally {
    saving.value = false
  }
}

async function test() {
  testing.value = true
  testResult.value = null
  try {
    const result = await api.testBot()
    testResult.value = result
    toast[result.ok ? 'success' : 'error'](result.message)
  }
  catch (error) {
    toast.error(String((error as Error)?.message ?? '测试失败'))
  }
  finally {
    testing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="qqrobot-page">
    <YdPageHeader title="机器人接入" description="QQ 开放平台机器人凭证、回调地址与事件回复策略">
      <template #actions>
        <YdButton variant="outline" :disabled="saving" @click="load">重置</YdButton>
        <YdButton variant="outline" :loading="testing" @click="test">测试连接</YdButton>
        <YdButton :loading="saving" @click="save">保存</YdButton>
      </template>
    </YdPageHeader>
    <div class="qqrobot-body">
      <YdLoadingOverlay :loading="loading">
        <div
          class="qqrobot-render-bar"
          :class="draft.enabled ? 'ok' : 'bad'"
        >
          <div class="qqrobot-render-main">
            <span class="qqrobot-render-dot" />
            <strong>{{ draft.enabled ? '机器人已启用' : '机器人未启用' }}</strong>
            <span class="qqrobot-muted">
              {{ draft.enabled ? '收到的事件会被处理并回复' : '事件会被忽略，不会调用开放平台接口' }}
            </span>
          </div>
          <span v-if="testResult" class="qqrobot-muted">{{ testResult.message }}</span>
        </div>
        <YdSettingsLayout v-model="section" :list="sections">
          <YdSettingsSection v-if="section === 'access'" title="开放平台凭证" description="在 QQ 开放平台「机器人」页签获取">
            <div class="qqrobot-grid">
              <label class="qqrobot-field qqrobot-switch">
                <span>启用机器人</span>
                <YdSwitch v-model="draft.enabled" />
              </label>
              <label class="qqrobot-field">
                <span>运行环境</span>
                <YdSelect
                  :model-value="draft.sandbox ? 'sandbox' : 'production'"
                  :options="environmentOptions"
                  @update:model-value="setEnvironment(String($event ?? 'sandbox'))"
                />
              </label>
              <label class="qqrobot-field">
                <span>AppID</span>
                <YdInput v-model="draft.appId" placeholder="机器人 AppID" />
              </label>
              <label class="qqrobot-field">
                <span>AppSecret</span>
                <YdInput
                  v-model="appSecret"
                  type="password"
                  :placeholder="secretHint"
                  autocomplete="new-password"
                />
              </label>
              <label class="qqrobot-field">
                <span>接口地址</span>
                <YdInput v-model="draft.apiBase" placeholder="https://api.sgroup.qq.com" />
              </label>
              <label class="qqrobot-field">
                <span>密钥引用名</span>
                <YdInput v-model="draft.secretRef" placeholder="qq-bot-secret" />
              </label>
            </div>
            <p class="qqrobot-muted">
              AppSecret 只写入插件密钥库，接口不会回显；密钥引用名用于在密钥库中区分多套机器人凭证。
            </p>
          </YdSettingsSection>

          <YdSettingsSection v-else-if="section === 'webhook'" title="回调与密钥" description="开放平台「回调配置」里填写的地址与校验材料">
            <div class="qqrobot-grid">
              <div class="qqrobot-field qqrobot-wide">
                <span>回调地址</span>
                <div class="qqrobot-bridge-token">
                  <code>{{ callbackUrl }}</code>
                  <YdCopyButton :text="callbackUrl" />
                </div>
              </div>
              <label class="qqrobot-field">
                <span>回调路径</span>
                <YdInput v-model="draft.webhookPath" placeholder="/api/plugins/qqrobot/qq/webhook" />
              </label>
              <label class="qqrobot-field qqrobot-switch">
                <span>校验回调签名</span>
                <YdSwitch v-model="draft.verifySignature" />
              </label>
              <label class="qqrobot-field qqrobot-wide">
                <span>回调公钥（Ed25519）</span>
                <YdTextarea
                  v-model="webhookPublicKey"
                  :placeholder="draft.publicKeyConfigured ? '已保存，留空表示不修改' : '粘贴开放平台提供的公钥'"
                />
              </label>
            </div>
            <p class="qqrobot-muted">
              签名校验使用 `X-Signature-Ed25519` 与 `X-Signature-Timestamp` 请求头，允许 5 分钟时钟漂移。
              平台发起的回调地址验证（op=13）始终直接回显，不受开关影响。
            </p>
          </YdSettingsSection>

          <YdSettingsSection v-else title="事件回复" description="好友添加与入群事件的自动欢迎语">
            <div class="qqrobot-grid">
              <label class="qqrobot-field qqrobot-switch">
                <span>好友添加自动回复</span>
                <YdSwitch v-model="draft.replyToFriendAdd" />
              </label>
              <label class="qqrobot-field qqrobot-switch">
                <span>入群自动回复</span>
                <YdSwitch v-model="draft.replyToGroupAdd" />
              </label>
              <label class="qqrobot-field qqrobot-wide">
                <span>好友添加欢迎语</span>
                <YdTextarea v-model="draft.friendAddMessage" :disabled="!draft.replyToFriendAdd" />
              </label>
              <label class="qqrobot-field qqrobot-wide">
                <span>入群欢迎语</span>
                <YdTextarea v-model="draft.groupAddMessage" :disabled="!draft.replyToGroupAdd" />
              </label>
            </div>
            <p class="qqrobot-muted">
              欢迎语按纯文本发送，不支持 Markdown。主动消息受开放平台频率限制，建议保持简短。
            </p>
          </YdSettingsSection>
        </YdSettingsLayout>
      </YdLoadingOverlay>
    </div>
  </div>
</template>
