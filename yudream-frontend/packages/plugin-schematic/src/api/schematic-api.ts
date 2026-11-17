import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { Job, JobPage } from '../types'
export function schematicApi(sdk: YuDreamPluginSdk) {
  return {
    mine: (page = 1) => sdk.http.get<JobPage>(`/me/jobs?page=${page}&size=12`),
    admin: (page = 1) => sdk.http.get<JobPage>(`/admin/jobs?page=${page}&size=12`),
    detail: (id: string) => sdk.http.get<Job>(`/me/jobs/${encodeURIComponent(id)}`),
    upload: (file: File) => { const data = new FormData(); data.append('file', file); return sdk.http.request<Job>('/me/jobs', { method: 'POST', data }) },
    remove: (id: string) => sdk.http.request(`/me/jobs/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    adminRemove: (id: string) => sdk.http.request(`/admin/jobs/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    shared: (token: string) => sdk.http.get<Job>(`/public/shared/${encodeURIComponent(token.trim())}`),
    preview: (job: Job) => sdk.http.url(job.previewPath),
  }
}
