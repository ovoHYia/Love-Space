<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Heart, Mail, MailCheck, Mails as MailHeart, PenLine, RefreshCw, Send, Trash2 } from 'lucide-vue-next'
import { api } from '../api'
import { errorMessage } from '../api/client'
import { useToast } from '../composables/toast'
import { authState } from '../stores/auth'
import BaseAvatar from '../components/BaseAvatar.vue'
import BaseModal from '../components/BaseModal.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadingState from '../components/LoadingState.vue'
import type { Letter, UserProfile } from '../types'
import { formatDate, formatDateTime, sameId } from '../utils'

const { show } = useToast()
const letters = ref<Letter[]>([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const composerOpen = ref(false)
const content = ref('')

const sortedLetters = computed(() => [...letters.value].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()))

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const page = await api.messages()
    letters.value = page.content ?? []
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

function sender(letter: Letter): UserProfile | undefined {
  if (letter.author || letter.sender) return letter.author || letter.sender
  if (letter.authorNickname) return { id: letter.authorId ?? '', nickname: letter.authorNickname }
  return sameId(letter.authorId, authState.user?.id) ? authState.user || undefined : authState.partner || undefined
}
function mine(letter: Letter) { return sameId(sender(letter)?.id ?? letter.authorId, authState.user?.id) }
function unread(letter: Letter) { return !mine(letter) && !(letter.read || letter.isRead || letter.readAt) }

async function openLetter(letter: Letter) {
  if (!unread(letter)) return
  try {
    const opened = await api.readMessage(letter.id)
    letter.content = opened.content
    letter.read = true
    letter.isRead = true
    letter.readAt = new Date().toISOString()
  } catch (cause) {
    show(errorMessage(cause), 'error')
  }
}

async function send() {
  saving.value = true
  try {
    await api.createMessage(content.value.trim())
    content.value = ''
    composerOpen.value = false
    show('信笺已经送到对方的信箱。', 'success')
    await load()
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    saving.value = false
  }
}

async function remove(letter: Letter) {
  if (!window.confirm('确定收回这封信笺吗？删除后无法恢复。')) return
  try {
    await api.deleteMessage(letter.id)
    letters.value = letters.value.filter((item) => item.id !== letter.id)
    show('这封信笺已经收回。', 'success')
  } catch (cause) {
    show(errorMessage(cause), 'error')
  }
}
</script>

<template>
  <div class="page-stack letters-page">
    <header class="page-header">
      <div><p class="eyebrow">SLOW WORDS, WARM HEART</p><h1>给你的信笺</h1><p>不追求秒回，只把此刻想告诉你的话，认真寄出去。</p></div>
      <button class="button primary" type="button" @click="composerOpen = true"><PenLine :size="18" />写一封信</button>
    </header>

    <section class="mailbox-summary">
      <div class="mailbox-icon"><MailHeart :size="25" /></div>
      <div><strong>{{ authState.user?.nickname }} 与 {{ authState.partner?.nickname || '心上人' }} 的小信箱</strong><p>共有 {{ letters.length }} 封信，其中 {{ letters.filter(unread).length }} 封还没拆开。</p></div>
      <span class="postmark">LOVE<br />POST</span>
    </section>

    <LoadingState v-if="loading" label="正在打开信箱…" />
    <div v-else-if="error" class="error-panel" role="alert"><p>{{ error }}</p><button class="button secondary" type="button" @click="load"><RefreshCw :size="17" />重新加载</button></div>
    <EmptyState v-else-if="!letters.length" title="信箱里还没有信" description="写一句平时不好意思当面说的话，让它慢慢抵达。"><button class="button primary small" type="button" @click="composerOpen = true"><PenLine :size="17" />写第一封信</button></EmptyState>
    <div v-else class="letter-grid">
      <article v-for="(letter, index) in sortedLetters" :key="letter.id" class="letter-card" :class="{ incoming: !mine(letter), unread: unread(letter), rose: index % 3 === 1, sage: index % 3 === 2 }">
        <div class="letter-top"><span class="letter-from"><BaseAvatar :user="sender(letter)" size="sm" /><span><small>FROM</small><strong>{{ sender(letter)?.nickname || (mine(letter) ? authState.user?.nickname : authState.partner?.nickname) }}</strong></span></span><span class="letter-status"><Mail v-if="unread(letter)" :size="15" /><MailCheck v-else :size="15" />{{ unread(letter) ? '等待拆开' : (mine(letter) ? '已寄出' : '已读') }}</span></div>
        <div class="letter-body">
          <template v-if="!unread(letter)"><span class="quote-mark">“</span><p>{{ letter.content }}</p><span class="letter-heart"><Heart :size="17" fill="currentColor" /></span></template>
          <button v-else class="sealed-letter" type="button" @click="openLetter(letter)"><span class="wax-seal"><Heart :size="22" fill="currentColor" /></span><strong>有一封信等你亲手拆开</strong><small>轻点封印，看看 TA 想说什么</small></button>
        </div>
        <footer><span>{{ formatDateTime(letter.createdAt) }}</span><button v-if="mine(letter)" class="icon-button danger" type="button" aria-label="删除这封信" @click="remove(letter)"><Trash2 :size="16" /></button><span v-else-if="letter.readAt">于 {{ formatDate(letter.readAt, { month: 'short', day: 'numeric' }) }} 拆阅</span></footer>
      </article>
    </div>
  </div>

  <BaseModal v-if="composerOpen" title="写给心上人的信" :description="`收信人：${authState.partner?.nickname || '你的心上人'}`" @close="composerOpen = false">
    <form class="stack-form letter-form" @submit.prevent="send">
      <div class="letter-paper-input"><span class="paper-to">TO：{{ authState.partner?.nickname || '宝贝' }}</span><textarea v-model="content" required minlength="1" maxlength="2000" rows="10" autofocus placeholder="想对 TA 说些什么？不必很长，真心就好。"></textarea><div class="paper-sign">FROM：{{ authState.user?.nickname }} ♡</div></div>
      <div class="character-row"><span>{{ content.length }}/2000</span><span>发送后会出现在对方的信箱</span></div>
      <button class="button primary full" type="submit" :disabled="saving || !content.trim()"><span v-if="saving" class="button-spinner"></span><Send v-else :size="18" />{{ saving ? '正在寄出…' : '把这封信寄出去' }}</button>
    </form>
  </BaseModal>
</template>
