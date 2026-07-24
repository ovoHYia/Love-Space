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
  moodDate?: string
  emoji: string
  label: string
  note?: string
  user?: UserProfile
  author?: UserProfile
  updatedAt?: string
}

export interface MoodTrendPoint {
  date: string
  userId: number | string
  nickname: string
  emoji: string
  label: string
  note?: string | null
  score: number
}

export interface MoodDistribution {
  label: string
  emoji: string
  count: number
  percentage: number
}

export interface MoodPersonSummary {
  userId: number | string
  nickname: string
  recordedDays: number
  averageScore: number
  dominantLabel?: string | null
  dominantEmoji?: string | null
}

export interface MonthlyHighlight {
  type: 'MEMORY' | 'DIARY' | 'LETTER' | 'WISH'
  id: number | string
  title: string
  date: string
}

export interface MonthlyReport {
  month: string
  from: string
  to: string
  daysInScope: number
  totalMoodEntries: number
  recordedDays: number
  sharedMoodDays: number
  longestStreak: number
  resonanceRate: number
  coverageRate: number
  insight: string
  trend: MoodTrendPoint[]
  distribution: MoodDistribution[]
  people: MoodPersonSummary[]
  activities: {
    memories: number
    diaries: number
    letters: number
    completedWishes: number
  }
  highlights: MonthlyHighlight[]
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

export interface WishInput {
  title: string
  description?: string
  category: 'TRAVEL' | 'DATE' | 'FOOD' | 'MOVIE' | 'OTHER'
  targetDate?: string
}

export interface Wish extends WishInput {
  id: number | string
  createdBy: number | string
  createdByNickname: string
  status: 'ACTIVE' | 'COMPLETED'
  completedBy?: number | string | null
  completedByNickname?: string | null
  completedAt?: string | null
  createdAt?: string
  updatedAt?: string
}

export type CalendarSource = 'CUSTOM' | 'ANNIVERSARY' | 'WISH' | 'MEMORY' | 'DIARY' | 'LETTER'

export interface CalendarEventInput {
  title: string
  description?: string
  startAt: string
  endAt?: string | null
  allDay: boolean
  category: 'DATE' | 'TRAVEL' | 'FAMILY' | 'PERSONAL' | 'OTHER'
  location?: string
}

export interface CalendarEntry {
  sourceType: CalendarSource
  id: number | string
  title: string
  description?: string | null
  startAt: string
  endAt?: string | null
  allDay: boolean
  category: string
  location?: string | null
  editable: boolean
  createdBy?: number | string
  createdByNickname?: string
}

export interface TrashItem {
  type: 'MEMORY' | 'DIARY' | 'MESSAGE' | 'ANNIVERSARY' | 'WISH' | 'CALENDAR_EVENT'
  id: number | string
  title: string
  deletedAt: string
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
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
  unreadCount: number
  summary: {
    total: number
    unread: number
    read: number
    anniversaries: number
    letters: number
    wishes: number
  }
}

export interface NotificationPreferences {
  anniversaryEnabled: boolean
  letterEnabled: boolean
  wishEnabled: boolean
  updatedAt?: string
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
