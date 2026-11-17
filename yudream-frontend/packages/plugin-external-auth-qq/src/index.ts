import { defineYuDreamPlugin } from '@yudream/plugin-sdk'
import 'virtual:uno.css'
import './styles.css'
import QqAuthPlugin from './QqAuthPlugin.vue'
export const Settings = QqAuthPlugin
export const routes = { Settings, 'external-auth-qq/Settings': Settings }
export default defineYuDreamPlugin({ routes, default: Settings })
