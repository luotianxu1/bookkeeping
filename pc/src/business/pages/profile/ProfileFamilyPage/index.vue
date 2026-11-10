<script setup lang="ts">
import PagePanel from '@/business/components/PagePanel.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'

import { confirmAction } from '@/business/utils/confirm'

import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import {
  createFamily,
  getFamilyOverview,
  joinFamily,
  unbindFamilyMember,
  type FamilyMember,
  type FamilyOverview,
} from '@/business/api/modules/auth'
import { ApiError } from '@/business/api/request'

const joinInviteCode = ref('')
const errorMessage = ref('')
const isLoading = ref(true)
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

onMounted(() => {
  void fetchFamilyOverview()
})

async function fetchFamilyOverview() {
  isLoading.value = true
  errorMessage.value = ''
  try {
    familyOverview.value = await getFamilyOverview()
  } catch (error) {
    errorMessage.value =
      error instanceof ApiError ? error.message : '获取家庭信息失败，请稍后再试'
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
    ElMessage.success('家庭已创建')
  } catch (error) {
    errorMessage.value =
      error instanceof ApiError ? error.message : '创建家庭失败，请稍后再试'
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
    ElMessage.success('已加入家庭')
  } catch (error) {
    errorMessage.value =
      error instanceof ApiError ? error.message : '加入家庭失败，请稍后再试'
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
    ElMessage.success('邀请码已复制')
  } catch {
    errorMessage.value = '复制失败，请手动复制邀请码'
  }
}

async function handleMemberAction(member: FamilyMember) {
  if (!member.canUnbind || unbindingMemberId.value === member.userId) {
    return
  }

  if (!(await confirmAction(`确认解绑 ${member.displayName} 吗？`))) {
    return
  }

  unbindingMemberId.value = member.userId
  errorMessage.value = ''
  try {
    familyOverview.value = await unbindFamilyMember(member.userId)
    ElMessage.success('已解绑家庭成员')
  } catch (error) {
    errorMessage.value =
      error instanceof ApiError ? error.message : '解绑失败，请稍后再试'
  } finally {
    unbindingMemberId.value = null
  }
}

type MemberRow = (typeof members.value)[number]
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader title="家庭成员"><template #right></template></PageHeader>
    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <div class="desktop-main-aside">
      <DesktopTable :rows="members" :loading="isLoading" title="家庭成员">
        <el-table-column
          prop="displayName"
          label="成员"
          min-width="220"
          sortable="custom"
          show-overflow-tooltip
        />
        <el-table-column
          prop="role"
          label="角色"
          min-width="140"
          sortable="custom"
          show-overflow-tooltip
        />
        <el-table-column
          prop="status"
          label="状态"
          min-width="140"
          sortable="custom"
          show-overflow-tooltip
        />
        <!-- @vue-generic {MemberRow} -->
        <el-table-column fixed="right" label="操作" width="150">
          <template #default="{ row }: { row: MemberRow }">
            <el-button
              v-if="row.canUnbind"
              link
              type="danger"
              :loading="unbindingMemberId === row.userId"
              @click="handleMemberAction(row)"
            >
              解绑
            </el-button>
          </template>
        </el-table-column>
      </DesktopTable>
      <div class="desktop-results">
        <PagePanel title="家庭邀请码">
          <template v-if="hasFamily">
            <strong class="desktop-invite-code">{{ inviteCode }}</strong>
            <p class="desktop-note">已绑定 {{ joinedCount }} 人</p>
            <el-button @click="handleCopyInviteCode">复制邀请码</el-button>
          </template>
          <template v-else>
            <el-button
              :loading="isCreating"
              type="primary"
              @click="handleCreateFamily"
            >
              创建家庭
            </el-button>
          </template>
        </PagePanel>
        <PagePanel title="加入家庭" v-if="!isLoading && !hasFamily">
          <el-input
            v-model="joinInviteCode"
            aria-label="家庭邀请码"
            placeholder="输入家庭邀请码"
          />
          <div class="desktop-bottom-actions">
            <el-button :loading="isJoining" type="primary" @click="submitJoin">
              加入家庭
            </el-button>
          </div>
        </PagePanel>
      </div>
    </div>
  </section>
</template>
