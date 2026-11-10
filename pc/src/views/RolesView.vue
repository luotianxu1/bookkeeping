<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElTableColumn } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  Plus,
  Search,
  RotateCw,
  ShieldCheck,
  KeyRound,
  LockKeyhole,
} from '@lucide/vue'
import { api } from '@/api/admin'
import type { Role } from '@/api/types'
import { useResource } from '@/composables/useResource'
import { can, isSuper, session } from '@/stores/session'
import { showError } from '@/utils/feedback'
const RoleColumn = ElTableColumn<Role>
const router = useRouter()
const { data: roles, loading, error, reload } = useResource(api.roles, [])
const keyword = ref('')
const status = ref('')
const filtered = computed(() =>
  roles.value.filter(
    (role) =>
      (!status.value || role.status === status.value) &&
      `${role.name} ${role.code} ${role.description}`
        .toLowerCase()
        .includes(keyword.value.trim().toLowerCase()),
  ),
)
const dialog = ref(false)
const editing = ref<Role | null>(null)
const saving = ref(false)
const deleting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  code: '',
  name: '',
  description: '',
  status: 'active' as 'active' | 'disabled',
})
const rules: FormRules = {
  code: [
    {
      required: true,
      pattern: /^[a-z][a-z0-9_]{2,63}$/,
      message: '3–64 位小写字母、数字或下划线，以字母开头',
      trigger: 'blur',
    },
  ],
  name: [
    {
      required: true,
      whitespace: true,
      max: 80,
      message: '请输入 1–80 个字符的角色名称',
      trigger: 'blur',
    },
  ],
}
function editable(role: Role) {
  return (
    !role.builtIn &&
    role.code !== session.data?.user.roleCode &&
    (isSuper() || role.permissions.every(can))
  )
}
function openRole(role: Role | null) {
  editing.value = role
  Object.assign(form, {
    code: role?.code || '',
    name: role?.name || '',
    description: role?.description || '',
    status: role?.status || 'active',
  })
  dialog.value = true
}
async function save() {
  if (saving.value || !(await formRef.value?.validate().catch(() => false)))
    return
  saving.value = true
  try {
    if (editing.value)
      await api.updateRole(editing.value.id, {
        name: form.name.trim(),
        description: form.description.trim(),
        status: form.status,
      })
    else
      await api.createRole({
        code: form.code,
        name: form.name.trim(),
        description: form.description.trim(),
      })
    dialog.value = false
    ElMessage.success(
      editing.value ? '角色已更新' : '角色已创建，可在权限管理中配置授权',
    )
    await reload()
  } catch (cause) {
    showError(cause)
  } finally {
    saving.value = false
  }
}
async function remove(role: Role) {
  if (deleting.value) return
  try {
    await ElMessageBox.confirm(
      `删除角色“${role.name}”？该角色的权限配置也将被删除。`,
      '删除角色',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }
  deleting.value = true
  try {
    await api.deleteRole(role.id)
    ElMessage.success('角色已删除')
    await reload()
  } catch (cause) {
    showError(cause)
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <section>
    <div class="page-heading">
      <h1>
        角色管理
        <el-tag disable-transitions size="small" type="info">
          {{ roles.length }}
        </el-tag>
      </h1>
      <div class="heading-actions">
        <el-button :loading="loading" aria-label="刷新角色列表" @click="reload">
          <RotateCw :size="16" />
          刷新
        </el-button>
        <el-button
          v-if="can('role.create')"
          type="primary"
          @click="openRole(null)"
        >
          <Plus :size="16" />
          新建角色
        </el-button>
      </div>
    </div>
    <div class="panel">
      <div class="filter-bar">
        <el-input
          v-model="keyword"
          placeholder="搜索角色名称、编码或描述"
          aria-label="搜索角色"
          clearable
          class="search-input"
        >
          <template #prefix><Search :size="16" /></template>
        </el-input>
        <el-select
          v-model="status"
          placeholder="全部状态"
          aria-label="角色状态"
          clearable
        >
          <el-option label="已启用" value="active" />
          <el-option label="已停用" value="disabled" />
        </el-select>
        <span class="filter-summary">{{ filtered.length }} 个角色</span>
      </div>
      <el-alert
        v-if="error"
        :title="error"
        type="error"
        :closable="false"
        class="panel-alert"
      >
        <el-button link type="primary" @click="reload">重试</el-button>
      </el-alert>
      <el-table
        v-loading="loading"
        :data="filtered"
        row-key="id"
        :empty-text="error ? '暂时无法获取角色' : '没有找到符合条件的角色'"
        class="data-table"
      >
        <RoleColumn label="角色" min-width="210">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar
                :size="32"
                shape="square"
                class="avatar"
                :class="{ 'avatar-admin': row.code === 'super_admin' }"
              >
                <ShieldCheck :size="18" />
              </el-avatar>
              <div>
                <strong>{{ row.name }}</strong>
                <span>{{ row.code }}</span>
              </div>
            </div>
          </template>
        </RoleColumn>
        <RoleColumn label="描述" min-width="250" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.description || '—' }}
          </template>
        </RoleColumn>
        <RoleColumn label="关联用户" width="100">
          <template #default="{ row }">
            <span class="number-cell">{{ row.userCount }}</span>
          </template>
        </RoleColumn>
        <RoleColumn label="权限" width="100">
          <template #default="{ row }">
            <span class="permission-count">
              <KeyRound :size="14" />
              {{ row.permissions.length }}
            </span>
          </template>
        </RoleColumn>
        <RoleColumn label="状态" width="110">
          <template #default="{ row }">
            <el-tag
              disable-transitions
              size="small"
              :type="row.status === 'active' ? 'success' : 'info'"
            >
              {{ row.status === 'active' ? '已启用' : '已停用' }}
            </el-tag>
          </template>
        </RoleColumn>
        <RoleColumn label="操作" fixed="right" min-width="215">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button
                v-if="can('permission.read')"
                link
                type="primary"
                @click="
                  router.push({ path: '/permissions', query: { role: row.id } })
                "
              >
                {{
                  can('permission.assign') && editable(row)
                    ? '配置权限'
                    : '查看权限'
                }}
              </el-button>
              <el-button
                v-if="can('role.update') && editable(row)"
                link
                type="primary"
                @click="openRole(row)"
              >
                编辑
              </el-button>
              <el-tooltip
                v-if="can('role.delete') && editable(row)"
                :disabled="row.userCount === 0"
                content="请先为关联用户分配其他角色"
              >
                <span>
                  <el-button
                    link
                    type="danger"
                    :disabled="row.userCount > 0 || deleting"
                    @click="remove(row)"
                  >
                    删除
                  </el-button>
                </span>
              </el-tooltip>
              <span v-if="row.builtIn" class="builtin-label">
                <LockKeyhole :size="13" />
                内置
              </span>
            </div>
          </template>
        </RoleColumn>
      </el-table>
    </div>
    <el-dialog
      v-model="dialog"
      :title="editing ? '编辑角色' : '新建角色'"
      width="510px"
      destroy-on-close
      :close-on-click-modal="false"
      :close-on-press-escape="!saving"
      :show-close="!saving"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="save"
      >
        <el-form-item label="角色名称" prop="name">
          <el-input
            v-model="form.name"
            placeholder="例如：用户管理员"
            :maxlength="80"
          />
        </el-form-item>
        <el-form-item label="角色编码" prop="code">
          <el-input
            v-model="form.code"
            :disabled="!!editing"
            placeholder="例如：user_manager"
            :maxlength="64"
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            :maxlength="255"
            show-word-limit
            placeholder="说明该角色的职责与适用范围"
          />
        </el-form-item>
        <el-form-item v-if="editing" label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button value="active">启用</el-radio-button>
            <el-radio-button value="disabled">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-alert
          v-if="editing && form.status === 'disabled'"
          title="停用后，该角色的用户将无法使用 PC 管理端。"
          type="warning"
          :closable="false"
        />
        <el-alert
          v-if="!editing"
          title="创建后请配置权限。"
          type="info"
          :closable="false"
        />
        <button type="submit" class="sr-only" tabindex="-1">保存</button>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">
          {{ editing ? '保存修改' : '创建角色' }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>
