import { Comment, Text, type VNode } from 'vue'

/**
 * 判断插槽渲染结果里是否存在真正会显示的节点，供空 header 隐藏逻辑使用。
 *
 * - 元素 / 组件节点与非空文本节点视为有效内容；
 * - 注释节点（如 v-if=false 生成的 children 为 "v-if" 的 comment）不算有效内容。
 *
 * 关键：不能只看 children 是否非空——v-if=false 的注释节点 children 是字符串
 * "v-if"，会被误判成文本，导致空 header 残留一行、页面顶部留白。这里用 Vue
 * 导出的 Comment/Text 符号常量精确区分（模块级引用，生产构建下依然可靠，
 * 不同于字符串 Symbol.for('v-cmt') / Symbol.for('v-txt')）。
 */
export function hasRenderableSlotContent(nodes: VNode[]): boolean {
  return nodes.some((node) => {
    if (node.type === Comment) return false
    if (node.type === Text) {
      return typeof node.children === 'string' && node.children.trim().length > 0
    }
    return true
  })
}
