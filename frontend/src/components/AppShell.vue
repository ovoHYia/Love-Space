<script setup lang="ts">
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { CalendarHeart, Feather, Heart, Home, Images, Mails as MailHeart, UserRound } from 'lucide-vue-next'
import BaseAvatar from './BaseAvatar.vue'
import { authState } from '../stores/auth'

const route = useRoute()
const nav = [
  { to: '/', label: '小窝', icon: Home, name: 'home' },
  { to: '/memories', label: '回忆', icon: Images, name: 'memories' },
  { to: '/diaries', label: '日记', icon: Feather, name: 'diaries' },
  { to: '/letters', label: '信笺', icon: MailHeart, name: 'letters' },
  { to: '/anniversaries', label: '日子', icon: CalendarHeart, name: 'anniversaries' },
  { to: '/profile', label: '我们', icon: UserRound, name: 'profile' },
]
</script>

<template>
  <div class="app-layout">
    <aside class="side-nav" aria-label="主导航">
      <RouterLink class="brand" to="/" aria-label="Love Space 首页">
        <span class="brand-mark"><Heart :size="21" fill="currentColor" /></span>
        <span><strong>Love Space</strong><small>我们的小时光</small></span>
      </RouterLink>
      <nav class="side-links">
        <RouterLink v-for="item in nav" :key="item.name" :to="item.to" :class="{ active: route.name === item.name }">
          <component :is="item.icon" :size="20" aria-hidden="true" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>
      <RouterLink class="side-profile" to="/profile">
        <BaseAvatar :user="authState.user" size="sm" />
        <span><strong>{{ authState.user?.nickname }}</strong><small>和 {{ authState.partner?.nickname || '心上人' }} 的空间</small></span>
      </RouterLink>
    </aside>

    <div class="app-column">
      <header class="mobile-header">
        <RouterLink class="mobile-brand" to="/">
          <span class="brand-mark"><Heart :size="17" fill="currentColor" /></span>
          <span><strong>{{ authState.spaceName }}</strong><small>Love Space</small></span>
        </RouterLink>
        <RouterLink to="/profile" aria-label="打开个人资料"><BaseAvatar :user="authState.user" size="sm" /></RouterLink>
      </header>
      <main class="page-container"><RouterView /></main>
      <nav class="bottom-nav" aria-label="主导航">
        <RouterLink v-for="item in nav" :key="item.name" :to="item.to" :class="{ active: route.name === item.name }" :aria-label="item.label">
          <component :is="item.icon" :size="20" aria-hidden="true" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>
    </div>
  </div>
</template>
