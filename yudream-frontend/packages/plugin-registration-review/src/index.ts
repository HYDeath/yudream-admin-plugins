import { defineYuDreamPlugin } from '@yudream/plugin-sdk'
import 'virtual:uno.css'
import './styles.css'
import RegistrationReviewPlugin from './RegistrationReviewPlugin.vue'

export const Apply = RegistrationReviewPlugin
export const Admin = RegistrationReviewPlugin
export const Settings = RegistrationReviewPlugin

export const routes = { Apply, Admin, Settings, 'registration-review/Apply': Apply, 'registration-review/Admin': Admin, 'registration-review/Settings': Settings }

export default defineYuDreamPlugin({ routes, default: Apply })
