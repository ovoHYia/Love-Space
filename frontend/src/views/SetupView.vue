<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Check, Eye, EyeOff, Heart, KeyRound, Sparkles, UsersRound } from 'lucide-vue-next'
import { api } from '../api'
import { ApiError, errorMessage } from '../api/client'
import { authState } from '../stores/auth'
import { useToast } from '../composables/toast'
import { formatDateTime, toBeijingOffsetDateTime } from '../utils'

const router = useRouter()
const { show } = useToast()
const step = ref(1)
const saving = ref(false)
const reveal = ref(false)
const error = ref('')
const fieldErrors = ref<Record<string, string>>({})
const form = reactive({
  spaceName: '我们的小时光',
  loveStartedAt: '',
  setupToken: '',
  firstUser: { username: '', password: '', nickname: '' },
  secondUser: { username: '', password: '', nickname: '' },
})

const canNext = computed(() => {
  if (step.value === 1) return form.spaceName.trim() && form.loveStartedAt
  if (step.value === 3) return form.setupToken.trim().length >= 32
  return /^[A-Za-z0-9_.-]{3,50}$/.test(form.firstUser.username.trim()) && form.firstUser.nickname.trim() && form.firstUser.password.length >= 8 &&
    /^[A-Za-z0-9_.-]{3,50}$/.test(form.secondUser.username.trim()) && form.secondUser.nickname.trim() && form.secondUser.password.length >= 8 &&
    form.firstUser.username.trim().toLowerCase() !== form.secondUser.username.trim().toLowerCase()
})

function next() {
  error.value = ''
  fieldErrors.value = {}
  if (!canNext.value) {
    error.value = step.value === 1 ? '请先写下空间名称和在一起的时间。' : '账号需为 3–50 位字母、数字或 _.-，两个账号不能相同，密码至少 8 位。'
    return
  }
  step.value = Math.min(3, step.value + 1)
}

