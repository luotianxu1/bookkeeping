<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Check,
  Search,
  ShieldCheck,
  LockKeyhole,
  KeyRound,
  RotateCw,
} from '@lucide/vue'
import { api } from '@/api/admin'
import type { Role } from '@/api/types'
import { useResource } from '@/composables/useResource'
import { can, isSuper, session } from '@/stores/session'
import { togglePermission } from '@/utils/permissions'
import { showError } from '@/utils/feedback'
const route = useRoute()
const { data, error, loading, reload } = useResource(
  async () => {
    const [roles, permissions] = await Promise.all([
      api.roles(),
      api.permissions(),
    ])
    return { roles, permissions }
  },
  { roles: [], permissions: [] },
)
const selectedId = ref<number | null>(null)
const selected = ref<string[]>([])
const keyword = ref('')
const saving = ref(false)
const current = computed(() =>
  data.value.roles.find((role) => role.id === selectedId.value),
)
const filteredRoles = computed(() =>
  data.value.roles.filter((role) =>
    `${role.name} ${role.code}`
      .toLowerCase()
      .includes(keyword.value.toLowerCase().trim()),
  ),
)
const groups = computed(() =>
  [
    ...new Set(data.value.permissions.map((permission) => permission.group)),
  ].map((name) => ({
    name,
    items: data.value.permissions.filter(
      (permission) => permission.group === name,
    ),
  })),
)
const editable = computed(
  () =>
    !!current.value &&
    !current.value.builtIn &&
    current.value.code !== session.data?.user.roleCode &&
    can('permission.assign') &&
    (isSuper() || current.value.permissions.every(can)),
)
const dirty = computed(
  () =>
    [...selected.value].sort().join(',') !==
    [...(current.value?.permissions || [])].sort().join(','),
)
watch(data, (value) => {
  const role =
    value.roles.find((item) => item.id === selectedId.value) ||
    value.roles.find((item) => String(item.id) === route.query.role) ||
    value.roles[0]
  selectedId.value = role?.id || null
  selected.value = [...(role?.permissions || [])]
})
async function confirmDiscard() {
  // Session expiry and explicit logout must always be able to leave this page.
  if (!session.data) return true
  if (saving.value) return false
  if (!dirty.value) return true
  try {
    await ElMessageBox.confirm(
      '当前权限配置尚未保存，确定放弃修改？',
      '未保存的修改',
      {
        confirmButtonText: '放弃修改',
        cancelButtonText: '继续编辑',
        type: 'warning',
      },
    )
    return true
  } catch {
    return false
  }
}
async function selectRole(role: Role) {
  if (role.id === selectedId.value || !(await confirmDiscard())) return
  selectedId.value = role.id
  selected.value = [...role.permissions]
}
function toggle(code: string, checked: boolean) {
  selected.value = togglePermission(
    selected.value,
    code,
    checked,
    data.value.permissions,
  )
}
function selectAll() {
  for (const permission of data.value.permissions.filter((item) =>
    can(item.code),
  ))
    toggle(permission.code, true)
}
async function refresh() {
  if (await confirmDiscard()) await reload()
}
async function save() {
  if (!current.value || !editable.value || saving.value) return
  saving.value = true
  try {
    const role = await api.grant(current.value.id, selected.value)
    data.value = {
      ...data.value,
      roles: data.value.roles.map((item) =>
        item.id === role.id ? role : item,
      ),
    }
    ElMessage.success('角色权限已保存，下次请求立即生效')
  } catch (cause) {
    showError(cause)
  } finally {
    saving.value = false
  }
}
onBeforeRouteLeave(confirmDiscard)
function beforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
</script>

