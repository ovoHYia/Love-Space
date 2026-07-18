<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { X } from 'lucide-vue-next'

defineProps<{ title: string; description?: string; wide?: boolean }>()
const emit = defineEmits<{ close: [] }>()
const card = ref<HTMLElement | null>(null)
let previousFocus: HTMLElement | null = null

function focusable() {
  return Array.from(card.value?.querySelectorAll<HTMLElement>(
    'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
  ) || []).filter(item => !item.hasAttribute('hidden'))
}

function onKey(event: KeyboardEvent) {
  if (event.key === 'Escape') emit('close')
  if (event.key !== 'Tab') return
  const items = focusable()
  if (!items.length) {
    event.preventDefault()
    return
  }
  const first = items[0]
  const last = items[items.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}
onMounted(async () => {
  previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  document.body.classList.add('modal-open')
  document.querySelector('#app')?.setAttribute('inert', '')
  window.addEventListener('keydown', onKey)
  await nextTick()
  focusable()[0]?.focus()
})
onUnmounted(() => {
  document.body.classList.remove('modal-open')
  document.querySelector('#app')?.removeAttribute('inert')
  window.removeEventListener('keydown', onKey)
  previousFocus?.focus()
})
</script>

<template>
  <Teleport to="body">
    <div class="modal-backdrop" role="presentation" @mousedown.self="emit('close')">
      <section ref="card" class="modal-card" :class="{ 'modal-wide': wide }" role="dialog" aria-modal="true" :aria-label="title" tabindex="-1">
        <header class="modal-header">
          <div>
            <p class="eyebrow">LOVE SPACE</p>
            <h2>{{ title }}</h2>
            <p v-if="description" class="muted">{{ description }}</p>
          </div>
          <button class="icon-button" type="button" aria-label="关闭" @click="emit('close')"><X :size="20" /></button>
        </header>
        <div class="modal-body"><slot /></div>
      </section>
    </div>
  </Teleport>
</template>
