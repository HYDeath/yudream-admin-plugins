<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  YdButton, YdForm, YdFormItem, YdInput, YdNumberField, YdSelect,
  YdSettingsSection, YdSwitch, YdTextarea, YdTablePicker,
  type TableColumn, type YdTablePickerQuery, type YdTablePickerResult,
} from '@yudream/components'
import type { AiProviderOption, AutomationPolicy, AutomationPolicyOverride, PolicyOverrideField, UserOption } from '../types'

type UserPickerRow = UserOption & { label: string }
const props = withDefaults(defineProps<{
  basePolicy: AutomationPolicy
  override: AutomationPolicyOverride
  aiProviders?: AiProviderOption[]
  allowInheritance?: boolean
  disabled?: boolean
  userFetcher?: (query: YdTablePickerQuery) => Promise<YdTablePickerResult<UserPickerRow>>
  userLabels?: Record<string, string>
  groupOptions?: { label: string, value: string }[]
}>(), {
  aiProviders: () => [], allowInheritance: true, disabled: false,
  userFetcher: undefined, userLabels: undefined, groupOptions: () => [],
})
const emit = defineEmits<{ 'update:override': [value: AutomationPolicyOverride] }>()
const runtimeFields: { key: PolicyOverrideField, label: string, help: string }[] = [
  { key: 'enabled', label: '自动化策略', help: '允许群消息触发自动化处理。' },
  { key: 'mediaEnabled', label: '媒体解析', help: 'B 站只发封面、标题、简介和原链接；抖音按现有策略处理。' },
  { key: 'joinVerificationEnabled', label: '入群验证', help: '自动审核匹配的入群申请。' },
  { key: 'websiteApprovalRequired', label: '以官网审核为准', help: '仅官网最终审核通过的账号自动入群；未绑定、待审核或服务不可用时留给管理员处理，不走答案或 AI 放行。' },
  { key: 'aiFallbackEnabled', label: 'AI 兜底', help: '入群验证规则未命中时再请求 AI。' },
  { key: 'riskMonitorEnabled', label: '风险监测', help: '累计群消息送 AI 审核，并按策略禁言和告警。' },
]
const riskNumberFields: { key: PolicyOverrideField, label: string, help: string, min: number, max: number }[] = [
  { key: 'riskBatchSize', label: '送检条数', help: '每群累计满该条数后，整批送 AI 审核一次。', min: 5, max: 100 },
  { key: 'riskAlertConfidence', label: '告警置信度（%）', help: '达到该置信度的风险消息才会推送告警。', min: 0, max: 100 },
]
const alertFields: { key: PolicyOverrideField, label: string, help: string }[] = [
  { key: 'riskAlertGroup', label: '推送到指定群', help: '告警发到单独指定的群，不会发回出事群。' },
  { key: 'riskAlertAdmin', label: '私信推送管理员', help: '向已绑定 QQ 的所选管理员发送私聊告警。' },
]
const muteFields: { key: PolicyOverrideField, label: string, max?: number }[] = [
  { key: 'riskMuteLowConfidence', label: '低档置信度（%）', max: 100 },
  { key: 'riskMuteLowSeconds', label: '低档禁言（秒）' },
  { key: 'riskMuteHighConfidence', label: '高档置信度（%）', max: 100 },
  { key: 'riskMuteHighSeconds', label: '高档禁言（秒）' },
]
const answerFields = [{ key: 'approvedAnswers', label: '通过答案' }, { key: 'rejectedAnswers', label: '拒绝答案' }] as const
const fallbackDecisionOptions = [
  { label: '无法判断时拒绝', value: 'fail-closed' },
  { label: '无法判断时保留人工审核', value: 'fail-open' },
]
const userPickerColumns: TableColumn<UserPickerRow>[] = [
  { id: 'nickname', accessorKey: 'nickname', header: '昵称', width: 160 },
  { id: 'username', accessorKey: 'username', header: '用户名', width: 160 },
  { id: 'deptNames', accessorKey: 'deptNames', header: '部门' },
]
const providerOptions = computed(() => [{ label: '不使用 AI', value: '' }, ...props.aiProviders.map(provider => ({ label: provider.name, value: provider.code }))])
const selectedProvider = computed(() => props.aiProviders.find(provider => provider.code === String(valueFor('providerCode') ?? '')))
const modelOptions = computed(() => [{ label: '由提供方默认模型决定', value: '' }, ...(selectedProvider.value?.models ?? []).map(model => ({ label: model.name, value: model.code }))])
function isInherited(field: PolicyOverrideField) { return props.allowInheritance && props.override[field] === null }
function valueFor(field: PolicyOverrideField) { return props.override[field] === null ? props.basePolicy[field] : props.override[field] }
function update(field: PolicyOverrideField, value: AutomationPolicyOverride[PolicyOverrideField]) { emit('update:override', { ...props.override, [field]: value }) }
function setOverride(field: PolicyOverrideField, enabled: boolean) { update(field, enabled ? props.basePolicy[field] : null) }
function textFor(field: 'approvedAnswers' | 'rejectedAnswers') { return ((valueFor(field) as string[]) ?? []).join('\n') }
function setAnswerList(field: 'approvedAnswers' | 'rejectedAnswers', value: string | number | undefined) { update(field, String(value ?? '').split('\n').map(item => item.trim()).filter(Boolean)) }
function setProvider(value: unknown) {
  const providerCode = String(value ?? '')
  emit('update:override', { ...props.override, providerCode, modelCode: providerCode !== String(valueFor('providerCode') ?? '') ? '' : props.override.modelCode })
}
function numberFor(field: PolicyOverrideField) { return Number(valueFor(field) ?? 0) }
function setNumber(field: PolicyOverrideField, value: number | undefined) { update(field, Number.isFinite(value) ? Math.round(Number(value)) : 0) }
const adminUserIds = computed(() => (valueFor('riskAlertAdminUserIds') as string[]) ?? [])
const extraAlertGroupId = ref('')
function addExtraAlertGroup() {
  const id = extraAlertGroupId.value.trim()
  if (!id || props.disabled || isInherited('riskAlertGroupChannelId')) return
  update('riskAlertGroupChannelId', id)
  extraAlertGroupId.value = ''
}
const alertGroupOptions = computed(() => {
  const known = [...props.groupOptions]
  const current = String(valueFor('riskAlertGroupChannelId') ?? '')
  if (current && !known.some(item => item.value === current)) known.push({ label: current, value: current })
  return known
})
</script>

