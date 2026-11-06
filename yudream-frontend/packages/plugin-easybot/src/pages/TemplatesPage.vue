<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { YdButton, YdInput, YdPageHeader, YdSelect } from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { createEasyBotApi } from '../api/easybot-api'
import type { ImageTemplateDetail, ImageTemplateSummary } from '../types'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const api = createEasyBotApi(props.sdk)

const records = ref<ImageTemplateSummary[]>([])
const keyword = ref('')
const category = ref('全部')
const loading = ref(false)
const message = ref('')
const detail = ref<ImageTemplateDetail | null>(null)
const previewStamp = ref(Date.now())
const saving = ref(false)

const categories = computed(() => {
  const set = new Set(records.value.map(item => item.category))
  return ['全部', ...Array.from(set)]
})

async function load() {
  loading.value = true
  message.value = ''
  try {
    const data = await api.templates(keyword.value, category.value === '全部' ? '' : category.value)
    records.value = data.records ?? []
  } catch (error) {
    message.value = error instanceof Error ? error.message : '模板列表加载失败'
  } finally {
    loading.value = false
  }
}

async function open(id: string) {
  try {
    detail.value = await api.template(id)
    previewStamp.value = Date.now()
  } catch (error) {
    message.value = error instanceof Error ? error.message : '模板详情加载失败'
  }
}

function close() {
  detail.value = null
}

async function save() {
  if (!detail.value) return
  saving.value = true
  try {
    await api.saveTemplate({
      id: detail.value.id,
      name: detail.value.name,
      version: detail.value.version,
      description: detail.value.description,
      author: detail.value.author,
      category: detail.value.category,
      tags: detail.value.tags,
      html: detail.value.html,
      width: detail.value.width,
    })
    message.value = '模板已保存'
    previewStamp.value = Date.now()
    await load()
  } catch (error) {
    message.value = error instanceof Error ? error.message : '保存失败'
  } finally {
    saving.value = false
  }
}

async function createBlank() {
  try {
    const saved = await api.saveTemplate({
      name: '新模板',
      version: '1.0.0',
      description: '',
      author: '',
      category: '通用',
      tags: [],
      width: 640,
      html: '<!doctype html><html><head><meta charset="utf-8"></head>'
        + '<body style="margin:0;background:#111;color:#fff;font-family:sans-serif">'
        + '<div id="template-root" style="padding:24px;width:640px">{{title}}</div></body></html>',
    })
    await load()
    await open(String(saved.id ?? ''))
  } catch (error) {
    message.value = error instanceof Error ? error.message : '创建失败'
  }
}

async function uploadCover(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !detail.value) return
  try {
    const cover = await api.uploadTemplateCover(detail.value.id, file)
    detail.value.cover = cover
    await load()
    message.value = '封面已更新'
  } catch (error) {
    message.value = error instanceof Error ? error.message : '封面上传失败'
  } finally {
    input.value = ''
  }
}

async function remove(item: ImageTemplateSummary) {
  if (!window.confirm(`确定删除模板「${item.name}」？`)) return
  try {
    await api.deleteTemplate(item.id)
    if (detail.value?.id === item.id) detail.value = null
    await load()
  } catch (error) {
    message.value = error instanceof Error ? error.message : '删除失败'
  }
}

function previewUrl(id: string, stamp: number) {
  return `/api/plugins/easybot/templates/${id}/render?t=${stamp}`
}

onMounted(load)
</script>

<template>
  <div class="easybot-page">
    <YdPageHeader
      title="图片模板"
      description="模板是一段可编辑的 HTML，出图统一走后台「消息渲染」能力；变量用 {{name}} 填写"
    >
      <template #actions>
        <YdButton variant="outline" @click="load">刷新</YdButton>
        <YdButton @click="createBlank">新建模板</YdButton>
      </template>
    </YdPageHeader>

    <div class="easybot-body">
      <p v-if="message" class="easybot-hint">{{ message }}</p>

      <div class="easybot-toolbar">
        <YdInput v-model="keyword" placeholder="搜索名称、描述或标签" @keyup.enter="load" />
        <YdSelect v-model="category" :options="categories.map(item => ({ label: item, value: item }))" />
        <YdButton variant="ghost" @click="load">搜索</YdButton>
      </div>

      <div v-if="loading" class="easybot-empty">加载中…</div>
      <div v-else-if="!records.length" class="easybot-empty">还没有模板，点右上角「新建模板」开始。</div>
      <div v-else class="easybot-template-grid">
        <article v-for="item in records" :key="item.id" class="easybot-template-card">
          <div class="easybot-template-cover" @click="open(item.id)">
            <img v-if="item.cover" :src="item.cover" :alt="item.name">
            <div v-else class="easybot-template-fallback">{{ item.name.slice(0, 2) }}</div>
            <span class="easybot-template-version">v{{ item.version }}</span>
            <span v-if="item.builtin" class="easybot-template-builtin">内置</span>
          </div>
          <div class="easybot-template-body">
            <h3>{{ item.name }}</h3>
            <p>{{ item.description || '暂无描述' }}</p>
            <div class="easybot-template-tags">
              <span v-for="tag in item.tags" :key="tag" class="easybot-chip">{{ tag }}</span>
              <span class="easybot-chip">{{ item.category }}</span>
            </div>
          </div>
          <footer class="easybot-template-foot">
            <span class="easybot-muted">{{ item.author }}</span>
            <div class="easybot-template-actions">
              <a :href="previewUrl(item.id, previewStamp)" target="_blank" rel="noreferrer">预览</a>
              <button type="button" @click="open(item.id)">编辑</button>
              <button type="button" class="danger" @click="remove(item)">删除</button>
            </div>
          </footer>
        </article>
      </div>

      <section v-if="detail" class="easybot-template-editor">
        <header class="easybot-template-editor-head">
          <div>
            <strong>{{ detail.name }}</strong>
            <span class="easybot-muted"> · v{{ detail.version }} · {{ detail.width }}px 宽</span>
          </div>
          <div class="easybot-template-actions">
            <label class="easybot-upload">上传封面
              <input type="file" accept="image/png,image/jpeg,image/svg+xml" @change="uploadCover">
            </label>
            <YdButton variant="ghost" @click="close">关闭</YdButton>
            <YdButton :loading="saving" @click="save">保存</YdButton>
          </div>
        </header>

        <div class="easybot-template-editor-body">
          <div class="easybot-template-fields">
            <label>名称 <YdInput v-model="detail.name" /></label>
            <label>版本 <YdInput v-model="detail.version" /></label>
            <label>分类 <YdInput v-model="detail.category" /></label>
            <label>作者 <YdInput v-model="detail.author" /></label>
            <label class="wide">描述 <YdInput v-model="detail.description" /></label>
            <label v-if="detail.variables.length" class="wide">
              变量：<code v-for="name in detail.variables" :key="name">{{ name }}</code>
            </label>
          </div>
          <div class="easybot-template-preview">
            <img :key="previewStamp" :src="previewUrl(detail.id, previewStamp)" alt="模板预览">
            <span class="easybot-muted">保存后用示例变量出图</span>
          </div>
        </div>

        <textarea v-model="detail.html" class="easybot-template-code" spellcheck="false" />
      </section>
    </div>
  </div>
</template>
