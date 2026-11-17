<script setup lang="ts">
import {
  YdAlert, YdButton, YdDrawer, YdEmpty, YdForm, YdFormItem, YdInput,
  YdLoadingOverlay, YdPageHeader, YdPagination, YdResponsiveEnhancedTable,
  YdSelect, YdSettingsLayout, YdSettingsSection, YdTag, useYdModal, useYdToast,
  type ActionMenuItem, type TableColumn, type YdTablePickerQuery, type YdTablePickerResult,
} from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { computed, onMounted, ref, watch } from 'vue'
import { createQqbotAutomationApi } from '../api/qqbot-automation-api'
import PolicyFieldsForm from '../components/PolicyFieldsForm.vue'
import {
  POLICY_OVERRIDE_FIELDS, completeOverride, emptyOverride, emptyPolicy, formatGroupLabel, policyFromOverride,
  type AutomationPolicy, type AutomationPolicyOverride, type AiProviderOption, type Option, type UserOption,
} from '../types'

type UserPickerRow = UserOption & { label: string }
const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const api = createQqbotAutomationApi(props.sdk)
const toast = useYdToast()
const modal = useYdModal()
const connections = ref<Option[]>([])
const groups = ref<Option[]>([])
const aiProviders = ref<AiProviderOption[]>([])
const connectionId = ref('')
const defaultPolicy = ref<AutomationPolicy>(emptyPolicy())
const defaultDraft = ref<AutomationPolicyOverride>(completeOverride(emptyPolicy()))
const overrides = ref<AutomationPolicyOverride[]>([])
const loading = ref(false)
const initialLoading = ref(false)
const optionsReady = ref(false)
const connectionReady = ref(false)
const mediaReady = ref(false)
const overridesLoading = ref(false)
const savingDefault = ref(false)
const savingOverride = ref(false)
const savingMediaSettings = ref(false)
const savingAlias = ref(false)
const error = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const editorOpen = ref(false)
const editorChannelId = ref('')
const extraGroupId = ref('')
const identifyChannelId = ref('')
const editorDraft = ref<AutomationPolicyOverride>(emptyOverride())
const editorLoading = ref(false)
const editorReady = ref(false)
const editorError = ref('')
const mediaSettingsDraft = ref({ hostDirectory: '/media', containerDirectory: '/app/download' })
const userLabelCache = ref<Record<string, string>>({})
const aliasDraft = ref('')
const identifyingGroupId = ref('')
let connectionRequest = 0
let overridesRequest = 0
let editorRequest = 0
const writing = computed(() => savingDefault.value || savingOverride.value || savingAlias.value || !!identifyingGroupId.value)
const activeSection = ref('connection')
const sectionList = [
  { icon: 'i-ri:links-line', label: 'QQ 连接', value: 'connection' },
  { icon: 'i-ri:shield-keyhole-line', label: '连接默认策略', value: 'default' },
  { icon: 'i-ri:team-line', label: '群级覆盖', value: 'overrides' },
  { icon: 'i-ri:folder-3-line', label: '媒体存储', value: 'media' },
]
function protocolLabel(item: Option) { return item.protocol === 'official' ? '官方 QQ' : item.protocol === 'milky' ? 'Milky' : item.platform || '未知平台' }
const currentConnection = computed(() => connections.value.find(item => item.id === connectionId.value))
const connectionOptions = computed(() => connections.value.map(item => ({ label: `${item.name}（${protocolLabel(item)}）`, value: item.id })))
const groupOptions = computed(() => {
  const options = new Map(groups.value.map(item => [item.id, { label: formatGroupLabel(item), value: item.id }]))
  for (const id of [editorChannelId.value, ...overrides.value.map(item => item.channelId)]) {
    if (id && !options.has(id)) options.set(id, { label: formatGroupLabel({ id }), value: id })
  }
  return [...options.values()]
})
const editorTitle = computed(() => editorChannelId.value ? `群级覆盖：${groupName(editorChannelId.value)}` : '新增群级覆盖')
const columns: TableColumn<AutomationPolicyOverride>[] = [
  { id: 'group', header: '群聊', minWidth: 160 },
  { id: 'capabilities', header: '群功能', minWidth: 180 },
  { id: 'overrides', header: '自定义', width: 76 },
]
const rowActions: ActionMenuItem<AutomationPolicyOverride>[] = [
  { label: '编辑', icon: 'morph:pencil', disabled: () => !connectionReady.value || !optionsReady.value, onClick: row => openEdit(row) },
  { label: '删除', icon: 'morph:trash-2', danger: true, disabled: () => !connectionReady.value, onClick: row => confirmDelete(row) },
]
function groupName(channelId: string) { return formatGroupLabel(groups.value.find(item => item.id === channelId) || { id: channelId }) }
function upsertGroup(row: Option) {
  const index = groups.value.findIndex(item => item.id === row.id)
  if (index >= 0) groups.value[index] = { ...groups.value[index], ...row }
  else groups.value = [...groups.value, row]
}
function showError(value: unknown, fallback: string) {
  error.value = value instanceof Error && value.message ? value.message : fallback
  toast.error(error.value)
}
async function saveAlias() {
  const channelId = identifyChannelId.value.trim()
  if (savingAlias.value || !connectionReady.value || !connectionId.value || !channelId) return
  savingAlias.value = true
  try { const saved = await api.saveGroupAlias({ connectionId: connectionId.value, channelId, alias: aliasDraft.value.trim() }); upsertGroup(saved); toast.success(saved.alias ? '群备注已保存' : '群备注已清除') }
  catch (cause) { showError(cause, '保存群备注失败') }
  finally { savingAlias.value = false }
}
async function identifyGroup(channelId: string) {
  if (!connectionReady.value || identifyingGroupId.value || !connectionId.value || !channelId) return
  identifyingGroupId.value = channelId
  try { await api.identifyGroup({ connectionId: connectionId.value, channelId }); toast.success('识别消息已发送，请到 QQ 群确认') }
  catch (cause) { showError(cause, '发送识别消息失败') }
  finally { identifyingGroupId.value = '' }
}
function countOverrides(override: AutomationPolicyOverride) { return POLICY_OVERRIDE_FIELDS.filter(field => override[field] !== null).length }
function applyPreset(kind: 'review' | 'chat') {
  editorDraft.value = { ...editorDraft.value, enabled: true, mediaEnabled: kind === 'chat',
    joinVerificationEnabled: kind === 'review', websiteApprovalRequired: kind === 'review', aiFallbackEnabled: false }
}
function mediaState(override: AutomationPolicyOverride) { return override.mediaEnabled === null ? '继承默认' : override.mediaEnabled ? '已开启' : '已关闭' }
function riskState(override: AutomationPolicyOverride) { return override.riskMonitorEnabled === null ? '继承默认' : override.riskMonitorEnabled ? '已开启' : '已关闭' }
async function fetchUserOptions(query: YdTablePickerQuery): Promise<YdTablePickerResult<UserPickerRow>> {
  const result = await api.userOptions(query.keyword, query.page, query.size)
  const list = result.records.map(user => ({ ...user, label: user.nickname || user.username || user.id }))
  for (const row of list) userLabelCache.value[row.id] = row.label
  return { list, total: Number(result.total) || 0 }
}
async function loadCurrentConnection() {
  const id = connectionId.value
  if (!id || writing.value) return
  const request = ++connectionRequest
  connectionReady.value = false
  loading.value = true
  error.value = ''
  try {
    const [policy, connectionGroups] = await Promise.all([api.defaultPolicy(id), api.groups(id)])
    if (request !== connectionRequest || id !== connectionId.value) return
    defaultPolicy.value = policy
    defaultDraft.value = completeOverride(policy)
    groups.value = connectionGroups
    connectionReady.value = true
    await loadOverrides()
  }
  catch (cause) { if (request === connectionRequest) showError(cause, '加载连接策略失败，请重试后再保存') }
  finally { if (request === connectionRequest) loading.value = false }
}
async function loadOverrides() {
  const id = connectionId.value
  const request = ++overridesRequest
  if (!id) { overrides.value = []; total.value = 0; overridesLoading.value = false; return }
  overridesLoading.value = true
  try {
    const result = await api.groupOverrides(id, page.value, size.value)
    if (request !== overridesRequest || id !== connectionId.value) return
    overrides.value = result.records
    total.value = result.total
  }
  catch (cause) { if (request === overridesRequest) showError(cause, '加载群级覆盖失败') }
  finally { if (request === overridesRequest) overridesLoading.value = false }
}
async function saveDefault() {
  if (!connectionId.value || !connectionReady.value || !optionsReady.value || loading.value || savingDefault.value) return
  savingDefault.value = true
  try {
    const saved = await api.saveDefaultPolicy({ ...policyFromOverride(defaultDraft.value), connectionId: connectionId.value, channelId: '' })
    defaultPolicy.value = saved
    defaultDraft.value = completeOverride(saved)
    toast.success('连接默认策略已保存')
  }
  catch (cause) { showError(cause, '保存连接默认策略失败') }
  finally { savingDefault.value = false }
}
function openCreate() {
  if (!connectionReady.value || !optionsReady.value) return
  editorRequest += 1
  editorReady.value = true
  editorLoading.value = false
  editorError.value = ''
  editorChannelId.value = ''
  extraGroupId.value = ''
  editorDraft.value = emptyOverride(connectionId.value)
  editorOpen.value = true
}
function addExtraGroup() {
  const id = extraGroupId.value.trim()
  if (!id) return
  editorChannelId.value = id
  extraGroupId.value = ''
  if (!groups.value.some(item => item.id === id)) upsertGroup({ id, name: id })
}
async function openEdit(row: AutomationPolicyOverride) {
  if (!connectionReady.value || !optionsReady.value) return
  editorChannelId.value = row.channelId
  editorDraft.value = { ...row }
  editorOpen.value = true
  await loadEditor()
}
async function loadEditor() {
  const request = ++editorRequest
  const id = connectionId.value
  const channelId = editorChannelId.value
  editorLoading.value = true
  editorReady.value = false
  editorError.value = ''
  try {
    const current = await api.groupOverride(id, channelId)
    if (request !== editorRequest || !editorOpen.value) return
    if (!current) throw new Error('该群覆盖已不存在，请关闭后重新加载列表。')
    editorDraft.value = current
    editorReady.value = true
  }
  catch (cause) { if (request === editorRequest) editorError.value = cause instanceof Error ? cause.message : '加载群级覆盖详情失败' }
  finally { if (request === editorRequest) editorLoading.value = false }
}
function closeEditor() {
  editorRequest += 1
  editorOpen.value = false
  editorReady.value = false
  editorLoading.value = false
  editorError.value = ''
  editorChannelId.value = ''
  extraGroupId.value = ''
  editorDraft.value = emptyOverride(connectionId.value)
}
async function saveOverride() {
  if (!editorReady.value || editorLoading.value || savingOverride.value || !connectionReady.value) return
  const channelId = editorChannelId.value
  if (!connectionId.value || !channelId) { toast.error('请选择需要配置的群聊'); return }
  savingOverride.value = true
  try {
    await api.saveGroupOverride({ ...editorDraft.value, connectionId: connectionId.value, channelId })
    toast.success('群级覆盖已保存')
    closeEditor()
    await loadOverrides()
  }
  catch (cause) { editorError.value = cause instanceof Error ? cause.message : '保存群级覆盖失败'; toast.error(editorError.value) }
  finally { savingOverride.value = false }
}
function beforeEditorClose(action: 'confirm' | 'cancel' | 'close', done: () => void) {
  if (savingOverride.value) return
  if (action === 'confirm') void saveOverride()
  else done()
}
function confirmDelete(row: AutomationPolicyOverride) {
  const id = connectionId.value
  modal.confirm({
    title: '删除群级覆盖', description: `删除后，“${groupName(row.channelId)}”将恢复使用连接默认策略。`, confirmButtonText: '删除覆盖',
    onConfirm: async () => {
      try { await api.deleteGroupOverride(id, row.channelId); toast.success('群级覆盖已删除'); if (connectionId.value === id) { if (overrides.value.length === 1 && page.value > 1) page.value -= 1; else await loadOverrides() } }
      catch (cause) { showError(cause, '删除群级覆盖失败') }
    },
  })
}
async function saveMediaSettings() {
  if (!mediaReady.value || savingMediaSettings.value || initialLoading.value) return
  savingMediaSettings.value = true
  try {
    const saved = await api.saveMediaSettings({ hostDirectory: mediaSettingsDraft.value.hostDirectory.trim() || null, containerDirectory: mediaSettingsDraft.value.containerDirectory.trim() })
    mediaSettingsDraft.value = { hostDirectory: saved.hostDirectory ?? '', containerDirectory: saved.containerDirectory || '/app/download' }
    toast.success('媒体存储设置已保存')
  }
  catch (cause) { showError(cause, '保存媒体存储设置失败') }
  finally { savingMediaSettings.value = false }
}
watch(connectionId, () => {
  connectionRequest += 1
  overridesRequest += 1
  connectionReady.value = false
  loading.value = false
  overridesLoading.value = false
  closeEditor()
  page.value = 1
  groups.value = []
  overrides.value = []
  total.value = 0
  identifyChannelId.value = ''
  aliasDraft.value = ''
  defaultPolicy.value = emptyPolicy(connectionId.value)
  defaultDraft.value = completeOverride(defaultPolicy.value)
  if (connectionId.value) void loadCurrentConnection()
}, { flush: 'sync' })
watch(identifyChannelId, id => { aliasDraft.value = groups.value.find(item => item.id === id)?.alias || '' })
watch([page, size], () => { if (connectionId.value && connectionReady.value) void loadOverrides() })
async function loadInitialData() {
  if (initialLoading.value || writing.value || savingMediaSettings.value) return
  initialLoading.value = true
  error.value = ''
  const results = await Promise.allSettled([
    Promise.all([api.connections(), api.aiOptions()]).then(([values, providers]) => { connections.value = values; aiProviders.value = providers; optionsReady.value = true }),
    api.mediaSettings().then(settings => { mediaSettingsDraft.value = { hostDirectory: settings.hostDirectory ?? '', containerDirectory: settings.containerDirectory || '/app/download' }; mediaReady.value = true }),
  ])
  for (const result of results) if (result.status === 'rejected') showError(result.reason, '部分设置读取失败，请重新加载后再保存')
  if (!connectionId.value && connections.value.length === 1) connectionId.value = connections.value[0]!.id
  initialLoading.value = false
}
onMounted(loadInitialData)
</script>

