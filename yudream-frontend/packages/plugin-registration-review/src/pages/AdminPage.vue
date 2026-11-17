<script setup lang="ts">
import type { RegistrationReviewApi } from '../api/registration-review-api'
import type { ReviewApplication } from '../types'
import { YdButton, YdDrawer, YdInput, YdLoadingOverlay, YdPageHeader, YdPagination, YdResponsiveEnhancedTable, YdTag, useYdToast, type ActionMenuItem, type TableColumn } from '@yudream/components'
import { onMounted, ref, watch } from 'vue'

const props = defineProps<{ model: { api: RegistrationReviewApi } }>()
const api = props.model.api
const toast = useYdToast()
const loading = ref(false)
const status = ref('')
const keyword = ref('')
const applications = ref<ReviewApplication[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const rejecting = ref<ReviewApplication | null>(null)
const rejectOpen = ref(false)
const rejectReason = ref('')
const saving = ref(false)
let request = 0
const columns: TableColumn<ReviewApplication>[] = [
  { id: 'applicant', header: '申请人', minWidth: 140 },
  { id: 'status', header: '状态', width: 100 },
  { id: 'score', header: '成绩', width: 130 },
  { id: 'time', header: '申请时间', minWidth: 160 },
  { accessorKey: 'message', header: '消息', minWidth: 160 },
]
const actions: ActionMenuItem<ReviewApplication>[] = [
  { label: '通过', visible: row => row.status === 'passed', disabled: () => saving.value, onClick: approve },
  { label: '先完成题库判分', visible: row => row.status === 'questionnaire_pending_review', onClick: () => { window.location.assign('/platform/plugins/questionbank/admin/review') } },
  { label: '拒绝', danger: true, visible: row => ['passed', 'questionnaire_pending_review'].includes(row.status), disabled: () => saving.value, onClick: row => { rejecting.value = row; rejectReason.value = ''; rejectOpen.value = true } },
]

function statusLabel(value: string) { return ({ questionnaire_ongoing: '答题中', questionnaire_pending_review: '待判分', passed: '待确认', failed: '未通过', approved: '已通过', rejected: '已拒绝' } as Record<string, string>)[value] ?? value }
function statusVariant(value: string) { return ['passed', 'approved'].includes(value) ? 'default' : ['failed', 'rejected'].includes(value) ? 'secondary' : 'outline' }
function formatTime(value: number) { return value ? new Date(Number(value)).toLocaleString() : '' }

async function load() {
  const current = ++request
  loading.value = true
  try {
    const result = await api.adminApplications(status.value, keyword.value, page.value, size.value)
    if (current !== request) return
    applications.value = result.records ?? []
    total.value = result.total ?? 0
  }
  catch (error) { if (current === request) toast.error(String((error as Error)?.message ?? '申请列表加载失败')) }
  finally { if (current === request) loading.value = false }
}
async function approve(application: ReviewApplication) {
  if (saving.value) return
  saving.value = true
  try { await api.approve(application.id); toast.success('申请已通过'); await load() }
  catch (error) { toast.error(String((error as Error)?.message ?? '通过失败')) }
  finally { saving.value = false }
}
async function reject() {
  if (!rejecting.value || saving.value) return
  saving.value = true
  try { await api.reject(rejecting.value.id, rejectReason.value); rejectOpen.value = false; toast.success('申请已拒绝'); await load() }
  catch (error) { toast.error(String((error as Error)?.message ?? '拒绝失败')) }
  finally { saving.value = false }
}
watch([status, keyword, size], () => { if (page.value !== 1) page.value = 1; else void load() })
watch(page, () => { void load() })
onMounted(load)
</script>

<template>
  <div class="p-4 flex flex-col gap-4">
    <YdPageHeader title="注册审核申请" description="查看问卷结果，并对达到及格线或等待简答判分的申请做最终确认。" />
    <div class="flex flex-wrap items-center gap-2">
      <YdInput v-model="keyword" placeholder="搜索用户" class="max-w-64" />
      <YdButton v-for="item in [{ value: '', label: '全部' }, { value: 'passed', label: '待确认' }, { value: 'questionnaire_pending_review', label: '待判分' }, { value: 'approved', label: '已通过' }, { value: 'rejected', label: '已拒绝' }]" :key="item.value" size="sm" :variant="status === item.value ? 'default' : 'outline'" @click="status = item.value">{{ item.label }}</YdButton>
    </div>
    <YdLoadingOverlay :loading="loading">
      <YdResponsiveEnhancedTable row-key="id" :data="applications" :columns="columns" :actions="actions">
        <template #cell-applicant="{ row }">{{ row.original.userLabel || row.original.userId }}</template>
        <template #cell-status="{ row }"><YdTag :variant="statusVariant(row.original.status)">{{ statusLabel(row.original.status) }}</YdTag></template>
        <template #cell-score="{ row }">{{ row.original.scorePercent }}% · {{ row.original.correctCount }}/{{ row.original.totalCount }}</template>
        <template #cell-time="{ row }">{{ formatTime(row.original.createdAt) }}</template>
      </YdResponsiveEnhancedTable>
    </YdLoadingOverlay>
    <YdPagination v-if="total > size" v-model:page="page" v-model:size="size" :total="total" />
    <YdDrawer v-model="rejectOpen" title="拒绝申请">
      <p class="mb-4">申请人：{{ rejecting?.userLabel || rejecting?.userId }}</p>
      <YdInput v-model="rejectReason" placeholder="填写拒绝原因" aria-label="拒绝原因" />
      <template #footer><YdButton variant="outline" :disabled="saving" @click="rejectOpen = false">取消</YdButton><YdButton :loading="saving" @click="reject">确认拒绝</YdButton></template>
    </YdDrawer>
  </div>
</template>
