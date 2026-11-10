<script setup lang="ts">
import PagePanel from '@/business/components/PagePanel.vue'

import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  getSalarySettings,
  saveSalarySettings,
  type SalarySettings,
  type SaveSalarySettingsParams,
} from '@/business/api/modules/finance'

import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'
import CommonInput from '@/business/components/common/CommonInput/index.vue'

import CommonSelect from '@/business/components/common/CommonSelect/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'

const settings = ref<SalarySettings | null>(null)
const isLoading = ref(true)
const isSaving = ref(false)
const pageError = ref('')
const formError = ref('')
const feedbackVisible = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')

const MAX_HOUSING_FUND_RATE = '12'
const MAX_PENSION_PERSONAL_RATE = '8'
const MAX_PENSION_COMPANY_RATE = '16'
const MAX_MEDICAL_PERSONAL_RATE = '2'
const MAX_MEDICAL_COMPANY_RATE = '10'
const MAX_UNEMPLOYMENT_PERSONAL_RATE = '0.5'
const MAX_UNEMPLOYMENT_COMPANY_RATE = '0.5'

const form = reactive<Record<string, string>>({
  monthlyGrossSalary: '',
  annualBonus: '',
  payDay: '15',
  socialSecurityBase: '',
  housingFundBase: '',
  housingFundPersonalRate: '',
  housingFundCompanyRate: '',
  pensionPersonalRate: '',
  pensionCompanyRate: '',
  medicalPersonalRate: '',
  medicalCompanyRate: '',
  medicalFixedAmount: '',
  unemploymentPersonalRate: '',
  unemploymentCompanyRate: '',
  taxFreeThreshold: '',
  taxYear: String(new Date().getFullYear()),
  childEducation: '',
  continuingEducation: '',
  housingLoan: '',
  housingRent: '',
  elderlyCare: '',
  seriousMedical: '',
  otherDeduction: '',
  remark: '',
})

const payDayOptions = computed(() =>
  Array.from({ length: 31 }, (_, index) => ({
    label: `${index + 1} 日`,
    value: String(index + 1),
  })),
)

onMounted(() => {
  void loadPage()
})

watch(
  () => form.monthlyGrossSalary,
  (value, previousValue) => {
    if (
      form.socialSecurityBase === '' ||
      form.socialSecurityBase === previousValue
    ) {
      form.socialSecurityBase = value
    }
    if (form.housingFundBase === '' || form.housingFundBase === previousValue) {
      form.housingFundBase = value
    }
  },
)

async function loadPage() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看工资设置'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    settings.value = await getSalarySettings(currentUser.id)
    fillForm(settings.value)
  } catch (error) {
    pageError.value = getErrorMessage(error, '工资设置加载失败')
    openFeedback(pageError.value, 'error')
  } finally {
    isLoading.value = false
  }
}

function fillForm(data: SalarySettings) {
  const grossSalary = String(data.monthlyGrossSalary)
  form.monthlyGrossSalary = grossSalary
  form.annualBonus = String(data.annualBonus)
  form.payDay = String(data.payDay)
  form.socialSecurityBase = resolveBaseValue(
    data.socialSecurityBase,
    grossSalary,
  )
  form.housingFundBase = resolveBaseValue(data.housingFundBase, grossSalary)
  form.housingFundPersonalRate = resolveRateValue(
    data.housingFundPersonalRate,
    MAX_HOUSING_FUND_RATE,
  )
  form.housingFundCompanyRate = resolveRateValue(
    data.housingFundCompanyRate,
    MAX_HOUSING_FUND_RATE,
  )
  form.pensionPersonalRate = resolveRateValue(
    data.pensionPersonalRate,
    MAX_PENSION_PERSONAL_RATE,
  )
  form.pensionCompanyRate = resolveRateValue(
    data.pensionCompanyRate,
    MAX_PENSION_COMPANY_RATE,
  )
  form.medicalPersonalRate = resolveRateValue(
    data.medicalPersonalRate,
    MAX_MEDICAL_PERSONAL_RATE,
  )
  form.medicalCompanyRate = resolveRateValue(
    data.medicalCompanyRate,
    MAX_MEDICAL_COMPANY_RATE,
  )
  form.medicalFixedAmount = String(data.medicalFixedAmount)
  form.unemploymentPersonalRate = resolveRateValue(
    data.unemploymentPersonalRate,
    MAX_UNEMPLOYMENT_PERSONAL_RATE,
  )
  form.unemploymentCompanyRate = resolveRateValue(
    data.unemploymentCompanyRate,
    MAX_UNEMPLOYMENT_COMPANY_RATE,
  )
  form.taxFreeThreshold = String(data.taxFreeThreshold)
  form.taxYear = String(data.taxYear)
  form.childEducation = String(data.childEducation)
  form.continuingEducation = String(data.continuingEducation)
  form.housingLoan = String(data.housingLoan)
  form.housingRent = String(data.housingRent)
  form.elderlyCare = String(data.elderlyCare)
  form.seriousMedical = String(data.seriousMedical)
  form.otherDeduction = String(data.otherDeduction)
  form.remark = data.remark ?? ''
}

