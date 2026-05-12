// 应用导航类型：描述 Pencil 文件中底部 Tab 对应的业务分区。
export type AppSection = 'finance' | 'food' | 'tools' | 'profile'

export type NavItem = {
  /** 导航图标文本，后续可替换为图标组件。 */
  icon: string
  /** 导航显示名称。 */
  label: string
  /** 导航对应的业务分区标识。 */
  section: AppSection
  /** 导航跳转路由路径。 */
  path: string
}