<template>
  <div class="qa-settings-page">
    <YdPageHeader title="群自动化策略" description="按连接维护默认策略，按群设置差异化覆盖；媒体存储为全局配置。">
      <YdButton v-if="!optionsReady || !mediaReady" variant="outline" :loading="initialLoading" @click="loadInitialData">重新加载设置</YdButton>
      <YdButton v-if="activeSection === 'connection'" variant="outline" :loading="loading" :disabled="!connectionId || initialLoading || writing" @click="loadCurrentConnection">重新加载连接</YdButton>
      <YdButton v-else-if="activeSection === 'default'" :loading="savingDefault" :disabled="!connectionReady || !optionsReady || loading || initialLoading" @click="saveDefault">保存默认策略</YdButton>
      <YdButton v-else-if="activeSection === 'overrides'" :disabled="!connectionReady || !optionsReady || loading || initialLoading" @click="openCreate">新增群级覆盖</YdButton>
      <YdButton v-else :loading="savingMediaSettings" :disabled="!mediaReady || initialLoading" @click="saveMediaSettings">保存媒体存储</YdButton>
    </YdPageHeader>
    <div class="qa-settings-notices">
      <YdAlert v-if="error" variant="destructive" title="操作未完成" :description="error" />
      <div v-if="activeSection !== 'media'" class="qa-connection-bar">
        <span class="qa-label">当前连接</span>
        <YdSelect v-model="connectionId" :options="connectionOptions" placeholder="选择 QQ 连接" :disabled="initialLoading || writing || !optionsReady" aria-label="当前 QQ 连接" />
        <YdTag v-if="currentConnection" :variant="currentConnection.protocol === 'official' ? 'default' : 'secondary'">{{ protocolLabel(currentConnection) }}</YdTag>
      </div>
      <YdAlert v-if="connectionId && !connectionReady && !loading && activeSection !== 'media'" variant="destructive" title="当前连接策略尚未读取成功" description="重新加载成功后才能编辑和保存，避免覆盖已有设置。">
        <YdButton size="sm" variant="outline" @click="loadCurrentConnection">重试读取连接</YdButton>
      </YdAlert>
    </div>
    <YdLoadingOverlay :loading="initialLoading || loading || savingDefault || savingMediaSettings">
      <YdSettingsLayout v-model="activeSection" :list="sectionList" surface width-class="max-w-6xl">
        <template #connection>
          <YdSettingsSection title="QQ 连接与群识别" description="上方连接选择器在各策略分区共享。官方群通常不返回群名，可发送识别消息并维护备注。">
            <YdEmpty v-if="!connectionId" description="先选择一个 QQ 连接，再维护策略和群备注。" />
            <template v-else>
              <YdForm v-if="currentConnection?.protocol === 'official'" :model="{ identifyChannelId, aliasDraft }" :disabled="!connectionReady || writing" class="qa-settings-form">
                <div class="qa-form-grid">
                  <YdFormItem label="群 openid"><YdSelect v-model="identifyChannelId" placeholder="选择要识别或备注的群" :options="groupOptions" :disabled="!connectionReady || writing" /></YdFormItem>
                  <YdFormItem label="后台备注"><YdInput v-model="aliasDraft" placeholder="如：运营群 / 告警群" maxlength="40" /></YdFormItem>
                </div>
                <div class="qa-actions">
                  <YdButton variant="outline" :disabled="!identifyChannelId || !connectionReady || savingAlias" :loading="!!identifyingGroupId" @click="identifyGroup(identifyChannelId)">发识别消息</YdButton>
                  <YdButton :disabled="!identifyChannelId || !connectionReady || !!identifyingGroupId" :loading="savingAlias" @click="saveAlias">保存备注</YdButton>
                </div>
              </YdForm>
              <p class="qa-help">{{ currentConnection?.protocol === 'official' ? '官方连接以公网直链发送媒体；入群审批需机器人具备群管理员权限并收到申请事件。风险告警发送到单独指定群。' : 'Milky 连接通过共享目录发送媒体，支持合并转发与语音。' }}</p>
            </template>
          </YdSettingsSection>
        </template>
        <template #default>
          <YdSettingsSection title="连接默认策略" description="未启用自定义覆盖的群级字段，将使用此处配置。">
            <PolicyFieldsForm v-if="connectionId && connectionReady" :base-policy="defaultPolicy" :override="defaultDraft" :ai-providers="aiProviders" :allow-inheritance="false" :disabled="savingDefault || !optionsReady" :user-fetcher="fetchUserOptions" :user-labels="userLabelCache" :group-options="groupOptions" @update:override="defaultDraft = $event" />
            <YdEmpty v-else description="选择连接并成功读取后，即可编辑默认策略。" />
          </YdSettingsSection>
        </template>
        <template #overrides>
          <YdSettingsSection title="群级覆盖" description="仅保存需要差异化的字段；删除覆盖后恢复使用连接默认策略。">
            <div class="qa-group-toolbar">
              <p class="qa-help">每个群可独立处理入群审核、分享链接和风险告警。账号绑定与欢迎消息在「QQ机器人 → 群组管理」配置。</p>
              <YdButton :disabled="!connectionReady || !optionsReady || writing" @click="openCreate">新增群策略</YdButton>
            </div>
            <YdLoadingOverlay :loading="overridesLoading">
              <YdEmpty v-if="!overrides.length && !overridesLoading" description="还没有单独配置的群。新增群策略，为审核群或交流群分配功能。">
                <YdButton :disabled="!connectionReady || !optionsReady" @click="openCreate">配置第一个群</YdButton>
              </YdEmpty>
              <YdResponsiveEnhancedTable v-else row-key="channelId" :actions="rowActions" :columns="columns" :data="overrides">
                <template #cell-group="{ row }"><div class="qa-group-identity"><strong class="qa-long-text">{{ groupName(row.original.channelId) }}</strong><YdTag variant="secondary">{{ row.original.enabled === null ? '继承默认' : row.original.enabled ? '启用' : '停用' }}</YdTag></div></template>
                <template #cell-capabilities="{ row }"><div class="qa-capabilities"><span>媒体 {{ mediaState(row.original) }}</span><span>审核 {{ row.original.websiteApprovalRequired ? '官网准入' : row.original.joinVerificationEnabled === null ? '继承默认' : row.original.joinVerificationEnabled ? '已开启' : '已关闭' }}</span><span>风险 {{ riskState(row.original) }}</span></div></template>
                <template #cell-overrides="{ row }">{{ countOverrides(row.original) }} 项</template>
              </YdResponsiveEnhancedTable>
            </YdLoadingOverlay>
            <YdPagination v-if="total > size" v-model:page="page" v-model:size="size" :total="total" />
          </YdSettingsSection>
        </template>
        <template #media>
          <YdSettingsSection title="媒体存储" description="填写 YuDream 后端容器可读取的目录和解析服务容器目录。官方连接签发公网直链时，后端进程须能读取媒体文件。">
            <YdForm :model="mediaSettingsDraft" :disabled="!mediaReady || savingMediaSettings" class="qa-settings-form">
              <div class="qa-form-grid">
                <YdFormItem label="后端可读目录" extra="填写后端容器看到的路径，例如 /media。"><YdInput v-model="mediaSettingsDraft.hostDirectory" placeholder="/media" clearable /></YdFormItem>
                <YdFormItem label="解析服务容器目录" extra="填写解析服务容器内的挂载路径，例如 /app/download。"><YdInput v-model="mediaSettingsDraft.containerDirectory" placeholder="/app/download" /></YdFormItem>
              </div>
            </YdForm>
          </YdSettingsSection>
        </template>
      </YdSettingsLayout>
    </YdLoadingOverlay>
    <YdDrawer v-model="editorOpen" :title="editorTitle" description="开启字段的「自定义」后设置覆盖值，其余字段继承连接默认策略。" :show-cancel-button="!savingOverride" cancel-button-text="取消" confirm-button-text="保存覆盖" :confirm-button-loading="savingOverride" :confirm-button-disabled="!editorChannelId || !editorReady || editorLoading" :close-on-click-overlay="!savingOverride" :close-on-press-escape="!savingOverride" :before-close="beforeEditorClose" content-class="qqbot-automation-drawer" @cancel="closeEditor" @close="closeEditor">
      <YdAlert v-if="editorError" variant="destructive" title="群级覆盖操作未完成" :description="editorError"><YdButton v-if="!editorReady" size="sm" variant="outline" @click="loadEditor">重试读取</YdButton></YdAlert>
      <YdLoadingOverlay :loading="editorLoading || savingOverride">
        <div class="qa-editor-content">
          <YdForm v-if="!editorChannelId" :model="{ editorChannelId, extraGroupId }" class="qa-settings-form">
            <YdFormItem label="群聊"><YdSelect v-model="editorChannelId" placeholder="选择需要单独配置的群聊" :options="groupOptions" /></YdFormItem>
            <YdFormItem label="补充官方群 openid" extra="选项来自收到过的群消息。无法区分时，可先发送识别消息并写备注。"><div class="qa-inline-input"><YdInput v-model="extraGroupId" placeholder="输入群 openid" @keydown.enter.prevent="addExtraGroup" /><YdButton variant="outline" :disabled="!extraGroupId.trim()" @click="addExtraGroup">添加</YdButton></div></YdFormItem>
          </YdForm>
          <p v-else class="qa-long-text">当前群聊：{{ groupName(editorChannelId) }}</p>
          <div class="qa-actions"><span class="qa-help">快速配置</span><YdButton variant="outline" size="sm" @click="applyPreset('review')">官网审核群</YdButton><YdButton variant="outline" size="sm" @click="applyPreset('chat')">游戏交流群</YdButton></div>
          <PolicyFieldsForm :base-policy="defaultPolicy" :override="editorDraft" :ai-providers="aiProviders" :disabled="!editorReady || editorLoading || savingOverride" :user-fetcher="fetchUserOptions" :user-labels="userLabelCache" :group-options="groupOptions" @update:override="editorDraft = $event" />
        </div>
      </YdLoadingOverlay>
    </YdDrawer>
  </div>
</template>
