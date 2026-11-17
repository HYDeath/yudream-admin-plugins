<script setup lang="ts">
import {
  YdAction,
  YdAlert,
  YdFilePicker,
  YdTextarea,
  YdSettingsLayout,
  YdSettingsSection,
  useYdModal,
  YdButton,
  YdCheckbox,
  YdInput,
  YdPageHeader,
  YdSelect,
  YdSlider,
  YdSwitch,
  useYdToast,
} from '@yudream/components'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { createQqRobotApi } from '../api/qqrobot-api'
import type { ImageTemplateDetail, ImageTemplateSummary, TemplateSchema } from '../types'

const props = defineProps<{ sdk: YuDreamPluginSdk }>()
const api = createQqRobotApi(props.sdk)
const toast = useYdToast()
const modal = useYdModal()
const loadError = ref('')
const previewLoading = ref(false)
let previewSequence = 0
let openSequence = 0

const records = ref<ImageTemplateSummary[]>([])
const keyword = ref('')
const category = ref('全部')
const loading = ref(false)
const saving = ref(false)
const detail = ref<ImageTemplateDetail | null>(null)
const previewStamp = ref(Date.now())
const activeGroup = ref('display')
const serverRows = ref<{ id: string, name: string, online: string, edition: string }[]>([])
const serverAvailable = ref(true)

const isServerList = computed(() => detail.value?.id === 'builtin-server-list')

const groups = computed(() => [
  { value: 'display', label: '基础显示设置' },
  ...(isServerList.value ? [{ value: 'servers', label: '服务器筛选' }] : []),
  { value: 'avatar', label: '玩家头像设置' },
  { value: 'background', label: '背景设置' },
  { value: 'css', label: '自定义 CSS' },
  { value: 'cache', label: '缓存与性能' },
  { value: 'meta', label: '模板信息' },
])

const themeOptions = computed(() => optionsFrom(detail.value?.schema?.themes, [
  { label: '液态玻璃', value: 'liquid-glass' },
  { label: '半透明', value: 'solid' },
  { label: '纯色', value: 'flat' },
]))
const resolutionOptions = computed(() => optionsFrom(detail.value?.schema?.resolutions, [
  { label: '标准 1×（900px）', value: 'standard' },
  { label: '高清 1.2×（1080px）', value: 'high' },
  { label: '极清 1.6×（1440px）', value: 'ultra' },
  { label: '超清 2×（1800px）', value: 'xlarge' },
  { label: '自定义宽度（900–1920px）', value: 'custom' },
]))
const avatarSourceOptions = computed(() => optionsFrom(detail.value?.schema?.avatarSources, [
  { label: '正版头像', value: 'mojang' },
  { label: '不显示头像', value: 'none' },
]))
const backgroundSourceOptions = computed(() => optionsFrom(detail.value?.schema?.backgroundSources, [
  { label: '固定图片', value: 'url' },
  { label: '随机图片 API', value: 'random' },
  { label: '不设置', value: 'none' },
]))
const apiSources = computed(() => detail.value?.schema?.apiSources ?? [])

const categories = computed(() => {
  const set = new Set(records.value.map(item => item.category))
  return ['全部', ...Array.from(set)]
})

const settings = computed(() => detail.value?.settings ?? {})
const includedServers = computed({
  get: () => toStringList(settings.value.includedServers),
  set: (value: string[]) => patch({ includedServers: value }),
})
const excludedServers = computed({
  get: () => toStringList(settings.value.excludedServers),
  set: (value: string[]) => patch({ excludedServers: value }),
})

const previewUrl = ref('')
const previewError = ref('')

function optionsFrom(value: { value: string, label: string }[] | undefined, fallback: { value: string, label: string }[]) {
  return value?.length ? value : fallback
}

function toStringList(value: unknown): string[] {
  return Array.isArray(value) ? value.map(item => String(item)) : []
}

/** 表单改的是 settings 快照；保存时才整体提交，避免每改一次就重建 HTML。 */
let previewTimer: ReturnType<typeof setTimeout> | null = null

