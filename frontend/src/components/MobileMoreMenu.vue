<script setup lang="ts">
import { computed, watch, type Component } from 'vue'
import { ChevronRight } from 'lucide-vue-next'
import { RouterLink, useRoute } from 'vue-router'
import BaseModal from './BaseModal.vue'

type NavigationItem = {
  to: string
  label: string
  mobileLabel?: string
  icon: Component
  name: string
}

type NavigationGroup = {
  label: string
  items: NavigationItem[]
}

const props = defineProps<{
  open: boolean
  groups: NavigationGroup[]
  menuId: string
}>()
const emit = defineEmits<{ close: [] }>()
const route = useRoute()
const groups = computed(() => props.groups.filter(group => group.items.length))

function itemLabel(item: NavigationItem) {
  return item.mobileLabel || item.label
}

function isCurrent(item: NavigationItem) {
  return route.name === item.name
}

function close() {
  emit('close')
}

watch(() => route.fullPath, () => {
  if (props.open) close()
})
</script>

<template>
  <BaseModal :open="props.open" :id="props.menuId" variant="sheet" title="更多入口" description="记录、共同计划和我们" @close="close">
    <nav class="mobile-more-nav" aria-label="更多入口">
      <section v-for="group in groups" :key="group.label" class="mobile-more-group">
        <h3>{{ group.label }}</h3>
        <div class="mobile-more-links">
          <RouterLink
            v-for="item in group.items"
            :key="item.name"
            class="mobile-more-link"
            :class="{ active: isCurrent(item) }"
            :to="item.to"
            :aria-current="isCurrent(item) ? 'page' : undefined"
            @click="close"
          >
            <span class="mobile-more-icon" aria-hidden="true"><component :is="item.icon" :size="20" /></span>
            <span class="mobile-more-label">{{ itemLabel(item) }}</span>
            <ChevronRight v-if="!isCurrent(item)" class="mobile-more-arrow" :size="17" aria-hidden="true" />
            <span v-else class="mobile-more-current" aria-hidden="true"></span>
          </RouterLink>
        </div>
      </section>
    </nav>
  </BaseModal>
</template>

<style scoped>
.mobile-more-nav { display: flex; flex-direction: column; gap: 22px; }
.mobile-more-group h3 { margin: 0 0 10px; color: var(--muted); font-size: 12px; font-weight: 800; letter-spacing: .08em; }
.mobile-more-links { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.mobile-more-link { min-width: 0; min-height: 58px; display: flex; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid var(--line); border-radius: 15px; background: rgba(255,253,251,.82); color: var(--ink); font-size: 14px; font-weight: 700; transition: background .16s ease, border-color .16s ease, box-shadow .16s ease, transform .16s ease; }
.mobile-more-link:hover { border-color: #efb9c1; background: var(--rose-pale); color: var(--rose-dark); }
.mobile-more-link.active { border-color: #efb9c1; background: var(--rose-pale); color: var(--rose-dark); box-shadow: inset 0 0 0 1px #f5cdd3; }
.mobile-more-icon { width: 38px; height: 38px; flex: 0 0 auto; display: grid; place-items: center; border-radius: 12px; background: #fff5f4; color: var(--rose-dark); }
.mobile-more-link.active .mobile-more-icon { background: #fff; }
.mobile-more-label { min-width: 0; overflow-wrap: anywhere; }
.mobile-more-arrow { flex: 0 0 auto; margin-left: auto; color: var(--muted); }
.mobile-more-current { width: 9px; height: 9px; flex: 0 0 auto; margin-left: auto; border-radius: 3px; background: var(--rose-dark); transform: rotate(45deg); }

@media (max-width: 390px) {
  .mobile-more-links { gap: 8px; }
  .mobile-more-link { gap: 8px; padding-left: 10px; padding-right: 10px; font-size: 13px; }
}
</style>
