import { createRouter, createWebHashHistory } from 'vue-router'
import { authState, bootstrapAuth } from '../stores/auth'
import SetupView from '../views/SetupView.vue'
import LoginView from '../views/LoginView.vue'
import AppShell from '../components/AppShell.vue'
import DashboardView from '../views/DashboardView.vue'
import MemoriesView from '../views/MemoriesView.vue'
import DiariesView from '../views/DiariesView.vue'
import LettersView from '../views/LettersView.vue'
import AnniversariesView from '../views/AnniversariesView.vue'
import WishesView from '../views/WishesView.vue'
import ProfileView from '../views/ProfileView.vue'
import DataManagementView from '../views/DataManagementView.vue'

const router = createRouter({
  history: createWebHashHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/setup', name: 'setup', component: SetupView, meta: { guest: true } },
    { path: '/login', name: 'login', component: LoginView, meta: { guest: true } },
    {
      path: '/', component: AppShell, meta: { auth: true }, children: [
        { path: '', name: 'home', component: DashboardView },
        { path: 'memories', name: 'memories', component: MemoriesView },
        { path: 'diaries', name: 'diaries', component: DiariesView },
        { path: 'letters', name: 'letters', component: LettersView },
        { path: 'anniversaries', name: 'anniversaries', component: AnniversariesView },
        { path: 'wishes', name: 'wishes', component: WishesView },
        { path: 'profile', name: 'profile', component: ProfileView },
        { path: 'data-management', name: 'data-management', component: DataManagementView },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  if (!authState.ready) {
    try {
      await bootstrapAuth()
    } catch {
      authState.initialized = true
      authState.authenticated = false
      if (to.name !== 'login') return { name: 'login', query: { server: 'offline' } }
    }
  }
  if (!authState.initialized && to.name !== 'setup') return { name: 'setup' }
  if (authState.initialized && to.name === 'setup') return authState.authenticated ? { name: 'home' } : { name: 'login' }
  if (to.meta.auth && !authState.authenticated) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.name === 'login' && authState.authenticated) return { name: 'home' }
})

export default router
