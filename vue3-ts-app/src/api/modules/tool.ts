import { requestDelete, requestGet, requestPost, requestPut, toolRequest } from '@/api/request'

export type ContactStatus = 'active' | 'archived'

export interface Contact {
  id: number
  userId: number
  name: string
  phone?: string | null
  remark?: string | null
  sortOrder: number
  status: ContactStatus | string
  createdAt: string
  updatedAt: string
}

export interface ContactQuery {
  userId?: number
  status?: ContactStatus | 'all'
  keyword?: string
}

export interface SaveContactParams {
  userId: number
  name: string
  phone?: string | null
  remark?: string | null
  sortOrder?: number
  status?: ContactStatus | string
}

export function getContacts(params: ContactQuery) {
  return requestGet<Contact[]>(toolRequest, '/api/tools/contacts', { params })
}

export function getContact(id: number) {
  return requestGet<Contact>(toolRequest, `/api/tools/contacts/${id}`)
}

export function createContact(params: SaveContactParams) {
  return requestPost<Contact, SaveContactParams>(toolRequest, '/api/tools/contacts', params)
}

export function updateContact(id: number, params: SaveContactParams) {
  return requestPut<Contact, SaveContactParams>(toolRequest, `/api/tools/contacts/${id}`, params)
}

export function deleteContact(id: number) {
  return requestDelete<void>(toolRequest, `/api/tools/contacts/${id}`)
}
