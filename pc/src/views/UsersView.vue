<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, ElTableColumn } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Search, RotateCw } from '@lucide/vue'
import { api } from '@/api/admin'
import type { Page, User, UserStatus } from '@/api/types'
import { useResource } from '@/composables/useResource'
import { can, isSuper, loadSession, session } from '@/stores/session'
import { formatDate, showError, statusLabel } from '@/utils/feedback'

const UserColumn = ElTableColumn<User>
const query = reactive({
  keyword: '',
  status: '',
  roleCode: '',
  page: 1,
  pageSize: 20,
})
const {
  data: result,
  loading,
  error,
  reload,
} = useResource(() => api.users({ ...query }), {
  items: [],
  total: 0,
  page: 1,
  pageSize: 20,
} as Page<User>)
const {
  data: roles,
  error: roleError,
  reload: reloadRoles,
} = useResource(api.roleOptions, [])
const dialog = ref(false)
const editing = ref<User | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  username: '',
  password: '',
  displayName: '',
  phone: '',
  email: '',
})
const rules: FormRules = {
  username: [
    {
      required: true,
      pattern: /^[a-zA-Z0-9_.-]{3,64}$/,
      message: '请输入 3–64 位字母、数字、点、短横线或下划线',
      trigger: 'blur',
    },
  ],
  displayName: [
    {
      required: true,
      whitespace: true,
      max: 80,
      message: '请输入 1–80 个字符的昵称',
      trigger: 'blur',
    },
  ],
  password: [
    {
      validator: (_rule, value: string, callback) =>
        callback(
          value &&
            value.trim() &&
            value.length >= 6 &&
            value.length <= 64 &&
            new TextEncoder().encode(value).length <= 72
            ? undefined
            : new Error('密码需为 6–64 位，且 UTF-8 不超过 72 字节'),
        ),
      trigger: 'blur',
    },
  ],
  email: [{ type: 'email', message: '请输入正确的邮箱', trigger: 'blur' }],
  phone: [
    {
      pattern: /^[+0-9 ()-]*$/,
      max: 32,
      message: '请输入正确的手机号',
      trigger: 'blur',
    },
  ],
}
function search() {
  query.page = 1
  void reload()
}
function reset() {
  Object.assign(query, { keyword: '', status: '', roleCode: '', page: 1 })
  void reload()
}
function openUser(user: User | null) {
  editing.value = user
  Object.assign(form, {
    username: user?.username || '',
    password: '',
    displayName: user?.displayName || '',
    phone: user?.phone || '',
    email: user?.email || '',
  })
  dialog.value = true
}
async function save() {
  if (saving.value || !(await formRef.value?.validate().catch(() => false)))
    return
  saving.value = true
  try {
    const profile = {
      displayName: form.displayName.trim(),
      phone: form.phone.trim(),
      email: form.email.trim(),
    }
    if (editing.value) await api.updateUser(editing.value.id, profile)
    else
      await api.createUser({
        ...profile,
        username: form.username.trim(),
        password: form.password,
      })
    dialog.value = false
    ElMessage.success(
      editing.value ? '用户资料已更新' : '用户已创建，默认分配普通用户角色',
    )
    await reload()
    if (editing.value?.id === session.data?.user.id) await loadSession(true)
  } catch (cause) {
    showError(cause)
  } finally {
    saving.value = false
  }
}
function manageable(user: User) {
  return isSuper() || roles.value.some((role) => role.code === user.roleCode)
}
const roleDialog = ref(false)
const roleUser = ref<User | null>(null)
const selectedRole = ref('')
async function openRole(user: User) {
  roleUser.value = user
  selectedRole.value = user.roleCode
  roleDialog.value = true
  await reloadRoles()
}
async function assignRole() {
  if (!roleUser.value || !selectedRole.value || saving.value) return
  saving.value = true
  try {
    await api.userRole(roleUser.value.id, selectedRole.value)
    roleDialog.value = false
    ElMessage.success('角色已更新')
    await reload()
  } catch (cause) {
    showError(cause)
  } finally {
    saving.value = false
  }
}
const changing = ref<number | null>(null)
async function changeStatus(user: User, status: UserStatus) {
  if (changing.value !== null) return
  const action =
    status === 'active' ? '启用' : status === 'locked' ? '锁定' : '停用'
  try {
    await ElMessageBox.confirm(
      `${action}“${user.displayName}”？${status !== 'active' ? '该用户的所有登录会话将立即失效。' : '启用后用户可重新登录。'}`,
      `${action}用户`,
      {
        type: 'warning',
        confirmButtonText: `确认${action}`,
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }
  changing.value = user.id
  try {
    await api.userStatus(user.id, status)
    ElMessage.success(`用户已${action}`)
    await reload()
  } catch (cause) {
    showError(cause)
  } finally {
    changing.value = null
  }
}
</script>

<template>
  <section>
    <div class="page-heading">
      <h1>
        用户管理
        <el-tag disable-transitions size="small" type="info">
          {{ result.total }}
        </el-tag>
      </h1>
      <div class="heading-actions">
        <el-button :loading="loading" aria-label="刷新用户列表" @click="reload">
          <RotateCw :size="16" />
          刷新
        </el-button>
        <el-button
          v-if="can('user.create')"
          type="primary"
          @click="openUser(null)"
        >
          <Plus :size="16" />
          新建用户
        </el-button>
      </div>
    </div>
    <div class="panel">
      <form class="filter-bar" @submit.prevent="search">
        <el-input
          v-model="query.keyword"
          aria-label="搜索用户"
          placeholder="搜索用户名、昵称、手机号或邮箱"
          clearable
          :maxlength="100"
          class="search-input"
        >
          <template #prefix><Search :size="16" /></template>
        </el-input>
        <el-select
          v-model="query.status"
          aria-label="用户状态"
          placeholder="全部状态"
          clearable
        >
          <el-option label="正常" value="active" />
          <el-option label="已停用" value="disabled" />
          <el-option label="已锁定" value="locked" />
        </el-select>
        <el-select
          v-model="query.roleCode"
          aria-label="筛选角色"
          placeholder="全部角色"
          clearable
        >
          <el-option
            v-for="role in roles"
            :key="role.id"
            :label="role.name"
            :value="role.code"
          />
        </el-select>
        <el-button type="primary" native-type="submit">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </form>
      <el-alert
        v-if="error"
        :title="error"
        type="error"
        :closable="false"
        show-icon
        class="panel-alert"
      >
        <el-button link type="primary" @click="reload">重新加载</el-button>
      </el-alert>
      <el-alert
        v-if="roleError"
        :title="'角色选项加载失败：' + roleError"
        type="warning"
        :closable="false"
        class="panel-alert"
      >
        <el-button link type="primary" @click="reloadRoles">重试</el-button>
      </el-alert>
      <el-table
        v-loading="loading"
        :data="result.items"
        row-key="id"
        class="data-table"
        :empty-text="error ? '暂时无法获取用户' : '没有找到符合条件的用户'"
      >
        <UserColumn label="用户" min-width="205">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar
                :size="32"
                shape="square"
                class="avatar"
                :class="{ 'avatar-admin': row.roleCode === 'super_admin' }"
              >
                {{ row.displayName.slice(0, 1) }}
              </el-avatar>
              <div>
                <strong>
                  {{ row.displayName }}
                  <span
                    v-if="row.id === session.data?.user.id"
                    class="self-badge"
                  >
                    你
                  </span>
                </strong>
                <span>@{{ row.username }}</span>
              </div>
            </div>
          </template>
        </UserColumn>
        <UserColumn label="联系方式" min-width="205">
          <template #default="{ row }">
            <div class="stack-cell">
              <span>{{ row.email || '未设置邮箱' }}</span>
              <small>{{ row.phone || '未设置手机号' }}</small>
            </div>
          </template>
        </UserColumn>
        <UserColumn label="角色" min-width="130">
          <template #default="{ row }">
            <el-tag
              disable-transitions
              size="small"
              :type="row.roleCode === 'super_admin' ? 'primary' : 'info'"
            >
              {{ row.roleName }}
            </el-tag>
          </template>
        </UserColumn>
        <UserColumn label="状态" width="100">
          <template #default="{ row }">
            <el-tag
              disable-transitions
              size="small"
              :type="
                row.status === 'active'
                  ? 'success'
                  : row.status === 'locked'
                    ? 'warning'
                    : 'info'
              "
            >
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </UserColumn>
        <UserColumn label="最近登录" min-width="155">
          <template #default="{ row }">
            <span class="date-text">{{ formatDate(row.lastLoginAt) }}</span>
          </template>
        </UserColumn>
        <UserColumn label="操作" fixed="right" min-width="215">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button
                v-if="can('user.update') && manageable(row)"
                link
                type="primary"
                @click="openUser(row)"
              >
                编辑
              </el-button>
              <el-button
                v-if="
                  can('user.assign-role') &&
                  manageable(row) &&
                  row.id !== session.data?.user.id
                "
                link
                type="primary"
                @click="openRole(row)"
              >
                分配角色
              </el-button>
              <template
                v-if="
                  can('user.status') &&
                  manageable(row) &&
                  row.id !== session.data?.user.id
                "
              >
                <el-button
                  link
                  :type="row.status === 'active' ? 'danger' : 'primary'"
                  :disabled="changing !== null"
                  @click="
                    changeStatus(
                      row,
                      row.status === 'active' ? 'disabled' : 'active',
                    )
                  "
                >
                  {{ row.status === 'active' ? '停用' : '启用' }}
                </el-button>
                <el-button
                  v-if="row.status === 'active'"
                  link
                  type="warning"
                  :disabled="changing !== null"
                  @click="changeStatus(row, 'locked')"
                >
                  锁定
                </el-button>
              </template>
              <span
                v-if="
                  !manageable(row) ||
                  (!can('user.update') &&
                    ((!can('user.status') && !can('user.assign-role')) ||
                      row.id === session.data?.user.id))
                "
                class="muted"
              >
                只读
              </span>
            </div>
          </template>
        </UserColumn>
      </el-table>
      <div class="table-footer">
        <span>共 {{ result.total }} 位用户</span>
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          :total="result.total"
          :page-sizes="[10, 20, 50]"
          layout="sizes, prev, pager, next"
          background
          @current-change="reload"
          @size-change="search"
        />
      </div>
    </div>
    <el-dialog
      v-model="dialog"
      :title="editing ? '编辑用户' : '新建用户'"
      width="510px"
      destroy-on-close
      :close-on-click-modal="false"
      :close-on-press-escape="!saving"
      :show-close="!saving"
      @closed="form.password = ''"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="save"
      >
        <el-form-item label="用户名" :prop="editing ? undefined : 'username'">
          <el-input
            v-model="form.username"
            :disabled="!!editing || saving"
            :maxlength="64"
            placeholder="例如：zhangsan"
            autocomplete="off"
          />
        </el-form-item>
        <el-form-item v-if="!editing" label="初始密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :maxlength="64"
            autocomplete="new-password"
            placeholder="请输入初始密码"
          />
        </el-form-item>
        <el-form-item label="昵称" prop="displayName">
          <el-input
            v-model="form.displayName"
            :maxlength="80"
            placeholder="用户显示名称"
          />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" :maxlength="32" placeholder="选填" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" :maxlength="255" placeholder="选填" />
        </el-form-item>
        <el-alert
          v-if="!editing"
          title="默认角色：普通用户"
          type="info"
          :closable="false"
        />
        <button type="submit" class="sr-only" tabindex="-1">保存</button>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">
          {{ editing ? '保存修改' : '创建用户' }}
        </el-button>
      </template>
    </el-dialog>
    <el-dialog
      v-model="roleDialog"
      title="分配角色"
      width="460px"
      :close-on-click-modal="false"
      :close-on-press-escape="!saving"
      :show-close="!saving"
    >
      <p class="dialog-intro">
        为
        <strong>{{ roleUser?.displayName }}</strong>
        选择角色。
      </p>
      <el-alert
        v-if="roleError"
        :title="roleError"
        type="error"
        :closable="false"
      />
      <el-select
        v-model="selectedRole"
        aria-label="分配的角色"
        placeholder="请选择角色"
        class="full-width"
      >
        <el-option
          v-for="role in roles"
          :key="role.id"
          :label="role.name"
          :value="role.code"
        />
      </el-select>
      <template #footer>
        <el-button :disabled="saving" @click="roleDialog = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="
            !!roleError || !roles.some((role) => role.code === selectedRole)
          "
          @click="assignRole"
        >
          保存角色
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>