function patch(values: Record<string, unknown>) {
  if (!detail.value) return
  detail.value.settings = { ...detail.value.settings, ...values }
  // 表单改动先落到本地快照，预览做 400ms 防抖；保存时才写库。
  if (previewTimer) clearTimeout(previewTimer)
  previewTimer = setTimeout(() => { void loadPreview() }, 400)
}

function value<T>(key: string, fallback: T): T {
  const current = settings.value[key]
  return (current === undefined || current === null ? fallback : current) as T
}

function num(key: string, fallback: number) {
  const current = Number(settings.value[key])
  return Number.isFinite(current) ? current : fallback
}

function apiEnabled(key: string) {
  const selected = settings.value.apiSources
  return !Array.isArray(selected) || selected.length === 0 || selected.map(String).includes(key)
}

function toggleApi(key: string, enabled: boolean) {
  const configured = settings.value.apiSources
  const selected = Array.isArray(configured) && configured.length
    ? configured.map(String)
    : apiSources.value.map(source => source.key)
  const next = enabled
    ? Array.from(new Set([...selected, key]))
    : selected.filter(item => item !== key)
  if (!next.length) {
    toast.info('至少保留一个数据源；旧模板的空列表表示启用全部数据源')
    return
  }
  patch({ apiSources: next })
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await api.templates(keyword.value, category.value === '全部' ? '' : category.value)
    records.value = data.records ?? []
  } catch (error) {
    loadError.value = String((error as Error)?.message ?? '模板列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadServers() {
  try {
    const data = await api.servers()
    serverRows.value = data.servers ?? []
    serverAvailable.value = data.available
  } catch {
    serverRows.value = []
  }
}

async function open(id: string) {
  const request = ++openSequence
  closePreview()
  detail.value = null
  try {
    const loaded = await api.template(id)
    if (request !== openSequence) return
    detail.value = loaded
    detail.value.settings = { ...(detail.value.settings ?? {}) }
    activeGroup.value = 'display'
    if (id === 'builtin-server-list') await loadServers()
    if (request === openSequence) await loadPreview()
  } catch (error) {
    if (request === openSequence) toast.error(String((error as Error)?.message ?? '模板详情加载失败'))
  }
}

function closePreview() {
  previewSequence++
  if (previewTimer) clearTimeout(previewTimer)
  previewTimer = null
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  previewError.value = ''
  previewLoading.value = false
}
function close() {
  openSequence++
  closePreview()
  detail.value = null
}

async function save() {
  if (!detail.value || saving.value) return
  saving.value = true
  const request = openSequence
  const id = detail.value.id
  try {
    const saved = await api.saveTemplate({
      id: detail.value.id,
      name: detail.value.name,
      version: detail.value.version,
      description: detail.value.description,
      author: detail.value.author,
      category: detail.value.category,
      tags: detail.value.tags,
      background: backgroundUrl.value,
      html: detail.value.html,
      width: detail.value.width,
      settings: detail.value.settings,
    })
    if (request === openSequence && detail.value?.id === id) {
      detail.value = { ...detail.value, ...saved, settings: { ...(saved.settings ?? {}) } }
      await loadPreview()
    }
    toast.success('模板配置已保存')
    await load()
  } catch (error) {
    toast.error(String((error as Error)?.message ?? '保存失败'))
  } finally {
    saving.value = false
  }
}

const backgroundUrl = computed(() => {
  const source = value<string>('backgroundSource', 'none')
  if (source === 'random') return value('backgroundApi', '')
  return detail.value?.background ?? ''
})

function refreshPreview() {
  void loadPreview()
}

/**
 * 模板出图是管理接口，`<img src>` 不会带 Authorization，会拿到 400 JSON。
 * 统一走 sdk.http.blob() 再转 object URL。
 */
async function loadPreview() {
  if (!detail.value) return
  const request = ++previewSequence
  const id = detail.value.id
  const stamp = Date.now()
  previewLoading.value = true
  previewError.value = ''
  try {
    const response = await props.sdk.http.blob(
      `/templates/${encodeURIComponent(id)}/render?t=${stamp}`,
      { method: 'POST', data: { variables: {}, settings: { ...detail.value.settings } } },
    )
    if (request !== previewSequence || detail.value?.id !== id) return
    const next = URL.createObjectURL(response.data)
    if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = next
    previewStamp.value = stamp
  } catch (error) {
    if (request !== previewSequence) return
    previewError.value = String((error as Error)?.message ?? '预览出图失败')
  } finally {
    if (request === previewSequence) previewLoading.value = false
  }
}

async function createBlank() {
  try {
    const saved = await api.saveTemplate({
      name: '新模板',
      version: '1.0.0',
      description: '',
      author: '',
      category: '通用',
      tags: [],
      width: 640,
      settings: {},
      html: '<!doctype html><html><head><meta charset="utf-8"></head>'
        + '<body style="margin:0;background:#111;color:#fff;font-family:sans-serif">'
        + '<div id="template-root" style="padding:24px;width:640px">{{title}}</div></body></html>',
    })
    await load()
    await open(String(saved.id ?? ''))
  } catch (error) {
    toast.error(String((error as Error)?.message ?? '创建失败'))
  }
}

async function uploadCover(files: File[]) {
  const file = files[0]
  if (!file || !detail.value) return
  const request = openSequence
  const id = detail.value.id
  try {
    const cover = await api.uploadTemplateCover(id, file)
    if (request === openSequence && detail.value?.id === id) detail.value.cover = cover
    await load()
    toast.success('封面已更新')
  } catch (error) {
    toast.error(String((error as Error)?.message ?? '封面上传失败'))
  }
}

async function remove(item: ImageTemplateSummary) {
  if (!await modal.confirm({ title: '删除模板', content: `确定删除模板「${item.name}」？`, confirmButtonText: '删除' })) return
  try {
    await api.deleteTemplate(item.id)
    if (detail.value?.id === item.id) close()
    await load()
  } catch (error) {
    toast.error(String((error as Error)?.message ?? '删除失败'))
  }
}

function toggleServer(id: string, keep: boolean) {
  const current = keep ? includedServers.value : excludedServers.value
  const other = keep ? excludedServers.value : includedServers.value
  const next = current.includes(id) ? current.filter(item => item !== id) : [...current, id]
  // 同一台服务器不能既保留又排除：勾选一边会自动从另一边移除。
  if (next.includes(id)) {
    const cleaned = other.filter(item => item !== id)
    if (keep) excludedServers.value = cleaned
    else includedServers.value = cleaned
  }
  if (keep) includedServers.value = next
  else excludedServers.value = next
}

onMounted(load)
onBeforeUnmount(close)
</script>

<template>
  <div class="qqrobot-page">
    <YdPageHeader
      title="图片模板"
      description="服务器在线列表按分组表单配置，出图统一走后台「消息渲染」能力"
    />

    <div class="qqrobot-body">
      <div class="qqrobot-toolbar">
        <div class="qqrobot-toolbar-info">
          <strong>模板列表</strong>
          <span class="qqrobot-muted">共 {{ records.length }} 个模板，服务端插件可直接按 ID 调用渲染</span>
        </div>
        <div class="qqrobot-toolbar-actions">
          <YdInput v-model="keyword" class="qqrobot-search" placeholder="搜索名称、描述或标签" @keyup.enter="load" />
          <YdSelect v-model="category" class="qqrobot-connection"
            :options="categories.map(item => ({ label: item, value: item }))" />
          <YdButton variant="outline" @click="load">搜索</YdButton>
          <YdButton variant="outline" @click="load">刷新</YdButton>
          <YdButton @click="createBlank">新建模板</YdButton>
        </div>
      </div>

      <YdAlert v-if="loadError" variant="destructive" title="模板加载失败" :description="loadError" />
      <div v-if="loading" class="qqrobot-empty">加载中…</div>
      <div v-else-if="!records.length && !loadError && !detail" class="qqrobot-empty">还没有模板，点「新建模板」开始。</div>
      <div v-else-if="!detail" class="qqrobot-template-grid">
        <article v-for="item in records" :key="item.id" class="qqrobot-template-card">
          <YdAction class="qqrobot-template-cover" :aria-label="`配置模板：${item.name}`" @click="open(item.id)">
            <img v-if="item.cover" :src="item.cover" :alt="item.name">
            <div v-else class="qqrobot-template-fallback">{{ item.name.slice(0, 2) }}</div>
            <span class="qqrobot-template-version">v{{ item.version }}</span>
            <span v-if="item.builtin" class="qqrobot-template-builtin">内置</span>
          </YdAction>
          <div class="qqrobot-template-body">
            <h3>{{ item.name }}</h3>
            <p>{{ item.description || '暂无描述' }}</p>
            <div class="qqrobot-template-tags">
              <span v-for="tag in item.tags" :key="tag" class="qqrobot-chip">{{ tag }}</span>
            </div>
          </div>
          <footer class="qqrobot-template-foot">
            <span class="qqrobot-muted">{{ item.author }}</span>
            <div class="qqrobot-template-actions">
              <YdButton size="sm" variant="outline" @click="open(item.id)">配置</YdButton>
              <YdButton size="sm" variant="ghost" @click="remove(item)">删除</YdButton>
            </div>
          </footer>
        </article>
      </div>

      <section v-if="detail" class="qqrobot-tpl-editor">
        <header class="qqrobot-tpl-head">
          <div>
            <YdButton variant="ghost" size="sm" @click="close">返回模板列表</YdButton>
            <h2>{{ detail.name }}</h2>
            <p>v{{ detail.version }} · {{ detail.width }}px 基准宽度 · {{ detail.author }}</p>
          </div>
          <div class="qqrobot-tpl-head-actions">
            <YdFilePicker accept="image/png,image/jpeg,image/svg+xml" label="上传封面" @select="uploadCover"><template #default="{ open, triggerAttrs }"><YdButton v-bind="triggerAttrs" variant="outline" @click="open">上传封面</YdButton></template></YdFilePicker>
            <YdButton variant="ghost" @click="close">关闭</YdButton>
            <YdButton variant="outline" @click="refreshPreview">刷新预览</YdButton>
            <YdButton :loading="saving" @click="save">保存配置</YdButton>
          </div>
        </header>

        <div class="qqrobot-tpl-body">


          <YdSettingsLayout v-model="activeGroup" :list="groups" surface align="start" class="qqrobot-tpl-form">
            <template #display><YdSettingsSection title="基础显示设置" description="">
              <div class="qqrobot-grid">
                <label class="qqrobot-field">
                  <span>标题</span>
                  <YdInput :model-value="value('title', detail.name)" @update:model-value="patch({ title: $event })" />
                </label>
                <label class="qqrobot-field">
                  <span>自定义信息（显示在标题上方）</span>
                  <YdInput :model-value="value('site', '')" @update:model-value="patch({ site: $event })" />
                </label>
                <label class="qqrobot-field">
                  <span>界面主题</span>
                  <YdSelect :model-value="value<string>('theme', 'liquid-glass')" :options="themeOptions"
                    @update:model-value="patch({ theme: $event })" />
                </label>
                <label class="qqrobot-field">
                  <span>渲染清晰度</span>
                  <YdSelect :model-value="value('resolution', 'standard')" :options="resolutionOptions"
                    @update:model-value="patch({ resolution: $event })" />
                  <small class="qqrobot-muted">只影响输出像素，不改变模板分辨率</small>
                </label>
                <label v-if="value<string>('resolution', 'standard') === 'custom'" class="qqrobot-field">
                  <span>自定义输出宽度（px）</span>
                  <YdInput :model-value="String(num('outputWidth', detail.width))" type="number"
                    min="900" max="1920" @update:model-value="patch({ outputWidth: Math.min(1920, Math.max(900, Number($event) || 900)) })" />
                  <small class="qqrobot-muted">大尺寸图片更清晰，也会增加渲染耗时和文件大小</small>
                </label>
                <label class="qqrobot-field">
                  <span>字体</span>
                  <YdSelect :model-value="value('font', 'noto-sans-sc')" :options="[
                    { label: 'Noto Sans SC（内置）', value: 'noto-sans-sc' },
                    { label: '思源黑体', value: 'source-han-sans' },
                    { label: '系统字体', value: 'system' },
                  ]" @update:model-value="patch({ font: $event })" />
                </label>
                <label v-if="value<string>('theme', 'liquid-glass') === 'liquid-glass'" class="qqrobot-field">
                  <span>液态玻璃外观</span>
                  <YdSelect :model-value="value('glass', 'clear')" :options="[
                    { label: '清澈（背景更通透）', value: 'clear' },
                    { label: '雾面（更强调文字）', value: 'frosted' },
                  ]" @update:model-value="patch({ glass: $event })" />
                </label>
                <div v-if="value<string>('theme', 'liquid-glass') === 'liquid-glass'" class="qqrobot-field qqrobot-wide">
                  <span>边缘折射范围：{{ num('refraction', 100) }}%</span>
                  <YdSlider :model-value="[num('refraction', 100)]" :min="0" :max="100" :step="5"
                    :tooltip="false" @update:model-value="patch({ refraction: $event?.[0] ?? 100 })" />
                </div>
                <div v-if="value<string>('theme', 'liquid-glass') === 'liquid-glass'" class="qqrobot-field qqrobot-wide">
                  <span>光照方向：{{ num('lightAngle', 315) }}°</span>
                  <YdSlider :model-value="[num('lightAngle', 315)]" :min="0" :max="360" :step="5"
                    :tooltip="false" @update:model-value="patch({ lightAngle: $event?.[0] ?? 315 })" />
                </div>
              </div>
              <div class="qqrobot-check-grid">
                <YdCheckbox :model-value="value('showServerCount', true)"
                  @update:model-value="patch({ showServerCount: Boolean($event) })">显示服务器数量</YdCheckbox>
                <YdCheckbox :model-value="value('showTotalOnline', true)"
                  @update:model-value="patch({ showTotalOnline: Boolean($event) })">显示总在线玩家</YdCheckbox>
                <YdCheckbox :model-value="value('showTime', true)"
                  @update:model-value="patch({ showTime: Boolean($event) })">显示当前时间</YdCheckbox>
                <YdCheckbox :model-value="value('showStatus', true)"
                  @update:model-value="patch({ showStatus: Boolean($event) })">显示在线状态</YdCheckbox>
                <YdCheckbox :model-value="value('showOnlyOnline', true)"
                  @update:model-value="patch({ showOnlyOnline: Boolean($event) })">只显示在线服务器</YdCheckbox>
                <YdCheckbox :model-value="value('showEdition', true)"
                  @update:model-value="patch({ showEdition: Boolean($event) })">显示基岩版标识</YdCheckbox>
              </div>
              <div v-if="apiSources.length" class="qqrobot-api-catalog">
                <div class="qqrobot-api-catalog-head">
                  <div>
                    <strong>内置 API / 数据源</strong>
                    <small>模板只读取后台注入的数据，不会在 HTML 中自行请求外部地址。</small>
                  </div>
                  <span class="qqrobot-chip on">{{ apiSources.length }} 项可用</span>
                </div>
                <label v-for="source in apiSources" :key="source.key" class="qqrobot-api-row">
                  <YdCheckbox :model-value="apiEnabled(source.key)"
                    @update:model-value="toggleApi(source.key, Boolean($event))" />
                  <span>
                    <strong>{{ source.label }}</strong><code>{{ source.key }}</code>
                    <small>{{ source.description }} · {{ source.provider }}</small>
                  </span>
                </label>
              </div>
            </YdSettingsSection></template>

            <template #servers><YdSettingsSection title="服务器筛选" description="列表来自 Minecraft 服务器插件；勾选「保留」只显示这些，勾选「排除」隐藏这些">
              <div v-if="!serverAvailable" class="qqrobot-blocked">
                <strong>没有检测到 Minecraft 服务器插件</strong>
                <p>启用 <code>minecraft-server</code> 插件并添加服务器后，这里会自动出现可选项。</p>
              </div>
              <div v-else-if="!serverRows.length" class="qqrobot-blocked">
                <strong>服务器列表为空</strong>
                <p>先到「Minecraft 服务器」插件里添加服务器，再回来配置筛选。</p>
              </div>
              <div v-else class="qqrobot-filter-list">
                <div v-for="server in serverRows" :key="server.id" class="qqrobot-filter-row">
                  <span class="qqrobot-filter-name">
                    <strong>{{ server.name }}</strong>
                    <small>{{ server.edition }} · {{ server.online }} 人在线</small>
                  </span>
                  <div class="qqrobot-filter-actions">
                    <YdCheckbox :model-value="includedServers.includes(server.id)"
                      @update:model-value="toggleServer(server.id, true)">保留</YdCheckbox>
                    <YdCheckbox :model-value="excludedServers.includes(server.id)"
                      @update:model-value="toggleServer(server.id, false)">排除</YdCheckbox>
                  </div>
                </div>
              </div>
              <p class="qqrobot-muted">
                已保留 {{ includedServers.length }} 个，已排除 {{ excludedServers.length }} 个；
                两边都为空时显示全部启用的服务器。
              </p>
            </YdSettingsSection></template>

            <template #avatar><YdSettingsSection title="玩家头像设置" description="">
              <div class="qqrobot-grid">
                <label class="qqrobot-field">
                  <span>玩家头像源</span>
                  <YdSelect :model-value="value('avatarSource', 'mojang')" :options="avatarSourceOptions"
                    @update:model-value="patch({ avatarSource: $event })" />
                </label>
                <label class="qqrobot-field">
                  <span>头像样式</span>
                  <YdSelect :model-value="value('avatarStyle', 'hat')" :options="[
                    { label: '显示帽子/头发外层', value: 'hat' },
                    { label: '只显示头部', value: 'head' },
                  ]" @update:model-value="patch({ avatarStyle: $event })" />
                </label>
              </div>
            </YdSettingsSection></template>

            <template #background><YdSettingsSection title="背景设置" description="">
              <div class="qqrobot-grid">
                <label class="qqrobot-field">
                  <span>背景来源</span>
                  <YdSelect :model-value="value<string>('backgroundSource', 'none')" :options="backgroundSourceOptions"
                    @update:model-value="patch({ backgroundSource: $event })" />
                </label>
                <label v-if="value<string>('backgroundSource', 'none') === 'random'" class="qqrobot-field">
                  <span>随机图片分类</span>
                  <YdInput :model-value="value('backgroundCategory', '二次元')"
                    @update:model-value="patch({ backgroundCategory: $event })" />
                </label>
                <label v-if="value<string>('backgroundSource', 'none') === 'random'" class="qqrobot-field qqrobot-wide">
                  <span>随机图片 API</span>
                  <YdInput :model-value="value('backgroundApi', '')"
                    placeholder="https://example.com/random?size=1920x1080"
                    @update:model-value="patch({ backgroundApi: $event })" />
                </label>
                <label v-if="value<string>('backgroundSource', 'none') === 'url'" class="qqrobot-field qqrobot-wide">
                  <span>背景图片地址</span>
                  <YdInput v-model="detail.background" placeholder="https://example.com/bg.jpg" />
                  <small class="qqrobot-muted">后台会先下载并转成 data URI；渲染容器不允许外链子资源</small>
                </label>
                <div class="qqrobot-field qqrobot-wide">
                  <span>背景暗化程度：{{ num('darken', 45) }}%</span>
                  <YdSlider :model-value="[num('darken', 45)]" :min="0" :max="100" :step="5"
                    :tooltip="false" @update:model-value="patch({ darken: $event?.[0] ?? 45 })" />
                </div>
              </div>
            </YdSettingsSection></template>

            <template #css><YdSettingsSection title="自定义 CSS" description="用于微调字体、颜色、间距；只作用在这张模板上">
              <YdTextarea :rows="8" spellcheck="false" :model-value="String(settings.customCss ?? '')" placeholder=".title { color: #ffffff; }" @update:model-value="patch({ customCss: String($event ?? '') })" />
            </YdSettingsSection></template>

            <template #cache><YdSettingsSection title="缓存与性能" description="">
              <div class="qqrobot-switch-list">
                <label class="qqrobot-switch-row">
                  <span><strong>缓存服务器和玩家数据</strong><small>减少向服务端查询的次数；随机图 API 不受影响</small></span>
                  <YdSwitch :model-value="num('cacheSeconds', 30) > 0"
                    @update:model-value="patch({ cacheSeconds: $event ? 30 : 0 })" />
                </label>
                <label class="qqrobot-switch-row">
                  <span><strong>缓存玩家头像</strong><small>头像变化不频繁，长缓存可以明显减少出图时间</small></span>
                  <YdSwitch :model-value="num('avatarCacheSeconds', 3600) > 0"
                    @update:model-value="patch({ avatarCacheSeconds: $event ? 3600 : 0 })" />
                </label>
                <label class="qqrobot-switch-row">
                  <span><strong>开启调试日志</strong><small>记录配置、出图耗时与异常，排障时再打开</small></span>
                  <YdSwitch :model-value="value('debug', false)"
                    @update:model-value="patch({ debug: Boolean($event) })" />
                </label>
              </div>
              <div class="qqrobot-grid">
                <label class="qqrobot-field">
                  <span>数据缓存时间（秒）</span>
                  <YdInput :model-value="String(num('cacheSeconds', 30))" type="number"
                    @update:model-value="patch({ cacheSeconds: Number($event) || 0 })" />
                </label>
                <label class="qqrobot-field">
                  <span>头像缓存时间（秒）</span>
                  <YdInput :model-value="String(num('avatarCacheSeconds', 3600))" type="number"
                    @update:model-value="patch({ avatarCacheSeconds: Number($event) || 0 })" />
                </label>
              </div>
            </YdSettingsSection></template>

            <template #meta><YdSettingsSection title="模板信息" description="">
              <div class="qqrobot-grid">
                <label class="qqrobot-field">
                  <span>名称</span>
                  <YdInput v-model="detail.name" />
                </label>
                <label class="qqrobot-field">
                  <span>版本</span>
                  <YdInput v-model="detail.version" />
                </label>
                <label class="qqrobot-field">
                  <span>分类</span>
                  <YdInput v-model="detail.category" />
                </label>
                <label class="qqrobot-field">
                  <span>模板基准宽度（px）</span>
                  <YdInput :model-value="String(detail.width)" type="number" min="320" max="1920"
                    @update:model-value="detail.width = Math.min(1920, Math.max(320, Number($event) || 320))" />
                  <small class="qqrobot-muted">普通模板会按这个宽度出图；服务器列表由清晰度设置控制</small>
                </label>
                <label class="qqrobot-field">
                  <span>作者</span>
                  <YdInput v-model="detail.author" />
                </label>
                <label class="qqrobot-field qqrobot-wide">
                  <span>描述</span>
                  <YdInput v-model="detail.description" />
                </label>
                <label v-if="detail.variables.length" class="qqrobot-field qqrobot-wide">
                  <span>模板变量</span>
                  <div class="qqrobot-template-tags">
                    <code v-for="name in detail.variables" :key="name" class="qqrobot-chip">{{ name }}</code>
                  </div>
                </label>
              </div>
              <details class="qqrobot-advanced">
                <summary>高级：直接编辑模板 HTML</summary>
                <YdTextarea v-model="detail.html" :rows="14" spellcheck="false" aria-label="模板 HTML" />
                <small class="qqrobot-muted">服务器在线列表的 HTML 由上面的配置生成，手改会在下次保存时被覆盖。</small>
              </details>
            </YdSettingsSection></template>
          </YdSettingsLayout>

          <aside class="qqrobot-tpl-preview">
            <div class="qqrobot-tpl-preview-head">
              <strong>实时预览</strong>
              <YdButton size="sm" variant="ghost" :loading="previewLoading" @click="refreshPreview">刷新</YdButton>
            </div>
            <p v-if="previewError" role="alert" class="qqrobot-preview-error">{{ previewError }}</p>
            <img v-if="previewUrl" :key="previewStamp" :src="previewUrl" alt="模板预览">
            <div v-else-if="previewLoading" class="qqrobot-preview-skeleton">正在出图…</div>
            <p class="qqrobot-muted">用示例变量出图；保存后按真实服务器数据渲染。</p>
          </aside>
        </div>
      </section>
    </div>
  </div>
</template>
