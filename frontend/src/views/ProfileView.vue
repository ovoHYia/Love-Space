<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ArchiveRestore, Camera, Download, Heart, KeyRound, LogOut, RefreshCw, Save, ShieldCheck, UserRound } from 'lucide-vue-next'
import { api } from '../api'
import { ApiError, errorMessage } from '../api/client'
import { useToast } from '../composables/toast'
import { applyAuth, authState, clearAuth } from '../stores/auth'
import BaseAvatar from '../components/BaseAvatar.vue'
import LoadingState from '../components/LoadingState.vue'
import BaseModal from '../components/BaseModal.vue'
import ConflictPanel from '../components/ConflictPanel.vue'
import { formatDate } from '../utils'
import { isStaleUpdate, STALE_UPDATE_MESSAGE } from '../utils/editConflict'
import { clearMemoryDraftsForUser } from '../utils/memoryDraft'
import { validateUploadFile } from '../utils/memoryMedia'
import { useResourceSync } from '../composables/resourceSync'
import { createRequestGeneration } from '../utils/latestRequest'

const router = useRouter()
const { show } = useToast()
const nickname = ref(authState.user?.nickname || '')
const spaceName = ref(authState.spaceName)
const loading = ref(false)
const saving = ref(false)
const spaceSaving = ref(false)
const uploading = ref(false)
const loggingOut = ref(false)
const error = ref('')
const avatarInput = ref<HTMLInputElement | null>(null)
const passwordOpen = ref(false)
const passwordSaving = ref(false)
const passwordForm = ref({ currentPassword: '', newPassword: '', confirmPassword: '' })
const profileFieldErrors = ref<Record<string, string>>({})
const spaceFieldErrors = ref<Record<string, string>>({})
const passwordFieldErrors = ref<Record<string, string>>({})
const profileConflict = ref(false)
const spaceConflict = ref(false)
// 编辑器打开时保留服务器版本；收到实时资料更新时不能用新版本覆盖这个版本。
const profileVersion = ref<number | string | undefined>(authState.user?.version)
const spaceVersion = ref<number | string | undefined>(authState.coupleVersion)
const profileRequests = createRequestGeneration()

const initials = computed(() => `${authState.user?.nickname?.slice(0, 1) || '我'} & ${authState.partner?.nickname?.slice(0, 1) || 'TA'}`)

onMounted(() => { void load() })
useResourceSync(['profile', 'space'], () => load())

async function load(forceEditorValues = false) {
  const request = profileRequests.begin()
  loading.value = true
  error.value = ''
  try {
    const latest = await api.me()
    if (!request.isLatest()) return
    const profileDirty = nickname.value.trim() !== (authState.user?.nickname || '')
    const spaceDirty = spaceName.value.trim() !== authState.spaceName
    applyAuth(latest)
    if (forceEditorValues || !profileDirty) {
      nickname.value = authState.user?.nickname || ''
      profileVersion.value = authState.user?.version
    } else profileConflict.value = true
    if (forceEditorValues || !spaceDirty) {
      spaceName.value = authState.spaceName
      spaceVersion.value = authState.coupleVersion
    } else spaceConflict.value = true
    if (forceEditorValues) {
      profileConflict.value = false
      spaceConflict.value = false
    }
    return true
  } catch (cause) {
    if (!request.isLatest()) return
    error.value = errorMessage(cause)
    return false
  } finally {
    if (request.isLatest()) loading.value = false
  }
}

async function loadLatestProfile() {
  await load(true)
  if (!error.value) show('已加载最新资料，请确认后再保存。', 'info')
}

async function saveProfile() {
  saving.value = true
  profileFieldErrors.value = {}
  try {
    const updated = await api.updateProfile(nickname.value.trim(), profileVersion.value)
    profileRequests.cancel()
    loading.value = false
    if (updated?.id) {
      authState.user = { ...authState.user!, ...updated }
      profileVersion.value = updated.version
    }
    else if (authState.user) authState.user.nickname = nickname.value.trim()
    show('昵称已经更新。', 'success')
  } catch (cause) {
    if (isStaleUpdate(cause)) {
      profileConflict.value = true
      show(STALE_UPDATE_MESSAGE, 'error')
      return
    }
    profileFieldErrors.value = cause instanceof ApiError ? cause.fieldErrors || {} : {}
    show(errorMessage(cause), 'error')
  } finally {
    saving.value = false
  }
}

async function saveSpaceName() {
  spaceSaving.value = true
  spaceFieldErrors.value = {}
  try {
    const updated = await api.updateSpaceName(spaceName.value.trim(), spaceVersion.value)
    profileRequests.cancel()
    loading.value = false
    authState.spaceName = updated.spaceName
    authState.coupleVersion = updated.version
    spaceVersion.value = updated.version
    spaceName.value = authState.spaceName
    show('空间名称已经更新。', 'success')
  } catch (cause) {
    if (isStaleUpdate(cause)) {
      spaceConflict.value = true
      show(STALE_UPDATE_MESSAGE, 'error')
      return
    }
    spaceFieldErrors.value = cause instanceof ApiError ? cause.fieldErrors || {} : {}
    show(errorMessage(cause), 'error')
  } finally {
    spaceSaving.value = false
  }
}

