<script setup lang="ts">
import { computed } from 'vue'
import { mediaUrl } from '../api/client'
import type { UserProfile } from '../types'

const props = withDefaults(defineProps<{ user?: UserProfile | null; size?: 'sm' | 'md' | 'lg' | 'xl' }>(), { size: 'md' })
const src = computed(() => mediaUrl(props.user?.avatarMediaId, props.user?.avatarUrl))
const initial = computed(() => props.user?.nickname?.trim().slice(0, 1) || '♡')
</script>

<template>
  <span class="avatar" :class="`avatar-${size}`" :aria-label="`${user?.nickname || '用户'}的头像`">
    <img v-if="src" :src="src" alt="" />
    <span v-else aria-hidden="true">{{ initial }}</span>
  </span>
</template>
