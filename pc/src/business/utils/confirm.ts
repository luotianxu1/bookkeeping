import { ElMessageBox } from 'element-plus'
export async function confirmAction(message: string): Promise<boolean> {
  try {
    await ElMessageBox.confirm(message, '确认操作', { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' })
    return true
  } catch { return false }
}
