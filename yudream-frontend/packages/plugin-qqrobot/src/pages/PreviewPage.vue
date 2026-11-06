<script setup lang="ts">
import { YdButton, YdPageHeader } from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { onMounted, ref } from 'vue'
import { useRenderStatus } from '../composables/use-render-status'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const { status, checking, check } = useRenderStatus(props.sdk)
const stamp = ref(Date.now())
const loading = ref(false)
const failed = ref(false)

function url(path: string) {
  return `/api/plugins/qqrobot/admin/preview/${path}?v=${stamp.value}`
}

async function refresh() {
  loading.value = true
  failed.value = false
  stamp.value = Date.now()
  await check()
}

function settled() {
  loading.value = false
}

function broken() {
  loading.value = false
  failed.value = true
}

onMounted(check)
</script>

<template>
  <div class="qqrobot-page">
    <YdPageHeader title="渲染预览" description="复用后台「消息渲染」能力出图：物品卡片、背包与潜影盒，不依赖服务端连接">
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
      <p class="qqrobot-muted">
        这是合成背包的预览：主存储 27 格、快捷栏 9 格、护甲 4 格与副手；包含附魔光效、耐久条和潜影盒展开。
        真实背包的图标由服务端从资源包（含 CraftEngine）提取后随快照上报。
      </p>
      <div class="qqrobot-preview-grid" :class="{ 'is-blocked': status && !status.ready }">
        <figure class="qqrobot-preview-card">
          <figcaption>背包渲染模型</figcaption>
          <img :src="url('inventory.png')" alt="背包渲染预览" @load="settled" @error="broken">
        </figure>
        <figure class="qqrobot-preview-card">
          <figcaption>单物品提示卡</figcaption>
          <img :src="url('item.png')" alt="物品渲染预览" @load="settled" @error="broken">
        </figure>
      </div>
      <p v-if="failed" class="qqrobot-preview-error">
        渲染服务没有返回图片。请确认「平台 → 能力目录 → 消息渲染」已启用且服务可达，再点「重新渲染」。
      </p>
    </div>
  </div>
</template>
