export type ApiService = 'default' | 'auth' | 'finance' | 'admin' | 'tool'

const apiBaseUrls: Record<ApiService, string> = {
  default: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8081',
  auth: import.meta.env.VITE_AUTH_API_BASE_URL ?? import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8081',
  finance: import.meta.env.VITE_FINANCE_API_BASE_URL ?? import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8082',
  admin: import.meta.env.VITE_ADMIN_API_BASE_URL ?? import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8081',
  tool: import.meta.env.VITE_TOOL_API_BASE_URL ?? '/tool-api',
}

export function getApiBaseUrl(service: ApiService = 'default') {
  return apiBaseUrls[service]
}
