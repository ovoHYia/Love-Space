<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Eye, EyeOff, Heart, LogIn } from 'lucide-vue-next'
import { api } from '../api'
import { errorMessage } from '../api/client'
import { applyAuth } from '../stores/auth'
import { useToast } from '../composables/toast'

const route = useRoute()
const router = useRouter()
const { show } = useToast()
const username = ref('')
const password = ref('')
const reveal = ref(false)
const loading = ref(false)
const error = ref('')

async function login() {
  loading.value = true
  error.value = ''
  try {
    applyAuth(await api.login(username.value.trim(), password.value))
    show('欢迎回来，今天也要好好相爱。', 'success')
    const target = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') ? route.query.redirect : '/'
    await router.replace(target)
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
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
      <form class="login-card" @submit.prevent="login">
        <div class="login-brand"><span class="brand-mark"><Heart :size="21" fill="currentColor" /></span><span><strong>Love Space</strong><small>只属于你们的小世界</small></span></div>
        <p class="eyebrow">WELCOME BACK</p>
        <h2>今天是谁回家啦？</h2>
        <p class="muted">输入你的专属账号，继续收藏两个人的故事。</p>
        <p v-if="route.query.server === 'offline'" class="form-error" role="status">暂时没有连接到服务器。确认后端服务启动后，可以直接尝试登录。</p>
        <label class="field"><span>账号</span><input v-model="username" required autocomplete="username" autofocus placeholder="请输入登录账号" /></label>
        <label class="field"><span>密码</span><span class="input-action"><input v-model="password" required :type="reveal ? 'text' : 'password'" autocomplete="current-password" placeholder="请输入密码" /><button type="button" :aria-label="reveal ? '隐藏密码' : '显示密码'" @click="reveal = !reveal"><EyeOff v-if="reveal" :size="18" /><Eye v-else :size="18" /></button></span></label>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <button class="button primary full" type="submit" :disabled="loading">
          <span v-if="loading" class="button-spinner"></span><LogIn v-else :size="18" />{{ loading ? '正在打开小屋…' : '回到我们的小屋' }}
        </button>
        <p class="login-foot">账号只在首次启动时创建；忘记密码时需要由服务器管理员重置。</p>
      </form>
    </section>
  </main>
</template>
