import { defineYuDreamPlugin } from '@yudream/plugin-sdk'
import 'virtual:uno.css'
import './style.css'
import BindSettingsPage from './pages/BindSettingsPage.vue'
import BotSettingsPage from './pages/BotSettingsPage.vue'
import BridgePage from './pages/BridgePage.vue'
import CommandsPage from './pages/CommandsPage.vue'
import GroupsPage from './pages/GroupsPage.vue'
import MarketPage from './pages/MarketPage.vue'
import PreviewPage from './pages/PreviewPage.vue'
import TemplatesPage from './pages/TemplatesPage.vue'

export const BindSettings = BindSettingsPage
export const BotSettings = BotSettingsPage
export const Commands = CommandsPage
export const Groups = GroupsPage
export const Market = MarketPage
export const Bridge = BridgePage
export const Preview = PreviewPage
export const Templates = TemplatesPage

export const routes = {
  BindSettings,
  BotSettings,
  Commands,
  Groups,
  Market,
  Bridge,
  Preview,
  Templates,
  'qqrobot/BindSettings': BindSettings,
  'qqrobot/BotSettings': BotSettings,
  'qqrobot/Commands': Commands,
  'qqrobot/Groups': Groups,
  'qqrobot/Market': Market,
  'qqrobot/Bridge': Bridge,
  'qqrobot/Preview': Preview,
  'qqrobot/Templates': Templates,
}

export default defineYuDreamPlugin({ routes, default: BindSettings })
