<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import {
  createFamily,
  getFamilyOverview,
  joinFamily,
  unbindFamilyMember,
  type FamilyMember,
  type FamilyOverview,
} from '@/api/modules/auth'
import { ApiError } from '@/api/request'

const joinInviteCode = ref('')
const pageMessage = ref('')
const errorMessage = ref('')
const isLoading = ref(false)
const isCreating = ref(false)
const isJoining = ref(false)
const unbindingMemberId = ref<number | null>(null)
const familyOverview = ref<FamilyOverview>({
  hasFamily: false,
  inviteCode: null,
  memberCount: 0,
  members: [],
})

const joinedCount = computed(() => familyOverview.value.memberCount)
const members = computed(() => familyOverview.value.members)
const hasFamily = computed(() => familyOverview.value.hasFamily)
const inviteCode = computed(() => familyOverview.value.inviteCode || '')

function getAvatarText(name: string) {
  const normalizedName = name.trim()
  if (!normalizedName) {
    return '--'
  }

  return normalizedName.slice(0, 2).toUpperCase()
}

function getMemberTone(member: FamilyMember) {
  return member.canUnbind ? 'success' : 'brand'
}

onMounted(() => {
  void fetchFamilyOverview()
})

async function fetchFamilyOverview() {
  isLoading.value = true
  errorMessage.value = ''
  try {
    familyOverview.value = await getFamilyOverview()
    pageMessage.value = familyOverview.value.hasFamily
      ? '家庭成员已绑定成功，可以在这里查看邀请码和管理成员。'
      : '你还没有加入家庭，可以先创建自己的家庭邀请码，或者输入邀请码加入已有家庭。'
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '获取家庭信息失败，请稍后再试'
  } finally {
    isLoading.value = false
  }
}

async function handleCreateFamily() {
  if (isCreating.value) {
    return
  }

  isCreating.value = true
  errorMessage.value = ''
  try {
    familyOverview.value = await createFamily()
    pageMessage.value = '家庭邀请码已生成，现在可以让家人输入邀请码加入。'
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '创建家庭失败，请稍后再试'
  } finally {
    isCreating.value = false
  }
}

async function submitJoin() {
  if (isJoining.value) {
    return
  }

  const normalizedCode = joinInviteCode.value.trim().toUpperCase()
  errorMessage.value = ''

  if (!normalizedCode) {
    errorMessage.value = '请输入家庭邀请码'
    return
  }

  isJoining.value = true
  try {
    familyOverview.value = await joinFamily({
      inviteCode: normalizedCode,
    })
    joinInviteCode.value = ''
    pageMessage.value = '已成功加入家庭，当前页面仅展示已绑定成员。'
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '加入家庭失败，请稍后再试'
  } finally {
    isJoining.value = false
  }
}

async function handleCopyInviteCode() {
  if (!inviteCode.value) {
    return
  }

  try {
    await navigator.clipboard.writeText(inviteCode.value)
    pageMessage.value = '家庭邀请码已复制，可以发给要加入的家庭成员。'
  } catch {
    errorMessage.value = '复制失败，请手动复制邀请码'
  }
}

async function handleMemberAction(member: FamilyMember) {
  if (!member.canUnbind || unbindingMemberId.value === member.userId) {
    return
  }

  if (!window.confirm(`确认解绑 ${member.displayName} 吗？`)) {
    return
  }

  unbindingMemberId.value = member.userId
  errorMessage.value = ''
  try {
    familyOverview.value = await unbindFamilyMember(member.userId)
    pageMessage.value = '已解绑该家庭成员，当前列表仅展示已绑定成员。'
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '解绑失败，请稍后再试'
  } finally {
    unbindingMemberId.value = null
  }
}
</script>

<template>
  <section class="profile-family-page" aria-label="家庭成员页面">
    <PageHeader title="绑定家庭成员" back-to="/profile" prefer-back-to />

    <section v-if="isLoading" class="family-card" aria-label="家庭信息加载中">
      <div class="family-card-header">
        <h2>家庭邀请码</h2>
      </div>
      <div class="family-empty">正在加载家庭信息...</div>
    </section>

    <section v-else-if="hasFamily" class="family-card" aria-label="家庭信息">
      <div class="family-card-header">
        <h2>家庭邀请码</h2>
        <span class="family-badge">已绑定 {{ joinedCount }} 人</span>
      </div>

      <div class="invite-card">
        <div>
          <strong>{{ inviteCode }}</strong>
        </div>
        <button type="button" class="copy-btn" @click="handleCopyInviteCode">复制</button>
      </div>
    </section>

    <section v-else class="family-card" aria-label="创建家庭">
      <div class="family-card-header">
        <h2>家庭邀请码</h2>
      </div>
      <p>还没有加入家庭时，可以先创建自己的家庭邀请码，再把邀请码发给家人加入。</p>
      <button class="join-entry-btn" type="button" :disabled="isCreating" @click="handleCreateFamily">
        {{ isCreating ? '创建中...' : '创建我的家庭邀请码' }}
      </button>
    </section>

    <section v-if="!isLoading && !hasFamily" class="family-card" aria-label="加入家庭">
      <div class="family-card-header">
        <h2>输入邀请码加入家庭</h2>
      </div>
      <p>{{ pageMessage }}</p>

      <label class="join-field">
        <span>家庭邀请码</span>
        <input
          v-model="joinInviteCode"
          type="text"
          inputmode="text"
          autocomplete="off"
          placeholder="例如 FAMILY-2048"
        />
      </label>

      <p v-if="errorMessage" class="join-error">{{ errorMessage }}</p>
      <button class="join-entry-btn" type="button" :disabled="isJoining" @click="submitJoin">
        {{ isJoining ? '加入中...' : '输入邀请码并加入' }}
      </button>
    </section>

    <section class="family-card" aria-label="成员列表">
      <div class="family-card-header">
        <h2>家庭成员</h2>
      </div>
      <p v-if="!errorMessage && pageMessage">{{ pageMessage }}</p>
      <p v-if="errorMessage && hasFamily" class="join-error">{{ errorMessage }}</p>
      <div v-if="isLoading" class="family-empty">正在加载家庭信息...</div>
      <div v-else-if="!members.length" class="family-empty">当前还没有已绑定成员。</div>
      <template v-else>
        <div v-for="member in members" :key="member.userId" class="member-row">
        <div :class="['member-avatar', `tone-${getMemberTone(member)}`]">
          {{ getAvatarText(member.displayName) }}
        </div>
        <div class="member-info">
          <strong>{{ member.displayName }}</strong>
          <span>{{ member.role }}</span>
        </div>
        <div class="member-actions">
          <span :class="['member-tag', `tone-${getMemberTone(member)}`]">{{ member.status }}</span>
          <button
            v-if="member.canUnbind"
            class="member-action-btn"
            type="button"
            :disabled="unbindingMemberId === member.userId"
            @click="handleMemberAction(member)"
          >
            {{ unbindingMemberId === member.userId ? '解绑中...' : '解绑' }}
          </button>
        </div>
        </div>
      </template>
    </section>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
