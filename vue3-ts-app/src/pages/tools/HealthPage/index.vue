<script setup lang="ts">
// 健康管理页：根据体重与活动量估算每日热量与主要营养素，提供体重打卡入口与身体数据维护（用于打卡时自动计算体脂）。
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonHeaderActionButton from '@/components/common/CommonHeaderActionButton/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonSkeleton from '@/components/common/CommonSkeleton/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import {
  getHealthProfile,
  getWeightRecords,
  saveHealthProfile,
  type HealthGender,
} from '@/api/modules/tool'
import { getStoredCurrentUser } from '@/utils/current-user'

type ActivityKey = 'sedentary' | 'light' | 'moderate' | 'active'

const router = useRouter()

const activityOptions: { label: string; value: ActivityKey }[] = [
  { label: '久坐', value: 'sedentary' },
  { label: '轻度活动', value: 'light' },
  { label: '中度活动', value: 'moderate' },
  { label: '高度活动', value: 'active' },
]

type ActivityProfile = {
  calorieFactor: number
  tdeeFactor: number
  proteinPerKg: [number, number]
  carbPerKg: [number, number]
  waterPerKg: [number, number]
}

// calorieFactor：按体重的快速估算法（kcal/kg）；tdeeFactor：Mifflin-St Jeor 公式对应的活动系数。
const ACTIVITY_PROFILES: Record<ActivityKey, ActivityProfile> = {
  sedentary: { calorieFactor: 25, tdeeFactor: 1.2, proteinPerKg: [1, 1.2], carbPerKg: [3, 4], waterPerKg: [30, 35] },
  light: { calorieFactor: 30, tdeeFactor: 1.375, proteinPerKg: [1.2, 1.4], carbPerKg: [4, 5], waterPerKg: [35, 40] },
  moderate: { calorieFactor: 35, tdeeFactor: 1.55, proteinPerKg: [1.4, 1.6], carbPerKg: [5, 6], waterPerKg: [40, 45] },
  active: { calorieFactor: 40, tdeeFactor: 1.725, proteinPerKg: [1.6, 2], carbPerKg: [6, 8], waterPerKg: [45, 50] },
}

// 脂肪需求一般占总热量 20–30%，按体重估算即可，不随活动量明显变化。
const FAT_PER_KG: [number, number] = [0.8, 1]

const weightInput = ref('65')
const activeActivity = ref<ActivityKey>('light')
const latestWeight = ref<number | null>(null)
// 首屏数据（上次打卡体重/身体档案）加载中，完成后显示真实数据
const isLoadingInitial = ref(true)

// 身体数据（性别/年龄/身高）：保存后体重打卡时自动按 BMI 估算体脂率。
const gender = ref('male')
const ageInput = ref('')
const heightInput = ref('')
const hasProfile = ref(false)
const profileModalOpen = ref(false)
const profileSaving = ref(false)
const profileError = ref('')

const weightNumber = computed(() => {
  const value = Number(weightInput.value)
  return Number.isFinite(value) && value >= 20 && value <= 300 ? value : null
})

const profile = computed(() => ACTIVITY_PROFILES[activeActivity.value])
const activityLabel = computed(() => activityOptions.find((option) => option.value === activeActivity.value)?.label ?? '')

// 已保存身体数据时启用 Mifflin-St Jeor 精确公式，否则回退按体重系数估算
const preciseMeta = computed(() => {
  if (!hasProfile.value) {
    return null
  }
  const age = Number(ageInput.value)
  const height = Number(heightInput.value)
  if (!Number.isFinite(age) || age <= 0 || !Number.isFinite(height) || height <= 0) {
    return null
  }
  return { age, heightCm: height, isMale: gender.value === 'male' }
})

const calories = computed(() => {
  const weight = weightNumber.value
  if (weight === null) {
    return null
  }
  const meta = preciseMeta.value
  if (meta) {
    // Mifflin-St Jeor：BMR = 10×体重 + 6.25×身高 − 5×年龄 +（男 +5 / 女 −161），TDEE = BMR × 活动系数
    const bmr = 10 * weight + 6.25 * meta.heightCm - 5 * meta.age + (meta.isMale ? 5 : -161)
    return Math.round(bmr * profile.value.tdeeFactor)
  }
  return Math.round(weight * profile.value.calorieFactor)
})

