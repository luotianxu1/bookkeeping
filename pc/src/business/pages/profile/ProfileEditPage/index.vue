<script setup lang="ts">
import PagePanel from '@/business/components/PagePanel.vue'

import { onMounted, ref } from 'vue'

import CommonInput from '@/business/components/common/CommonInput/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'

import PageHeader from '@/business/components/common/PageHeader/index.vue'
import { getCurrentUser, updateProfile } from '@/business/api/modules/auth'
import { getErrorMessage } from '@/business/api/request'
import {
  getStoredCurrentUser,
  setStoredCurrentUser,
} from '@/business/utils/current-user'

const user = ref(getStoredCurrentUser())
const displayName = ref(user.value?.displayName ?? '')
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const saved = ref(false)
onMounted(async () => {
  try {
    user.value = await getCurrentUser()
    displayName.value = user.value.displayName
    setStoredCurrentUser(user.value)
  } catch (e) {
    error.value = getErrorMessage(e)
  } finally {
    loading.value = false
  }
})
async function save() {
  if (saving.value) return
  error.value = ''
  if (!displayName.value.trim() || displayName.value.trim().length > 80) {
    error.value = '昵称需为 1 到 80 个字符'
    return
  }
  saving.value = true
  try {
    user.value = await updateProfile({ displayName: displayName.value.trim() })
    displayName.value = user.value.displayName
    setStoredCurrentUser(user.value)
    saved.value = true
  } catch (e) {
    error.value = getErrorMessage(e)
  } finally {
    saving.value = false
  }
}
</script>
<template>
  <section class="desktop-workspace desktop-workspace--form">
    <PageHeader title="个人资料">
      <template #right>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="loading"
          @click="save"
        >
          保存资料
        </el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <PagePanel title="基本信息">
      <el-form label-position="top">
        <div class="desktop-form-grid">
          <CommonInput
            :model-value="user?.username ?? ''"
            label="用户名"
            readonly
          />
          <CommonInput
            v-model="displayName"
            label="昵称"
            placeholder="请输入昵称"
            :disabled="saving"
          />
        </div>
      </el-form>
    </PagePanel>
    <CommonFeedback v-model="saved" message="个人资料已保存" type="success" />
  </section>
</template>
