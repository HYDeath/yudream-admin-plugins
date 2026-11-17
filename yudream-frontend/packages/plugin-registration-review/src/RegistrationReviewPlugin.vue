<script setup lang="ts">
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { computed } from 'vue'
import { createRegistrationReviewApi } from './api/registration-review-api'
import AdminPage from './pages/AdminPage.vue'
import ApplyPage from './pages/ApplyPage.vue'
import SettingsPage from './pages/SettingsPage.vue'

const props = defineProps<{ sdk: YuDreamPluginSdk, route?: { meta?: { plugin?: { component?: string } } } }>()
const model = { api: createRegistrationReviewApi(props.sdk) }
const page = computed(() => {
  switch (props.route?.meta?.plugin?.component?.split('/').pop()) {
    case 'Admin': return AdminPage
    case 'Settings': return SettingsPage
    default: return ApplyPage
  }
})
</script>

<template>
  <div class="registration-review-plugin"><component :is="page" :model="model" /></div>
</template>
