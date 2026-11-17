<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  YdAlert, YdButton, YdForm, YdFormItem, YdIcon, YdInput, YdLoadingOverlay,
  YdModal, YdPagination, YdResponsiveEnhancedTable, YdSelect, YdTablePageLayout,
  useYdModal, useYdToast, type StatusColumn, type TableColumn,
} from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { createQqbotAutomationApi } from '../api/qqbot-automation-api'
import { formatGroupLabel, type MediaJob, type Option } from '../types'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const api = createQqbotAutomationApi(props.sdk)
const toast = useYdToast()
const modal = useYdModal()
const jobs = ref<MediaJob[]>([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const loading = ref(false)
const clearing = ref(false)
const error = ref('')
const testOpen = ref(false)
const submittingTest = ref(false)
const optionsLoading = ref(false)
const groupsLoading = ref(false)
const testError = ref('')
const connections = ref<Option[]>([])
const groups = ref<Option[]>([])
const testConnectionId = ref('')
const testChannelId = ref('')
const extraGroupId = ref('')
const testUrl = ref('')
let listRequest = 0
let groupRequest = 0
let disposed = false
const canTest = computed(() => !!testConnectionId.value && !!testChannelId.value && !!testUrl.value.trim() && !optionsLoading.value && !groupsLoading.value && !submittingTest.value)
function protocolLabel(item: Option) { return item.protocol === 'official' ? '官方 QQ' : item.protocol === 'milky' ? 'Milky' : item.platform || '未知平台' }
const connectionOptions = computed(() => connections.value.map(item => ({ label: `${item.name}（${protocolLabel(item)}）`, value: item.id })))
const groupOptions = computed(() => {
  const known = groups.value.map(item => ({ label: formatGroupLabel(item), value: item.id }))
  if (testChannelId.value && !known.some(item => item.value === testChannelId.value)) known.push({ label: formatGroupLabel({ id: testChannelId.value }), value: testChannelId.value })
  return known
})
const columns: TableColumn<MediaJob>[] = [
  { accessorKey: 'sourceUrl', header: '媒体链接', minWidth: 240, fixed: 'left' },
  { accessorKey: 'connectionId', header: '连接', width: 150 },
  { accessorKey: 'channelId', header: '群聊', width: 150 },
  { id: 'trigger', header: '触发方式', width: 120 },
  { id: 'result', header: '结果', minWidth: 180 },
  { accessorKey: 'createdAt', header: '创建时间', width: 180 },
]
const jobStatusColumn: StatusColumn<MediaJob> = {
  statusKey: 'status', header: '状态', width: 110,
  statusMap: {
    QUEUED: { label: '等待中', variant: 'secondary', tone: 'info' },
    PENDING: { label: '等待中', variant: 'secondary', tone: 'info' },
    RUNNING: { label: '处理中', variant: 'default', tone: 'warning' },
    SUCCESS: { label: '已完成', variant: 'default', tone: 'success' },
    COMPLETED: { label: '已完成', variant: 'default', tone: 'success' },
    FAILED: { label: '失败', variant: 'destructive', tone: 'danger' },
    SEND_FAILED: { label: '发送失败', variant: 'destructive', tone: 'danger' },
  },
}
function responseMessage(cause: unknown, fallback: string) {
  if (cause && typeof cause === 'object') {
    const data = (cause as { response?: { data?: unknown } }).response?.data
    if (typeof data === 'string') return data
    if (data && typeof data === 'object') {
      const payload = data as { message?: unknown, error?: unknown, data?: { message?: unknown } }
      if (typeof payload.message === 'string') return payload.message
      if (typeof payload.error === 'string') return payload.error
      if (typeof payload.data?.message === 'string') return payload.data.message
    }
  }
  return cause instanceof Error && cause.message ? cause.message : fallback
}
function showError(cause: unknown, fallback: string) { error.value = responseMessage(cause, fallback); toast.error(error.value) }
function triggerLabel(trigger?: string) { return trigger === 'MANUAL_TEST' ? '手动测试' : trigger === 'EVENT' || !trigger ? '群消息触发' : trigger }
function formatTime(value: number | string | null | undefined) {
  const timestamp = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(timestamp) && timestamp > 0 ? new Date(timestamp).toLocaleString('zh-CN', { hour12: false }) : '—'
}
async function clearJobs() {
  if (clearing.value) return
  clearing.value = true
  try {
    const result = await api.clearMediaJobs()
    toast.success(`已删除 ${result.deleted} 条媒体任务`)
    if (page.value !== 1) page.value = 1
    else await load()
  }
  catch (cause) { showError(cause, '删除媒体任务失败') }
  finally { clearing.value = false }
}
function confirmClearJobs() {
  if (!total.value || clearing.value) return
  modal.confirm({ title: '清空媒体任务', content: `确认删除全部 ${total.value} 条媒体任务吗？该操作不可恢复。`, onConfirm: () => clearJobs() })
}
async function load() {
  const request = ++listRequest
  loading.value = true
  error.value = ''
  try {
    const result = await api.mediaJobs(page.value, size.value)
    if (request !== listRequest || disposed) return
    jobs.value = result.records
    total.value = result.total
  }
  catch (cause) { if (request === listRequest && !disposed) showError(cause, '加载媒体任务失败') }
  finally { if (request === listRequest) loading.value = false }
}
async function loadConnections() {
  optionsLoading.value = true
  testError.value = ''
  try { connections.value = await api.connections() }
  catch (cause) { testError.value = responseMessage(cause, '加载 QQ 连接失败，请重试') }
  finally { optionsLoading.value = false }
}
async function openTest() {
  if (submittingTest.value) return
  testConnectionId.value = ''
  testChannelId.value = ''
  extraGroupId.value = ''
  testUrl.value = ''
  groups.value = []
  testError.value = ''
  testOpen.value = true
  if (!connections.value.length) await loadConnections()
}
async function loadTestGroups() {
  const request = ++groupRequest
  const id = testConnectionId.value
  testChannelId.value = ''
  extraGroupId.value = ''
  groups.value = []
  testError.value = ''
  if (!id) { groupsLoading.value = false; return }
  groupsLoading.value = true
  try {
    const result = await api.groups(id)
    if (request === groupRequest && testOpen.value) groups.value = result
  }
  catch (cause) { if (request === groupRequest) testError.value = responseMessage(cause, '加载群聊列表失败，请重试') }
  finally { if (request === groupRequest) groupsLoading.value = false }
}
function closeTest() {
  groupRequest += 1
  testOpen.value = false
  testConnectionId.value = ''
  testChannelId.value = ''
  extraGroupId.value = ''
  testUrl.value = ''
  testError.value = ''
  groups.value = []
}
function addExtraGroup() {
  const id = extraGroupId.value.trim()
  if (!id || submittingTest.value || groupsLoading.value) return
  testChannelId.value = id
  extraGroupId.value = ''
}
async function monitorTest(id: string) {
  for (let attempt = 0; attempt < 60 && !disposed; attempt += 1) {
    await new Promise(resolve => window.setTimeout(resolve, 1000))
    if (disposed) return
    try {
      const job = await api.mediaJob(id)
      if (!job || ['QUEUED', 'PENDING', 'RUNNING'].includes(job.status)) continue
      await load()
      if (disposed) return
      if (job.status === 'FAILED' || job.status === 'SEND_FAILED') toast.error(job.error || '媒体任务失败')
      else if (job.status === 'COMPLETED' || job.status === 'SUCCESS') toast.success('媒体解析完成')
      return
    }
    catch { return }
  }
}
async function submitTest() {
  if (!canTest.value) return
  submittingTest.value = true
  testError.value = ''
  try {
    const result = await api.startMediaTest({ connectionId: testConnectionId.value, channelId: testChannelId.value, sourceUrl: testUrl.value.trim() })
    toast.success('测试任务已创建，解析完成后将发送到所选 QQ 群')
    closeTest()
    if (page.value !== 1) page.value = 1
    else await load()
    void monitorTest(result.id)
  }
  catch (cause) { testError.value = responseMessage(cause, '创建测试任务失败') }
  finally { submittingTest.value = false }
}
function beforeTestClose(action: 'confirm' | 'cancel' | 'close', done: () => void) {
  if (submittingTest.value) return
  if (action === 'confirm') void submitTest()
  else done()
}
watch([page, size], () => void load())
watch(testConnectionId, () => void loadTestGroups())
onMounted(load)
onBeforeUnmount(() => { disposed = true; listRequest += 1; groupRequest += 1 })
</script>

<template>
  <YdTablePageLayout title="媒体任务" :show-search="false">
    <template #actions>
      <div class="qa-actions">
        <YdButton variant="destructive" :loading="clearing" :disabled="!total || loading" @click="confirmClearJobs"><YdIcon name="i-lucide:trash-2" />清空任务</YdButton>
        <YdButton @click="openTest"><YdIcon name="i-lucide:flask-conical" />发起测试</YdButton>
      </div>
    </template>
    <template #table>
      <YdAlert v-if="error" class="mb-3" variant="destructive" title="任务读取未完成" :description="error"><YdButton variant="outline" size="sm" @click="load">重试读取</YdButton></YdAlert>
      <YdLoadingOverlay :loading="loading || clearing">
        <YdResponsiveEnhancedTable row-key="id" :status-column="jobStatusColumn" :columns="columns" :data="jobs" empty-text="暂无媒体任务">
          <template #cell-sourceUrl="{ row }"><a class="qa-long-text" :href="row.original.sourceUrl" target="_blank" rel="noreferrer">{{ row.original.sourceUrl }}</a></template>
          <template #cell-connectionId="{ row }"><span class="qa-long-text">{{ row.original.connectionId }}</span></template>
          <template #cell-channelId="{ row }"><span class="qa-long-text">{{ row.original.channelId }}</span></template>
          <template #cell-trigger="{ row }">{{ triggerLabel(row.original.trigger) }}</template>
          <template #cell-result="{ row }">
            <div class="qa-field-stack">
              <a v-if="row.original.downloadUrl" :href="row.original.downloadUrl" target="_blank" rel="noreferrer">打开解析结果</a>
              <span v-if="row.original.error" class="qa-long-text">{{ row.original.error }}</span>
              <span v-if="row.original.commentError" class="qa-long-text">评论未发送：{{ row.original.commentError }}</span>
              <span v-if="!row.original.downloadUrl && !row.original.error">—</span>
            </div>
          </template>
          <template #cell-createdAt="{ row }">{{ formatTime(row.original.createdAt) }}</template>
        </YdResponsiveEnhancedTable>
      </YdLoadingOverlay>
    </template>
    <template #footer><YdPagination v-model:page="page" v-model:size="size" :total="total" /></template>
  </YdTablePageLayout>

  <YdModal v-model="testOpen" title="发起媒体解析测试" description="解析链接后，将媒体文件发送到所选 QQ 群。" :show-cancel-button="!submittingTest" cancel-button-text="取消" confirm-button-text="开始测试" :confirm-button-loading="submittingTest" :confirm-button-disabled="!canTest" :close-on-click-overlay="!submittingTest" :close-on-press-escape="!submittingTest" :before-close="beforeTestClose" @cancel="closeTest" @close="closeTest">
    <YdAlert v-if="testError" variant="destructive" title="测试操作未完成" :description="testError" class="mb-3"><YdButton size="sm" variant="outline" @click="testConnectionId ? loadTestGroups() : loadConnections()">重新读取选项</YdButton></YdAlert>
    <YdLoadingOverlay :loading="optionsLoading || groupsLoading || submittingTest">
      <YdForm :model="{ testConnectionId, testChannelId, extraGroupId, testUrl }" :disabled="submittingTest" class="qa-media-test-form">
        <YdFormItem label="QQ 连接"><YdSelect v-model="testConnectionId" placeholder="选择连接" :options="connectionOptions" :disabled="submittingTest" /></YdFormItem>
        <YdFormItem label="群聊"><YdSelect v-model="testChannelId" placeholder="选择群聊" :options="groupOptions" :disabled="!testConnectionId || submittingTest" /></YdFormItem>
        <YdFormItem label="补充官方群 openid" extra="官方群选项来自收到过的群消息。群名缺失时，可到策略页发送识别消息并写备注。"><div class="qa-inline-input"><YdInput v-model="extraGroupId" placeholder="输入官方群 openid" :disabled="!testConnectionId || submittingTest" @keydown.enter.prevent="addExtraGroup" /><YdButton variant="outline" :disabled="!testConnectionId || !extraGroupId.trim() || submittingTest" @click="addExtraGroup">添加</YdButton></div></YdFormItem>
        <YdFormItem label="媒体链接"><YdInput v-model="testUrl" type="url" placeholder="粘贴抖音或 Bilibili 链接" /></YdFormItem>
      </YdForm>
    </YdLoadingOverlay>
  </YdModal>
</template>
