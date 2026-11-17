<script setup lang="ts">
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { onMounted, ref } from 'vue'
import { YdAlert, YdButton, YdForm, YdFormItem, YdInput, YdNumberField, YdPageHeader, YdPageMain, YdSwitch } from '@yudream/components'
import { createQqAuthApi } from './api/qq-auth-api'
import type { QqSettings } from './types'
const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const api = createQqAuthApi(props.sdk)
const loading = ref(true), saving = ref(false), error = ref(''), message = ref(''), appKey = ref('')
const values = ref<QqSettings>({ enabled: false, appId: '', appKeyConfigured: false, redirectUri: '', displayName: 'QQ 登录', sort: 80, configured: false, active: false })
async function load() { try { values.value = await api.settings() } catch (e) { error.value = e instanceof Error ? e.message : '无法读取配置' } finally { loading.value = false } }
async function save() {
  if (saving.value) return
  saving.value = true; error.value = ''; message.value = ''
  try {
    const patch: Record<string,unknown> = { enabled: values.value.enabled, appId: values.value.appId, redirectUri: values.value.redirectUri, displayName: values.value.displayName, sort: values.value.sort }
    if (appKey.value.trim()) patch.appKey = appKey.value.trim()
    values.value = await api.save(patch); appKey.value = ''; message.value = 'QQ 登录配置已保存'
  } catch (e) { error.value = (e as { response?: { data?: { message?: string } } })?.response?.data?.message || (e instanceof Error ? e.message : '保存失败') }
  finally { saving.value = false }
}
onMounted(load)
</script>
<template>
  <div>
    <YdPageHeader title="QQ 登录设置" description="按我们的官网流程提供 QQ Graph OAuth 登录；会话签发和外部身份绑定仍由宿主负责。"><YdButton :loading="saving" :disabled="loading" @click="save">保存设置</YdButton></YdPageHeader>
    <YdPageMain>
      <YdAlert v-if="error" title="配置未保存" :description="error" />
      <p v-if="loading">正在读取配置…</p>
      <template v-else>
        <YdAlert :title="values.active ? 'QQ 登录已启用' : 'QQ 登录尚未启用'" description="QQ 群六位绑定码由 qq-binding 提供。这里是浏览器 OAuth 登录，两者不是同一个入口。" />
        <YdForm :model="values" layout="vertical" class="max-w-2xl mt-4">
          <YdFormItem label="启用 QQ 登录"><YdSwitch v-model="values.enabled" :disabled="saving" /></YdFormItem>
          <YdFormItem label="QQ AppID"><YdInput v-model="values.appId" :disabled="saving" /></YdFormItem>
          <YdFormItem label="QQ AppKey" extra="只写入 SecretStore，不回显。留空保持原值。"><YdInput v-model="appKey" type="password" :disabled="saving" :placeholder="values.appKeyConfigured ? '已配置密钥' : '填写测试应用密钥'" /></YdFormItem>
          <YdFormItem label="回调地址" extra="QQ 平台登记相同地址；必须使用前端回调页，不是 JSON API。"><YdInput v-model="values.redirectUri" placeholder="https://example.com/external-login/callback" :disabled="saving" /></YdFormItem>
          <YdFormItem label="登录入口名称"><YdInput v-model="values.displayName" :disabled="saving" /></YdFormItem>
          <YdFormItem label="入口排序"><YdNumberField v-model="values.sort" :min="1" :max="999" :disabled="saving" /></YdFormItem>
        </YdForm>
        <p v-if="message" role="status">{{ message }}</p>
      </template>
    </YdPageMain>
  </div>
</template>
