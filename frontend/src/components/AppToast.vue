<script setup lang="ts">
import { CircleCheck, CircleX, Info, X } from 'lucide-vue-next'
import { useToast } from '../composables/toast'

const { toasts, dismiss } = useToast()
const icons = { success: CircleCheck, error: CircleX, info: Info }
</script>

<template>
  <div class="toast-region" aria-live="polite" aria-atomic="false">
    <TransitionGroup name="toast">
      <div v-for="item in toasts" :key="item.id" class="toast" :class="`toast-${item.tone}`" :role="item.tone === 'error' ? 'alert' : 'status'" aria-atomic="true">
        <component :is="icons[item.tone]" :size="19" aria-hidden="true" />
        <span>{{ item.message }}</span>
        <button class="icon-button subtle" type="button" aria-label="关闭提示" @click="dismiss(item.id)">
          <X :size="17" aria-hidden="true" />
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>
