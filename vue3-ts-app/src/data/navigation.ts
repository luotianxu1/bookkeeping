// 底部导航静态数据：对应 Pencil 设计稿里的四个主入口。
import type { NavItem } from '@/types/navigation'

export const mainNavItems: NavItem[] = [
  { label: '财务', section: 'finance', path: '/finance' },
  { label: '餐饮', section: 'food', path: '/food' },
  { label: '工具', section: 'tools', path: '/tools' },
  { label: '我的', section: 'profile', path: '/profile' },
]
