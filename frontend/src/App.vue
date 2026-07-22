<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { RouterView } from 'vue-router'
import AppToast from './components/AppToast.vue'
import router from './router'
import { clearAuth } from './stores/auth'
import { useToast } from './composables/toast'

const { show } = useToast()

function onSessionExpired(event: Event) {
  const detail = event instanceof CustomEvent ? event.detail as { code?: string } : undefined
  clearAuth()
  if (detail?.code === 'PASSWORD_CHANGED') show('密码已修改，请重新登录。', 'info')
  if (router.currentRoute.value.name !== 'login') router.replace({ name: 'login', query: { expired: '1' } })
}

onMounted(() => window.addEventListener('love-space-unauthenticated', onSessionExpired))
onUnmounted(() => window.removeEventListener('love-space-unauthenticated', onSessionExpired))
</script>

<template>
  <div class="ambient ambient-one" aria-hidden="true"></div>
  <div class="ambient ambient-two" aria-hidden="true"></div>
  <RouterView />
  <AppToast />
</template>