async function submit() {
  saving.value = true
  error.value = ''
  fieldErrors.value = {}
  try {
    if (!canNext.value) {
      error.value = '请填入 .env 中的初始化口令。'
      return
    }
    await api.initialize({
      spaceName: form.spaceName.trim(),
      loveStartedAt: toBeijingOffsetDateTime(form.loveStartedAt),
      firstUser: { ...form.firstUser, username: form.firstUser.username.trim(), nickname: form.firstUser.nickname.trim() },
      secondUser: { ...form.secondUser, username: form.secondUser.username.trim(), nickname: form.secondUser.nickname.trim() },
    }, form.setupToken.trim())
    authState.initialized = true
    show('你们的小世界准备好了，现在登录看看吧。', 'success')
    await router.replace('/login')
  } catch (cause) {
    fieldErrors.value = cause instanceof ApiError ? cause.fieldErrors || {} : {}
    error.value = errorMessage(cause)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <main class="auth-page setup-page">
    <section class="auth-visual" aria-hidden="true">
      <div class="visual-copy">
        <span class="visual-heart"><Heart :size="30" fill="currentColor" /></span>
        <p class="eyebrow">WELCOME HOME</p>
        <h1>把平凡日子，<br />认真收藏。</h1>
        <p>这里没有陌生人，只有你、我，和一路走来的小小故事。</p>
      </div>
      <div class="paper-note note-one">今天也要记得说爱你 ♡</div>
      <div class="paper-note note-two">我们的第 1 个数字小家</div>
    </section>

    <section class="auth-panel">
      <div class="setup-card">
        <div class="setup-progress" aria-label="初始化进度">
          <span v-for="index in 3" :key="index" :class="{ active: step >= index }"><Check v-if="step > index" :size="13" />{{ step > index ? '' : index }}</span>
          <i :class="{ active: step >= 2 }"></i><i :class="{ active: step >= 3 }"></i>
        </div>

        <Transition name="step" mode="out-in">
          <form v-if="step === 1" key="space" class="setup-step" @submit.prevent="next">
            <span class="step-icon"><Sparkles :size="24" /></span>
            <p class="eyebrow">第一步 · 为爱命名</p>
            <h2>先布置你们的小空间</h2>
            <p class="muted">这些信息会出现在首页，也会用来计算在一起的每一秒。</p>
            <label class="field"><span>空间名称</span><input id="setup-space-name" v-model="form.spaceName" required maxlength="30" autocomplete="off" placeholder="例如：我们的小时光" :aria-invalid="Boolean(fieldErrors.spaceName)" :aria-describedby="fieldErrors.spaceName ? 'setup-space-name-error' : undefined" /><small v-if="fieldErrors.spaceName" id="setup-space-name-error" class="field-error">{{ fieldErrors.spaceName }}</small></label>
            <label class="field"><span>在一起的日期与时间</span><input id="setup-love-started-at" v-model="form.loveStartedAt" required type="datetime-local" :aria-invalid="Boolean(fieldErrors.loveStartedAt)" :aria-describedby="fieldErrors.loveStartedAt ? 'setup-love-started-at-error' : undefined" /><small v-if="fieldErrors.loveStartedAt" id="setup-love-started-at-error" class="field-error">{{ fieldErrors.loveStartedAt }}</small></label>
            <p v-if="error" class="form-error" role="alert">{{ error }}</p>
            <button class="button primary full" type="submit">下一步 <ArrowRight :size="18" /></button>
          </form>

          <form v-else-if="step === 2" key="accounts" class="setup-step" @submit.prevent="next">
            <span class="step-icon"><UsersRound :size="24" /></span>
            <p class="eyebrow">第二步 · 两个专属账号</p>
            <h2>认识一下小屋的主人</h2>
            <p class="muted">两个账号彼此独立，登录后看到的是同一个情侣空间。</p>
            <div class="account-grid">
              <fieldset>
                <legend>♡ 第一个人</legend>
                <label class="field"><span>昵称</span><input id="setup-first-nickname" v-model="form.firstUser.nickname" required maxlength="20" autocomplete="nickname" placeholder="你希望对方看到的名字" :aria-invalid="Boolean(fieldErrors['firstUser.nickname'])" :aria-describedby="fieldErrors['firstUser.nickname'] ? 'setup-first-nickname-error' : undefined" /><small v-if="fieldErrors['firstUser.nickname']" id="setup-first-nickname-error" class="field-error">{{ fieldErrors['firstUser.nickname'] }}</small></label>
                <label class="field"><span>登录账号</span><input id="setup-first-username" v-model="form.firstUser.username" required minlength="3" maxlength="50" pattern="[A-Za-z0-9_.-]+" autocomplete="username" placeholder="3 位以上字母、数字或 _.-" :aria-invalid="Boolean(fieldErrors['firstUser.username'])" :aria-describedby="fieldErrors['firstUser.username'] ? 'setup-first-username-error' : undefined" /><small v-if="fieldErrors['firstUser.username']" id="setup-first-username-error" class="field-error">{{ fieldErrors['firstUser.username'] }}</small></label>
                <label class="field"><span>密码</span><span class="input-action"><input id="setup-first-password" v-model="form.firstUser.password" required minlength="8" maxlength="72" :type="reveal ? 'text' : 'password'" autocomplete="new-password" placeholder="至少 8 位" :aria-invalid="Boolean(fieldErrors['firstUser.password'])" :aria-describedby="fieldErrors['firstUser.password'] ? 'setup-first-password-error' : undefined" /><button type="button" :aria-label="reveal ? '隐藏密码' : '显示密码'" @click="reveal = !reveal"><EyeOff v-if="reveal" :size="18" /><Eye v-else :size="18" /></button></span><small v-if="fieldErrors['firstUser.password']" id="setup-first-password-error" class="field-error">{{ fieldErrors['firstUser.password'] }}</small></label>
              </fieldset>
              <fieldset>
                <legend>♡ 第二个人</legend>
                <label class="field"><span>昵称</span><input id="setup-second-nickname" v-model="form.secondUser.nickname" required maxlength="20" autocomplete="nickname" placeholder="对方的昵称" :aria-invalid="Boolean(fieldErrors['secondUser.nickname'])" :aria-describedby="fieldErrors['secondUser.nickname'] ? 'setup-second-nickname-error' : undefined" /><small v-if="fieldErrors['secondUser.nickname']" id="setup-second-nickname-error" class="field-error">{{ fieldErrors['secondUser.nickname'] }}</small></label>
                <label class="field"><span>登录账号</span><input id="setup-second-username" v-model="form.secondUser.username" required minlength="3" maxlength="50" pattern="[A-Za-z0-9_.-]+" autocomplete="username" placeholder="与第一个账号不同" :aria-invalid="Boolean(fieldErrors['secondUser.username'])" :aria-describedby="fieldErrors['secondUser.username'] ? 'setup-second-username-error' : undefined" /><small v-if="fieldErrors['secondUser.username']" id="setup-second-username-error" class="field-error">{{ fieldErrors['secondUser.username'] }}</small></label>
                <label class="field"><span>密码</span><input id="setup-second-password" v-model="form.secondUser.password" required minlength="8" maxlength="72" :type="reveal ? 'text' : 'password'" autocomplete="new-password" placeholder="至少 8 位" :aria-invalid="Boolean(fieldErrors['secondUser.password'])" :aria-describedby="fieldErrors['secondUser.password'] ? 'setup-second-password-error' : undefined" /><small v-if="fieldErrors['secondUser.password']" id="setup-second-password-error" class="field-error">{{ fieldErrors['secondUser.password'] }}</small></label>
              </fieldset>
            </div>
            <p v-if="error" class="form-error" role="alert">{{ error }}</p>
            <div class="button-row"><button class="button ghost" type="button" @click="step = 1"><ArrowLeft :size="18" />返回</button><button class="button primary" type="submit">确认账号 <ArrowRight :size="18" /></button></div>
          </form>

          <div v-else key="confirm" class="setup-step">
            <span class="step-icon"><KeyRound :size="24" /></span>
            <p class="eyebrow">最后一步 · 确认入住</p>
            <h2>准备好开启小世界了吗？</h2>
            <div class="confirm-space">
              <span class="confirm-heart"><Heart :size="25" fill="currentColor" /></span>
              <strong>{{ form.spaceName }}</strong>
              <p>{{ form.firstUser.nickname }} & {{ form.secondUser.nickname }}</p>
            <small>从 {{ formatDateTime(form.loveStartedAt) }} 开始</small>
            </div>
            <label class="field"><span>初始化口令</span><input id="setup-token" v-model="form.setupToken" required minlength="32" autocomplete="one-time-code" placeholder="填写 .env 中的 SETUP_TOKEN" :aria-invalid="Boolean(fieldErrors.setupToken)" :aria-describedby="fieldErrors.setupToken ? 'setup-token-error' : 'setup-token-hint'" /><small v-if="fieldErrors.setupToken" id="setup-token-error" class="field-error">{{ fieldErrors.setupToken }}</small><small v-else id="setup-token-hint">它只在第一次创建空间时使用，不会保存到网站。</small></label>
            <p class="privacy-note">只有知道账号和密码的你们能够进入。初始化完成后，为保护已有回忆，页面不会再次开放。</p>
            <p v-if="error" class="form-error" role="alert">{{ error }}</p>
            <div class="button-row"><button class="button ghost" type="button" :disabled="saving" @click="step = 2"><ArrowLeft :size="18" />返回</button><button class="button primary" type="button" :disabled="saving" @click="submit"><span v-if="saving" class="button-spinner"></span><Heart v-else :size="18" fill="currentColor" />{{ saving ? '正在准备…' : '创建 Love Space' }}</button></div>
          </div>
        </Transition>
      </div>
    </section>
  </main>
</template>