function resolveBaseValue(value: number, grossSalary: string) {
  return value > 0 ? String(value) : grossSalary
}

function resolveRateValue(value: number, fallback: string) {
  return value > 0 ? String(value) : fallback
}

async function submitQuickAdjust() {
  if (isSaving.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    formError.value = '请先登录后保存工资设置'
    return
  }

  const payload: SaveSalarySettingsParams = {
    userId: currentUser.id,
    monthlyGrossSalary: toNumber(form.monthlyGrossSalary),
    transportSubsidy: 0,
    mealSubsidy: 0,
    annualBonus: toNumber(form.annualBonus),
    payDay: toInteger(form.payDay),
    socialSecurityBase: toNumber(form.socialSecurityBase),
    housingFundBase: toNumber(form.housingFundBase),
    housingFundPersonalRate: toNumber(form.housingFundPersonalRate),
    housingFundCompanyRate: toNumber(form.housingFundCompanyRate),
    pensionPersonalRate: toNumber(form.pensionPersonalRate),
    pensionCompanyRate: toNumber(form.pensionCompanyRate),
    medicalPersonalRate: toNumber(form.medicalPersonalRate),
    medicalCompanyRate: toNumber(form.medicalCompanyRate),
    medicalFixedAmount: toNumber(form.medicalFixedAmount),
    unemploymentPersonalRate: toNumber(form.unemploymentPersonalRate),
    unemploymentCompanyRate: toNumber(form.unemploymentCompanyRate),
    taxFreeThreshold: toNumber(form.taxFreeThreshold),
    taxYear: toInteger(form.taxYear),
    childEducation: toNumber(form.childEducation),
    continuingEducation: toNumber(form.continuingEducation),
    housingLoan: toNumber(form.housingLoan),
    housingRent: toNumber(form.housingRent),
    elderlyCare: toNumber(form.elderlyCare),
    seriousMedical: toNumber(form.seriousMedical),
    otherDeduction: toNumber(form.otherDeduction),
    remark: form.remark.trim(),
  }

  if (!isValidPayload(payload)) {
    return
  }

  isSaving.value = true
  formError.value = ''

  try {
    settings.value = await saveSalarySettings(payload)
    fillForm(settings.value)
    openFeedback('工资设置已保存', 'success')
  } catch (error) {
    formError.value = getErrorMessage(error, '工资设置保存失败')
  } finally {
    isSaving.value = false
  }
}

function isValidPayload(payload: SaveSalarySettingsParams) {
  if (payload.monthlyGrossSalary < 0) {
    formError.value = '税前工资不能为负数'
    return false
  }
  if (payload.socialSecurityBase < 0 || payload.housingFundBase < 0) {
    formError.value = '基数不能为负数'
    return false
  }
  if (payload.taxYear < 2000) {
    formError.value = '纳税年度不正确'
    return false
  }
  return true
}

function toNumber(value: string) {
  const normalized = Number(value || 0)
  return Number.isFinite(normalized) ? normalized : 0
}

function toInteger(value: string) {
  const normalized = Number.parseInt(value || '0', 10)
  return Number.isFinite(normalized) ? normalized : 0
}

function openFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  feedbackVisible.value = true
}
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader title="工资设置">
      <template #right>
        <el-button
          type="primary"
          :loading="isSaving"
          :disabled="!settings"
          @click="submitQuickAdjust"
        >
          保存设置
        </el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <el-alert
      v-if="formError"
      :title="formError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <template v-if="settings">
      <div class="desktop-columns">
        <PagePanel title="收入与发薪">
          <div class="desktop-form-grid">
            <CommonInput
              v-model="form.monthlyGrossSalary"
              label="税前工资"
              input-type="number"
              input-mode="decimal"
            />
            <CommonSelect
              v-model="form.payDay"
              label="发薪日"
              :options="payDayOptions"
            />
            <CommonInput
              v-model="form.annualBonus"
              label="年内累计奖金"
              input-type="number"
              input-mode="decimal"
            />
          </div>
        </PagePanel>
        <PagePanel title="缴费基数">
          <div class="desktop-form-grid">
            <CommonInput
              v-model="form.socialSecurityBase"
              label="社保基数"
              input-type="number"
              input-mode="decimal"
            />
            <CommonInput
              v-model="form.housingFundBase"
              label="公积金基数"
              input-type="number"
              input-mode="decimal"
            />
          </div>
        </PagePanel>
      </div>
      <div class="desktop-columns">
        <PagePanel title="公积金">
          <div class="desktop-form-grid">
            <CommonInput
              percentage
              v-model="form.housingFundPersonalRate"
              label="个人比例 (%)"
              input-type="number"
              input-mode="decimal"
            />
            <CommonInput
              percentage
              v-model="form.housingFundCompanyRate"
              label="单位比例 (%)"
              input-type="number"
              input-mode="decimal"
            />
          </div>
        </PagePanel>
        <PagePanel title="养老保险">
          <div class="desktop-form-grid">
            <CommonInput
              percentage
              v-model="form.pensionPersonalRate"
              label="个人比例 (%)"
              input-type="number"
              input-mode="decimal"
            />
            <CommonInput
              percentage
              v-model="form.pensionCompanyRate"
              label="单位比例 (%)"
              input-type="number"
              input-mode="decimal"
            />
          </div>
        </PagePanel>
        <PagePanel title="医疗保险">
          <div class="desktop-form-grid">
            <CommonInput
              percentage
              v-model="form.medicalPersonalRate"
              label="个人比例 (%)"
              input-type="number"
              input-mode="decimal"
            />
            <CommonInput
              percentage
              v-model="form.medicalCompanyRate"
              label="单位比例 (%)"
              input-type="number"
              input-mode="decimal"
            />
          </div>
        </PagePanel>
        <PagePanel title="失业保险">
          <div class="desktop-form-grid">
            <CommonInput
              percentage
              v-model="form.unemploymentPersonalRate"
              label="个人比例 (%)"
              input-type="number"
              input-mode="decimal"
            />
            <CommonInput
              percentage
              v-model="form.unemploymentCompanyRate"
              label="单位比例 (%)"
              input-type="number"
              input-mode="decimal"
            />
            <CommonInput
              v-model="form.medicalFixedAmount"
              label="大额医疗 (元/月)"
              input-type="number"
              input-mode="decimal"
            />
          </div>
        </PagePanel>
      </div>
      <PagePanel title="个税与专项扣除">
        <div class="desktop-form-grid">
          <CommonInput
            v-model="form.taxFreeThreshold"
            label="个税起征点"
            input-type="number"
            input-mode="decimal"
          />
          <CommonInput
            v-model="form.taxYear"
            label="纳税年度"
            input-type="number"
            input-mode="numeric"
          />
          <CommonInput
            v-model="form.childEducation"
            label="子女教育"
            input-type="number"
            input-mode="decimal"
          />
          <CommonInput
            v-model="form.continuingEducation"
            label="继续教育"
            input-type="number"
            input-mode="decimal"
          />
          <CommonInput
            v-model="form.housingLoan"
            label="住房贷款"
            input-type="number"
            input-mode="decimal"
          />
          <CommonInput
            v-model="form.housingRent"
            label="住房租金"
            input-type="number"
            input-mode="decimal"
          />
          <CommonInput
            v-model="form.elderlyCare"
            label="赡养老人"
            input-type="number"
            input-mode="decimal"
          />
          <CommonInput
            v-model="form.seriousMedical"
            label="大病医疗"
            input-type="number"
            input-mode="decimal"
          />
          <CommonInput
            v-model="form.otherDeduction"
            label="其他扣除"
            input-type="number"
            input-mode="decimal"
          />
        </div>
      </PagePanel>
      <PagePanel title="备注">
        <CommonInput v-model="form.remark" label="备注" />
      </PagePanel>
    </template>
    <CommonFeedback
      v-model="feedbackVisible"
      :message="feedbackMessage"
      :type="feedbackType"
    />
  </section>
</template>
