<script setup lang="ts">
import { YdButton, YdPageHeader } from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRenderStatus } from '../composables/use-render-status'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const { status, checking, check } = useRenderStatus(props.sdk)
const loading = ref(false)
const failed = ref(false)
const message = ref('')
const images = ref<Record<string, string>>({})
const stamps = ref<Record<string, number>>({})
const urls = new Set<string>()

const cards = [
  { key: 'inventory', title: '背包渲染模型', hint: '主存储 27 格、快捷栏 9 格、护甲与副手' },
  { key: 'containers', title: '末影箱 + 多潜影盒', hint: '每个潜影盒单独一块，标题带来源槽位' },
  { key: 'item', title: '单物品提示卡', hint: '名称、附魔、耐久与稀有度' },
]

/**
 * 预览图是管理接口，必须带 Authorization 头。
 * `<img src>` 不会带请求头，所以统一走 sdk.http.blob() 取回再转 object URL，
 * 否则会拿到 400 的 JSON 被浏览器当成坏图。
 */
async function loadImage(key: string) {
  const path = `/admin/preview/${key}.png`
  const response = await props.sdk.http.blob(`${path}?v=${Date.now()}`)
  const objectUrl = URL.createObjectURL(response.data)
  const previous = images.value[key]
  if (previous) {
    URL.revokeObjectURL(previous)
    urls.delete(previous)
  }
  urls.add(objectUrl)
  images.value = { ...images.value, [key]: objectUrl }
  stamps.value = { ...stamps.value, [key]: Date.now() }
}

async function loadAll() {
  failed.value = false
  message.value = ''
  await Promise.all(cards.map(async (card) => {
    try {
      await loadImage(card.key)
    }
    catch (error) {
      failed.value = true
      message.value = String((error as Error)?.message ?? '预览出图失败')
    }
  }))
}

async function refresh() {
  loading.value = true
  try {
    await check()
    await loadAll()
  }
  finally {
    loading.value = false
  }
}

onMounted(async () => {
  await check()
  await refresh()
})

onBeforeUnmount(() => {
  urls.forEach(url => URL.revokeObjectURL(url))
  urls.clear()
})
</script>

<template>
  <div class="qqrobot-page">
    <YdPageHeader title="渲染预览" description="复用后台「消息渲染」能力出图：背包、容器与物品卡，不依赖服务端连接">
      <template #actions>
        <YdButton variant="outline" :loading="loading || checking" @click="refresh">重新渲染</YdButton>
      </template>
    </YdPageHeader>
    <div class="qqrobot-body">
      <div v-if="status" class="qqrobot-render-bar" :class="status.ready ? 'ok' : 'bad'">
        <div class="qqrobot-render-main">
          <span class="qqrobot-render-dot" />
          <strong>{{ status.ready ? '渲染服务可用' : '渲染服务未就绪' }}</strong>
          <span class="qqrobot-muted">{{ status.message }}</span>
          <span v-if="status.ready && status.size" class="qqrobot-chip ghost">{{ status.size }} · {{ status.latencyMs }}ms</span>
        </div>
        <span v-if="!status.ready" class="qqrobot-muted">{{ status.hint }}</span>
      </div>
      <div class="qqrobot-preview-grid" :class="{ 'is-blocked': status && !status.ready }">
        <figure v-for="card in cards" :key="card.key" class="qqrobot-preview-card">
          <figcaption>
            {{ card.title }}
            <span class="qqrobot-muted">{{ card.hint }}</span>
          </figcaption>
          <img v-if="images[card.key]" :key="stamps[card.key]" :src="images[card.key]" :alt="card.title">
          <div v-else class="qqrobot-preview-skeleton">加载中…</div>
        </figure>
      </div>
      <p v-if="failed" class="qqrobot-preview-error">
        预览出图失败：{{ message }}。请确认「平台 → 能力目录 → 消息渲染」已启用且服务可达，再点「重新渲染」。
      </p>
      <details class="qqrobot-preview-help">
        <summary>关于这些预览图</summary>
        <ul>
          <li>背包：主存储 27 格、快捷栏 9 格、护甲 4 格与副手；含附魔光效、耐久条和潜影盒展开。</li>
          <li>容器：对应「末影箱 / 潜影盒 [序号]」，只画容器不铺背包网格；有几个潜影盒就出几块，标题带来源槽位。</li>
          <li>图标由服务端从资源包（含 CraftEngine）提取，使用真实背包时需要服务端桥接在线。</li>
          <li>这里用示例数据出图，只验证渲染链路，不依赖服务端连接。</li>
        </ul>
      </details>
    </div>
  </div>
</template>
