export interface Job {
  id: string; filename: string; status: 'QUEUED' | 'RENDERING' | 'READY' | 'FAILED';
  shareToken: string; previewPath: string; submittedAt: string; error?: string;
  name?: string; author?: string; createdAt?: number; width?: number; height?: number; length?: number;
  blocks?: number; volume?: number; formatVersion?: number; dataVersion?: number; renderer?: string;
  materials?: Record<string, number>;
}
export interface JobPage { records: Job[]; total: number }
export const statusName = (status: Job['status']) => ({ QUEUED: '等待渲染', RENDERING: '正在渲染', READY: '已完成', FAILED: '处理失败' })[status]
