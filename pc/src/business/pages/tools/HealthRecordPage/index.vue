<script setup lang="ts">
import { formatPercentage } from '@/business/utils/percentage'

import PagePanel from '@/business/components/PagePanel.vue'

// 体重记录页：每日体重打卡（同日覆盖，默认填入上次体重）、echarts 趋势与历史列表（打卡时自动计算体脂）。
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import type { ECharts, EChartsCoreOption } from 'echarts'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'
import CommonInput from '@/business/components/common/CommonInput/index.vue'

import PageHeader from '@/business/components/common/PageHeader/index.vue'
import {
  getWeightRecords,
  saveWeightRecord,
  type WeightRecord,
} from '@/business/api/modules/tool'
import { getStoredCurrentUser } from '@/business/utils/current-user'

const dateInput = ref(todayString())
const weightInput = ref('')
const remarkInput = ref('')

const records = ref<WeightRecord[]>([])
const saving = ref(false)
const errorMessage = ref('')
// 首屏记录加载中，完成后显示趋势图与历史列表
const isLoading = ref(true)

let echartsLib: typeof import('@/business/utils/echarts') | null = null
let chart: ECharts | null = null
const chartRef = ref<HTMLDivElement | null>(null)

// 图表按日期升序绘制，列表保持接口返回的倒序
const timeline = computed(() =>
  [...records.value].sort((a, b) => a.recordDate.localeCompare(b.recordDate)),
)

// 最新一次打卡的体脂率（未设身体数据时为空）

function todayString() {
  const now = new Date()
  return new Date(now.getTime() - now.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 10)
}

onMounted(async () => {
  const user = getStoredCurrentUser()
  if (!user) {
    errorMessage.value = '请先登录后再打卡'
    isLoading.value = false
    return
  }

  try {
    records.value = await getWeightRecords(user.id, 30)
    if (records.value.length > 0 && !weightInput.value) {
      weightInput.value = String(records.value[0].weight)
    }
    isLoading.value = false
    await nextTick()
    await renderChart()
  } catch (error) {
    errorMessage.value = (error as Error).message || '加载记录失败，请稍后再试'
  } finally {
    isLoading.value = false
  }

  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})

function handleResize() {
  chart?.resize()
}

async function renderChart() {
  if (!chartRef.value || timeline.value.length === 0) {
    return
  }
  if (!echartsLib) {
    echartsLib = await import('@/business/utils/echarts')
  }
  if (!chart) {
    chart = echartsLib.init(chartRef.value)
  }

  const option: EChartsCoreOption = {
    grid: { left: 44, right: 16, top: 24, bottom: 28 },
    tooltip: {
      trigger: 'axis',
      formatter: (params: unknown) => {
        const list = params as { name: string; value: number }[]
        const point = timeline.value.find(
          (item) => item.recordDate.slice(5) === list[0]?.name,
        )
        const bodyFat =
          point?.bodyFatRate != null
            ? `<br/>体脂：${formatPercentage(point.bodyFatRate)}`
            : ''
        return `${list[0]?.name}<br/>体重：${list[0]?.value}kg${bodyFat}`
      },
    },
    xAxis: {
      type: 'category',
      data: timeline.value.map((item) => item.recordDate.slice(5)),
      boundaryGap: false,
    },
    yAxis: {
      type: 'value',
      scale: true,
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: timeline.value.map((item) => item.weight),
      },
    ],
  }
  chart.setOption(option, true)
}

async function onSubmit() {
  if (saving.value || isLoading.value) return
  const user = getStoredCurrentUser()
  if (!user) {
    errorMessage.value = '请先登录后再打卡'
    return
  }

  const weight = Number(weightInput.value)
  if (!Number.isFinite(weight) || weight < 20 || weight > 300) {
    errorMessage.value = '体重需在 20–300kg 之间'
    return
  }
  if (!dateInput.value) {
    errorMessage.value = '请选择打卡日期'
    return
  }

  errorMessage.value = ''
  saving.value = true
  try {
    const saved = await saveWeightRecord({
      userId: user.id,
      recordDate: dateInput.value,
      weight,
      remark: remarkInput.value.trim() || null,
    })

    // 同日打卡覆盖：替换本地列表中的同日记录
    const index = records.value.findIndex(
      (item) => item.recordDate === saved.recordDate,
    )
    if (index >= 0) {
      records.value.splice(index, 1, saved)
    } else {
      records.value = [saved, ...records.value]
    }

    await nextTick()
    await renderChart()
  } catch (error) {
    errorMessage.value = (error as Error).message || '保存失败，请稍后再试'
  } finally {
    saving.value = false
  }
}
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader title="体重记录"><template #right></template></PageHeader>
    <div class="desktop-calculator">
      <PagePanel title="添加记录">
        <el-form
          class="record-form-card"
          @submit.prevent="onSubmit"
          label-position="top"
        >
          <h2 class="record-form-title">今日打卡</h2>
          <div class="record-form-row">
            <CommonInput
              v-model="dateInput"
              label="打卡日期"
              input-type="date"
            />
            <CommonInput
              v-model="weightInput"
              label="体重（kg）"
              placeholder="例如：78.9"
              input-type="number"
              input-mode="decimal"
            />
          </div>
          <CommonInput
            v-model="remarkInput"
            label="备注（选填）"
            placeholder="例如：晨起空腹"
          />
          <p v-if="errorMessage" class="record-error">{{ errorMessage }}</p>
          <CommonButton
            class="record-submit"
            type="submit"
            :disabled="saving || isLoading"
          >
            {{ saving ? '保存中…' : '保存打卡' }}
          </CommonButton>
          <p class="record-hint">同一天重复打卡会覆盖当天的记录</p>
        </el-form>
      </PagePanel>
      <PagePanel title="体重趋势">
        <div
          ref="chartRef"
          class="desktop-chart record-chart"
          style="height: 360px"
        />
        <el-empty
          v-if="!timeline.length && !isLoading"
          description="暂无记录"
        />
      </PagePanel>
    </div>
    <DesktopTable
      title="历史体重"
      searchable
      :rows="records"
      :loading="isLoading"
      aria-label="历史记录"
    >
      <template #toolbar><h2>最近记录</h2></template>
      <el-table-column prop="recordDate" label="日期" min-width="140" />
      <el-table-column prop="weight" label="体重（kg）" min-width="140" />
      <!-- @vue-generic {WeightRecord} -->
      <el-table-column label="体脂率" min-width="120">
        <template #default="{ row }: { row: WeightRecord }">
          {{
            row.bodyFatRate == null ? '—' : formatPercentage(row.bodyFatRate)
          }}
        </template>
      </el-table-column>
      <el-table-column
        prop="remark"
        label="备注"
        min-width="220"
        show-overflow-tooltip
      />
    </DesktopTable>
  </section>
</template>
