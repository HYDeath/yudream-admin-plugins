<script setup lang="ts">
import { YdAlert } from '@yudream/components'
import type { Job } from '../types'
import { statusName } from '../types'
defineProps<{ job: Job; previewUrl: string }>()
const date = (value?: number) => value && value > 0 ? new Date(value).toLocaleString('zh-CN') : '文件未提供'
</script>
<template>
  <article class="schematic-result">
    <div class="schematic-preview">
      <img v-if="job.status === 'READY'" :src="previewUrl" :alt="`${job.name}的两个视角`" />
      <p v-else role="status">{{ statusName(job.status) }}</p>
    </div>
    <YdAlert v-if="job.error" variant="destructive">{{ job.error }}</YdAlert>
    <h2>{{ job.name || job.filename }}</h2>
    <dl v-if="job.status === 'READY'" class="schematic-metadata">
      <div><dt>保存者 / 作者</dt><dd>{{ job.author }}</dd></div>
      <div><dt>创建时间</dt><dd>{{ date(job.createdAt) }}</dd></div>
      <div><dt>方块 / 区域体积</dt><dd>{{ job.blocks?.toLocaleString() }} / {{ job.volume?.toLocaleString() }}</dd></div>
      <div><dt>尺寸</dt><dd>{{ job.width }} × {{ job.height }} × {{ job.length }}</dd></div>
      <div><dt>投影格式</dt><dd>{{ job.formatVersion ? `Litematica ${job.formatVersion}` : 'Java 结构 NBT' }}</dd></div>
      <div><dt>游戏数据版本</dt><dd>{{ job.dataVersion || '文件未提供' }}</dd></div>
    </dl>
    <p class="schematic-muted">{{ job.renderer }}</p>
    <p class="schematic-share">工具箱分享码：<code>{{ job.shareToken }}</code></p>
    <a v-if="job.status === 'READY'" :href="previewUrl" download="建筑投影.png" target="_blank" rel="noopener">下载透明 PNG ↗</a>
  </article>
</template>
