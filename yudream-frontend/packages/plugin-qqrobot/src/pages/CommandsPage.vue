<script setup lang="ts">
import {
  YdButton,
  YdDrawer,
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
import { emptyCommandSettings, type CommandBinding, type CommandSettings, type CommandTestResult } from '../types'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const api = createQqRobotApi(props.sdk)
const toast = useYdToast()
const loading = ref(true)
const saving = ref(false)
const draft = ref<CommandSettings>(emptyCommandSettings())
const triggerDraft = ref('')
const prefixDraft = ref('')
const editing = ref<CommandBinding | null>(null)
const editorOpen = ref(false)
const filter = ref('')
const simulateText = ref('背包')
const simulateScope = ref<'private' | 'group' | 'game'>('group')
const simulateResult = ref<CommandTestResult | null>(null)
const simulating = ref(false)

const scopeLabels: Record<string, string> = {
  private: '私聊',
  group: '群聊',
  game: '游戏内',
}

const enabledCount = computed(() => draft.value.commands.filter(item => item.enabled).length)

const visibleCommands = computed(() => {
  const keyword = filter.value.trim().toLowerCase()
  if (!keyword)
    return draft.value.commands
  return draft.value.commands.filter(item =>
    item.label.toLowerCase().includes(keyword)
    || item.action.toLowerCase().includes(keyword)
    || item.triggers.some(trigger => trigger.toLowerCase().includes(keyword)))
})

async function load() {
  loading.value = true
  try {
    draft.value = await api.commandSettings()
  }
  catch (error) {
    toast.error(String((error as Error)?.message ?? '加载指令设置失败'))
  }
  finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    draft.value = await api.saveCommandSettings(draft.value)
    toast.success('指令设置已保存，已连接的服务端会立即同步')
  }
  catch (error) {
    toast.error(String((error as Error)?.message ?? '保存失败'))
  }
  finally {
    saving.value = false
  }
}

function edit(command: CommandBinding) {
  editing.value = command
  triggerDraft.value = ''
  editorOpen.value = true
}

function addTrigger() {
  const command = editing.value
  const value = triggerDraft.value.trim().toLowerCase()
  if (!command || !value)
    return
  triggerDraft.value = ''
  if (command.triggers.includes(value))
    return
  command.triggers = [...command.triggers, value].slice(0, 8)
}

function removeTrigger(command: CommandBinding, trigger: string) {
  command.triggers = command.triggers.filter(item => item !== trigger)
}

function toggleScope(command: CommandBinding, scope: 'private' | 'group' | 'game', on: boolean) {
  const set = new Set(command.scopes)
  if (on)
    set.add(scope)
  else
    set.delete(scope)
  command.scopes = Array.from(set)
}

function addPrefix() {
  const value = prefixDraft.value.trim()
  prefixDraft.value = ''
  if (!value || draft.value.extraPrefixes.includes(value))
    return
  draft.value.extraPrefixes = [...draft.value.extraPrefixes, value].slice(0, 8)
}

function removePrefix(prefix: string) {
  draft.value.extraPrefixes = draft.value.extraPrefixes.filter(item => item !== prefix)
}

