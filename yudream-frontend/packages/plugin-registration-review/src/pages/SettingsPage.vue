<script setup lang="ts">
import type { RegistrationReviewApi } from '../api/registration-review-api'
import type { SettingField, SettingSchema, SettingValues } from '../types'
import { YdButton, YdInput, YdNumberField, YdSelect, YdPageHeader, YdSwitch, YdTag, useYdToast } from '@yudream/components'
import { computed, onMounted, ref } from 'vue'

const props = defineProps<{ model: { api: RegistrationReviewApi } }>()
const api = props.model.api
const toast = useYdToast()
const loading = ref(false)
const saving = ref(false)
const schema = ref<SettingSchema | null>(null)
const values = ref<SettingValues>({})
const original = ref<SettingValues>({})
const dirty = computed(() => JSON.stringify(values.value) !== JSON.stringify(original.value))

function text(field: SettingField) { const value = values.value[field.key]; return Array.isArray(value) ? value.join(', ') : String(value ?? '') }
function setField(field: SettingField, value: unknown) { values.value = { ...values.value, [field.key]: value } }
function setList(field: SettingField, value: string) { setField(field, value.split(/[,，\s]+/).filter(Boolean)) }
async function load() {
  loading.value = true
  try {
    const [loadedSchema, current] = await Promise.all([api.settingSchema(), api.settings()])
    schema.value = loadedSchema
    const merged: SettingValues = {}
    for (const section of loadedSchema.sections) for (const field of section.fields) merged[field.key] = current[field.key] ?? field.default ?? (field.type === 'switch' ? false : field.type === 'tags' ? [] : field.type === 'number' ? 0 : '')
    values.value = merged
    original.value = JSON.parse(JSON.stringify(merged))
  }
  catch (error) { toast.error(String((error as Error)?.message ?? '设置加载失败')) }
  finally { loading.value = false }
}
async function save() {
  saving.value = true
  try { const saved = await api.saveSettings(values.value); values.value = saved; original.value = JSON.parse(JSON.stringify(saved)); toast.success('设置已保存') }
  catch (error) { toast.error(String((error as Error)?.message ?? '保存失败')) }
  finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <div class="p-4 flex flex-col gap-4">
    <YdPageHeader title="审核设置" description="审核模式、及格线、抽题范围和简答判定方式。">
      <YdButton :disabled="!dirty" :loading="saving" @click="save">保存设置</YdButton>
    </YdPageHeader>
    <div v-if="loading" class="rr-card rr-empty">加载中…</div>
    <div v-else-if="schema" class="flex flex-col gap-4">
      <div v-for="section in schema.sections" :key="section.code" class="rr-card">
        <div class="rr-title">{{ section.title }}</div>
        <p v-if="section.description" class="rr-muted">{{ section.description }}</p>
        <div class="rr-form mt-4">
          <div v-for="field in section.fields" :key="field.key" class="rr-row">
            <span class="rr-label">{{ field.label }}</span>
            <div class="rr-control">
              <div v-if="field.type === 'switch'" class="flex items-center gap-3">
                <YdSwitch :model-value="Boolean(values[field.key])" @update:model-value="setField(field, $event)" />
                <YdTag :variant="values[field.key] ? 'default' : 'outline'">{{ values[field.key] ? '已开启' : '已关闭' }}</YdTag>
              </div>
              <YdNumberField v-else-if="field.type === 'number'" :min="field.min" :max="field.max" :model-value="Number(values[field.key])" @update:model-value="setField(field, $event)" />
              <YdSelect v-else-if="field.type === 'select'" :model-value="text(field)" :options="field.options ?? []" @update:model-value="setField(field, $event)" />
              <YdInput v-else-if="field.type === 'tags'" :model-value="text(field)" placeholder="逗号分隔" @update:model-value="setList(field, String($event))" />
              <YdInput v-else :model-value="text(field)" @update:model-value="setField(field, String($event))" />
              <p v-if="field.help" class="rr-muted">{{ field.help }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