async function uploadAvatar(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  const problem = validateUploadFile(file, true)
  if (problem) {
    show(problem, 'error')
    return
  }
  uploading.value = true
  try {
    await api.updateAvatar(file)
    profileRequests.cancel()
    await load()
    show('新头像很好看，已经换上啦。', 'success')
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    uploading.value = false
    if (avatarInput.value) avatarInput.value.value = ''
  }
}

async function logout() {
  const userId = authState.user?.id
  loggingOut.value = true
  try {
    await api.logout()
    clearMemoryDraftsForUser(userId)
    clearAuth()
    await navigateToLogin()
  } catch (cause) {
    if (cause instanceof ApiError && (cause.status === 401 || cause.status === 403)) {
      clearMemoryDraftsForUser(userId)
      clearAuth()
      await navigateToLogin()
      return
    }
    show('暂时无法确认已退出服务器，请恢复网络后重试。', 'error')
  } finally {
    loggingOut.value = false
  }
}

async function navigateToLogin(query: Record<string, string> = {}) {
  const failure = await router.replace({ name: 'login', query }).catch(() => true)
  if (failure || router.currentRoute.value.name !== 'login') {
    const queryString = new URLSearchParams(query).toString()
    window.location.hash = `/login${queryString ? `?${queryString}` : ''}`
  }
}

async function changePassword() {
  passwordFieldErrors.value = {}
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    show('两次输入的新密码不一致。', 'error')
    return
  }
  passwordSaving.value = true
  try {
    await api.changePassword(passwordForm.value.currentPassword, passwordForm.value.newPassword)
    passwordForm.value = { currentPassword: '', newPassword: '', confirmPassword: '' }
    passwordOpen.value = false
    show('密码已修改，请重新登录。', 'success')
    clearAuth()
    await navigateToLogin({ expired: '1' })
  } catch (cause) {
    passwordFieldErrors.value = cause instanceof ApiError ? cause.fieldErrors || {} : {}
    show(errorMessage(cause), 'error')
  } finally {
    passwordSaving.value = false
  }
}
</script>