const calorieHint = computed(() => {
  const weight = weightNumber.value
  if (weight === null) {
    return ''
  }
  const meta = preciseMeta.value
  if (meta) {
    return `按 Mifflin-St Jeor 公式（${meta.isMale ? '男' : '女'} · ${meta.age}岁 · ${meta.heightCm}cm）× ${profile.value.tdeeFactor} 估算（${activityLabel.value}）`
  }
  return `按 ${weight}kg × ${profile.value.calorieFactor} kcal/kg 估算（${activityLabel.value}）`
})

const nutrientCards = computed(() => {
  const weight = weightNumber.value
  if (weight === null) {
    return []
  }
  return [
    { key: 'protein', title: '蛋白质', range: buildRange(weight, profile.value.proteinPerKg), unit: 'g', hint: `${formatPerKg(profile.value.proteinPerKg)} g/kg` },
    { key: 'carb', title: '碳水化合物', range: buildRange(weight, profile.value.carbPerKg), unit: 'g', hint: `${formatPerKg(profile.value.carbPerKg)} g/kg` },
    { key: 'fat', title: '脂肪', range: buildRange(weight, FAT_PER_KG), unit: 'g', hint: '0.8–1 g/kg' },
    { key: 'water', title: '饮水', range: buildRange(weight, profile.value.waterPerKg), unit: 'ml', hint: `${formatPerKg(profile.value.waterPerKg)} ml/kg` },
    { key: 'fiber', title: '膳食纤维', range: '25–30', unit: 'g', hint: '固定推荐量' },
  ]
})

onMounted(async () => {
  const user = getStoredCurrentUser()
  if (!user) {
    isLoadingInitial.value = false
    return
  }

  try {
    const records = await getWeightRecords(user.id, 1)
    // 体重输入框默认填入上次打卡的体重
    const lastWeight = records[0]?.weight
    if (lastWeight != null) {
      latestWeight.value = lastWeight
      weightInput.value = String(lastWeight)
    }
  } catch {
    latestWeight.value = null
  }

  try {
    const profile = await getHealthProfile(user.id)
    if (profile) {
      hasProfile.value = true
      gender.value = profile.gender === 2 ? 'female' : 'male'
      ageInput.value = String(profile.age)
      heightInput.value = String(profile.heightCm)
    }
  } catch {
    // 档案加载失败不打断页面，保存时会再校验
  }

  isLoadingInitial.value = false
})

async function onSaveProfile() {
  const user = getStoredCurrentUser()
  if (!user) {
    profileError.value = '请先登录'
    return
  }

  const age = Number(ageInput.value)
  if (!Number.isFinite(age) || age < 10 || age > 100) {
    profileError.value = '年龄需在 10–100 岁之间'
    return
  }
  const height = Number(heightInput.value)
  if (!Number.isFinite(height) || height < 80 || height > 250) {
    profileError.value = '身高需在 80–250cm 之间'
    return
  }

  profileError.value = ''
  profileSaving.value = true
  try {
    await saveHealthProfile({
      userId: user.id,
      gender: (gender.value === 'female' ? 2 : 1) as HealthGender,
      age,
      heightCm: height,
    })
    hasProfile.value = true
    profileError.value = ''
    profileModalOpen.value = false
  } catch (error) {
    profileError.value = (error as Error).message || '保存失败，请稍后再试'
  } finally {
    profileSaving.value = false
  }
}

function buildRange(weight: number, perKg: [number, number]) {
  return `${Math.round(weight * perKg[0])}–${Math.round(weight * perKg[1])}`
}

function formatPerKg(perKg: [number, number]) {
  return `${perKg[0]}–${perKg[1]}`
}

function goRecords() {
  void router.push('/tools/health/records')
}
</script>

