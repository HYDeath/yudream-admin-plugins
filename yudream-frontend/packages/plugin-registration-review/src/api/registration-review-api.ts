import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { Page, ReviewApplication, ReviewMeta, SettingSchema, SettingValues } from '../types'

function query(params: Record<string, string | number | undefined>) {
  const value = new URLSearchParams()
  for (const [key, item] of Object.entries(params)) {
    if (item !== undefined && item !== '') value.set(key, String(item))
  }
  const text = value.toString()
  return text ? `?${text}` : ''
}

export function createRegistrationReviewApi(sdk: YuDreamPluginSdk) {
  const { http } = sdk
  return {
    meta: () => http.get<ReviewMeta>('/me/meta'),
    myApplications: () => http.get<{ records: ReviewApplication[] }>('/me/applications').then(body => body.records ?? []),
    start: (method = 'quiz') => http.post<ReviewApplication>('/me/applications/start', { method }),
    application: (id: string) => http.get<ReviewApplication>(`/me/applications/${encodeURIComponent(id)}`),
    refresh: (id: string) => http.post<ReviewApplication>(`/me/applications/${encodeURIComponent(id)}/refresh`, {}),
    adminApplications: (status: string, keyword: string, page: number, size: number) =>
      http.get<Page<ReviewApplication>>(`/admin/applications${query({ status, keyword, page, size })}`),
    approve: (id: string) => http.post<{ success: boolean, application: ReviewApplication }>(`/admin/applications/${encodeURIComponent(id)}/approve`, {}),
    reject: (id: string, reason: string) => http.post<{ success: boolean, application: ReviewApplication }>(`/admin/applications/${encodeURIComponent(id)}/reject`, { reason }),
    settingSchema: () => http.get<SettingSchema>('/admin/settings/schema'),
    settings: () => http.get<{ values: SettingValues }>('/admin/settings').then(body => body.values ?? {}),
    saveSettings: (values: SettingValues) => http.request<{ values: SettingValues }>('/admin/settings', { method: 'PUT', data: values }).then(body => body.values ?? {}),
  }
}

export type RegistrationReviewApi = ReturnType<typeof createRegistrationReviewApi>
