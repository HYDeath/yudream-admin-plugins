<script setup lang="ts">
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { YdAlert, YdButton, YdPageHeader, YdSettingsSection } from '@yudream/components'
import { ref } from 'vue'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const loading = ref(false)
const code = ref('')
const expiresAt = ref('')
const errorMessage = ref('')

async function issue() {
  if (loading.value) return
  loading.value = true
  errorMessage.value = ''
  code.value = ''
  expiresAt.value = ''
  try {
    const result = await props.sdk.http.post<{ code: string, expiresAt: string }>('/binding-codes', {})
    code.value = result.code
    expiresAt.value = result.expiresAt
  }
  catch (error) {
    errorMessage.value = String((error as Error)?.message ?? '绑定码生成失败，请重试')
  }
  finally { loading.value = false }
}
</script>

<template>
  <div class="qq-binding-page">
    <YdPageHeader title="QQ 账号绑定" description="用一次性绑定码关联 QQ 与你的系统账号。" />
    <div class="qq-binding-body">
      <YdSettingsSection title="生成绑定码" description="在已接入官方机器人的 QQ 群或私聊中发送「/绑定 六位绑定码」。绑定码不会显示真实 QQ 号。">
        <YdAlert v-if="errorMessage" variant="destructive" title="绑定码生成失败" :description="errorMessage" />
        <div v-if="code" class="qq-binding-code" aria-live="polite">
          <strong>{{ code }}</strong>
          <span>过期时间：{{ new Date(expiresAt).toLocaleString() }}</span>
          <p>发送：/绑定 {{ code }}</p>
        </div>
        <YdButton :loading="loading" @click="issue">{{ code ? '重新生成绑定码' : '生成绑定码' }}</YdButton>
      </YdSettingsSection>
    </div>
  </div>
</template>
