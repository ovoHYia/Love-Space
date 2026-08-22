<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Eye, EyeOff, Heart, LogIn } from 'lucide-vue-next'
import { api } from '../api'
import { ApiError, errorMessage } from '../api/client'
import { applyAuth, authState, bootstrapAuth } from '../stores/auth'
import { useToast } from '../composables/toast'

const route = useRoute()
const router = useRouter()
const { show } = useToast()
const username = ref('')
const password = ref('')
const reveal = ref(false)
const loading = ref(false)
const retrying = ref(false)
const error = ref('')
const errorEl = ref<HTMLElement | null>(null)
const sessionExpired = computed(() => route.query.expired === '1')
const serverOffline = computed(() => route.query.server === 'offline')
const loginDescriptionIds = computed(() => {
  const ids = []
  if (serverOffline.value) ids.push('login-offline')
  if (error.value) ids.push('login-error')
  return ids.length ? ids.join(' ') : undefined
})
const connectionErrorMessage = '小屋暂时连接不上，请稍后再试。若问题持续出现，请联系管理员。'

async function login() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try {
    applyAuth(await api.login(username.value.trim(), password.value))
    show('欢迎回来，今天也要好好相爱。', 'success')
    const rawRedirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const target = /^\/(?!\/|\\)/.test(rawRedirect) ? rawRedirect : '/'
    await router.replace(target)
  } catch (cause) {
    const connectionFailure = cause instanceof ApiError && (cause.code === 'NETWORK_ERROR' || cause.code === 'CSRF_INIT_FAILED')
    error.value = connectionFailure ? connectionErrorMessage : errorMessage(cause)
    await nextTick()
    errorEl.value?.focus()
  } finally {
    loading.value = false
  }
}

async function retryConnection() {
  if (retrying.value) return
  retrying.value = true
  error.value = ''
  try {
    await bootstrapAuth(true)
    if (!authState.initialized) {
      await router.replace({ name: 'setup' })
    } else if (authState.authenticated) {
      await router.replace({ name: 'home' })
    } else {
      show('连接已恢复，请登录。', 'info')
    }
  } catch (cause) {
    error.value = cause instanceof ApiError ? connectionErrorMessage : errorMessage(cause)
  } finally {
    retrying.value = false
  }
}
</script>

<template>
  <main class="auth-page login-page">
    <section class="auth-visual" aria-hidden="true">
      <div class="visual-copy">
        <span class="visual-heart"><Heart :size="30" fill="currentColor" /></span>
        <p class="eyebrow">LOVE SPACE</p>
        <h1>欢迎回到，<br />我们的小时光。</h1>
        <p>每一次打开，都是在认真回应那些值得珍藏的日子。</p>
      </div>
      <div class="login-quote">“有人可念，有事可记，日子便有了柔软的光。”</div>
    </section>
    <section class="auth-panel">
      <form class="login-card" aria-labelledby="login-title" :aria-busy="loading" @submit.prevent="login">
        <div class="login-brand"><span class="brand-mark"><Heart :size="21" fill="currentColor" /></span><span><strong>Love Space</strong><small>只属于你们的小世界</small></span></div>
        <p class="eyebrow">WELCOME BACK</p>
        <h2 id="login-title">今天是谁回家啦？</h2>
        <p class="muted">输入你的专属账号，继续收藏两个人的故事。</p>
        <p v-if="sessionExpired" class="form-error" role="alert" aria-live="assertive">登录状态已过期，请重新登录后继续刚才的操作。</p>
        <p v-if="serverOffline" id="login-offline" class="form-error" role="status" aria-live="polite">小屋暂时连接不上，请稍后再试。若问题持续出现，请联系管理员。</p>
        <button v-if="serverOffline" class="button ghost full" type="button" :disabled="retrying" @click="retryConnection">{{ retrying ? '正在重新连接…' : '重新连接' }}</button>
        <label class="field"><span>账号</span><input id="login-username" v-model="username" required autocomplete="username" autofocus placeholder="请输入登录账号" :disabled="loading" :aria-invalid="Boolean(error)" :aria-describedby="loginDescriptionIds" /></label>
        <label class="field"><span>密码</span><span class="input-action"><input id="login-password" v-model="password" required :type="reveal ? 'text' : 'password'" autocomplete="current-password" placeholder="请输入密码" :disabled="loading" :aria-invalid="Boolean(error)" :aria-describedby="loginDescriptionIds" /><button type="button" :disabled="loading" :aria-label="reveal ? '隐藏密码' : '显示密码'" @click="reveal = !reveal"><EyeOff v-if="reveal" :size="18" aria-hidden="true" /><Eye v-else :size="18" aria-hidden="true" /></button></span></label>
        <p v-if="error" id="login-error" ref="errorEl" class="form-error" role="alert" aria-live="assertive" tabindex="-1">{{ error }}</p>
        <button class="button primary full" type="submit" :disabled="loading">
          <span v-if="loading" class="button-spinner"></span><LogIn v-else :size="18" />{{ loading ? '正在打开小屋…' : '回到我们的小屋' }}
        </button>
        <p class="login-foot">账号只在首次启动时创建，请妥善保管登录密码。</p>
      </form>
    </section>
  </main>
</template>
