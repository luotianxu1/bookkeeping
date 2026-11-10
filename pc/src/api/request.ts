export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}
const base = (import.meta.env.VITE_AUTH_API_BASE_URL || '/auth-api').replace(
  /\/$/,
  '',
)

export async function request<T>(
  path: string,
  options: { method?: string; body?: unknown } = {},
): Promise<T> {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 20_000)
  try {
    const response = await fetch(base + path, {
      method: options.method || 'GET',
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        ...(options.body !== undefined
          ? { 'Content-Type': 'application/json' }
          : {}),
      },
      body:
        options.body !== undefined ? JSON.stringify(options.body) : undefined,
      signal: controller.signal,
    })
    const result = (await response.json().catch(() => null)) as {
      code: number
      message: string
      data: T
    } | null
    if (!response.ok || !result || result.code !== 0) {
      const status = response.ok ? result?.code || 502 : response.status
      if (status === 401 && path !== '/api/auth/login')
        window.dispatchEvent(new Event('session-expired'))
      if (status === 403) window.dispatchEvent(new Event('permissions-changed'))
      throw new ApiError(
        result?.message ||
          (status >= 500 ? '服务暂时不可用，请稍后重试' : '请求失败，请重试'),
        status,
      )
    }
    return result.data
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError(
      controller.signal.aborted
        ? '请求超时，请重试'
        : '连接失败，请检查网络或服务是否启动',
      0,
    )
  } finally {
    clearTimeout(timeout)
  }
}