<template>
  <div class="page-stack profile-page">
    <header class="page-header"><div><p class="eyebrow">OUR LITTLE HOME</p><h1>关于我们</h1><p>把这里整理成你们最熟悉、最舒服的样子。</p></div></header>
    <LoadingState v-if="loading" label="正在整理个人资料…" />
    <div v-else-if="error" class="error-panel" role="alert"><p>{{ error }}</p><button class="button secondary" type="button" @click="$router.go(0)"><RefreshCw :size="17" />重新加载</button></div>
    <template v-else>
      <section class="profile-hero">
        <div class="profile-couple">
          <BaseAvatar :user="authState.user" size="xl" /><span class="profile-heart"><Heart :size="24" fill="currentColor" /></span><BaseAvatar :user="authState.partner" size="xl" />
        </div>
        <p class="eyebrow">{{ authState.spaceName }}</p><h2>{{ authState.user?.nickname }} & {{ authState.partner?.nickname || '心上人' }}</h2><p>从 {{ formatDate(authState.loveStartedAt) }} 开始，把平凡日子认真收藏。</p>
      </section>

      <div class="settings-grid">
        <section class="card settings-card">
          <div class="section-heading"><div><p class="eyebrow">MY PROFILE</p><h2>我的资料</h2></div><UserRound :size="21" /></div>
          <div class="avatar-setting">
            <div class="avatar-edit"><BaseAvatar :user="authState.user" size="lg" /><button type="button" aria-label="更换头像" :disabled="uploading" @click="avatarInput?.click()"><span v-if="uploading" class="button-spinner"></span><Camera v-else :size="17" /></button><input ref="avatarInput" class="sr-only" type="file" accept="image/*" @change="uploadAvatar" /></div>
            <div><strong>更换头像</strong><p>选择一张清晰的方形图片效果最好。</p></div>
          </div>
          <form class="stack-form compact-form" @submit.prevent="saveProfile">
            <ConflictPanel v-if="profileConflict" @reload="loadLatestProfile" />
            <label class="field"><span>昵称</span><input id="profile-nickname" v-model="nickname" required maxlength="20" autocomplete="nickname" :aria-invalid="Boolean(profileFieldErrors.nickname)" :aria-describedby="profileFieldErrors.nickname ? 'profile-nickname-error' : undefined" /><small v-if="profileFieldErrors.nickname" id="profile-nickname-error" class="field-error">{{ profileFieldErrors.nickname }}</small></label>
            <label class="field"><span>登录账号</span><input :value="authState.user?.username || '已设置'" disabled /><small>为了安全，登录账号不能在这里修改。</small></label>
            <button class="button primary" type="submit" :disabled="saving || nickname.trim() === authState.user?.nickname"><span v-if="saving" class="button-spinner"></span><Save v-else :size="17" />{{ saving ? '正在保存…' : '保存资料' }}</button>
            <button class="button secondary" type="button" @click="passwordOpen = true"><KeyRound :size="17" />修改密码</button>
          </form>
        </section>

        <section class="card settings-card">
          <div class="section-heading"><div><p class="eyebrow">OUR SPACE</p><h2>空间信息</h2></div><Heart :size="21" /></div>
          <form class="space-name-editor" @submit.prevent="saveSpaceName">
            <ConflictPanel v-if="spaceConflict" @reload="loadLatestProfile" />
            <label class="field"><span>空间名称</span><input id="space-name" v-model="spaceName" required maxlength="100" autocomplete="off" :aria-invalid="Boolean(spaceFieldErrors.spaceName)" :aria-describedby="spaceFieldErrors.spaceName ? 'space-name-error' : undefined" /><small v-if="spaceFieldErrors.spaceName" id="space-name-error" class="field-error">{{ spaceFieldErrors.spaceName }}</small></label>
            <button class="button primary" type="submit" :disabled="spaceSaving || !spaceName.trim() || spaceName.trim() === authState.spaceName"><span v-if="spaceSaving" class="button-spinner"></span><Save v-else :size="17" />{{ spaceSaving ? '正在保存…' : '保存空间名称' }}</button>
          </form>
          <dl class="space-facts">
            <div><dt>成员</dt><dd>{{ initials }}</dd></div><div><dt>恋爱起点</dt><dd>{{ formatDate(authState.loveStartedAt) }}</dd></div><div><dt>可见范围</dt><dd><ShieldCheck :size="16" />仅你们两个人</dd></div>
          </dl>
          <div class="safety-note"><ShieldCheck :size="20" /><p><strong>回忆由服务器保存</strong><span>退出登录不会删除任何内容，换设备登录也能继续查看。</span></p></div>
        </section>
      </div>

      <section class="card account-actions-card">
        <div><span class="account-icon"><ArchiveRestore :size="20" /></span><p><strong>数据与回收站</strong><span>导出你的空间数据，或者恢复最近删除的内容。</span></p></div>
        <RouterLink class="button secondary" to="/data-management"><Download :size="17" />管理数据</RouterLink>
      </section>

      <section class="card account-actions-card">
        <div><span class="account-icon"><LogOut :size="20" /></span><p><strong>暂时离开小屋</strong><span>退出后需要重新输入账号和密码，所有回忆都会保留。</span></p></div>
        <button class="button danger-button" type="button" :disabled="loggingOut" @click="logout"><span v-if="loggingOut" class="button-spinner"></span><LogOut v-else :size="17" />{{ loggingOut ? '正在退出…' : '退出登录' }}</button>
      </section>

      <footer class="product-foot"><span class="brand-mark"><Heart :size="15" fill="currentColor" /></span><p><strong>Love Space</strong><span>愿每一个普通日子，都有人与你认真度过。</span></p><small>v1.0</small></footer>
    </template>
  </div>
  <BaseModal v-if="passwordOpen" title="修改登录密码" description="更新后请使用新密码登录。" @close="passwordOpen = false">
    <form class="stack-form" @submit.prevent="changePassword">
      <label class="field"><span>当前密码</span><input id="password-current" v-model="passwordForm.currentPassword" required type="password" minlength="8" autocomplete="current-password" :aria-invalid="Boolean(passwordFieldErrors.currentPassword)" :aria-describedby="passwordFieldErrors.currentPassword ? 'password-current-error' : undefined" /><small v-if="passwordFieldErrors.currentPassword" id="password-current-error" class="field-error">{{ passwordFieldErrors.currentPassword }}</small></label>
      <label class="field"><span>新密码</span><input id="password-new" v-model="passwordForm.newPassword" required type="password" minlength="8" maxlength="72" autocomplete="new-password" :aria-invalid="Boolean(passwordFieldErrors.newPassword)" :aria-describedby="passwordFieldErrors.newPassword ? 'password-new-error' : undefined" /><small v-if="passwordFieldErrors.newPassword" id="password-new-error" class="field-error">{{ passwordFieldErrors.newPassword }}</small></label>
      <label class="field"><span>再次输入新密码</span><input id="password-confirm" v-model="passwordForm.confirmPassword" required type="password" minlength="8" maxlength="72" autocomplete="new-password" :aria-invalid="passwordForm.confirmPassword.length > 0 && passwordForm.newPassword !== passwordForm.confirmPassword" :aria-describedby="passwordForm.confirmPassword.length > 0 && passwordForm.newPassword !== passwordForm.confirmPassword ? 'password-confirm-error' : undefined" /><small v-if="passwordForm.confirmPassword.length > 0 && passwordForm.newPassword !== passwordForm.confirmPassword" id="password-confirm-error" class="field-error">两次输入的新密码不一致。</small></label>
      <div class="modal-actions"><button class="button ghost" type="button" @click="passwordOpen = false">取消</button><button class="button primary" type="submit" :disabled="passwordSaving"><span v-if="passwordSaving" class="button-spinner"></span><KeyRound v-else :size="17" />{{ passwordSaving ? '正在更新…' : '更新密码' }}</button></div>
    </form>
  </BaseModal>
</template>