<template>
  <section>
    <div class="page-heading">
      <h1>权限配置</h1>
      <el-button :disabled="loading || saving" @click="refresh">
        <RotateCw :size="16" />
        刷新权限
      </el-button>
    </div>
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
      class="spaced-alert"
    >
      <el-button link type="primary" @click="refresh">重新加载</el-button>
    </el-alert>
    <div v-loading="loading" class="permission-layout">
      <aside class="panel role-picker">
        <div class="panel-heading">
          <h2>
            选择角色
            <el-tag disable-transitions size="small" type="info">
              {{ data.roles.length }}
            </el-tag>
          </h2>
        </div>
        <div class="role-search">
          <el-input
            v-model="keyword"
            placeholder="搜索角色"
            aria-label="搜索授权角色"
            clearable
          >
            <template #prefix><Search :size="15" /></template>
          </el-input>
        </div>
        <div class="role-list">
          <el-button
            v-for="role in filteredRoles"
            :key="role.id"
            class="role-choice"
            :class="{ selected: role.id === selectedId }"
            :aria-pressed="role.id === selectedId"
            text
            :disabled="saving"
            @click="selectRole(role)"
          >
            <span class="role-choice-icon"><ShieldCheck :size="19" /></span>
            <span>
              <strong>
                {{ role.name }}
                <LockKeyhole v-if="role.builtIn" :size="12" />
              </strong>
              <small>
                {{ role.userCount }} 位用户 ·
                {{ role.status === 'active' ? '已启用' : '已停用' }}
              </small>
            </span>
            <Check
              v-if="role.id === selectedId"
              :size="16"
              class="role-selected-check"
            />
          </el-button>
          <el-empty
            v-if="!loading && !filteredRoles.length"
            description="没有匹配的角色"
            :image-size="60"
          />
        </div>
      </aside>
      <div v-if="current" class="panel permission-panel">
        <div class="permission-header">
          <div>
            <h2>
              {{ current.name }}
              <el-tag disable-transitions size="small" type="info">
                {{ current.code }}
              </el-tag>
            </h2>
            <p v-if="current.description">{{ current.description }}</p>
          </div>
          <span class="permission-total">
            <KeyRound :size="18" />
            <strong>{{ selected.length }}</strong>
            / {{ data.permissions.length }}
          </span>
        </div>
        <div class="permission-toolbar">
          <span>
            {{
              editable
                ? '自动关联依赖权限'
                : current.builtIn
                  ? '内置角色 · 权限只读'
                  : current.code === session.data?.user.roleCode
                    ? '当前所属角色 · 权限只读'
                    : '权限只读 · 无法修改此角色的授权'
            }}
          </span>
          <div v-if="editable">
            <el-button
              link
              type="primary"
              :disabled="saving"
              @click="selectAll"
            >
              全选可授权权限
            </el-button>
            <el-button link :disabled="saving" @click="selected = []">
              清空
            </el-button>
          </div>
        </div>
        <div class="permission-groups">
          <section
            v-for="group in groups"
            :key="group.name"
            class="permission-group"
          >
            <div class="permission-group-heading">
              <h3>{{ group.name }}</h3>
              <span>
                {{
                  group.items.filter((item) => selected.includes(item.code))
                    .length
                }}
                / {{ group.items.length }}
              </span>
            </div>
            <div class="permission-items">
              <div
                v-for="permission in group.items"
                :key="permission.code"
                class="permission-item"
                :class="{ checked: selected.includes(permission.code) }"
              >
                <el-checkbox
                  :model-value="selected.includes(permission.code)"
                  :disabled="!editable || saving || !can(permission.code)"
                  :aria-label="permission.name"
                  @change="(value) => toggle(permission.code, !!value)"
                >
                  <el-tooltip :content="permission.description" placement="top">
                    <strong>{{ permission.name }}</strong>
                  </el-tooltip>
                </el-checkbox>
                <code>{{ permission.code }}</code>
              </div>
            </div>
          </section>
        </div>
        <div class="permission-savebar">
          <span :class="{ 'unsaved-label': dirty }">
            <i v-if="dirty" />
            {{ dirty ? '有未保存的权限修改' : '当前权限配置已同步' }}
          </span>
          <div>
            <el-button
              v-if="editable"
              :disabled="!dirty || saving"
              @click="selected = [...current.permissions]"
            >
              还原修改
            </el-button>
            <el-button
              v-if="editable"
              type="primary"
              :disabled="!dirty"
              :loading="saving"
              @click="save"
            >
              <Check :size="16" />
              保存权限
            </el-button>
            <span v-else class="builtin-label">
              <LockKeyhole :size="14" />
              只读权限
            </span>
          </div>
        </div>
      </div>
      <div v-else class="panel permission-panel">
        <el-empty description="请选择一个角色查看权限" />
      </div>
    </div>
  </section>
</template>
