<script setup lang="ts">
import type { RegistrationReviewApi } from '../api/registration-review-api'
import type { ReviewApplication, ReviewMeta } from '../types'
import { YdButton, YdPageHeader, YdTag, useYdToast } from '@yudream/components'
import { computed, onMounted, ref } from 'vue'

const props = defineProps<{ model: { api: RegistrationReviewApi } }>()
const api = props.model.api
const toast = useYdToast()
const loading = ref(false)
const starting = ref(false)
const meta = ref<ReviewMeta | null>(null)
const applications = ref<ReviewApplication[]>([])

const canStart = computed(() => meta.value?.enabled === true && meta.value.questionbankAvailable === true)
const active = computed(() => applications.value.find(item => ['questionnaire_ongoing', 'questionnaire_pending_review', 'passed'].includes(item.status)))

function statusLabel(status: string) {
  return ({ questionnaire_ongoing: '答题中', questionnaire_pending_review: '待判分', passed: '待管理员确认', failed: '未通过', approved: '已通过', rejected: '已拒绝' } as Record<string, string>)[status] ?? status
}
function statusVariant(status: string) {
  return ['passed', 'approved'].includes(status) ? 'default' : ['failed', 'rejected'].includes(status) ? 'secondary' : 'outline'
}
function formatTime(value: number) { return value ? new Date(Number(value)).toLocaleString() : '' }

async function load() {
  loading.value = true
  try {
    const [loadedMeta, mine] = await Promise.all([api.meta(), api.myApplications()])
    meta.value = loadedMeta
    applications.value = mine
  }
  catch (error) { toast.error(String((error as Error)?.message ?? '注册审核加载失败')) }
  finally { loading.value = false }
}

async function start(method = 'quiz') {
  starting.value = true
  try {
    const application = await api.start(method)
    toast.success(application.mode === 'triple' ? '三连核验已提交，等待管理员确认' : '已创建问卷申请，请完成作答')
    await load()
    if (application.questionBankSessionId) window.location.href = application.questionBankSessionPath
  }
  catch (error) {
    const body = (error as { response?: { data?: { message?: string } } })?.response?.data
    toast.error(body?.message ?? String((error as Error)?.message ?? '无法开始问卷'))
  }
  finally { starting.value = false }
}

async function refresh(application: ReviewApplication) {
  try {
    const updated = await api.refresh(application.id)
    applications.value = applications.value.map(item => item.id === updated.id ? updated : item)
    toast.success(updated.message || '状态已刷新')
  }
  catch (error) { toast.error(String((error as Error)?.message ?? '刷新失败')) }
}

function openQuestionnaire(application: ReviewApplication) { window.location.href = application.questionBankSessionPath }

onMounted(load)
</script>

<template>
  <div class="p-4 flex flex-col gap-4">
    <YdPageHeader title="注册审核" description="完成题库问卷后，系统按配置的及格线生成审核申请；管理员确认后才算通过。" />
    <div v-if="loading" class="rr-card rr-empty">加载中…</div>
    <template v-else>
      <div v-if="!meta?.enabled" class="rr-card rr-muted">当前未开放注册审核，请联系管理员。</div>
      <div v-else-if="!meta?.questionbankAvailable && meta?.mode !== 'triple' && meta?.mode !== 'both'" class="rr-card rr-muted">题库插件未启用，当前无法开始问卷。请管理员先启用 questionbank。</div>
      <div v-else class="rr-card">
        <div class="rr-title">问卷审核规则</div>
        <div class="rr-meta">
          <YdTag variant="outline">抽 {{ meta?.drawCount }} 题</YdTag>
          <YdTag variant="outline">及格线 {{ meta?.passingPercent }}%</YdTag>
          <span>问卷完成后等待管理员确认</span>
        </div>
        <p class="rr-muted mt-3">题目和作答页面由 questionbank 提供。本插件记录申请状态，不复制题目与判分逻辑。</p>
        <div class="mt-4 flex items-center gap-2">
          <YdButton v-if="meta?.mode !== 'triple'" :loading="starting" :disabled="Boolean(active) || !meta?.questionbankAvailable" @click="start('quiz')">{{ meta?.mode === 'triple_quiz' ? '三连通过后开始问卷' : '开始问卷' }}</YdButton>
          <YdButton v-if="meta?.mode === 'triple' || meta?.mode === 'both'" :disabled="starting || Boolean(active) || !meta?.bilibiliIntegration" @click="start('triple')">提交三连准入</YdButton>
          <a v-if="meta?.mode !== 'quiz'" href="/platform/plugins/bilibili-triple">前往 B站三连核验</a>
          <span v-if="active" class="rr-muted">已有进行中的申请，请先完成或刷新当前申请。</span>
        </div>
      </div>

      <div class="rr-card">
        <div class="rr-title mb-3">我的审核申请</div>
        <div v-if="applications.length === 0" class="rr-empty">还没有申请记录</div>
        <div v-else class="flex flex-col gap-3">
          <div v-for="application in applications" :key="application.id" class="rounded-md border p-3">
            <div class="flex items-start justify-between gap-3">
              <span class="rr-title">第 {{ application.attemptNumber }} 次申请</span>
              <YdTag :variant="statusVariant(application.status)">{{ statusLabel(application.status) }}</YdTag>
            </div>
            <div class="rr-meta">
              <span>{{ application.correctCount }} / {{ application.totalCount }} 题</span>
              <span>得分 {{ application.scorePercent }}%</span>
              <span>{{ formatTime(application.createdAt) }}</span>
            </div>
            <p class="rr-muted mt-2">{{ application.message }}</p>
            <div class="mt-2 flex gap-2">
              <YdButton v-if="application.status === 'questionnaire_ongoing'" size="sm" @click="openQuestionnaire(application)">继续答题</YdButton>
              <YdButton v-if="['questionnaire_ongoing', 'questionnaire_pending_review', 'passed'].includes(application.status)" size="sm" variant="outline" @click="refresh(application)">刷新状态</YdButton>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
