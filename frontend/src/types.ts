export interface UserProfile {
  id: number | string
  username?: string
  nickname: string
  avatarUrl?: string | null
  version?: number | string
}

export interface CoupleSummary {
  id: number | string
  spaceName: string
  loveStartedAt: string
  version?: number | string
}

export interface AuthPayload {
  user: UserProfile
  partner: UserProfile
  couple: CoupleSummary
}

export interface Mood {
  id?: number | string
  userId?: number | string
  moodDate?: string
  emoji: string
  label: string
  note?: string
  updatedAt?: string
  version?: number | string
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
  id: number | string
  contentType: string
  mediaType: string
  originalName: string
  url: string
  byteSize: number
  sha256?: string | null
  createdAt: string
}

export interface Memory {
  id: number | string
  title: string
  description?: string
  eventAt: string
  eventTimeKnown: boolean
  location?: string
  tags?: string[]
  createdAt?: string
  authorId: number | string
  authorNickname: string
  media: MediaItem[]
  updatedAt?: string
  version?: number | string
}

export interface MemoryTag {
  name: string
  memoryCount: number
}

export interface AlbumItem {
  media: MediaItem
  memoryId: number | string
  memoryTitle: string
  eventAt: string
  location?: string | null
  tags: string[]
}

export interface ExportPreparation {
  downloadUrl: string
  filename: string
  expiresAt: string
}

export interface MediaIntegrity {
  scannedRecords: number
  healthyRecords: number
  hashBackfilled: number
  missingFiles: number
  sizeMismatches: number
  hashMismatches: number
  orphanFiles: number
  quarantinedFiles: number
  quarantineFailures: number
  checkedAt: string
  details: string[]
}

export interface SyncEvent {
  action: string
  resource: string
  actorId: number | string
  sourceClientId?: string | null
  occurredAt: string
}

export type GameType = 'TACIT_QUIZ' | 'DRAW_GUESS' | 'MEMORY_GUESS' | 'TRUTH_CARD'
export type GameStatus = 'ACTIVE' | 'FINISHED'

export interface GamePoint {
  x: number
  y: number
}

export interface GameStroke {
  tool?: 'DRAW' | 'ERASE'
  color: string
  width: number
  points: GamePoint[]
}

export interface GameGuess {
  userId: number | string
  nickname: string
  text: string
  correct: boolean
  createdAt: string
}

export interface GameMemory {
  imageUrl?: string | null
  title?: string | null
  description?: string | null
  eventAt?: string | null
  location?: string | null
}

export interface GameSession {
  id: number | string
  revision: number | string
  gameType: GameType
  status: GameStatus
  createdBy: number | string
  createdByNickname: string
  roundNumber: number
  currentTurnUserId?: number | string | null
  prompt?: string | null
  options: string[]
  myAnswer?: string | null
  partnerAnswer?: string | null
  answersRevealed: boolean
  matched?: boolean | null
  score: number
  secretWord?: string | null
  strokes: GameStroke[]
  guesses: GameGuess[]
  roundComplete: boolean
  cardCategory?: string | null
  memory?: GameMemory | null
  createdAt: string
  updatedAt: string
  finishedAt?: string | null
}

export interface Diary {
  id: number | string
  title: string
  content: string
  diaryDate: string
  mood?: string
  authorId: number | string
  authorNickname: string
  createdAt?: string
  updatedAt?: string
  version?: number | string
}

export interface Letter {
  id: number | string
  content?: string | null
  createdAt: string
  readAt?: string | null
  scheduled: boolean
  deliverAt: string
  authorId: number | string
  authorNickname: string
  recipientId: number | string
  recipientNickname: string
  version?: number | string
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
  version?: number | string
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
  version?: number | string
}

export interface WishUpdateInput extends WishInput {
  version: number | string
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

export interface CalendarEventUpdateInput extends CalendarEventInput {
  version: number | string
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
  version?: number | string
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
  version?: number | string
}

export interface DashboardPayload {
  account: AuthPayload
  todayMoods: Mood[]
  recentMemories: Memory[]
  recentDiaries: Diary[]
  recentMessages: Letter[]
  anniversaries: Anniversary[]
  dueReminders: Anniversary[]
  unreadMessages: number
}

export interface SpringPage<T> {
  content: T[]
  totalPages: number
  totalElements: number
  page: number
  size: number
  first: boolean
  last: boolean
}
