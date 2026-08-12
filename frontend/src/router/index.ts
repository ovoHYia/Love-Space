import { createRouter, createWebHashHistory } from 'vue-router'
import { authState, bootstrapAuth, clearAuth, isSessionInvalidationCode } from '../stores/auth'
import { ApiError } from '../api/client'
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
import CalendarView from '../views/CalendarView.vue'
import ReportsView from '../views/ReportsView.vue'
import NotificationsView from '../views/NotificationsView.vue'
import GamesView from '../views/GamesView.vue'

const router = createRouter({
  history: createWebHashHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/setup', name: 'setup', component: SetupView, meta: { guest: true } },
    { path: '/login', name: 'login', component: LoginView, meta: { guest: true } },
    {
      path: '/', component: AppShell, meta: { auth: true }, children: [
        { path: '', name: 'home', component: DashboardView },
        { path: 'calendar', name: 'calendar', component: CalendarView },
        { path: 'reports', name: 'reports', component: ReportsView },
        { path: 'notifications', name: 'notifications', component: NotificationsView },
        { path: 'memories', name: 'memories', component: MemoriesView },
        { path: 'games', name: 'games', component: GamesView },
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
    } catch (error) {
      authState.initialized = true
      if (error instanceof ApiError && isSessionInvalidationCode(error.code)) clearAuth(error.code)
      else clearAuth()
      if (to.name !== 'login') return { name: 'login', query: { server: 'offline' } }
    }
  }
  if (!authState.initialized && to.name !== 'setup') return { name: 'setup' }
  if (authState.initialized && to.name === 'setup') return authState.authenticated ? { name: 'home' } : { name: 'login' }
  if (authState.forcedLogoutReason && to.meta.auth) return { name: 'login', query: { expired: '1' } }
  if (to.meta.auth && !authState.authenticated) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.name === 'login' && authState.authenticated) return { name: 'home' }
})

export default router
