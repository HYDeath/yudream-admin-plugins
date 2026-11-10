<script setup lang="ts">
import {
  YdButton,
  YdInput,
  YdLoadingOverlay,
  YdNumberField,
  YdPageHeader,
  YdSelect,
  YdSwitch,
  YdTextarea,
  useYdToast,
} from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { computed, onMounted, ref } from 'vue'
import { createQqRobotApi } from '../api/qqrobot-api'
import { emptyBindSettings, type BindSettings } from '../types'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const api = createQqRobotApi(props.sdk)
const toast = useYdToast()
const loading = ref(true)
const saving = ref(false)
const draft = ref<BindSettings>(emptyBindSettings())
const commandDraft = ref('')
const activeAnchor = ref('mode')

const anchors = [
  { id: 'mode', label: '绑定模式' },
  { id: 'code', label: '验证码' },
  { id: 'message', label: '消息模板' },
  { id: 'event', label: '联动事件' },
  { id: 'card', label: '群名片更改' },
]
const modeOptions = [
  { label: '双向验证', value: 'dual' },
  { label: '社交平台信任模式', value: 'trust' },
]
const charsetOptions = [
  { label: '纯数字 (0-9)', value: 'numeric' },
  { label: '字母数字', value: 'alnum' },
]

async function load() {
  loading.value = true
  try {
    draft.value = await api.bindSettings()
  }
  catch (error) {
    toast.error(String((error as Error)?.message ?? '加载绑定设置失败'))
  }
  finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    draft.value = await api.saveBindSettings(draft.value)
    toast.success('绑定设置已保存')
  }
  catch (error) {
    toast.error(String((error as Error)?.message ?? '保存失败'))
  }
  finally {
    saving.value = false
  }
}

function setForcedServers(value: string) {
  draft.value.forcedServers = value.split(/[\n,，]/).map(item => item.trim()).filter(Boolean)
}

function addCommand() {
  const value = commandDraft.value.trim()
  if (!value)
    return
  draft.value.bindSuccessCommands = [...draft.value.bindSuccessCommands, value].slice(0, 20)
  commandDraft.value = ''
}

function removeCommand(command: string) {
  draft.value.bindSuccessCommands = draft.value.bindSuccessCommands.filter(item => item !== command)
}

function setIgnoredPermissionGroups(value: string) {
  draft.value.ignoredPermissionGroups = value.split(/[\n,，]/).map(item => item.trim()).filter(Boolean)
}

