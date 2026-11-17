<script setup lang="ts">
import { YdAlert, YdButton, YdPageHeader, YdResponsiveTable, YdPagination, useYdModal } from '@yudream/components'
import type { TableColumn } from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { ref, onMounted } from 'vue'
import { schematicApi } from '../api/schematic-api'
import { statusName } from '../types'
import type { Job } from '../types'
const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const api = schematicApi(props.sdk), modal = useYdModal()
const records = ref<Job[]>([]), page = ref(1), total = ref(0), loading = ref(false), error = ref('')
const columns: TableColumn<Job>[] = [{ accessorKey: 'filename', header: '文件' }, { accessorKey: 'status', header: '状态' }, { accessorKey: 'submittedAt', header: '提交时间' }, { id: 'actions', header: '操作' }]
async function load() { loading.value = true; error.value = ''; try { const result = await api.admin(page.value); records.value = result.records; total.value = result.total } catch (e) { error.value = (e as Error).message } finally { loading.value = false } }
async function remove(job: Job) { if (!(await modal.confirm({ title: '删除投影任务', content: `删除“${job.filename}”及预览并撤销分享码？` }))) return; try { await api.adminRemove(job.id); if (records.value.length === 1 && page.value > 1) page.value--; await load() } catch (e) { error.value = (e as Error).message } }
onMounted(load)
</script>
<template><div class="schematic-page"><YdPageHeader title="投影任务管理" description="管理建筑预览及分享码。" /><YdAlert v-if="error" variant="destructive">{{ error }}</YdAlert><YdButton variant="outline" :loading="loading" @click="load">刷新任务</YdButton><YdResponsiveTable :columns="columns" :data="records" row-key="id" :loading="loading">
  <template #cell-status="{ row }">{{ statusName(row.original.status) }}</template>
  <template #cell-actions="{ row }"><YdButton variant="ghost" :disabled="['QUEUED', 'RENDERING'].includes(row.original.status)" @click="remove(row.original)">删除</YdButton></template>
  <template #card="{ row }"><div class="schematic-history"><div><strong>{{ row.filename }}</strong><span>{{ statusName(row.status) }}</span></div><YdButton variant="ghost" :disabled="['QUEUED', 'RENDERING'].includes(row.status)" @click="remove(row)">删除</YdButton></div></template>
</YdResponsiveTable><YdPagination v-if="total > 12" v-model:page="page" :size="12" :total="total" @page-change="load" /></div></template>
