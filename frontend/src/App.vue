<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { RouterView } from 'vue-router'
import AppToast from './components/AppToast.vue'
import router from './router'
import { clearAuth } from './stores/auth'

function onSessionExpired() {
  clearAuth()
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
