import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { ref } from 'vue'
import { createEasyBotApi } from '../api/easybot-api'
import type { RenderStatus } from '../types'

/**
 * 渲染是宿主能力（消息渲染 / Headless Chromium），插件只消费。
 * 页面打开时自检一次，未就绪时展示去启用能力的引导，而不是让图片静默变空白。
 */
export function useRenderStatus(sdk: YuDreamPluginSdk) {
  const api = createEasyBotApi(sdk)
  const status = ref<RenderStatus | null>(null)
  const checking = ref(false)

  async function check() {
    checking.value = true
    try {
      status.value = await api.renderStatus()
    }
    catch (error) {
      status.value = {
        ready: false,
        message: String((error as Error)?.message ?? '渲染自检失败'),
        capability: 'message-render',
        capabilityName: '消息渲染',
        latencyMs: 0,
        hint: '请在「平台 → 能力目录 → 消息渲染」确认能力已启用，并检查服务地址可从后台访问。',
      }
    }
    finally {
      checking.value = false
    }
  }

  return { status, checking, check }
}
