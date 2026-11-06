<script setup lang="ts">
import {
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
import { onMounted, ref } from 'vue'
import { createEasyBotApi } from '../api/easybot-api'
import { emptyGroup, type ConnectionOption, type GroupSettings } from '../types'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const api = createEasyBotApi(props.sdk)
const toast = useYdToast()
const modal = useYdModal()
const groups = ref<GroupSettings[]>([])
const connections = ref<ConnectionOption[]>([])
const connectionId = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const loading = ref(false)
const editorOpen = ref(false)
const draft = ref<GroupSettings>(emptyGroup())

async function load() {
  loading.value = true
  try {
    const result = await api.groups(page.value, size.value)
    groups.value = result.records
    total.value = result.total
  }
  catch (error) {
    toast.error(String((error as Error)?.message ?? '加载群组失败'))
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
  draft.value = emptyGroup()
  editorOpen.value = true
}

function openEdit(group: GroupSettings) {
  draft.value = { ...group }
  editorOpen.value = true
}

async function save() {
  if (!draft.value.groupId.trim()) {
    toast.error('请填写群号')
    return
  }
  try {
    await api.saveGroup(draft.value)
    toast.success('群组策略已保存')
    editorOpen.value = false
    await load()
  }
  catch (error) {
    toast.error(String((error as Error)?.message ?? '保存失败'))
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

onMounted(async () => {
  await Promise.all([load(), loadConnections()])
})
</script>

<template>
  <div class="easybot-page">
    <YdPageHeader title="群组管理" description="按群配置绑定、群名片与退群提示；未配置的群继承全局默认">
      <template #actions>
        <YdSelect v-model="connectionId" class="easybot-connection"
          :options="connections.map(item => ({ label: item.name, value: item.id }))"
          placeholder="选择消息连接" />
        <YdButton @click="openCreate">新增群组</YdButton>
      </template>
    </YdPageHeader>
    <div class="easybot-body">
      <div v-if="!groups.length && !loading" class="easybot-empty">
        <YdEmpty description="还没有群组策略，新增一个群号即可开始。" />
      </div>
      <div v-else class="easybot-group-list">
        <article v-for="group in groups" :key="group.groupId" class="easybot-group-card">
          <div class="easybot-group-main">
            <div class="easybot-group-title">{{ displayName(group) }}</div>
            <div class="easybot-muted">群号 {{ group.groupId }}</div>
            <div class="easybot-tags">
              <span :class="['easybot-chip', group.enabled ? 'on' : 'off']">{{ group.enabled ? '启用' : '停用' }}</span>
              <span v-if="group.bindEnabled" class="easybot-chip on">绑定</span>
              <span v-if="group.cardEnabled" class="easybot-chip on">群名片</span>
              <span v-if="group.quitNoticeEnabled" class="easybot-chip on">退群提示</span>
            </div>
          </div>
          <div class="easybot-group-actions">
            <YdButton variant="outline" size="sm" @click="openEdit(group)">编辑</YdButton>
            <YdButton variant="outline" size="sm" class="easybot-danger" @click="remove(group)">删除</YdButton>
          </div>
        </article>
      </div>
      <div class="easybot-pager">
        <YdPagination v-model:page="page" v-model:size="size" :total="total" :sizes="[10, 20, 50]"
          @page-change="load" @size-change="load" />
      </div>
    </div>

    <YdDrawer v-model="editorOpen" :title="draft.groupId ? `群组：${draft.groupId}` : '新增群组'">
      <div class="easybot-grid">
        <label class="easybot-field">
          <span>群号</span>
          <YdInput v-model="draft.groupId" :disabled="Boolean(draft.groupId && groups.some(item => item.groupId === draft.groupId))" placeholder="例如 123456789" />
        </label>
        <label class="easybot-field">
          <span>显示名称</span>
          <YdInput v-model="draft.displayName" placeholder="聊天大厅" />
        </label>
        <label class="easybot-field easybot-switch"><span>启用该群策略</span><YdSwitch v-model="draft.enabled" /></label>
        <label class="easybot-field easybot-switch"><span>允许群内绑定</span><YdSwitch v-model="draft.bindEnabled" /></label>
        <label class="easybot-field easybot-switch"><span>同步群名片</span><YdSwitch v-model="draft.cardEnabled" /></label>
        <label class="easybot-field easybot-switch"><span>退群提示</span><YdSwitch v-model="draft.quitNoticeEnabled" /></label>
        <label class="easybot-field easybot-wide">
          <span>退群消息（#name / #group）</span>
          <YdTextarea v-model="draft.quitMessage" />
        </label>
        <label class="easybot-field easybot-wide">
          <span>群名片格式</span>
          <YdInput v-model="draft.cardFormat" />
        </label>
        <label class="easybot-field">
          <span>群名片最大长度</span>
          <YdNumberField v-model="draft.cardMaxLength" :min="1" :max="64" />
        </label>
      </div>
      <template #footer>
        <div class="easybot-drawer-actions">
          <YdButton variant="outline" @click="editorOpen = false">取消</YdButton>
          <YdButton @click="save">保存</YdButton>
        </div>
      </template>
    </YdDrawer>
  </div>
</template>
