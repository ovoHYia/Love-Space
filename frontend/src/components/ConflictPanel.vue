<script setup lang="ts">
import { STALE_UPDATE_MESSAGE } from '../utils/editConflict'

defineEmits<{ (event: 'reload'): void }>()
</script>

<template>
  <div class="conflict-panel" role="alert">
    <p>{{ STALE_UPDATE_MESSAGE }}</p>
    <button class="button secondary small" type="button" @click="$emit('reload')">加载最新内容</button>
  </div>
</template>
