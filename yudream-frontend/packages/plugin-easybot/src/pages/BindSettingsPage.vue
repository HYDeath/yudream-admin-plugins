<script setup lang="ts">
import {
  YdButton,
  YdInput,
  YdLoadingOverlay,
  YdNumberField,
  YdPageHeader,
  YdSelect,
  YdSettingsLayout,
  YdSettingsSection,
  YdSwitch,
  YdTextarea,
  useYdToast,
} from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { onMounted, ref } from 'vue'
import { createEasyBotApi } from '../api/easybot-api'
import { emptyBindSettings, type BindSettings } from '../types'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const api = createEasyBotApi(props.sdk)
const toast = useYdToast()
const loading = ref(true)
const saving = ref(false)
const draft = ref<BindSettings>(emptyBindSettings())
const section = ref('basic')

const sections = [
  { label: '基础设置', value: 'basic', icon: 'i-ri:settings-3-line' },
  { label: '自定义消息', value: 'messages', icon: 'i-ri:message-3-line' },
]
const modeOptions = [
  { label: '双向验证', value: 'dual' },
  { label: '社交平台信任模式', value: 'trust' },
]
const charsetOptions = [
  { label: '纯数字 (0-9)', value: 'numeric' },
  { label: '字母数字', value: 'alnum' },
]

async function load() {
  loading.value = true
  try {
    draft.value = await api.bindSettings()
  }
  catch (error) {
    toast.error(String((error as Error)?.message ?? '加载绑定设置失败'))
  }
  finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    draft.value = await api.saveBindSettings(draft.value)
    toast.success('绑定设置已保存')
  }
  catch (error) {
    toast.error(String((error as Error)?.message ?? '保存失败'))
  }
  finally {
    saving.value = false
  }
}

function setForcedServers(value: string) {
  draft.value.forcedServers = value.split(/[\n,，]/).map(item => item.trim()).filter(Boolean)
}

onMounted(load)
</script>

<template>
  <div class="easybot-page">
    <YdPageHeader title="绑定设置" description="社交账号与游戏账号的绑定方式、验证码与消息模板">
      <template #actions>
        <YdButton variant="outline" :disabled="saving" @click="load">重置</YdButton>
        <YdButton :loading="saving" @click="save">保存</YdButton>
      </template>
    </YdPageHeader>
    <div class="easybot-body">
      <YdLoadingOverlay :loading="loading">
        <YdSettingsLayout v-model="section" :list="sections">
          <YdSettingsSection v-if="section === 'basic'" title="基础设置" description="绑定模式与验证码规则">
            <div class="easybot-grid">
              <label class="easybot-field">
                <span>绑定模式</span>
                <YdSelect v-model="draft.bindMode" :options="modeOptions" />
              </label>
              <label class="easybot-field">
                <span>强制绑定的服务器</span>
                <YdTextarea :model-value="draft.forcedServers.join('\n')" placeholder="每行一个服务器 ID"
                  @update:model-value="setForcedServers(String($event ?? ''))" />
              </label>
              <label class="easybot-field">
                <span>可绑定游戏账号数量</span>
                <YdNumberField v-model="draft.accountsPerSocial" :min="1" :max="20" />
              </label>
              <label class="easybot-field">
                <span>验证码有效时间（分钟）</span>
                <YdNumberField v-model="draft.codeTtlMinutes" :min="1" :max="120" />
              </label>
              <label class="easybot-field">
                <span>通用前缀</span>
                <YdInput v-model="draft.commonPrefix" placeholder="默认无前缀" />
              </label>
              <label class="easybot-field">
                <span>验证码生成模式</span>
                <YdSelect v-model="draft.codeCharset" :options="charsetOptions" />
              </label>
              <label class="easybot-field">
                <span>生成验证码长度</span>
                <YdNumberField v-model="draft.codeLength" :min="4" :max="12" />
              </label>
              <label class="easybot-field easybot-switch">
                <span>允许解绑</span>
                <YdSwitch v-model="draft.allowUnbind" />
              </label>
              <label class="easybot-field easybot-switch">
                <span>启用无关键词模式</span>
                <YdSwitch v-model="draft.noKeywordMode" />
              </label>
              <label class="easybot-field easybot-switch">
                <span>禁止中文名</span>
                <YdSwitch v-model="draft.forbidChinese" />
              </label>
              <label class="easybot-field">
                <span>绑定关键词</span>
                <YdInput v-model="draft.bindKeyword" placeholder="绑定" />
              </label>
              <label class="easybot-field">
                <span>解绑关键词</span>
                <YdInput v-model="draft.unbindKeyword" placeholder="解绑" />
              </label>
            </div>
          </YdSettingsSection>
          <YdSettingsSection v-else title="自定义消息" description="支持 & 颜色代码与 #account / #name / #why 占位符">
            <div class="easybot-grid">
              <label class="easybot-field easybot-wide">
                <span>未绑定账号踢出消息</span>
                <YdTextarea v-model="draft.unboundKickMessage" />
              </label>
              <label class="easybot-field easybot-wide">
                <span>绑定成功消息</span>
                <YdTextarea v-model="draft.bindSuccessMessage" />
              </label>
              <label class="easybot-field easybot-wide">
                <span>绑定失败消息</span>
                <YdTextarea v-model="draft.bindFailMessage" />
              </label>
              <label class="easybot-field easybot-wide">
                <span>解绑成功消息</span>
                <YdTextarea v-model="draft.unbindSuccessMessage" />
              </label>
              <label class="easybot-field easybot-wide">
                <span>解绑失败消息</span>
                <YdTextarea v-model="draft.unbindFailMessage" />
              </label>
            </div>
          </YdSettingsSection>
        </YdSettingsLayout>
      </YdLoadingOverlay>
    </div>
  </div>
</template>
