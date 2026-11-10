<script setup lang="ts">
import MetricStrip from '@/business/components/MetricStrip.vue'
import PagePanel from '@/business/components/PagePanel.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'

import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import CommonLoading from '@/business/components/common/CommonLoading/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import { getFoodDish, type FoodDish } from '@/business/api/modules/food'

import { getErrorMessage } from '@/business/api/request'

const route = useRoute()

const dish = ref<FoodDish | null>(null)
const isLoading = ref(true)
const pageError = ref('')

onMounted(() => {
  void loadDetail()
})

async function loadDetail() {
  const dishId = Number(route.params.dishId)
  if (!Number.isFinite(dishId)) {
    pageError.value = '菜品参数无效'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    dish.value = await getFoodDish(dishId)
  } catch (error) {
    pageError.value = getErrorMessage(error, '菜品详情加载失败')
  } finally {
    isLoading.value = false
  }
}
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader :title="dish?.name || '菜品详情'">
      <template #right></template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <CommonLoading v-if="isLoading" />
    <template v-if="dish">
      <MetricStrip
        :items="[
          { label: '烹饪时间（分钟）', value: dish.cookMinutes },
          { label: '食材数量', value: dish.ingredients.length },
          { label: '步骤数', value: dish.steps.length },
        ]"
      />
      <div class="desktop-main-aside">
        <PagePanel title="做法步骤">
          <p class="desktop-note">{{ dish.description }}</p>
          <el-timeline>
            <el-timeline-item
              v-for="step in dish.steps"
              :key="step.stepNo"
              :timestamp="'步骤 ' + step.stepNo"
              placement="top"
            >
              <p>{{ step.content }}</p>
            </el-timeline-item>
          </el-timeline>
        </PagePanel>
        <div class="desktop-results">
          <DesktopTable
            :rows="dish.ingredients"
            :loading="false"
            title="所需食材"
          >
            <el-table-column
              prop="ingredientName"
              label="食材"
              min-width="140"
              sortable="custom"
              show-overflow-tooltip
            />
            <el-table-column
              prop="amount"
              label="用量"
              min-width="140"
              sortable="custom"
              show-overflow-tooltip
            />
          </DesktopTable>
          <PagePanel title="口味与特点">
            <el-tag v-for="tag in dish.highlightTags" :key="tag">
              {{ tag }}
            </el-tag>
          </PagePanel>
        </div>
      </div>
    </template>
  </section>
</template>
