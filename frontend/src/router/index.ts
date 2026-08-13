import { createRouter, createWebHashHistory } from 'vue-router'
import { authState, bootstrapAuth, clearAuth, isSessionInvalidationCode } from '../stores/auth'
import { ApiError } from '../api/client'
import SetupView from '../views/SetupView.vue'
import LoginView from '../views/LoginView.vue'
import AppShell from '../components/AppShell.vue'
import DashboardView from '../views/DashboardView.vue'

// 非首屏视图懒加载：首屏只需 Setup/Login/AppShell/Dashboard，其余按路由分包。
const MemoriesView = () => import('../views/MemoriesView.vue')
const DiariesView = () => import('../views/DiariesView.vue')
const LettersView = () => import('../views/LettersView.vue')
const AnniversariesView = () => import('../views/AnniversariesView.vue')
const WishesView = () => import('../views/WishesView.vue')
const ProfileView = () => import('../views/ProfileView.vue')
const DataManagementView = () => import('../views/DataManagementView.vue')
const CalendarView = () => import('../views/CalendarView.vue')
const ReportsView = () => import('../views/ReportsView.vue')
const NotificationsView = () => import('../views/NotificationsView.vue')
const GamesView = () => import('../views/GamesView.vue')

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
