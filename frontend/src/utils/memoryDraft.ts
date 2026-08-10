export const MEMORY_DRAFT_VERSION = 1 as const
export const MEMORY_DRAFT_MAX_AGE_MS = 24 * 60 * 60 * 1000
export const NEW_MEMORY_DRAFT_KEY = 'love-space:memory-draft:new'

export interface MemoryFileSnapshot {
  name: string
  size: number
  type: string
}

export interface MemoryEditorFormValues {
  title: string
  description: string
  eventAt: string
  eventTimeKnown: boolean
  location: string
  tags: readonly string[]
}

export interface MemoryEditorSnapshot extends MemoryEditorFormValues {
  tagInput: string
  newFiles: MemoryFileSnapshot[]
}

export interface MemoryDraftForm {
  title: string
  description: string
  eventAt: string
  eventTimeKnown: boolean
  location: string
  tags: string[]
  tagInput: string
}

export interface MemoryDraft {
  version: typeof MEMORY_DRAFT_VERSION
  updatedAt: number
  form: MemoryDraftForm
  pendingMedia: MemoryFileSnapshot[]
}

function trimmed(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function fileSnapshot(value: unknown): MemoryFileSnapshot | null {
  if (!value || typeof value !== 'object') return null
  const candidate = value as Partial<MemoryFileSnapshot>
  if (typeof candidate.name !== 'string' || typeof candidate.type !== 'string' || typeof candidate.size !== 'number') return null
  if (!Number.isFinite(candidate.size) || candidate.size < 0) return null
  return {
    name: candidate.name.trim(),
    size: candidate.size,
    type: candidate.type.trim(),
  }
}

function copyFiles(values: readonly MemoryFileSnapshot[]) {
  return values.map(fileSnapshot).filter((value): value is MemoryFileSnapshot => value !== null)
}

export function createMemoryEditorSnapshot(
  form: MemoryEditorFormValues,
  tagInput: string,
  newFiles: readonly MemoryFileSnapshot[],
): MemoryEditorSnapshot {
  return {
    title: trimmed(form.title),
    description: trimmed(form.description),
    eventAt: trimmed(form.eventAt),
    eventTimeKnown: form.eventTimeKnown !== false,
    location: trimmed(form.location),
    tags: form.tags.map(trimmed),
    tagInput: trimmed(tagInput),
    newFiles: copyFiles(newFiles),
  }
}

export function memorySnapshotKey(snapshot: MemoryEditorSnapshot) {
  return JSON.stringify(snapshot)
}

export function areMemorySnapshotsEqual(left: MemoryEditorSnapshot, right: MemoryEditorSnapshot) {
  return memorySnapshotKey(createMemoryEditorSnapshot(left, left.tagInput, left.newFiles))
    === memorySnapshotKey(createMemoryEditorSnapshot(right, right.tagInput, right.newFiles))
}

export function memoryDraftKey(memoryId?: number | string | null) {
  return memoryId === undefined || memoryId === null
    ? NEW_MEMORY_DRAFT_KEY
    : `love-space:memory-draft:edit:${String(memoryId)}`
}

export function createMemoryDraft(
  form: MemoryEditorFormValues,
  tagInput: string,
  pendingMedia: readonly MemoryFileSnapshot[],
  updatedAt = Date.now(),
): MemoryDraft {
  return {
    version: MEMORY_DRAFT_VERSION,
    updatedAt,
    form: {
      title: form.title,
      description: form.description,
      eventAt: form.eventAt,
      eventTimeKnown: form.eventTimeKnown !== false,
      location: form.location,
      tags: [...form.tags],
      tagInput,
    },
    pendingMedia: copyFiles(pendingMedia),
  }
}

export function serializeMemoryDraft(draft: MemoryDraft) {
  return JSON.stringify({
    version: MEMORY_DRAFT_VERSION,
    updatedAt: draft.updatedAt,
    form: {
      title: draft.form.title,
      description: draft.form.description,
      eventAt: draft.form.eventAt,
      eventTimeKnown: draft.form.eventTimeKnown !== false,
      location: draft.form.location,
      tags: [...draft.form.tags],
      tagInput: draft.form.tagInput,
    },
    pendingMedia: copyFiles(draft.pendingMedia),
  })
}

export function isMemoryDraftExpired(updatedAt: number, now = Date.now()) {
  return !Number.isFinite(updatedAt) || now - updatedAt > MEMORY_DRAFT_MAX_AGE_MS
}

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((item) => typeof item === 'string')
}

export function parseMemoryDraft(raw: string | null, now = Date.now()): MemoryDraft | null {
  if (!raw) return null
  try {
    const value: unknown = JSON.parse(raw)
    if (!value || typeof value !== 'object') return null
    const candidate = value as Partial<MemoryDraft>
    if (candidate.version !== MEMORY_DRAFT_VERSION || typeof candidate.updatedAt !== 'number' || isMemoryDraftExpired(candidate.updatedAt, now)) return null
    if (!candidate.form || typeof candidate.form !== 'object') return null
    const form = candidate.form as Partial<MemoryDraftForm>
    if (
      typeof form.title !== 'string'
      || typeof form.description !== 'string'
      || typeof form.eventAt !== 'string'
      || typeof form.location !== 'string'
      || !isStringArray(form.tags)
      || typeof form.tagInput !== 'string'
    ) return null
    const pendingMedia = Array.isArray(candidate.pendingMedia) ? candidate.pendingMedia : []
    return {
      version: MEMORY_DRAFT_VERSION,
      updatedAt: candidate.updatedAt,
      form: {
        title: form.title,
        description: form.description,
        eventAt: form.eventAt,
        eventTimeKnown: form.eventTimeKnown !== false,
        location: form.location,
        tags: [...form.tags],
        tagInput: form.tagInput,
      },
      pendingMedia: pendingMedia.map(fileSnapshot).filter((item): item is MemoryFileSnapshot => item !== null),
    }
  } catch {
    return null
  }
}
