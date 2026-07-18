<script setup lang="ts">
withDefaults(defineProps<{ label?: string }>(), { label: '正在把回忆轻轻翻开…' })
</script>

<template>
  <div class="loading-state" role="status">
    <span class="spinner" aria-hidden="true"></span>
    <p>{{ label }}</p>
  </div>
</template>
