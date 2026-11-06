import { defineYuDreamPlugin } from '@yudream/plugin-sdk'
import 'virtual:uno.css'
import './style.css'
import BindSettingsPage from './pages/BindSettingsPage.vue'
import BridgePage from './pages/BridgePage.vue'
import GroupsPage from './pages/GroupsPage.vue'
import MarketPage from './pages/MarketPage.vue'
import PreviewPage from './pages/PreviewPage.vue'
import TemplatesPage from './pages/TemplatesPage.vue'

export const BindSettings = BindSettingsPage
export const Groups = GroupsPage
export const Market = MarketPage
export const Bridge = BridgePage
export const Preview = PreviewPage
export const Templates = TemplatesPage

export const routes = {
  BindSettings,
  Groups,
  Market,
  Bridge,
  Preview,
  Templates,
  'easybot/BindSettings': BindSettings,
  'easybot/Groups': Groups,
  'easybot/Market': Market,
  'easybot/Bridge': Bridge,
  'easybot/Preview': Preview,
  'easybot/Templates': Templates,
}

export default defineYuDreamPlugin({ routes, default: BindSettings })
