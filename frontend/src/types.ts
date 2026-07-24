export interface UserProfile {
  id: number | string
  username?: string
  nickname: string
  avatarMediaId?: number | string | null
  avatarUrl?: string | null
}

export interface CoupleSummary {
  id?: number | string
  spaceName?: string
  loveStartedAt?: string
}

export interface AuthPayload {
  user?: UserProfile
  currentUser?: UserProfile
  partner?: UserProfile
  couple?: CoupleSummary
  spaceName?: string
  loveStartedAt?: string
}

export interface Mood {
  id?: number | string
  userId?: number | string
  emoji: string
  label: string
  note?: string
  user?: UserProfile
  author?: UserProfile
  updatedAt?: string
}

export interface MediaItem {
  id?: number | string
  mediaId?: number | string
  contentType?: string
  mediaType?: string
  type?: string
  originalName?: string
  url?: string
}

export interface Memory {
  id: number | string
  title: string
  description?: string
  eventAt: string
  location?: string
  createdAt?: string
  author?: UserProfile
  authorId?: number | string
  authorNickname?: string
  media?: MediaItem[]
  files?: MediaItem[]
}

export interface Diary {
  id: number | string
  title: string
  content: string
  diaryDate: string
  mood?: string
  author?: UserProfile
  authorId?: number | string
  authorNickname?: string
  createdAt?: string
  updatedAt?: string
}

export interface Letter {
  id: number | string
  content?: string | null
  createdAt: string
  read?: boolean
  isRead?: boolean
  readAt?: string | null
  scheduled?: boolean
  deliverAt?: string
  author?: UserProfile
  sender?: UserProfile
  authorId?: number | string
  authorNickname?: string
  recipientId?: number | string
  recipientNickname?: string
}

export interface Anniversary {
  id: number | string
  title: string
  eventDate: string
  type: string
  recurringYearly: boolean
  reminderDays: number
  note?: string
  daysUntil?: number
}

export interface AppNotification {
  id: number | string
  type: string
  title: string
  body: string
  referenceType?: string | null
  referenceId?: number | string | null
  readAt?: string | null
  createdAt: string
}

export interface NotificationList {
  items: AppNotification[]
  unreadCount: number
}

export interface DashboardPayload {
  account?: AuthPayload
  currentUser?: UserProfile
  user?: UserProfile
  partner?: UserProfile
  couple?: CoupleSummary
  spaceName?: string
  loveStartedAt?: string
  moods?: Mood[]
  todayMoods?: Mood[]
  recentMemories?: Memory[]
  latestMessages?: Letter[]
  latestMessage?: Letter
  recentMessages?: Letter[]
  nextAnniversaries?: Anniversary[]
  nextAnniversary?: Anniversary
  anniversaries?: Anniversary[]
  dueReminders?: Anniversary[]
  randomMemory?: Memory
}

export interface SpringPage<T> {
  content: T[]
  totalPages?: number
  totalElements?: number
  page?: number
  size?: number
  first?: boolean
  number?: number
  last?: boolean
}
