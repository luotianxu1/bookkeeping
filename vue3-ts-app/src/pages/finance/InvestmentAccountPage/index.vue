<script setup lang="ts">
// 投资账户页：还原 Pencil「投资账户」页面中的总览、筛选和持仓列表。
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonSelect from '@/components/common/CommonSelect/index.vue'
import CommonSwitch from '@/components/common/CommonSwitch/index.vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import {
  investmentHoldings,
  investmentMetrics,
  investmentOverview,
  investmentTabs,
} from '@/data/account'

const activeTab = ref(investmentTabs[0])
const showAddModal = ref(false)
const router = useRouter()

// 新增投资资产弹窗表单：对应设计稿中的资产名称、资产分类、账户、买入金额、自动同步配置。
const addAssetName = ref('')
const addAssetCategoryOptions = ['基金', 'A股', '其他']
const addAssetCategory = ref(addAssetCategoryOptions[0])
const addAssetAccount = ref('基金账户')
const addAssetAccountOptions = ['基金账户', '股票账户']
const addAssetCost = ref('')
const addAssetAutoSync = ref(true)

const holdings = computed(() => {
  if (activeTab.value === '全部' || activeTab.value === '基金') return investmentHoldings
  return []
})

function openAddModal() {
  showAddModal.value = true
}

function closeAddModal() {
  showAddModal.value = false
}

function saveAsset() {
  closeAddModal()
}

function openInvestmentDetail() {
  router.push('/finance/accounts/investment/detail')
}
</script>

<template>
  <section class="investment-account-page" aria-label="投资账户">
    <PageHeader title="投资账户" back-to="/finance/accounts" back-label="返回账户管理" />

    <section class="investment-summary-card" aria-label="投资总览">
      <div class="investment-summary-top">
        <div class="investment-summary-main">
          <p>{{ investmentOverview.label }}</p>
          <AmountText tag="strong" :value="investmentOverview.amount" />
          <span>{{ investmentOverview.syncText }}</span>
        </div>
        <div class="investment-summary-side">
          <span>{{ investmentOverview.todayLabel }}</span>
          <AmountText tag="strong" :value="investmentOverview.todayValue" />
          <AmountText tag="span" :value="investmentOverview.todayRate" />
        </div>
      </div>

      <div class="investment-metrics">
        <template v-for="(metric, index) in investmentMetrics" :key="metric.label">
          <div class="investment-metric-item">
            <span>{{ metric.label }}</span>
            <AmountText tag="strong" :value="metric.value" />
          </div>
          <div v-if="index !== investmentMetrics.length - 1" class="investment-metric-divider"></div>
        </template>
      </div>
    </section>

    <SegmentedControl v-model="activeTab" :options="investmentTabs" label="投资分类筛选" />

    <section class="investment-holdings" aria-label="持仓列表">
      <article
        v-for="holding in holdings"
        :key="holding.name"
        class="investment-holding-card"
        @click="openInvestmentDetail"
      >
        <div class="holding-row top">
          <div class="holding-left">
            <div class="holding-title">
              <strong>{{ holding.name }}</strong>
              <span>{{ holding.units }}</span>
            </div>
            <div class="holding-tags">
              <span class="holding-tag">{{ holding.tag }}</span>
              <AmountText tag="span" class="holding-market-value" :value="holding.marketValue" />
            </div>
          </div>
          <div class="holding-right">
            <span>{{ holding.dayLabel }}</span>
            <AmountText tag="strong" :value="holding.dayValue" />
          </div>
        </div>

        <div class="holding-row middle">
          <div class="holding-left compact">
            <AmountText tag="strong" :value="holding.netValue" />
            <span>{{ holding.netValueDate }}</span>
          </div>
          <div class="holding-right compact">
            <span>{{ holding.costLabel }}</span>
            <AmountText tag="strong" :value="holding.costValue" />
          </div>
        </div>

        <div class="holding-divider"></div>

        <div class="holding-row bottom">
          <div class="holding-left compact">
            <span>{{ holding.pnlLabel }}</span>
            <div class="holding-pnl-line">
              <AmountText tag="strong" :value="holding.pnlValue" />
              <AmountText tag="span" class="holding-pnl-rate" :value="holding.pnlRate" />
            </div>
          </div>
          <div class="holding-right compact">
            <span>{{ holding.allocationLabel }}</span>
            <div class="holding-allocation">
              <div class="holding-allocation-track">
                <span :style="{ width: `${holding.progress}%` }"></span>
              </div>
              <AmountText tag="strong" :value="holding.allocationValue" />
            </div>
          </div>
        </div>
      </article>
    </section>

    <button class="investment-fab" type="button" aria-label="新增投资资产" @click="openAddModal">
      +
    </button>

    <CommonModal v-model="showAddModal" title="添加资产">
      <div class="investment-add-modal-form">
        <CommonInput
          v-model="addAssetName"
          label="资产名称"
          placeholder="请输入资产名称（如：中证500指数）"
        />
        <div class="investment-add-modal-field">
          <span>资产分类</span>
          <SegmentedControl
            v-model="addAssetCategory"
            :options="addAssetCategoryOptions"
            label="资产分类"
          />
        </div>
        <CommonSelect v-model="addAssetAccount" label="所属账户" :options="addAssetAccountOptions" />
        <CommonInput v-model="addAssetCost" label="买入金额" placeholder="请输入金额" input-mode="decimal" />
        <CommonSwitch v-model="addAssetAutoSync" label="自动同步净值" />
      </div>

      <template #footer>
        <div class="investment-add-modal-actions">
          <CommonButton variant="secondary" @click="closeAddModal">取消</CommonButton>
          <CommonButton @click="saveAsset">保存</CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
