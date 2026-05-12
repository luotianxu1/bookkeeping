<script setup lang="ts">
// 记一笔-支出页：还原 Pencil「记一笔-支出」页面结构与交互。
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'

// 记账类型：顶部支出/收入/转账切换。
const entryTypeOptions = ['支出', '收入', '转账']
const entryType = ref(entryTypeOptions[0])
const router = useRouter()

// 分类数据：分类选择区展示项。
const categoryOptions = [
  { icon: '🍽', label: '餐饮' },
  { icon: '🧴', label: '日用' },
  { icon: '🚗', label: '交通' },
  { icon: '🎬', label: '娱乐' },
  { icon: '🛍', label: '购物' },
  { icon: '🧩', label: '其他' },
  { icon: '⚙', label: '管理分类' },
]
const activeCategory = ref(categoryOptions[0].label)

// 表单数据：账户、时间、备注和金额。
const accountName = ref('现金账户')
const accountOptions = ['现金账户', '招商银行卡', '支付宝', '微信']
const transferOutAccount = ref('现金账户')
const transferInAccount = ref('投资账户')
const transferOutOptions = ['现金账户', '招商银行卡', '支付宝', '微信']
const transferInOptions = ['投资账户', '现金账户', '招商银行卡', '支付宝']
const entryTime = ref('2026-03-21T20:30')
const note = ref('今天一起做饭买菜')
const amountInput = ref('0.00')

// 数字键盘：用于金额输入的按钮布局。
const keypadRows = [
  ['1', '2', '3', '删'],
  ['4', '5', '6', '清空'],
  ['7', '8', '9', '再记'],
  ['.', '0', '+', '保存'],
]

const amountDisplay = computed(() => `${amountInput.value}`)

function selectCategory(label: string) {
  if (label === '管理分类') {
    router.push('/finance/categories')
    return
  }

  activeCategory.value = label
}

function onKeypadPress(key: string) {
  if (key === '删') {
    amountInput.value = amountInput.value.length > 1 ? amountInput.value.slice(0, -1) : '0'
    return
  }

  if (key === '清空') {
    amountInput.value = '0.00'
    return
  }

  if (key === '保存' || key === '再记') return

  if (key === '+') return

  if (amountInput.value === '0.00') {
    amountInput.value = key === '.' ? '0.' : key
    return
  }

  if (key === '.' && amountInput.value.includes('.')) return

  amountInput.value += key
}
</script>

<template>
  <section class="expense-entry-page" aria-label="记一笔-支出">
    <PageHeader title="记一笔" back-to="/finance" back-label="返回财务首页" />

    <SegmentedControl v-model="entryType" :options="entryTypeOptions" label="记账类型切换" />

    <section class="expense-amount-card" aria-label="金额">
      <p>{{ amountDisplay }}</p>
    </section>

    <section v-if="entryType !== '转账'" class="expense-detail-card" aria-label="分类与详情">
      <div class="category-grid">
        <button
          v-for="item in categoryOptions"
          :key="item.label"
          type="button"
          :class="['category-item', { active: activeCategory === item.label }]"
          @click="selectCategory(item.label)"
        >
          <span>{{ item.icon }}</span>
          <strong>{{ item.label }}</strong>
        </button>
      </div>

      <div class="expense-info-row">
        <span>账户</span>
        <label class="expense-inline-control">
          <select v-model="accountName" aria-label="选择账户">
            <option v-for="account in accountOptions" :key="account" :value="account">
              {{ account }}
            </option>
          </select>
        </label>
      </div>
      <div class="expense-divider"></div>
      <div class="expense-info-row">
        <span>时间</span>
        <label class="expense-inline-control">
          <input v-model="entryTime" type="datetime-local" aria-label="选择时间" />
        </label>
      </div>
    </section>

    <section v-else class="expense-detail-card transfer-detail-card" aria-label="转账详情">
      <div class="expense-info-row transfer-row">
        <span>转出账户</span>
        <label class="expense-inline-control">
          <select v-model="transferOutAccount" aria-label="选择转出账户">
            <option v-for="account in transferOutOptions" :key="account" :value="account">
              {{ account }}
            </option>
          </select>
        </label>
      </div>
      <div class="expense-divider"></div>
      <div class="expense-info-row transfer-row">
        <span>转入账户</span>
        <label class="expense-inline-control">
          <select v-model="transferInAccount" aria-label="选择转入账户">
            <option v-for="account in transferInOptions" :key="account" :value="account">
              {{ account }}
            </option>
          </select>
        </label>
      </div>
    </section>

    <section class="expense-note-card" aria-label="备注">
      <label class="expense-note-input-wrap">
        <span>备注</span>
        <textarea
          v-model="note"
          class="expense-note-input"
          placeholder="输入备注内容"
          aria-label="输入备注"
        ></textarea>
      </label>
    </section>

    <section class="expense-keypad" aria-label="数字键盘">
      <div v-for="(row, rowIndex) in keypadRows" :key="`row-${rowIndex}`" class="expense-keypad-row">
        <button
          v-for="key in row"
          :key="key"
          type="button"
          :class="[
            'expense-keypad-key',
            { 'key-muted': key === '删' || key === '清空' || key === '再记' },
            { 'key-save': key === '保存' },
          ]"
          @click="onKeypadPress(key)"
        >
          {{ key }}
        </button>
      </div>
    </section>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
