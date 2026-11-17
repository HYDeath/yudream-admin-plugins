<script setup lang="ts">
import {
  YdAlert,
  YdResponsiveTable,
  YdButton,
  YdDrawer,
  YdEmpty,
  YdInput,
  YdNumberField,
  YdPageHeader,
  YdPagination,
  YdSelect,
  YdSwitch,
  YdTextarea,
  useYdModal,
  useYdToast,
} from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { onMounted, ref, watch } from 'vue'
import { createQqRobotApi } from '../api/qqrobot-api'
import { emptyGroup, type ConnectionOption, type GroupSettings } from '../types'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const api = createQqRobotApi(props.sdk)
const toast = useYdToast()
const modal = useYdModal()
const groups = ref<GroupSettings[]>([])
const connections = ref<ConnectionOption[]>([])
const connectionId = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const saving = ref(false)
const editing = ref(false)
const availableGroups = ref<{ id: string, name: string }[]>([])
const optionsLoading = ref(false)
let optionsSequence = 0
const editorOpen = ref(false)
const draft = ref<GroupSettings>(emptyGroup())

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await api.groups(page.value, size.value)
    groups.value = result.records
    total.value = result.total
  }
  catch (error) {
    loadError.value = String((error as Error)?.message ?? '加载群组失败')
  }
  finally {
    loading.value = false
  }
}

async function loadConnections() {
  try {
    connections.value = await api.connections()
    connectionId.value = connections.value[0]?.id ?? ''
  }
  catch {
    connections.value = []
  }
}

function openCreate() {
  editing.value = false
  draft.value = emptyGroup()
  editorOpen.value = true
}

function applyRole(role: 'account' | 'review') {
  const account = role === 'account'
  draft.value = { ...draft.value, enabled: true, bindEnabled: account, queryEnabled: account,
    passwordEnabled: account, cardEnabled: account, welcomeEnabled: !account }
}
function openEdit(group: GroupSettings) {
  editing.value = true
  draft.value = { ...emptyGroup(), ...group }
  editorOpen.value = true
}

async function save() {
  if (saving.value) return
  if (!draft.value.groupId.trim()) {
    toast.error('请填写群号')
    return
  }
  try {
    saving.value = true
    await api.saveGroup(draft.value)
    toast.success('群组策略已保存')
    editorOpen.value = false
    await load()
  }
  catch (error) {
    toast.error(String((error as Error)?.message ?? '保存失败'))
  } finally {
    saving.value = false
  }
}

async function remove(group: GroupSettings) {
  const confirmed = await modal.confirm({
    title: '删除群组策略',
    content: `确定删除群 ${group.groupId} 的策略吗？删除后将继承全局设置。`,
    confirmButtonText: '删除',
  })
  if (!confirmed) return
  try {
    await api.deleteGroup(group.groupId)
    toast.success('已删除')
    await load()
  }
  catch (error) {
    toast.error(String((error as Error)?.message ?? '删除失败'))
  }
}

function displayName(group: GroupSettings) {
  return group.displayName || group.groupId
}

watch(connectionId, async (id) => {
  const request = ++optionsSequence
  availableGroups.value = []
  if (!id) { optionsLoading.value = false; return }
  optionsLoading.value = true
  try {
    const rows = await api.availableGroups(id)
    if (request === optionsSequence) availableGroups.value = rows
  } catch (error) {
    if (request === optionsSequence) toast.error(String((error as Error)?.message ?? '群列表加载失败，可手动填写群号'))
  } finally {
    if (request === optionsSequence) optionsLoading.value = false
  }
})

onMounted(async () => {
  await Promise.all([load(), loadConnections()])
})
</script>

<template>
  <div class="qqrobot-page">
    <YdPageHeader title="群组管理" description="按群分配账号服务、背包查询和新人欢迎；审核规则在「群自动化」单独配置">
    </YdPageHeader>
    <div class="qqrobot-body">
      <div class="qqrobot-toolbar">
        <div class="qqrobot-toolbar-info">
          <strong>群组策略</strong>
          <span class="qqrobot-muted">共 {{ total }} 个群单独配置，其余群继承全局默认</span>
        </div>
        <div class="qqrobot-toolbar-actions">
