import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { clearStoredToken, getStoredToken } from '@/utils/auth-token'
import { showAuthPrompt } from '@/utils/auth-prompt'
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
    })

    instance.interceptors.request.use((config) => {
        const token = getStoredToken()
        if (token) {
            config.headers.Authorization = `${token.tokenType} ${token.accessToken}`
        }
        return config
    })

    instance.interceptors.response.use(
        (response) => response,
        (error: AxiosError) => {
            const status = error.response?.status
            if (status === 401) {
                clearStoredToken()
                clearStoredCurrentUser()
                if (window.location.pathname !== '/login') {
                    showAuthPrompt(window.location.pathname + window.location.search)
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

function getErrorMessage(error: AxiosError) {
    const data = error.response?.data
    if (isRecord(data) && typeof data.message === 'string') {
        return data.message
    }

    if (error.response?.status) {
        return `请求失败：${error.response.status}`
    }

    return error.message || '网络异常，请稍后再试'
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

const request = createRequest()

export async function get<T>(url: string, config?: AxiosRequestConfig) {
    const response = await request.get<ApiResponse<T> | T>(url, config)
    return unwrapApiResponse<T>(response.data, response.status)
}

export async function post<T, D = unknown>(
    url: string,
    data?: D,
    config?: AxiosRequestConfig,
) {
    const response = await request.post<ApiResponse<T> | T>(url, data, config)
    return unwrapApiResponse<T>(response.data, response.status)
}

export async function put<T, D = unknown>(
    url: string,
    data?: D,
    config?: AxiosRequestConfig,
) {
    const response = await request.put<ApiResponse<T> | T>(url, data, config)
    return unwrapApiResponse<T>(response.data, response.status)
}

export async function del<T>(url: string, config?: AxiosRequestConfig) {
    const response = await request.delete<ApiResponse<T> | T>(url, config)
    return unwrapApiResponse<T>(response.data, response.status)
}

export default request

export const authRequest = createRequest({ service: 'auth' })
export const financeRequest = createRequest({ service: 'finance' })
export const adminRequest = createRequest({ service: 'admin' })
export const toolRequest = createRequest({ service: 'tool' })
export const foodRequest = createRequest({ service: 'food' })

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
