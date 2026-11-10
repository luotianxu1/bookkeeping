export function validateNewPassword(password: string, confirmation: string) {
  if (!password.trim() || password.length < 6 || password.length > 64)
    return '密码长度需为 6 到 64 位'
  if (new TextEncoder().encode(password).length > 72)
    return '密码过长，请减少字符数量'
  if (password !== confirmation) return '两次输入的密码不一致'
  return ''
}
