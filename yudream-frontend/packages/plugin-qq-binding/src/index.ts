import { defineYuDreamPlugin } from '@yudream/plugin-sdk'
import 'virtual:uno.css'
import './style.css'
import QqBindingSettings from './pages/QqBindingSettings.vue'

export const Settings = QqBindingSettings
export const routes = { Settings, 'qq-binding/Settings': Settings }
export default defineYuDreamPlugin({ routes, default: Settings })
