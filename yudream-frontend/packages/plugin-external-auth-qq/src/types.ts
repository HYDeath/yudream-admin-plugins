export interface QqSettings {
  enabled: boolean
  appId: string
  appKeyConfigured: boolean
  redirectUri: string
  displayName: string
  sort: number
  configured: boolean
  active: boolean
}
export interface SettingsSchema { version: number, sections: { code: string, title: string, description?: string, fields: { key: string, label: string, type: string, default?: unknown, help?: string, min?: number, max?: number }[] }[] }
