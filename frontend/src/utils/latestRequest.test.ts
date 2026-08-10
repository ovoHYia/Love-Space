import { describe, expect, it } from 'vitest'
import { createRequestGeneration, type RequestGeneration } from './latestRequest'

describe('latest request generation', () => {
  it('lets only the newest response commit page state', async () => {
    const generation = createRequestGeneration()
    const values: string[] = []
    const first = generation.begin()
    const second = generation.begin()

    const oldResponse = new Promise<string>(resolve => { setTimeout(() => resolve('旧筛选结果'), 10) })
    const newResponse = Promise.resolve('新筛选结果')
    const commit = (request: RequestGeneration, value: string) => {
      if (request.isLatest()) values.push(value)
    }
    commit(second, await newResponse)
    commit(first, await oldResponse)

    expect(values).toEqual(['新筛选结果'])
  })

  it('invalidates an in-flight response when the state is cancelled', () => {
    const generation = createRequestGeneration()
    const request = generation.begin()

    generation.cancel()

    expect(request.isLatest()).toBe(false)
  })
})