function jump(id: string) {
  activeAnchor.value = id
  const el = document.getElementById(`qqrobot-anchor-${id}`)
  el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const messageFields = computed(() => [
  { key: 'unboundKickMessage', label: '未绑定账号踢出消息', hint: '玩家未绑定就进强制绑定服时看到' },
  { key: 'bindSuccessMessage', label: '绑定成功消息', hint: '进服聊天栏提示' },
  { key: 'bindFailMessage', label: '绑定失败消息', hint: '验证码错误或已过期' },
  { key: 'unbindSuccessMessage', label: '解绑成功消息', hint: '允许解绑时生效' },
  { key: 'unbindFailMessage', label: '解绑失败消息', hint: '未绑定或参数错误' },
] as const)

onMounted(load)
</script>

<template>
  <div class="qqrobot-page">
    <YdPageHeader title="绑定设置" description="社交账号与游戏账号的绑定方式、验证码与消息模板">
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
            <section id="qqrobot-anchor-mode" class="qqrobot-doc-section">
              <header>
                <h3>绑定模式</h3>
                <p>决定绑定是双向验证，还是完全信任社交平台身份</p>
              </header>
              <div class="qqrobot-segment">
                <button
                  v-for="item in modeOptions"
                  :key="item.value"
                  type="button"
                  :class="{ on: draft.bindMode === item.value }"
                  @click="draft.bindMode = item.value as BindSettings['bindMode']"
                >{{ item.label }}</button>
              </div>
              <p class="qqrobot-muted">
                {{ draft.bindMode === 'dual'
                  ? '玩家在 QQ 获取验证码后，需进服在聊天栏输入验证码，两边都确认才完成绑定。'
                  : '以社交平台身份为准：QQ 侧通过即视为绑定成功，不再要求游戏内输入验证码。' }}
              </p>
              <div class="qqrobot-grid">
                <label class="qqrobot-field qqrobot-wide">
                  <span>强制绑定的服务器</span>
                  <YdTextarea :model-value="draft.forcedServers.join('\n')" :rows="2"
                    placeholder="每行一个服务器 ID，未绑定的玩家会被踢出"
                    @update:model-value="setForcedServers(String($event ?? ''))" />
                  <small class="qqrobot-muted">仅在列表内的服务端执行强制绑定，留空表示全部允许游客进入。</small>
                </label>
                <label class="qqrobot-field">
                  <span>通用前缀</span>
                  <YdInput v-model="draft.commonPrefix" placeholder="默认无前缀" />
                </label>
                <label class="qqrobot-field">
                  <span>每个社交账号可绑定的游戏账号</span>
                  <YdNumberField v-model="draft.accountsPerSocial" :min="1" :max="20" />
                </label>
              </div>
              <div class="qqrobot-switch-list">
                <label class="qqrobot-switch-row">
                  <span><strong>启用无关键词模式</strong><small>检测到有效验证码就直接响应，无需先发「绑定」</small></span>
                  <YdSwitch v-model="draft.noKeywordMode" />
                </label>
                <label class="qqrobot-switch-row">
                  <span><strong>禁止中文名</strong><small>仅信任模式生效，不允许绑定非标准用户名</small></span>
                  <YdSwitch v-model="draft.forbidChinese" />
                </label>
                <label class="qqrobot-switch-row">
                  <span><strong>允许解绑</strong><small>允许玩家在群里发送解绑关键词解除绑定</small></span>
                  <YdSwitch v-model="draft.allowUnbind" />
                </label>
              </div>
            </section>

            <section id="qqrobot-anchor-code" class="qqrobot-doc-section">
              <header>
                <h3>验证码</h3>
                <p>验证码由 QQ 侧生成，玩家进服后在聊天栏输入</p>
              </header>
              <div class="qqrobot-grid">
                <label class="qqrobot-field">
                  <span>生成模式</span>
                  <YdSelect v-model="draft.codeCharset" :options="charsetOptions" />
                </label>
                <label class="qqrobot-field">
                  <span>验证码长度</span>
                  <YdNumberField v-model="draft.codeLength" :min="4" :max="12" />
                </label>
                <label class="qqrobot-field">
                  <span>有效时间（分钟）</span>
                  <YdNumberField v-model="draft.codeTtlMinutes" :min="1" :max="120" />
                </label>
                <label class="qqrobot-field">
                  <span>绑定关键词</span>
                  <YdInput v-model="draft.bindKeyword" placeholder="绑定" />
                </label>
                <label class="qqrobot-field">
                  <span>解绑关键词</span>
                  <YdInput v-model="draft.unbindKeyword" placeholder="解绑" />
                </label>
              </div>
              <div class="qqrobot-sample">
                <div class="qqrobot-sample-label">示例</div>
                <div class="qqrobot-sample-body">
                  <span class="qqrobot-sample-q">玩家</span>
                  <span class="qqrobot-sample-text">发送「{{ draft.bindKeyword || '绑定' }} Steve」</span>
                  <span class="qqrobot-sample-arrow">→</span>
                  <span class="qqrobot-sample-code">{{ draft.codeCharset === 'numeric' ? '1'.repeat(draft.codeLength) : 'A'.repeat(draft.codeLength) }}</span>
                  <span class="qqrobot-sample-hint">{{ draft.codeTtlMinutes }} 分钟内有效</span>
                </div>
              </div>
            </section>

            <section id="qqrobot-anchor-message" class="qqrobot-doc-section">
              <header>
                <h3>消息模板</h3>
                <p>支持 &amp; 颜色代码；可用占位符 #account（社交账号）、#name（昵称）、#why（失败原因）</p>
              </header>
              <div class="qqrobot-message-list">
                <label v-for="field in messageFields" :key="field.key" class="qqrobot-message-item">
                  <span class="qqrobot-message-label">
                    <strong>{{ field.label }}</strong>
                    <small>{{ field.hint }}</small>
                  </span>
                  <YdTextarea v-model="draft[field.key]" :rows="2" />
                </label>
              </div>
            </section>

            <section id="qqrobot-anchor-event" class="qqrobot-doc-section">
              <header>
                <h3>联动事件</h3>
                <p>玩家绑定成功后，以控制台身份在绑定所在服务器执行命令，可对接其他插件的发放逻辑</p>
              </header>
              <div class="qqrobot-inline">
                <YdInput v-model="commandDraft" placeholder="例如 give $player diamond 1"
                  @keyup.enter="addCommand" />
                <YdButton variant="outline" @click="addCommand">添加</YdButton>
              </div>
              <div class="qqrobot-tags">
                <span class="qqrobot-chip ghost">$player 玩家名</span>
                <span class="qqrobot-chip ghost">$account 社交账号</span>
                <span class="qqrobot-chip ghost">$name 社交昵称</span>
              </div>
              <div class="qqrobot-command-list">
                <code v-for="command in draft.bindSuccessCommands" :key="command">
                  {{ command }}
                  <button type="button" @click="removeCommand(command)">×</button>
                </code>
                <span v-if="!draft.bindSuccessCommands.length" class="qqrobot-muted">
                  暂无联动命令，绑定成功时不做额外操作。
                </span>
              </div>
            </section>

            <section id="qqrobot-anchor-card" class="qqrobot-doc-section">
              <header>
                <h3>群名片更改</h3>
                <p>绑定成功后按该成员名下的全部游戏账号更新群名片；消息校正会增加一次绑定查询。</p>
              </header>
              <div class="qqrobot-switch-list">
                <label class="qqrobot-switch-row">
                  <span><strong>退群时自动解绑所有账号</strong><small>成员退群后删除该社交账号名下的全部游戏绑定。</small></span>
                  <YdSwitch v-model="draft.autoUnbindOnGroupLeave" />
                </label>
                <label class="qqrobot-switch-row">
                  <span><strong>绑定成功后改名</strong><small>绑定或解绑成功后将群名片改为已绑定的游戏账号，多个账号用空格分隔。</small></span>
                  <YdSwitch v-model="draft.renameOnBindSuccess" />
                </label>
                <label class="qqrobot-switch-row">
                  <span><strong>不允许用户改名</strong><small>成员发消息时检查并校正群名片，可能增加响应耗时。</small></span>
                  <YdSwitch v-model="draft.preventUserRename" />
                </label>
                <label class="qqrobot-switch-row">
                  <span><strong>忽略已包含绑定名的用户</strong><small>昵称或群名片已包含任意绑定游戏名时跳过改名。</small></span>
                  <YdSwitch v-model="draft.ignoreAlreadyBoundName" />
                </label>
                <label class="qqrobot-switch-row">
                  <span><strong>忽略群主</strong><small>不修改群主的群名片。</small></span>
                  <YdSwitch v-model="draft.ignoreGroupOwner" />
                </label>
                <label class="qqrobot-switch-row">
                  <span><strong>忽略管理员</strong><small>不修改管理员或群管的群名片。</small></span>
                  <YdSwitch v-model="draft.ignoreGroupAdmins" />
                </label>
              </div>
              <label class="qqrobot-field qqrobot-wide">
                <span>忽略改名的自定义权限组</span>
                <YdTextarea :model-value="draft.ignoredPermissionGroups.join('\n')" :rows="2"
                  placeholder="每行一个角色或权限组名称，例如 staff、vip"
                  @update:model-value="setIgnoredPermissionGroups(String($event ?? ''))" />
                <small class="qqrobot-muted">事件中的角色名命中任意一项时跳过改名。</small>
              </label>
            </section>
          </div>
        </div>
      </YdLoadingOverlay>
      <div class="qqrobot-save-bar">
        <span class="qqrobot-muted">修改后需保存才会生效</span>
        <div class="qqrobot-save-actions">
          <YdButton variant="outline" :disabled="saving || loading" @click="load">重置</YdButton>
          <YdButton :loading="saving" @click="save">保存设置</YdButton>
        </div>
      </div>
    </div>
  </div>
</template>
