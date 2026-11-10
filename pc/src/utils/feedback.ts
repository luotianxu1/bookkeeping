import { ElMessage } from 'element-plus'
export const showError = (error: unknown) =>
  ElMessage.error(error instanceof Error ? error.message : '操作失败，请重试')
export const formatDate = (value: string | null) =>
  value ? value.replace('T', ' ').slice(0, 16) : '从未登录'
export const statusLabel = (value: string) =>
  ({ active: '正常', disabled: '已停用', locked: '已锁定' })[value] || value
