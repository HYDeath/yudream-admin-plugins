import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { QqSettings, SettingsSchema } from '../types'
export function createQqAuthApi(sdk: YuDreamPluginSdk) {
  const { http } = sdk
  return {
    schema: () => http.get<SettingsSchema>('/admin/settings/schema'),
    settings: () => http.get<{ values: QqSettings }>('/admin/settings').then(body => body.values),
    save: (values: Record<string, unknown>) => http.request<{ values: QqSettings }>('/admin/settings', { method: 'PUT', data: values }).then(body => body.values),
  }
}
export type QqAuthApi = ReturnType<typeof createQqAuthApi>
