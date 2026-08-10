export interface RequestGeneration {
  generation: number
  isLatest: () => boolean
}

/**
 * 为一个页面状态域分配递增请求代次。
 * 每个页面/状态域单独创建实例，不会把互不相关的页面请求串行化。
 */
export function createRequestGeneration() {
  let currentGeneration = 0

  return {
    begin(): RequestGeneration {
      const generation = ++currentGeneration
      return {
        generation,
        isLatest: () => generation === currentGeneration,
      }
    },
    cancel() {
      currentGeneration++
    },
  }
}
