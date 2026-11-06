import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type {
  BindSettings,
  BridgeState,
  ConnectionOption,
  QqRobotNotice,
  GroupSettings,
  ImageTemplateDetail,
  ImageTemplateSummary,
  PageResult,
  RenderStatus,
  ResourceCard,
} from '../types'

export function createQqRobotApi(sdk: YuDreamPluginSdk) {
  return {
    bindSettings: () => sdk.http.get<BindSettings>('/admin/settings/bind'),
    saveBindSettings: (settings: BindSettings) =>
      sdk.http.request<BindSettings>('/admin/settings/bind', { method: 'PUT', data: settings }),

    groups: (page: number, size: number) =>
      sdk.http.get<PageResult<GroupSettings>>(`/admin/groups?page=${page}&size=${size}`),
    saveGroup: (group: GroupSettings) =>
      sdk.http.request<GroupSettings>('/admin/groups', { method: 'PUT', data: group }),
    deleteGroup: (groupId: string) =>
      sdk.http.request<{ deleted: boolean }>(`/admin/groups/${encodeURIComponent(groupId)}`, { method: 'DELETE' }),

    connections: () => sdk.http.get<ConnectionOption[]>('/admin/connections'),
    availableGroups: (connectionId: string) =>
      sdk.http.get<{ id: string, name: string }[]>(`/admin/options/groups?connectionId=${encodeURIComponent(connectionId)}`),

    cards: (page: number, size: number, keyword = '') =>
      sdk.http.get<PageResult<ResourceCard>>(`/resources?page=${page}&size=${size}&keyword=${encodeURIComponent(keyword)}`),
    saveCard: (card: Partial<ResourceCard>) =>
      sdk.http.request<ResourceCard>('/admin/resources', { method: 'PUT', data: card }),
    deleteCard: (id: string) =>
      sdk.http.request<{ deleted: boolean }>(`/admin/resources/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    refreshCard: (id: string) =>
      sdk.http.request<{ refreshed: boolean }>(`/admin/resources/${encodeURIComponent(id)}/refresh`, { method: 'POST' }),

    bridge: () => sdk.http.get<BridgeState>('/admin/bridge'),
    rotateToken: () => sdk.http.request<{ token: string }>('/admin/bridge/rotate-token', { method: 'POST' }),
    kick: (serverId: string, player: string, message: string) =>
      sdk.http.request<{ sent: boolean }>('/admin/bridge/kick', { method: 'POST', data: { serverId, player, message } }),

    renderStatus: () => sdk.http.get<RenderStatus>('/admin/render/status'),
    notices: () => sdk.http.get<{ notices: QqRobotNotice[] }>('/notices'),

    templates: (keyword = '', category = '') =>
      sdk.http.get<{ records: ImageTemplateSummary[] }>(
        `/admin/templates?keyword=${encodeURIComponent(keyword)}&category=${encodeURIComponent(category)}`),
    template: (id: string) =>
      sdk.http.get<ImageTemplateDetail>(`/admin/templates/${encodeURIComponent(id)}`),
    saveTemplate: (template: Partial<ImageTemplateDetail>) =>
      sdk.http.request<ImageTemplateDetail>('/admin/templates', { method: 'PUT', data: template }),
    deleteTemplate: (id: string) =>
      sdk.http.request<{ deleted: boolean }>(`/admin/templates/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    uploadTemplateCover: async (id: string, file: File) => {
      const form = new FormData()
      form.append('file', file)
      const data = await sdk.http.request<{ cover: string }>(
        `/admin/templates/${encodeURIComponent(id)}/cover`, { method: 'POST', data: form })
      return data.cover
    },
  }
}

export type QqRobotApi = ReturnType<typeof createQqRobotApi>
