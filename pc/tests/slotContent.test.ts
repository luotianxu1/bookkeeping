import { describe, expect, it } from 'vitest'
import { createCommentVNode, createTextVNode, h } from 'vue'
import { hasRenderableSlotContent } from '../src/business/utils/slotContent'

describe('hasRenderableSlotContent', () => {
  it('treats v-if=false comment node as empty', () => {
    // Vue 对 v-if=false 生成注释节点，children 为字符串 "v-if"。
    // 这类节点不能算有效内容，否则空 header 会残留一行造成顶部留白。
    expect(hasRenderableSlotContent([createCommentVNode('v-if', true)])).toBe(
      false,
    )
  })

  it('treats empty / whitespace-only slots as empty', () => {
    expect(hasRenderableSlotContent([])).toBe(false)
    expect(hasRenderableSlotContent([createTextVNode('   ')])).toBe(false)
    expect(
      hasRenderableSlotContent([
        createCommentVNode('v-if', true),
        createTextVNode('\n  '),
      ]),
    ).toBe(false)
  })

  it('treats element, component and non-empty text nodes as content', () => {
    expect(hasRenderableSlotContent([h('button', '保存')])).toBe(true)
    expect(hasRenderableSlotContent([createTextVNode('保存')])).toBe(true)
  })

  it('ignores comments even when mixed with a real node', () => {
    expect(
      hasRenderableSlotContent([
        createCommentVNode('v-if', true),
        h('button', '保存'),
      ]),
    ).toBe(true)
    expect(
      hasRenderableSlotContent([createCommentVNode('v-if', true)]),
    ).toBe(false)
  })
})
