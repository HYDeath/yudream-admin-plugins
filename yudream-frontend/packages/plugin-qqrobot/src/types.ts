export interface BindSettings {
  bindMode: 'dual' | 'trust'
  forcedServers: string[]
  accountsPerSocial: number
  codeTtlMinutes: number
  codeLength: number
  allowUnbind: boolean
  noKeywordMode: boolean
  forbidChinese: boolean
  commonPrefix: string
  codeCharset: 'numeric' | 'alnum'
  bindKeyword: string
  unbindKeyword: string
  unboundKickMessage: string
  bindSuccessMessage: string
  bindFailMessage: string
  unbindSuccessMessage: string
  unbindFailMessage: string
}

export interface GroupSettings {
  groupId: string
  displayName: string
  enabled: boolean
  bindEnabled: boolean
  cardEnabled: boolean
  quitNoticeEnabled: boolean
  quitMessage: string
  cardFormat: string
  cardMaxLength: number
}

export interface ResourceCard {
  id: string
  title: string
  subtitle: string
  description: string
  author: string
  free: boolean
  platforms: string[]
  tags: string[]
  views: number
  likes: number
  downloads: number
  status: string
  coverUrl: string
  cardUrl: string
  createdAt: string
  updatedAt: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface RenderStatus {
  ready: boolean
  message: string
  capability: string
  capabilityName: string
  latencyMs: number
  size?: string
  hint: string
}

export interface BridgeServer {
  sessionId: string
  serverId: string
  serverName: string
  connectedAt: string
  lastSeen: string
  online?: number
  max?: number
  tps?: string
  motd?: string
  version?: string
  packTextures?: number
}

export interface BridgeState {
  servers: BridgeServer[]
  token: string
  pendingBindings: number
  tokenPersistent?: boolean
  tokenMeta?: { fingerprint: string, persistent: boolean, generatedAt: string, rotatedBy?: string }
}

export interface QqRobotNotice {
  id: string
  level: string
  title: string
  message: string
  createdAt: string
  expiresAt: string
}

export interface ImageTemplateSummary {
  id: string
  name: string
  version: string
  description: string
  author: string
  category: string
  tags: string[]
  cover: string
  builtin: boolean
  variables: string[]
  width: number
  updatedAt: string
}

export interface ImageTemplateDetail {
  id: string
  name: string
  version: string
  description: string
  author: string
  category: string
  tags: string[]
  cover: string
  html: string
  width: number
  variables: string[]
  updatedAt: string
}

export interface ConnectionOption {
  id: string
  name: string
  platform: string
}

export function emptyBindSettings(): BindSettings {
  return {
    bindMode: 'dual',
    forcedServers: [],
    accountsPerSocial: 2,
    codeTtlMinutes: 5,
    codeLength: 4,
    allowUnbind: false,
    noKeywordMode: true,
    forbidChinese: true,
    commonPrefix: '',
    codeCharset: 'numeric',
    bindKeyword: '绑定',
    unbindKeyword: '解绑',
    unboundKickMessage: '&8[&c!&8] &c你还没有绑定社交账号，无法进入本服务器。',
    bindSuccessMessage: '&8[&a!&8] &f绑定 &a#account &f成功，欢迎进入服务器！',
    bindFailMessage: '&8[&c!&8] &c绑定失败：#why',
    unbindSuccessMessage: '&8[&a!&8] &f已解除绑定。',
    unbindFailMessage: '&8[&c!&8] &c解绑失败：#why',
  }
}

export function emptyGroup(): GroupSettings {
  return {
    groupId: '',
    displayName: '',
    enabled: true,
    bindEnabled: true,
    cardEnabled: true,
    quitNoticeEnabled: true,
    quitMessage: '&e#name &7离开了我们，后会有期。',
    cardFormat: '&7[&a#group&7] &f#name',
    cardMaxLength: 16,
  }
}
