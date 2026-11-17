<script setup lang="ts">
import { YdAlert, YdButton, YdFileUpload, YdInput, YdPageHeader, YdPagination, YdEmpty, useYdModal, useYdToast } from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { schematicApi } from '../api/schematic-api'
import type { Job } from '../types'
import { statusName } from '../types'
import ProjectionResult from '../components/ProjectionResult.vue'
const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const api = schematicApi(props.sdk), modal = useYdModal(), toast = useYdToast()
const jobs = ref<Job[]>([]), current = ref<Job | null>(null), page = ref(1), total = ref(0), error = ref(''), shareCode = ref(''), busy = ref(false), loading = ref(false)
const files = ref<Array<{ name: string; size: number; url?: string }>>([])
let timer: ReturnType<typeof setTimeout> | undefined, disposed = false, generation = 0
async function load() {
  loading.value = true
  try { const result = await api.mine(page.value); if (!disposed) { jobs.value = result.records; total.value = result.total } }
  catch (e) { if (!disposed) error.value = (e as Error).message }
  finally { loading.value = false }
}
async function poll(id: string, revision: number) {
  try {
    const job = await api.detail(id)
    if (disposed || revision !== generation) return
    current.value = job
    if (job.status === 'QUEUED' || job.status === 'RENDERING') timer = setTimeout(() => void poll(id, revision), 2000)
    else { busy.value = false; await load() }
  } catch (e) { if (!disposed) { busy.value = false; error.value = (e as Error).message } }
}
async function upload({ file }: { file: File }) {
  error.value = ''; busy.value = true
  try { const job = await api.upload(file); current.value = job; page.value = 1; generation++; void poll(job.id, generation); return { url: api.preview(job) } }
  catch (e) { busy.value = false; error.value = (e as Error).message; throw e }
}
function validate(file: File) { const valid = file.size > 0 && file.size <= 16 * 1024 * 1024; if (!valid) error.value = '请选择 16 MiB 以内的 NBT 投影；后端按实际内容识别格式'; return valid }
async function shared() { error.value = ''; try { const job = await api.shared(shareCode.value); generation++; clearTimeout(timer); current.value = job } catch (e) { error.value = (e as Error).message } }
async function select(job: Job) { if (busy.value) return; current.value = job; generation++; clearTimeout(timer); if (job.status === 'QUEUED' || job.status === 'RENDERING') { busy.value = true; void poll(job.id, generation) } }
async function remove(job: Job) { if (!(await modal.confirm({ title: '删除建筑投影', content: `删除“${job.name || job.filename}”并撤销分享码？` }))) return; try { await api.remove(job.id); if (current.value?.id === job.id) current.value = null; if (jobs.value.length === 1 && page.value > 1) page.value--; toast.success('已删除'); await load() } catch (e) { error.value = (e as Error).message } }
onMounted(load)
onBeforeUnmount(() => { disposed = true; generation++; clearTimeout(timer) })
</script>
<template>
  <div class="schematic-page">
    <YdPageHeader title="建筑投影" description="上传投影，查看建筑的两个角度。群内结果也能凭分享码在这里查阅。" />
    <YdAlert v-if="error" variant="destructive">{{ error }}</YdAlert>
    <section class="schematic-panel">
      <h2>生成建筑预览</h2>
      <YdFileUpload v-model="files" :disabled="busy" :before-upload="validate" :http-request="upload" :after-upload="value => value.url" description="拖入 Litematic 或 Java 结构 NBT，名称与后缀不限，最大 16 MiB" />
      <p class="schematic-muted">默认导出透明双视角 PNG。每次处理一个投影，最多 35 万非空气方块。</p>
      <div class="schematic-toolbar"><YdInput v-model="shareCode" placeholder="粘贴群回复中的工具箱分享码" /><YdButton :disabled="busy || !shareCode.trim()" @click="shared">查看分享</YdButton></div>
    </section>
    <ProjectionResult v-if="current" :job="current" :preview-url="api.preview(current)" />
    <section class="schematic-panel">
      <div class="schematic-toolbar"><h2>我的投影</h2><YdButton variant="outline" :loading="loading" @click="load">刷新</YdButton></div>
      <YdEmpty v-if="!loading && !jobs.length" description="还没有投影。绑定相同账号后，群文件的渲染结果也会出现在这里。" />
      <div v-for="job in jobs" :key="job.id" class="schematic-history"><div><strong>{{ job.name || job.filename }}</strong><span>{{ statusName(job.status) }}</span></div><div><YdButton variant="ghost" :disabled="busy" @click="select(job)">查看</YdButton><YdButton variant="ghost" :disabled="job.status === 'QUEUED' || job.status === 'RENDERING'" @click="remove(job)">删除</YdButton></div></div>
      <YdPagination v-if="total > 12" v-model:page="page" :size="12" :total="total" @page-change="load" />
    </section>
  </div>
</template>