<YdButton variant="outline" :disabled="loading" @click="load">刷新</YdButton>
          <YdButton @click="openCreate">新增群组</YdButton>
        </div>
      </div>
      <YdAlert v-if="loadError" variant="destructive" title="群策略加载失败" :description="loadError" />
      <div v-if="!groups.length && !loading && !loadError" class="qqrobot-doc-section qqrobot-groups-blank">
        <YdEmpty description="还没有群组策略。把机器人拉进群后新增群号，就能单独配置绑定、群名片和退群提示。">
          <YdButton @click="openCreate">新增群组</YdButton>
        </YdEmpty>
        <div class="qqrobot-groups-hints">
          <div><strong>群名片同步</strong><span>绑定后自动改成「[服务器] 游戏ID」这类格式，可自定义模板与长度。</span></div>
          <div><strong>退群提示</strong><span>玩家退群时在群里发一条消息，支持 #name / #group 占位符。</span></div>
          <div><strong>按群覆盖</strong><span>未配置的群继承全局绑定设置，只有需要差异化时才新增。</span></div>
        </div>
      </div>
      <YdResponsiveTable v-else :columns="[{ accessorKey: 'displayName', header: '群组' }, { accessorKey: 'groupId', header: '群号' }, { id: 'policy', header: '策略' }, { id: 'actions', header: '管理' }]" :data="groups" row-key="groupId" :loading="loading">
        <template #cell-displayName="{ row }">{{ displayName(row.original) }}</template>
        <template #cell-policy="{ row }">{{ row.original.enabled ? '启用' : '停用' }} · {{ row.original.bindEnabled ? '允许绑定' : '禁止绑定' }} · {{ row.original.cardEnabled ? '同步名片' : '不改名片' }}</template>
        <template #cell-actions="{ row }"><div class="qqrobot-group-actions"><YdButton variant="outline" size="sm" @click="openEdit(row.original)">编辑</YdButton><YdButton variant="ghost" size="sm" @click="remove(row.original)">删除</YdButton></div></template>
        <template #card="{ row }"><div class="qqrobot-binding-card"><strong>{{ displayName(row) }}</strong><span class="qqrobot-muted">{{ row.groupId }} · {{ row.enabled ? '启用' : '停用' }}</span><div class="qqrobot-group-actions"><YdButton size="sm" variant="outline" @click="openEdit(row)">编辑</YdButton><YdButton size="sm" variant="ghost" @click="remove(row)">删除</YdButton></div></div></template>
      </YdResponsiveTable>
      <div v-if="total > size" class="qqrobot-pager">
        <YdPagination v-model:page="page" v-model:size="size" :total="total" :sizes="[10, 20, 50]"
          @page-change="load" @size-change="load" />
      </div>
    </div>

    <YdDrawer v-model="editorOpen" :title="draft.groupId ? `群组：${draft.groupId}` : '新增群组'">
      <div class="qqrobot-toolbar-actions"><YdButton variant="outline" size="sm" @click="applyRole('account')">账号服务群</YdButton><YdButton variant="outline" size="sm" @click="applyRole('review')">审核迎新群</YdButton></div>
      <div class="qqrobot-grid">
        <label v-if="!editing" class="qqrobot-field qqrobot-wide"><span>从消息连接读取可选群</span><YdSelect v-model="connectionId" :options="connections.map(item => ({ label: item.name, value: item.id }))" placeholder="选择消息连接" /><small class="qqrobot-muted">连接仅用于读取群列表；策略按群号保存。</small></label>
        <label v-if="!editing && availableGroups.length" class="qqrobot-field qqrobot-wide"><span>选择群</span><YdSelect v-model="draft.groupId" :disabled="optionsLoading" :options="availableGroups.map(item => ({ label: `${item.name}（${item.id}）`, value: item.id }))" /></label>
        <label class="qqrobot-field">
          <span>群号</span>
          <YdInput v-model="draft.groupId" :disabled="editing" placeholder="例如 123456789" />
        </label>
        <label class="qqrobot-field">
          <span>显示名称</span>
          <YdInput v-model="draft.displayName" placeholder="聊天大厅" />
        </label>
        <label class="qqrobot-field qqrobot-switch"><span>启用该群策略</span><YdSwitch v-model="draft.enabled" /></label>
        <label class="qqrobot-field qqrobot-switch"><span>允许群内绑定</span><YdSwitch v-model="draft.bindEnabled" /></label>
        <label class="qqrobot-field qqrobot-switch"><span>允许背包与玩家查询</span><YdSwitch v-model="draft.queryEnabled" /></label>
        <label class="qqrobot-field qqrobot-switch"><span>允许密码服务</span><YdSwitch v-model="draft.passwordEnabled" /></label>
        <label class="qqrobot-field qqrobot-switch"><span>欢迎新人入群</span><YdSwitch v-model="draft.welcomeEnabled" /></label>
        <label v-if="draft.welcomeEnabled" class="qqrobot-field qqrobot-wide"><span>欢迎消息（#mention 提及新人，#name / #group）</span><YdTextarea v-model="draft.welcomeMessage" :rows="4" /><small class="qqrobot-muted">仅收到真实成员入群事件时发送；加入机器人不会触发新人欢迎。</small></label>
        <label class="qqrobot-field qqrobot-switch"><span>同步群名片</span><YdSwitch v-model="draft.cardEnabled" /></label>
        <label class="qqrobot-field qqrobot-switch"><span>退群提示</span><YdSwitch v-model="draft.quitNoticeEnabled" /></label>
        <label class="qqrobot-field qqrobot-wide">
          <span>退群消息（#name / #group）</span>
          <YdTextarea v-model="draft.quitMessage" />
        </label>
        <label class="qqrobot-field qqrobot-wide">
          <span>群名片格式</span>
          <YdInput v-model="draft.cardFormat" />
        </label>
        <label class="qqrobot-field">
          <span>群名片最大长度</span>
          <YdNumberField v-model="draft.cardMaxLength" :min="1" :max="64" />
        </label>
      </div>
      <template #footer>
        <div class="qqrobot-drawer-actions">
          <YdButton variant="outline" @click="editorOpen = false">取消</YdButton>
          <YdButton :loading="saving" @click="save">保存策略</YdButton>
        </div>
      </template>
    </YdDrawer>
  </div>
</template>
