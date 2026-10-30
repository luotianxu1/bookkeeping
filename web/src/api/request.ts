import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { clearStoredCurrentUser } from '@/utils/current-user'
import { getApiBaseUrl, type ApiService } from './api-base-url'

export interface ApiErrorPayload {
    message: string
    code?: number
    status?: number
    data?: unknown
    timestamp?: number
}

export class ApiError extends Error {
    code?: number
    status?: number
    data?: unknown
    timestamp?: number

    constructor(payload: ApiErrorPayload) {
        super(payload.message)
        this.name = 'ApiError'
        this.code = payload.code
        this.status = payload.status
        this.data = payload.data
        this.timestamp = payload.timestamp
    }
}

export interface ApiResponse<T> {
    code: number
    data: T
    message: string
    timestamp: number
}

export interface RequestOptions {
    service?: ApiService
    baseURL?: string
    timeout?: number
}

export function createRequest(options: RequestOptions = {}) {
    const instance = axios.create({
        baseURL: options.baseURL ?? getApiBaseUrl(options.service),
        timeout: options.timeout ?? 10000,
        // JWT 由 httpOnly Cookie 携带，同源代理下浏览器自动附带；显式开启以兼容绝对地址配置
        withCredentials: true,
    })

    instance.interceptors.response.use(
        (response) => response,
        (error: AxiosError) => {
            // 主动取消的请求（AbortController）原样抛出，页面用 isCancelRequest 静默忽略
            if (axios.isCancel(error)) {
                return Promise.reject(error)
            }

            const status = error.response?.status
            if (status === 401) {
                clearStoredCurrentUser()
                if (window.location.pathname !== '/login') {
                    // 凭证失效：清空后跳登录页并携带回跳地址（动态引入避免循环依赖）
                    const redirect = window.location.pathname + window.location.search
                    void import('@/router').then(({ router }) => {
                        void router.push({ path: '/login', query: { redirect } })
                    })
                }
            }

            return Promise.reject(
                new ApiError({
                    message: getErrorMessage(error),
                    status,
                    data: error.response?.data,
                }),
            )
        },
    )

    return instance
}

/**
 * 错误消息归一化：页面 catch 里统一用它替代手写的
 * `error instanceof Error ? error.message : 'xxx'`。
 */
export function getErrorMessage(error: unknown, fallback = '网络异常，请稍后再试'): string {
    if (error instanceof ApiError) {
        return error.message
    }

    if (error instanceof AxiosError) {
        const data = error.response?.data
        if (isRecord(data) && typeof data.message === 'string') {
            return data.message
        }

        if (error.response?.status) {
            return `请求失败：${error.response.status}`
        }

        return error.message || fallback
    }

    if (error instanceof Error && error.message) {
        return error.message
    }

    return fallback
}

/** 请求是否为主动取消（AbortController 中止），页面 catch 中应静默忽略 */
export function isCancelRequest(error: unknown): boolean {
    return axios.isCancel(error)
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null
}

function isApiResponse<T>(value: unknown): value is ApiResponse<T> {
    return (
        isRecord(value) &&
        typeof value.code === 'number' &&
        'data' in value &&
        typeof value.message === 'string' &&
        typeof value.timestamp === 'number'
    )
}

function unwrapApiResponse<T>(payload: unknown, status?: number): T {
    if (!isApiResponse<T>(payload)) {
        return payload as T
    }

    if (payload.code !== 0) {
        throw new ApiError({
            code: payload.code,
            message: payload.message || '请求失败',
            status,
            data: payload.data,
            timestamp: payload.timestamp,
        })
    }

    return payload.data
}

export const authRequest = createRequest({ service: 'auth' })
export const financeRequest = createRequest({ service: 'finance' })
export const toolRequest = createRequest({ service: 'tool' })
export const foodRequest = createRequest({ service: 'food' })
export const marketRequest = createRequest({ service: 'market' })

// requestXxx 的 config 透传 AxiosRequestConfig，取消请求传 { signal } 即可：
//   const controller = new AbortController()
//   requestGet(client, url, { signal: controller.signal })
//   controller.abort()  // 参数变更或页面卸载时中止旧请求

export async function requestGet<T>(
    client: ReturnType<typeof createRequest>,
    url: string,
    config?: AxiosRequestConfig,
) {
    const response = await client.get<ApiResponse<T> | T>(url, config)
    return unwrapApiResponse<T>(response.data, response.status)
}

export async function requestPost<T, D = unknown>(
    client: ReturnType<typeof createRequest>,
    url: string,
    data?: D,
    config?: AxiosRequestConfig,
) {
    const response = await client.post<ApiResponse<T> | T>(url, data, config)
    return unwrapApiResponse<T>(response.data, response.status)
}

export async function requestPut<T, D = unknown>(
    client: ReturnType<typeof createRequest>,
    url: string,
    data?: D,
    config?: AxiosRequestConfig,
) {
    const response = await client.put<ApiResponse<T> | T>(url, data, config)
    return unwrapApiResponse<T>(response.data, response.status)
}

export async function requestDelete<T>(
    client: ReturnType<typeof createRequest>,
    url: string,
    config?: AxiosRequestConfig,
) {
    const response = await client.delete<ApiResponse<T> | T>(url, config)
    return unwrapApiResponse<T>(response.data, response.status)
}
