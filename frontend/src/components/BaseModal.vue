<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { X } from 'lucide-vue-next'

const props = withDefaults(defineProps<{
  title: string
  description?: string
  wide?: boolean
  open?: boolean
  variant?: 'modal' | 'sheet'
  id?: string
  closeDisabled?: boolean
}>(), { open: true, variant: 'modal' })
type CloseSource = 'escape' | 'backdrop' | 'button'
const emit = defineEmits<{ close: [source: CloseSource] }>()
const card = ref<HTMLElement | null>(null)
const rendered = ref(false)
const motionState = ref<'closed' | 'entering' | 'open' | 'leaving'>('closed')
let previousFocus: HTMLElement | null = null
let modalActive = false
let leaveTimer: number | null = null
const titleId = computed(() => props.id ? props.id + '-title' : undefined)
const descriptionId = computed(() => props.id && props.description ? props.id + '-description' : undefined)

function focusable() {
  return Array.from(card.value?.querySelectorAll<HTMLElement>(
    'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
  ) || []).filter(item => !item.hasAttribute('hidden') && !item.closest('[inert]'))
}

function onKey(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    if (!props.closeDisabled) emit('close', 'escape')
    return
  }
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

async function activate() {
  if (modalActive) return
  modalActive = true
  previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  document.body.classList.add('modal-open')
  document.querySelector('#app')?.setAttribute('inert', '')
  window.addEventListener('keydown', onKey)
  await nextTick()
  focusable()[0]?.focus()
}

function clearLeaveTimer() {
  if (leaveTimer !== null) {
    window.clearTimeout(leaveTimer)
    leaveTimer = null
  }
}

function openModal() {
  clearLeaveTimer()
  rendered.value = true
  motionState.value = 'entering'
  void activate()
  void nextTick(() => {
    window.requestAnimationFrame(() => {
      if (props.open) motionState.value = 'open'
    })
  })
}

function closeModal() {
  if (!rendered.value) return
  motionState.value = 'leaving'
  clearLeaveTimer()
  leaveTimer = window.setTimeout(() => {
    if (props.open) return
    rendered.value = false
    motionState.value = 'closed'
    leaveTimer = null
    deactivate()
  }, 155)
}

function deactivate() {
  if (!modalActive) return
  modalActive = false
  document.body.classList.remove('modal-open')
  document.querySelector('#app')?.removeAttribute('inert')
  window.removeEventListener('keydown', onKey)
  if (previousFocus?.isConnected) previousFocus.focus()
  previousFocus = null
}

onMounted(() => {
  if (props.open) {
    rendered.value = true
    motionState.value = 'open'
    void activate()
  }
})
watch(() => props.open, (open) => {
  if (open) openModal()
  else closeModal()
})
onBeforeUnmount(() => {
  clearLeaveTimer()
  deactivate()
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="rendered"
      class="modal-backdrop"
      :class="{
        'modal-sheet-backdrop': props.variant === 'sheet',
        'modal-sheet-entering': props.variant === 'sheet' && motionState === 'entering',
        'modal-sheet-leaving': props.variant === 'sheet' && motionState === 'leaving',
      }"
      role="presentation"
      @click.self="!props.closeDisabled && emit('close', 'backdrop')"
    >
        <section
          :id="props.id"
          ref="card"
          class="modal-card"
          :class="{ 'modal-wide': props.wide, 'modal-sheet': props.variant === 'sheet' }"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="titleId"
          :aria-describedby="descriptionId"
          :aria-label="titleId ? undefined : props.title"
          tabindex="-1"
        >
          <div v-if="props.variant === 'sheet'" class="modal-sheet-handle" aria-hidden="true"></div>
          <header class="modal-header">
            <div>
              <p class="eyebrow">LOVE SPACE</p>
              <h2 :id="titleId">{{ props.title }}</h2>
              <p v-if="props.description" :id="descriptionId" class="muted">{{ props.description }}</p>
            </div>
            <button
              class="icon-button"
              type="button"
              :disabled="props.closeDisabled"
              :aria-label="props.closeDisabled ? '关闭（保存中，暂不可关闭）' : '关闭'"
              :title="props.closeDisabled ? '保存中，暂不可关闭' : '关闭'"
              @click="!props.closeDisabled && emit('close', 'button')"
            ><X :size="20" /></button>
          </header>
          <div class="modal-body"><slot /></div>
        </section>
    </div>
  </Teleport>
</template>