<template>
  <YdForm :model="override" :disabled="disabled" class="qa-policy-form">
    <YdSettingsSection title="运行开关" :description="allowInheritance ? '开启字段旁的「自定义」后编辑该字段；关闭则继承连接默认策略。灰色控件显示当前继承值。' : '控制本连接下各自动化能力的启用状态。'">
      <div class="qa-form-grid">
        <YdFormItem v-for="field in runtimeFields" :key="field.key" :extra="field.help">
          <template #label>
            <span class="qa-field-heading"><span>{{ field.label }}</span><YdSwitch v-if="allowInheritance" :model-value="!isInherited(field.key)" :disabled="disabled" :aria-label="`自定义${field.label}`" @update:model-value="setOverride(field.key, $event ?? false)">自定义</YdSwitch></span>
          </template>
          <YdSwitch :model-value="Boolean(valueFor(field.key))" :disabled="disabled || isInherited(field.key)" :aria-label="field.label" @update:model-value="update(field.key, $event ?? false)" />
        </YdFormItem>
      </div>
    </YdSettingsSection>

    <YdSettingsSection title="媒体服务" description="设置媒体解析服务地址；机器人容器也必须能访问该服务。">
      <YdFormItem extra="使用 /api/download 解析文件。容器部署时请使用局域网 IP、域名或 Docker 服务名，避免填写 127.0.0.1。官方 QQ 自动使用公网直链发送。">
        <template #label><span class="qa-field-heading"><span>解析服务地址</span><YdSwitch v-if="allowInheritance" :model-value="!isInherited('mediaProviderEndpoint')" :disabled="disabled" aria-label="自定义解析服务地址" @update:model-value="setOverride('mediaProviderEndpoint', $event ?? false)">自定义</YdSwitch></span></template>
        <YdInput type="url" placeholder="http://服务器地址:端口" :model-value="String(valueFor('mediaProviderEndpoint') ?? '')" :disabled="disabled || isInherited('mediaProviderEndpoint')" @update:model-value="update('mediaProviderEndpoint', String($event ?? ''))" />
      </YdFormItem>
    </YdSettingsSection>

    <YdSettingsSection title="入群审核" description="每行一个匹配答案。官方问答带有「问题：答案」时，只填写答案也能命中。">
      <div class="qa-form-grid">
        <YdFormItem v-for="field in answerFields" :key="field.key">
          <template #label><span class="qa-field-heading"><span>{{ field.label }}</span><YdSwitch v-if="allowInheritance" :model-value="!isInherited(field.key)" :disabled="disabled" :aria-label="`自定义${field.label}`" @update:model-value="setOverride(field.key, $event ?? false)">自定义</YdSwitch></span></template>
          <YdTextarea :rows="5" placeholder="每行一个答案" :model-value="textFor(field.key)" :disabled="disabled || isInherited(field.key)" @update:model-value="setAnswerList(field.key, $event)" />
        </YdFormItem>
      </div>
      <p class="qa-help">官方 QQ 仅处理入群申请事件，机器人须为群管理员并订阅群成员事件；已入群通知无法审批。处理记录见系统日志「QQ 群自动化 / 入群验证」。</p>
    </YdSettingsSection>

    <YdSettingsSection title="AI 决策" description="入群验证兜底与风险监测共用此模型配置。">
      <div class="qa-form-grid">
        <YdFormItem>
          <template #label><span class="qa-field-heading"><span>AI 服务提供方</span><YdSwitch v-if="allowInheritance" :model-value="!isInherited('providerCode')" :disabled="disabled" aria-label="自定义 AI 服务提供方" @update:model-value="setOverride('providerCode', $event ?? false)">自定义</YdSwitch></span></template>
          <YdSelect :options="providerOptions" :model-value="String(valueFor('providerCode') ?? '')" :disabled="disabled || isInherited('providerCode')" @update:model-value="setProvider" />
        </YdFormItem>
        <YdFormItem>
          <template #label><span class="qa-field-heading"><span>AI 模型</span><YdSwitch v-if="allowInheritance" :model-value="!isInherited('modelCode')" :disabled="disabled" aria-label="自定义 AI 模型" @update:model-value="setOverride('modelCode', $event ?? false)">自定义</YdSwitch></span></template>
          <YdSelect :options="modelOptions" :model-value="String(valueFor('modelCode') ?? '')" :disabled="disabled || isInherited('modelCode') || !selectedProvider" @update:model-value="update('modelCode', String($event ?? ''))" />
        </YdFormItem>
        <YdFormItem>
          <template #label><span class="qa-field-heading"><span>AI 无法判断时</span><YdSwitch v-if="allowInheritance" :model-value="!isInherited('failClosed')" :disabled="disabled" aria-label="自定义 AI 兜底结果" @update:model-value="setOverride('failClosed', $event ?? false)">自定义</YdSwitch></span></template>
          <YdSelect :options="fallbackDecisionOptions" :model-value="Boolean(valueFor('failClosed')) ? 'fail-closed' : 'fail-open'" :disabled="disabled || isInherited('failClosed')" @update:model-value="update('failClosed', $event === 'fail-closed')" />
        </YdFormItem>
      </div>
    </YdSettingsSection>

    <YdSettingsSection v-if="valueFor('riskMonitorEnabled')" title="风险监测与告警" description="送检内容包含发送者消息身份、绑定账号昵称与时间，AI 按条判定风险和置信度。">
      <div class="qa-form-grid">
        <YdFormItem v-for="field in riskNumberFields" :key="field.key" :extra="field.help">
          <template #label><span class="qa-field-heading"><span>{{ field.label }}</span><YdSwitch v-if="allowInheritance" :model-value="!isInherited(field.key)" :disabled="disabled" :aria-label="`自定义${field.label}`" @update:model-value="setOverride(field.key, $event ?? false)">自定义</YdSwitch></span></template>
          <YdNumberField :min="field.min" :max="field.max" :model-value="numberFor(field.key)" :disabled="disabled || isInherited(field.key)" @update:model-value="setNumber(field.key, $event)" />
        </YdFormItem>
        <YdFormItem v-for="field in alertFields" :key="field.key" :extra="field.help">
          <template #label><span class="qa-field-heading"><span>{{ field.label }}</span><YdSwitch v-if="allowInheritance" :model-value="!isInherited(field.key)" :disabled="disabled" :aria-label="`自定义${field.label}`" @update:model-value="setOverride(field.key, $event ?? false)">自定义</YdSwitch></span></template>
          <YdSwitch :model-value="Boolean(valueFor(field.key))" :disabled="disabled || isInherited(field.key)" :aria-label="field.label" @update:model-value="update(field.key, $event ?? false)" />
        </YdFormItem>
      </div>
      <YdFormItem v-if="valueFor('riskAlertGroup')" extra="没有群名时，可先到「QQ 连接」发送识别消息并写备注，也可补充已知的群 openid。">
        <template #label><span class="qa-field-heading"><span>告警推送群</span><YdSwitch v-if="allowInheritance" :model-value="!isInherited('riskAlertGroupChannelId')" :disabled="disabled" aria-label="自定义告警推送群" @update:model-value="setOverride('riskAlertGroupChannelId', $event ?? false)">自定义</YdSwitch></span></template>
        <div class="qa-field-stack">
          <YdSelect placeholder="选择接收告警的群" :options="alertGroupOptions" :model-value="String(valueFor('riskAlertGroupChannelId') ?? '')" :disabled="disabled || isInherited('riskAlertGroupChannelId')" @update:model-value="update('riskAlertGroupChannelId', String($event ?? ''))" />
          <div class="qa-inline-input"><YdInput v-model="extraAlertGroupId" placeholder="补充官方群 openid" :disabled="disabled || isInherited('riskAlertGroupChannelId')" @keydown.enter.prevent="addExtraAlertGroup" /><YdButton variant="outline" :disabled="disabled || isInherited('riskAlertGroupChannelId') || !extraAlertGroupId.trim()" @click="addExtraAlertGroup">添加</YdButton></div>
        </div>
      </YdFormItem>
      <YdFormItem v-if="valueFor('riskAlertAdmin') && userFetcher" extra="管理员需已绑定 QQ，告警通过绑定会话送达。">
        <template #label><span class="qa-field-heading"><span>接收告警的管理员</span><YdSwitch v-if="allowInheritance" :model-value="!isInherited('riskAlertAdminUserIds')" :disabled="disabled" aria-label="自定义接收告警的管理员" @update:model-value="setOverride('riskAlertAdminUserIds', $event ?? false)">自定义</YdSwitch></span></template>
        <YdTablePicker :model-value="adminUserIds" :columns="userPickerColumns" :fetcher="userFetcher" row-key="id" label-key="label" title="选择接收告警的管理员" placeholder="搜索并选择管理员" search-placeholder="输入用户名 / 昵称后回车" :initial-labels="userLabels" :disabled="disabled || isInherited('riskAlertAdminUserIds')" @update:model-value="update('riskAlertAdminUserIds', $event)">
          <template #cell-deptNames="{ row }">{{ (row.original.deptNames || []).join('、') || '—' }}</template>
        </YdTablePicker>
      </YdFormItem>
      <YdFormItem extra="Milky 与官方 QQ 均支持。达到高档阈值时使用高档时长，否则达到低档阈值时使用低档时长；86400 秒为 1 天。">
        <template #label><span class="qa-field-heading"><span>自动禁言</span><YdSwitch v-if="allowInheritance" :model-value="!isInherited('riskMuteEnabled')" :disabled="disabled" aria-label="自定义自动禁言" @update:model-value="setOverride('riskMuteEnabled', $event ?? false)">自定义</YdSwitch></span></template>
        <YdSwitch :model-value="Boolean(valueFor('riskMuteEnabled'))" :disabled="disabled || isInherited('riskMuteEnabled')" @update:model-value="update('riskMuteEnabled', $event ?? false)" />
      </YdFormItem>
      <div v-if="valueFor('riskMuteEnabled')" class="qa-form-grid">
        <YdFormItem v-for="field in muteFields" :key="field.key">
          <template #label><span class="qa-field-heading"><span>{{ field.label }}</span><YdSwitch v-if="allowInheritance" :model-value="!isInherited(field.key)" :disabled="disabled" :aria-label="`自定义${field.label}`" @update:model-value="setOverride(field.key, $event ?? false)">自定义</YdSwitch></span></template>
          <YdNumberField :min="0" :max="field.max" :model-value="numberFor(field.key)" :disabled="disabled || isInherited(field.key)" @update:model-value="setNumber(field.key, $event)" />
        </YdFormItem>
      </div>
    </YdSettingsSection>
  </YdForm>
</template>
