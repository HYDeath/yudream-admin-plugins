<script setup lang="ts">
import {
  YdButton,
  YdDrawer,
  YdEmpty,
  YdInput,
  YdLoadingOverlay,
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
import { createQqRobotApi } from '../api/qqrobot-api'
import { useRenderStatus } from '../composables/use-render-status'
import type { ResourceCard } from '../types'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const api = createQqRobotApi(props.sdk)
const { status: renderStatus, check: checkRender } = useRenderStatus(props.sdk)
const toast = useYdToast()
const modal = useYdModal()
const cards = ref<ResourceCard[]>([])
const page = ref(1)
const size = ref(8)
const total = ref(0)
const keyword = ref('')
const loading = ref(false)
const editorOpen = ref(false)
const saving = ref(false)
const draft = ref<Partial<ResourceCard>>(emptyCard())
const brokenCovers = ref<Record<string, boolean>>({})

function emptyCard(): Partial<ResourceCard> {
  return {
    title: '',
    subtitle: '',
    description: '',
    author: '官方',
    free: true,
    platforms: ['Java'],
    tags: [],
    status: 'published',
  }
}

async function load() {
  loading.value = true
  try {
    const result = await api.cards(page.value, size.value, keyword.value)
    cards.value = result.records
    total.value = result.total
  }
  catch (error) {
    toast.error(String((error as Error)?.message ?? '加载资源失败'))
  }
  finally {
    loading.value = false
  }
}

function openCreate() {
  draft.value = emptyCard()
  editorOpen.value = true
}

function openEdit(card: ResourceCard) {
  draft.value = { ...card, tags: [...card.tags], platforms: [...card.platforms] }
  editorOpen.value = true
}

async function save() {
  if (!String(draft.value.title ?? '').trim()) {
    toast.error('请填写资源标题')
    return
  }
  saving.value = true
  try {
    await api.saveCard(draft.value)
    toast.success('资源已保存，卡片将在首次访问时重新渲染')
    editorOpen.value = false
    await load()
  }
  catch (error) {
    toast.error(String((error as Error)?.message ?? '保存失败'))
  }
  finally {
    saving.value = false
  }
}

async function remove(card: ResourceCard) {
  const confirmed = await modal.confirm({
    title: '删除资源',
    content: `确定删除「${card.title}」吗？该操作不可撤销。`,
    confirmButtonText: '删除',
  })
  if (!confirmed) return
  try {
    await api.deleteCard(card.id)
    toast.success('已删除')
    await load()
  }
  catch (error) {
    toast.error(String((error as Error)?.message ?? '删除失败'))
  }
}

async function refresh(card: ResourceCard) {
  try {
    await api.refreshCard(card.id)
    const next = { ...brokenCovers.value }
    delete next[card.id]
    brokenCovers.value = next
    toast.success('已清除缓存，卡片将在下次访问时重新出图')
  }
  catch (error) {
    toast.error(String((error as Error)?.message ?? '刷新失败'))
  }
}

function cardImage(card: ResourceCard) {
  const version = String(card.updatedAt ?? '').replace(/\D/g, '').slice(0, 14) || Date.now()
  return `${card.cardUrl || `/api/plugins/qqrobot/resources/${card.id}/card.png`}?v=${version}`
}

function coverFailed(card: ResourceCard) {
  brokenCovers.value = { ...brokenCovers.value, [card.id]: true }
}

function coverFallback(card: ResourceCard) {
  return card.coverUrl || ''
}

function parseList(value: unknown) {
  return String(value ?? '').split(/[,，]/).map(item => item.trim()).filter(Boolean)
}

onMounted(() => {
  load()
  checkRender()
})
</script>

<template>
  <div class="qqrobot-page">
    <YdPageHeader title="资源市场" description="资源卡片由服务端渲染为图片，可直接在群聊中发送；支持 CE 资源包图标">
      <template #actions>
        <YdInput v-model="keyword" class="qqrobot-search" placeholder="搜索标题、描述或标签" @keyup.enter="page = 1; load()" />
        <YdButton variant="outline" @click="page = 1; load()">搜索</YdButton>
        <YdButton @click="openCreate">新增资源</YdButton>
      </template>
    </YdPageHeader>
    <div class="qqrobot-body">
      <div v-if="renderStatus && !renderStatus.ready" class="qqrobot-render-bar bad">
        <div class="qqrobot-render-main">
          <span class="qqrobot-render-dot" />
          <strong>卡片暂不可出图</strong>
          <span class="qqrobot-muted">{{ renderStatus.message }}</span>
        </div>
        <span class="qqrobot-muted">{{ renderStatus.hint }}</span>
      </div>
      <YdLoadingOverlay :loading="loading">
        <div v-if="!cards.length" class="qqrobot-doc-section qqrobot-market-blank">
          <YdEmpty description="还没有资源。新增后可自动生成卡片图，群聊里直接发图即可。">
            <template #image>
              <div class="qqrobot-market-blank-icon"><span class="i-ri:store-3-line" /></div>
            </template>
            <YdButton @click="openCreate">新增第一个资源</YdButton>
          </YdEmpty>
          <div class="qqrobot-market-hints">
            <div>
              <strong>封面自动渲染</strong>
              <span>填好标题、平台与标签，后台用统一卡片模板出图，不需要自己作图。</span>
            </div>
            <div>
              <strong>支持 CE 资源包</strong>
              <span>卡片内展示物品图标时，会按 CraftEngine 的模型路径解析材质。</span>
            </div>
            <div>
              <strong>群内直接发图</strong>
              <span>卡片接口公开只读，可直接作为图片消息链接发给 QQ 群或放进图片模板引用。</span>
            </div>
          </div>
        </div>
        <div v-else class="qqrobot-market-grid">
          <article v-for="card in cards" :key="card.id" class="qqrobot-market-card">
            <div class="qqrobot-market-cover">
              <img v-if="!brokenCovers[card.id]" :src="cardImage(card)" :alt="card.title" loading="lazy"
                @error="coverFailed(card)">
              <img v-else-if="coverFallback(card)" :src="coverFallback(card)" :alt="card.title" loading="lazy">
              <div v-else class="qqrobot-market-fallback">{{ card.title.slice(0, 6) }}</div>
              <span class="qqrobot-market-badge">{{ card.free ? '免费' : '付费' }}</span>
            </div>
            <div class="qqrobot-market-body">
              <h3>{{ card.title }}</h3>
              <p class="qqrobot-muted">{{ card.subtitle || card.description }}</p>
              <div class="qqrobot-tags">
                <span v-for="platform in card.platforms" :key="platform" class="qqrobot-chip">{{ platform }}</span>
                <span v-for="tag in card.tags.slice(0, 3)" :key="tag" class="qqrobot-chip ghost">{{ tag }}</span>
              </div>
              <div class="qqrobot-market-foot">
                <span>{{ card.author }}</span>
                <span>◉ {{ card.views }} · ♥ {{ card.likes }} · ↓ {{ card.downloads }}</span>
              </div>
              <div class="qqrobot-market-actions">
                <YdButton variant="outline" size="sm" @click="refresh(card)">刷新卡片</YdButton>
                <YdButton variant="outline" size="sm" @click="openEdit(card)">编辑</YdButton>
                <YdButton variant="outline" size="sm" class="qqrobot-danger" @click="remove(card)">删除</YdButton>
              </div>
            </div>
          </article>
        </div>
      </YdLoadingOverlay>
      <div class="qqrobot-pager">
        <YdPagination v-model:page="page" v-model:size="size" :total="total" :sizes="[8, 16, 32]"
          @page-change="load" @size-change="load" />
      </div>
    </div>

    <YdDrawer v-model="editorOpen" :title="draft.id ? '编辑资源' : '新增资源'">
      <div class="qqrobot-grid">
        <label class="qqrobot-field qqrobot-wide"><span>标题</span><YdInput v-model="draft.title" /></label>
        <label class="qqrobot-field qqrobot-wide"><span>副标题</span><YdInput v-model="draft.subtitle" /></label>
        <label class="qqrobot-field qqrobot-wide"><span>描述</span><YdTextarea v-model="draft.description" /></label>
        <label class="qqrobot-field"><span>作者</span><YdInput v-model="draft.author" /></label>
        <label class="qqrobot-field"><span>状态</span>
          <YdSelect v-model="draft.status" :options="[{ label: '已发布', value: 'published' }, { label: '草稿', value: 'draft' }]" />
        </label>
        <label class="qqrobot-field qqrobot-wide">
          <span>封面图 URL（可选，支持 http(s) 或 data:image）</span>
          <YdInput v-model="draft.coverUrl" placeholder="https://… 保存后由后台下载并内联，渲染时不走外链" />
        </label>
        <label class="qqrobot-field qqrobot-wide"><span>平台（逗号分隔）</span>
          <YdInput :model-value="(draft.platforms ?? []).join('，')"
            @update:model-value="draft.platforms = parseList($event)" />
        </label>
        <label class="qqrobot-field qqrobot-wide"><span>标签（逗号分隔）</span>
          <YdInput :model-value="(draft.tags ?? []).join('，')"
            @update:model-value="draft.tags = parseList($event)" />
        </label>
        <label class="qqrobot-field qqrobot-switch"><span>免费资源</span><YdSwitch v-model="draft.free" /></label>
      </div>
      <template #footer>
        <div class="qqrobot-drawer-actions">
          <YdButton variant="outline" @click="editorOpen = false">取消</YdButton>
          <YdButton :loading="saving" @click="save">保存</YdButton>
        </div>
      </template>
    </YdDrawer>
  </div>
</template>