<template>
  <section class="health-page" aria-label="健康管理页面">
    <PageHeader title="健康管理" back-to="/tools" back-label="返回工具页" prefer-back-to>
      <template #right>
        <CommonHeaderActionButton label="身体数据" @click="profileModalOpen = true">
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path
              d="M12 7.5A2.5 2.5 0 1 0 12 2.5A2.5 2.5 0 0 0 12 7.5ZM6 21V13.8C6 12.8 6.8 12 7.8 12H9L10 8.5H14L15 12H16.2C17.2 12 18 12.8 18 13.8V21M9.5 21V17.5H14.5V21"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </CommonHeaderActionButton>
      </template>
    </PageHeader>

    <section class="health-record-card" aria-label="体重打卡入口">
      <div class="health-record-info">
        <h2>体重打卡</h2>
        <CommonSkeleton v-if="isLoadingInitial" variant="list" :rows="1" />
        <p v-else>最新体重：{{ latestWeight === null ? '暂无记录' : `${latestWeight}kg` }}</p>
      </div>
      <CommonButton size="sm" @click="goRecords">去打卡</CommonButton>
    </section>

    <section class="health-hero" aria-label="体重设置">
      <div class="health-hero-header">
        <h2>每日营养需求</h2>
        <p>根据体重估算每天需要的热量与主要营养素</p>
      </div>
      <CommonInput
        v-model="weightInput"
        label="体重（kg）"
        placeholder="例如：65"
        input-type="number"
        input-mode="decimal"
      />
    </section>

    <div class="health-activity" aria-label="日常活动量选择">
      <span class="health-field-label">日常活动量</span>
      <SegmentedControl
        v-model="activeActivity"
        :options="activityOptions"
        label="日常活动量"
        variant="brand"
      />
    </div>

    <p v-if="weightNumber === null" class="health-message">
      请输入有效体重（20–300kg）后查看每日所需
    </p>

    <template v-else>
      <section class="health-calorie-card" aria-label="每日热量">
        <span class="health-calorie-label">每日热量</span>
        <p class="health-calorie-value">
          {{ calories }}<span>kcal</span>
        </p>
        <span class="health-calorie-hint">{{ calorieHint }}</span>
      </section>

      <section class="health-grid" aria-label="每日营养素">
        <article v-for="item in nutrientCards" :key="item.key" class="health-card">
          <span class="health-card-title">{{ item.title }}</span>
          <p class="health-card-value">
            {{ item.range }}<span>{{ item.unit }}</span>
          </p>
          <span class="health-card-hint">{{ item.hint }}</span>
        </article>
      </section>
    </template>

    <section class="health-note" aria-label="估算说明">
      <p>以上为成年人常用系数的估算值，蛋白质、碳水与饮水需求会随活动量档位提高；实际需求与年龄、性别、健康状况有关，仅供参考。</p>
      <p>脂肪按 0.8–1 g/kg 估算；膳食纤维及维生素、矿物质按固定推荐量摄入（如纤维 25–30g、钙 800mg），不随体重与活动量变化。</p>
    </section>

    <CommonModal v-model="profileModalOpen" title="身体数据">
      <div class="health-modal-body">
        <div class="health-field">
          <span class="health-field-label">性别</span>
          <SegmentedControl
            v-model="gender"
            label="性别"
            variant="brand"
            :options="[
              { label: '男', value: 'male' },
              { label: '女', value: 'female' },
            ]"
          />
        </div>

        <div class="health-profile-row">
          <CommonInput
            v-model="ageInput"
            label="年龄（岁）"
            placeholder="例如：30"
            input-type="number"
            input-mode="numeric"
          />
          <CommonInput
            v-model="heightInput"
            label="身高（cm）"
            placeholder="例如：175"
            input-type="number"
            input-mode="decimal"
          />
        </div>

        <p v-if="profileError" class="health-profile-error">{{ profileError }}</p>
        <p class="health-profile-hint">
          {{ hasProfile ? '身体数据已保存，打卡时会自动计算并记录体脂率' : '保存后每次打卡会自动按 BMI 估算体脂率并记录' }}
        </p>
      </div>

      <template #footer>
        <CommonButton class="health-profile-save" :disabled="profileSaving" @click="onSaveProfile">
          {{ profileSaving ? '保存中…' : '保存' }}
        </CommonButton>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
