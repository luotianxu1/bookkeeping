/** 表单未变时，超时/断网重试复用 ID；成功后清除，下一笔即使内容相同也用新 ID。 */
export function useIdempotentSubmit() {
  let fingerprint = ''
  let requestId = ''
  let stableFields: object = {}
  return {
    // 自动交易时间等字段仅在新请求时生成，不能让重试时间改变请求摘要。
    withRequestId<T extends object, S extends object = Record<never, never>>(
      payload: T, createStableFields?: () => S,
    ): T & S & { requestId: string } {
      const next = JSON.stringify(payload)
      if (!requestId || fingerprint !== next) {
        stableFields = createStableFields?.() ?? {}
        fingerprint = next
        requestId = crypto.randomUUID()
      }
      return { ...payload, ...stableFields, requestId } as T & S & { requestId: string }
    },
    reset() { fingerprint = ''; requestId = ''; stableFields = {} },
  }
}