async function simulate() {
  const text = simulateText.value.trim()
  if (!text)
    return
  simulating.value = true
  try {
    simulateResult.value = await api.testCommand(text, simulateScope.value, draft.value)
  }
  catch (error) {
    toast.error(String((error as Error)?.message ?? '模拟失败'))
  }
  finally {
    simulating.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="qqrobot-page">
    <YdPageHeader title="指令设置" description="QQ 群聊、私聊与游戏内共用的触发词；不要求斜杠，也可自定义前缀与别名">
    </YdPageHeader>
    <div class="qqrobot-body">
      <YdLoadingOverlay :loading="loading">
        <section class="qqrobot-doc-section">
          <header class="qqrobot-cmd-section-head">
            <div>
              <h3>前缀规则</h3>
              <p>默认不需要前缀，玩家发「背包」「潜影盒 2」即可触发；<code>/背包</code> 始终可用。开启“必须带前缀”后只接受下面列出的前缀。</p>
            </div>
          </header>
          <div class="qqrobot-switch-list">
            <label class="qqrobot-switch-row">
              <span><strong>必须带前缀</strong><small>开启后「背包」不再生效，只能发「!背包」这类带前缀的写法</small></span>
              <YdSwitch v-model="draft.prefixRequired" />
            </label>
            <label class="qqrobot-switch-row">
              <span><strong>接受 / 前缀</strong><small>允许玩家发送「/背包」，对齐原版命令习惯</small></span>
              <YdSwitch v-model="draft.allowSlash" />
            </label>
          </div>
          <div class="qqrobot-field">
            <span>额外前缀</span>
            <div class="qqrobot-inline qqrobot-prefix-row">
              <YdInput v-model="prefixDraft" placeholder="输入前缀后回车，例如 # 或 !" @keyup.enter="addPrefix" />
              <YdButton variant="outline" @click="addPrefix">添加</YdButton>
            </div>
            <div class="qqrobot-tags">
              <span v-for="prefix in draft.extraPrefixes" :key="prefix" class="qqrobot-chip ghost">
                {{ prefix }}
                <button type="button" @click="removePrefix(prefix)">×</button>
              </span>
              <span v-if="!draft.extraPrefixes.length" class="qqrobot-muted">暂无额外前缀，仅支持无前缀与 /</span>
            </div>
          </div>
        </section>

        <section class="qqrobot-doc-section">
          <header class="qqrobot-cmd-section-head">
            <div>
              <h3>指令模拟 <span class="qqrobot-count">不执行副作用</span></h3>
              <p>用当前草稿（含未保存改动）走一遍匹配，确认玩家发这句话时会命中哪条指令、带出什么参数。</p>
            </div>
          </header>
          <div class="qqrobot-simulate">
            <div class="qqrobot-simulate-input">
              <YdInput v-model="simulateText" placeholder="输入玩家可能发送的一句话，例如「绑定 Steve」" @keyup.enter="simulate" />
              <div class="qqrobot-segmented">
                <button
                  v-for="scope in (['private', 'group', 'game'] as const)"
                  :key="scope"
                  type="button"
                  :class="{ active: simulateScope === scope }"
                  @click="simulateScope = scope"
                >{{ scopeLabels[scope] }}</button>
              </div>
              <YdButton :loading="simulating" @click="simulate">模拟</YdButton>
            </div>
            <div v-if="simulateResult" class="qqrobot-simulate-result" :class="{ miss: !simulateResult.matched }">
              <template v-if="simulateResult.matched">
                <strong class="qqrobot-simulate-title">命中「{{ simulateResult.label }}」</strong>
                <span class="qqrobot-simulate-pair">动作 <code>{{ simulateResult.action }}</code></span>
                <span v-if="simulateResult.args" class="qqrobot-simulate-pair">参数 <code>{{ simulateResult.args }}</code></span>
                <span class="qqrobot-muted">{{ simulateResult.replyHint ? `即时回复：${simulateResult.replyHint}` : '该指令没有即时回复，会走渲染或服务端请求' }}</span>
              </template>
              <template v-else>
                <strong class="qqrobot-simulate-title">没有命中任何指令</strong>
                <span class="qqrobot-muted">机器人不会回复这句话；检查触发词、生效范围，或「必须带前缀」是否开启。</span>
              </template>
            </div>
          </div>
        </section>

        <section class="qqrobot-doc-section">
          <header class="qqrobot-cmd-section-head">
            <div>
              <h3>指令列表 <span class="qqrobot-count">{{ enabledCount }}/{{ draft.commands.length }} 已启用</span></h3>
              <p>每个指令支持多个别名，触发后按生效范围回复。点击任意一行编辑别名与提示语。</p>
            </div>
            <div class="qqrobot-cmd-search">
              <YdInput v-model="filter" class="qqrobot-cmd-filter" placeholder="搜索指令或别名" />
            </div>
          </header>

          <div class="qqrobot-cmd-table">
            <div class="qqrobot-cmd-row qqrobot-cmd-head">
              <span>指令</span>
              <span>触发词</span>
              <span>生效范围</span>
              <span>状态</span>
            </div>
            <button
              v-for="command in visibleCommands"
              :key="command.action"
              type="button"
              class="qqrobot-cmd-row"
              :class="{ off: !command.enabled }"
              @click="edit(command)"
            >
              <span class="qqrobot-cmd-name">
                <strong>{{ command.label }}</strong>
                <code>{{ command.action }}</code>
              </span>
              <span class="qqrobot-tags qqrobot-cmd-triggers" data-label="触发词">
                <span v-for="trigger in command.triggers.slice(0, 4)" :key="trigger" class="qqrobot-chip">{{ trigger }}</span>
                <span v-if="command.triggers.length > 4" class="qqrobot-chip ghost">+{{ command.triggers.length - 4 }}</span>
                <span v-if="!command.triggers.length" class="qqrobot-chip off">未配置</span>
              </span>
              <span class="qqrobot-tags qqrobot-cmd-scopes" data-label="生效范围">
                <span v-for="scope in command.scopes" :key="scope" class="qqrobot-chip ghost">{{ scopeLabels[scope] ?? scope }}</span>
                <span v-if="!command.scopes.length" class="qqrobot-chip off">无</span>
              </span>
              <span class="qqrobot-cmd-state" data-label="状态">
                <span :class="['qqrobot-chip', command.enabled ? 'on' : 'off']">{{ command.enabled ? '启用' : '停用' }}</span>
              </span>
            </button>
            <div v-if="!visibleCommands.length" class="qqrobot-cmd-empty qqrobot-muted">没有匹配的指令。</div>
          </div>
        </section>
      </YdLoadingOverlay>
      <div class="qqrobot-save-bar">
        <span class="qqrobot-muted">{{ enabledCount }}/{{ draft.commands.length }} 个指令启用中</span>
        <div class="qqrobot-save-actions">
          <YdButton variant="outline" :disabled="saving || loading" @click="load">重置</YdButton>
          <YdButton :loading="saving" @click="save">保存指令</YdButton>
        </div>
      </div>
    </div>

    <YdDrawer v-model="editorOpen" :title="editing ? `编辑指令：${editing.label}` : '编辑指令'">
      <div v-if="editing" class="qqrobot-cmd-editor">
        <label class="qqrobot-switch-row">
          <span><strong>启用该指令</strong><small>关闭后所有别名都不再响应</small></span>
          <YdSwitch v-model="editing.enabled" />
        </label>

        <div class="qqrobot-field">
          <span>触发词（第一个为展示名，支持多个别名）</span>
          <div class="qqrobot-tags">
            <span v-for="trigger in editing.triggers" :key="trigger" class="qqrobot-chip on">
              {{ trigger }}
              <button type="button" @click="removeTrigger(editing, trigger)">×</button>
            </span>
            <span v-if="!editing.triggers.length" class="qqrobot-chip off">未配置，指令不可用</span>
          </div>
          <div class="qqrobot-inline">
            <YdInput v-model="triggerDraft" placeholder="输入别名后回车，例如 bag" @keyup.enter="addTrigger" />
            <YdButton variant="outline" @click="addTrigger">添加</YdButton>
          </div>
        </div>

        <div class="qqrobot-field">
          <span>生效范围</span>
          <div class="qqrobot-tags">
            <label v-for="scope in (['private', 'group', 'game'] as const)" :key="scope" class="qqrobot-scope">
              <YdSwitch
                :model-value="editing.scopes.includes(scope)"
                @update:model-value="(value) => toggleScope(editing!, scope, Boolean(value))"
              />
              {{ scopeLabels[scope] }}
            </label>
          </div>
        </div>

        <label class="qqrobot-field">
          <span>触发提示（QQ 侧文字回复，可选）</span>
          <YdTextarea v-model="editing.replyHint" :rows="3" placeholder="仅在需要文字提示的指令上使用，图片类指令留空即可" />
        </label>
      </div>
      <template #footer>
        <div class="qqrobot-drawer-actions">
          <YdButton @click="editorOpen = false">完成</YdButton>
        </div>
      </template>
    </YdDrawer>
  </div>
</template>
