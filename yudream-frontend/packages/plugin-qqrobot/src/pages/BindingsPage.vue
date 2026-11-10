<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import {
  YdAlert,
  YdButton,
  YdInput,
  YdPageHeader,
  YdPagination,
  YdResponsiveTable,
  useYdToast,
} from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { computed, onMounted, ref } from 'vue'
import { createQqRobotApi } from '../api/qqrobot-api'
import type { BindingRecord, PendingCode } from '../types'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const api = createQqRobotApi(props.sdk)
const toast = useYdToast()

const records = ref<BindingRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const loading = ref(false)
const error = ref('')
const issuing = ref(false)
const issuingPlayer = ref('')
const issued = ref<PendingCode | null>(null)

const columns: TableColumn<BindingRecord>[] = [
  { accessorKey: 'playerName', header: '游戏角色' },
  { accessorKey: 'socialName', header: '社交昵称' },
  { accessorKey: 'socialId', header: '社交账号' },
  { accessorKey: 'serverId', header: '服务器' },
  { accessorKey: 'boundAt', header: '绑定时间' },
  { id: 'actions', header: '管理' },
]

const keywordHint = computed(() => keyword.value.trim() ? '按社交账号筛选' : '全部绑定')

function errorMessage(value: unknown) {
  const text = String((value as Error)?.message ?? value ?? '')
  return text || '请求失败'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await api.bindings(page.value, size.value, keyword.value.trim())
    records.value = result.records
    total.value = result.total
  }
  catch (exception) {
    error.value = errorMessage(exception)
  }
  finally {
    loading.value = false
  }
}

async function issue() {
  const player = issuingPlayer.value.trim()
  if (!player) {
    toast.error('请填写游戏角色名')
    return
  }
  issuing.value = true
  try {
    issued.value = await api.issueBindingCode(player)
    toast.success(`已生成 ${player} 的绑定码`)
    issuingPlayer.value = ''
  }
  catch (exception) {
    toast.error(errorMessage(exception))
  }
  finally {
    issuing.value = false
  }
}

async function unbind(row: BindingRecord) {
  try {
    await api.deleteBinding(row.socialId, row.playerName)
    toast.success(`已解除 ${row.playerName} 的绑定`)
    await load()
  }
  catch (exception) {
    toast.error(errorMessage(exception))
  }
}

function relative(value?: string) {
  if (!value)
    return '-'
  const time = new Date(value)
  if (Number.isNaN(time.getTime()))
    return value
  return time.toLocaleString('zh-CN', { hour12: false })
}

function expiresIn(value?: string) {
  if (!value)
    return ''
  const seconds = Math.max(0, Math.round((new Date(value).getTime() - Date.now()) / 1000))
  if (seconds <= 0)
    return '已过期'
  if (seconds < 60)
    return `${seconds} 秒后过期`
  return `${Math.ceil(seconds / 60)} 分钟后过期`
}

function resetSearch() {
  keyword.value = ''
  page.value = 1
  load()
}

onMounted(load)
</script>

<template>
  <div class="qqrobot-page">
    <YdPageHeader title="绑定管理" description="查看社交账号与游戏角色的绑定关系，或为指定角色直接签发绑定码">
      <template #actions>
        <YdInput v-model="keyword" class="qqrobot-search" placeholder="按社交账号筛选" @keyup.enter="page = 1; load()" />
        <YdButton variant="outline" @click="page = 1; load()">筛选</YdButton>
        <YdButton v-if="keyword" variant="ghost" @click="resetSearch">清除</YdButton>
      </template>
    </YdPageHeader>
    <div class="qqrobot-body">
      <YdAlert v-if="error" title="加载失败" :description="error" />

      <section class="qqrobot-doc-section">
        <header class="qqrobot-cmd-section-head">
          <div>
            <h3>直接签发绑定码</h3>
            <p>给指定游戏角色生成一次性验证码，玩家在 QQ 私聊或群聊发送该验证码即可完成绑定。</p>
          </div>
        </header>
        <div class="qqrobot-inline qqrobot-issue-row">
          <YdInput v-model="issuingPlayer" placeholder="游戏角色名，例如 Steve" @keyup.enter="issue" />
          <YdButton :loading="issuing" @click="issue">生成绑定码</YdButton>
        </div>
        <div v-if="issued" class="qqrobot-issue-result">
          <span class="qqrobot-muted">绑定码</span>
          <code>{{ issued.code }}</code>
          <span class="qqrobot-chip ghost">{{ expiresIn(issued.expiresAt) }}</span>
        </div>
      </section>

      <section class="qqrobot-doc-section">
        <header class="qqrobot-cmd-section-head">
          <div>
            <h3>绑定关系 <span class="qqrobot-count">{{ total }} 条 · {{ keywordHint }}</span></h3>
            <p>解绑后该社交账号需要重新获取验证码；已绑定角色不会自动从游戏内移除。</p>
          </div>
        </header>
        <YdResponsiveTable :columns="columns" :data="records" row-key="id" :loading="loading"
          empty-text="还没有绑定记录。玩家在 QQ 里完成绑定后会出现在这里。">
          <template #cell-serverId="{ row }">
            <span class="qqrobot-muted">{{ (row as any).original?.serverId || '不限' }}</span>
          </template>
          <template #cell-boundAt="{ row }">
            <span class="qqrobot-muted">{{ relative((row as any).original?.boundAt) }}</span>
          </template>
          <template #cell-actions="{ row }">
            <YdButton size="sm" variant="outline" class="qqrobot-danger"
              @click="unbind((row as any).original)">解绑</YdButton>
          </template>
          <template #card="{ row }">
            <div class="qqrobot-binding-card">
              <strong>{{ (row as any).original?.playerName }}</strong>
              <span class="qqrobot-muted">{{ (row as any).original?.socialName || (row as any).original?.socialId }}</span>
              <span class="qqrobot-muted">{{ relative((row as any).original?.boundAt) }}</span>
              <YdButton size="sm" variant="outline" class="qqrobot-danger"
                @click="unbind((row as any).original)">解绑</YdButton>
            </div>
          </template>
        </YdResponsiveTable>
        <div v-if="total > size" class="qqrobot-pager">
          <YdPagination v-model:page="page" v-model:size="size" :total="total" :sizes="[10, 20, 50]"
            @page-change="load" @size-change="load" />
        </div>
      </section>
    </div>
  </div>
</template>
