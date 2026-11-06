<script setup lang="ts">
import {
  YdButton,
  YdCopyButton,
  YdEmpty,
  YdInput,
  YdLoadingOverlay,
  YdPageHeader,
  useYdToast,
} from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { computed, onMounted, ref } from 'vue'
import { createQqRobotApi } from '../api/qqrobot-api'
import type { BridgeServer, BridgeState, QqRobotNotice } from '../types'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const api = createQqRobotApi(props.sdk)
const toast = useYdToast()
const loading = ref(true)
const state = ref<BridgeState>({ servers: [], token: '', pendingBindings: 0 })
const kicking = ref('')
const kickMessage = ref('请先绑定社交账号')
const notices = ref<QqRobotNotice[]>([])

const servers = computed(() => state.value.servers ?? [])

async function load() {
  loading.value = true
  try {
    state.value = await api.bridge()
    notices.value = (await api.notices()).notices ?? []
  }
  catch (error) {
    toast.error(String((error as Error)?.message ?? '加载桥接状态失败'))
  }
  finally {
    loading.value = false
  }
}

async function rotate() {
  try {
    const result = await api.rotateToken()
    state.value = { ...state.value, token: result.token }
    toast.success('桥接令牌已轮换，请在服务端配置中更新')
    notices.value = (await api.notices()).notices ?? []
  }
  catch (error) {
    toast.error(String((error as Error)?.message ?? '轮换失败'))
  }
}

async function kick(server: BridgeServer) {
  if (!kicking.value.trim()) {
    toast.error('请输入玩家名')
    return
  }
  try {
    const result = await api.kick(server.serverId, kicking.value.trim(), kickMessage.value)
    toast[result.sent ? 'success' : 'error'](result.sent ? '已发送踢出指令' : '服务器未连接')
  }
  catch (error) {
    toast.error(String((error as Error)?.message ?? '发送失败'))
  }
}

function relative(value: string) {
  const seconds = Math.max(0, Math.round((Date.now() - new Date(value).getTime()) / 1000))
  if (seconds < 60) return `${seconds} 秒前`
  if (seconds < 3600) return `${Math.floor(seconds / 60)} 分钟前`
  return `${Math.floor(seconds / 3600)} 小时前`
}

onMounted(load)
</script>

<template>
  <div class="qqrobot-page">
    <YdPageHeader title="服务端桥接" description="Minecraft 服务端通过 WebSocket 连入后台；令牌仅在本页展示">
      <template #actions>
        <YdButton variant="outline" @click="load">刷新</YdButton>
        <YdButton variant="outline" class="qqrobot-danger" @click="rotate">轮换令牌</YdButton>
      </template>
    </YdPageHeader>
    <div class="qqrobot-body">
      <YdLoadingOverlay :loading="loading">
        <div v-for="notice in notices" :key="notice.id" class="qqrobot-render-bar bad">
          <div class="qqrobot-render-main">
            <span class="qqrobot-render-dot" />
            <strong>{{ notice.title }}</strong>
            <span class="qqrobot-muted">{{ relative(notice.createdAt) }}</span>
          </div>
          <span class="qqrobot-muted">{{ notice.message }}</span>
        </div>
        <section class="qqrobot-bridge-token">
          <div>
            <div class="qqrobot-bridge-label">桥接令牌</div>
            <code>{{ state.token }}</code>
            <div v-if="state.tokenMeta" class="qqrobot-bridge-label">
              指纹 {{ state.tokenMeta.fingerprint }} · 生成于 {{ relative(state.tokenMeta.generatedAt) }}
              <template v-if="state.tokenMeta.rotatedBy"> · 最近轮换 {{ state.tokenMeta.rotatedBy }}</template>
            </div>
          </div>
          <YdCopyButton :text="state.token" />
        </section>
        <p v-if="state.tokenPersistent === false" class="qqrobot-muted">
          当前令牌仅保存在进程内：后台未配置密钥存储（YUDREAM_CREDENTIAL_KEY），重启后令牌会变化，
          需要同步更新服务端插件配置。
        </p>
        <div class="qqrobot-bridge-meta">
          <span>在线服务端 {{ servers.length }}</span>
          <span>待领取绑定码 {{ state.pendingBindings }}</span>
        </div>
        <div v-if="!servers.length" class="qqrobot-empty">
          <YdEmpty description="还没有服务端连入。把令牌填入服务端 config.yml 后重启，连接会显示在这里。" />
        </div>
        <div v-else class="qqrobot-bridge-list">
          <article v-for="server in servers" :key="server.sessionId" class="qqrobot-bridge-card">
            <div class="qqrobot-bridge-head">
              <div>
                <h3>{{ server.serverName }}</h3>
                <span class="qqrobot-muted">{{ server.serverId }} · {{ relative(server.lastSeen) }}</span>
              </div>
              <span class="qqrobot-chip on">已连接</span>
            </div>
            <div class="qqrobot-bridge-stats">
              <div><strong>{{ server.online ?? 0 }}/{{ server.max ?? 0 }}</strong><span>在线</span></div>
              <div><strong>{{ server.tps ?? '-' }}</strong><span>TPS</span></div>
              <div><strong>{{ server.packTextures ?? 0 }}</strong><span>纹理</span></div>
            </div>
            <div class="qqrobot-bridge-kick">
              <YdInput v-model="kicking" placeholder="玩家名" />
              <YdInput v-model="kickMessage" placeholder="踢出原因" />
              <YdButton variant="outline" size="sm" @click="kick(server)">踢出</YdButton>
            </div>
          </article>
        </div>
      </YdLoadingOverlay>
    </div>
  </div>
</template>
