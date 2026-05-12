// 底部导航静态数据：对应 Pencil 设计稿里的四个主入口。
import type { NavItem } from '@/types/navigation'

export const mainNavItems: NavItem[] = [
  { icon: '财', label: '财务', section: 'finance', path: '/finance' },
  { icon: '🍽', label: '餐饮', section: 'food', path: '/food' },
  { icon: '◇', label: '工具', section: 'tools', path: '/tools' },
  { icon: '♙', label: '我的', section: 'profile', path: '/profile' },
]
