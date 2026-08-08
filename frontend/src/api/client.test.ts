// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { startDownload } from './client'

describe('streaming downloads', () => {
  afterEach(() => vi.restoreAllMocks())

  it('hands the export URL to the browser without creating a Blob', () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    startDownload('/data/export')

    expect(click).toHaveBeenCalledOnce()
    expect(document.querySelector('a')).toBeNull()
  })
})
