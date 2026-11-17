import { defineYuDreamPlugin } from '@yudream/plugin-sdk'
import 'virtual:uno.css'
import './style.css'
import Workbench from './pages/WorkbenchPage.vue'
import Admin from './pages/AdminPage.vue'
export const routes = { 'schematic/Workbench': Workbench, 'schematic/Admin': Admin }
export default defineYuDreamPlugin({ routes, default: Workbench })
