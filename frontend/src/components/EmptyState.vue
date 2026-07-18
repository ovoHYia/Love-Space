<script setup lang="ts">
import { Heart } from 'lucide-vue-next'

withDefaults(defineProps<{ title: string; description?: string }>(), { description: '' })
</script>

<template>
  <div class="empty-state">
    <div class="empty-icon" aria-hidden="true"><Heart :size="23" /></div>
    <h3>{{ title }}</h3>
    <p v-if="description">{{ description }}</p>
    <slot />
  </div>
</template>
