<template>
  <div class="server-management">
    <div class="page-header">
      <div>
        <h1 class="page-title">服务器管理</h1>
        <p class="page-subtitle">添加和管理用于部署的目标服务器</p>
      </div>
      <div class="header-actions">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索名称或IP..."
          class="search-input"
          clearable
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" size="large" @click="handleCreate" class="action-btn">
          <template #icon><el-icon><Plus /></el-icon></template>
          新增服务器
        </el-button>
      </div>
    </div>

    <div class="table-container">
      <el-table :data="serverList" v-loading="loading" style="width: 100%" :header-cell-style="{ background: '#F9FAFB', color: '#4B5563', fontWeight: 600 }">
        <el-table-column prop="name" label="名称" width="180">
          <template #default="scope">
            <span class="font-medium">{{ scope.row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="180">
          <template #default="scope">
            <span class="mono-text">{{ scope.row.ip }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="port" label="端口" width="100" />
        <el-table-column prop="username" label="用户名" width="150" />
        <el-table-column label="操作" min-width="200" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" plain class="soft-btn" @click="handleEdit(scope.row)">
              编辑
            </el-button>
            <el-button size="small" type="danger" plain class="soft-btn" @click="handleDelete(scope.row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    
    <div class="pagination-container">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :background="true"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>

    <!-- Create/Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑服务器' : '新增服务器'"
      width="600px"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        v-loading="submitting"
        element-loading-text="正在验证SSH连接并保存..."
      >
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入服务器名称" />
        </el-form-item>
        <el-form-item label="IP地址" prop="ip">
          <el-input v-model="form.ip" placeholder="请输入IP地址" />
        </el-form-item>
        <el-form-item label="端口" prop="port">
          <el-input-number v-model="form.port" :min="1" :max="65535" />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入SSH用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入SSH密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false" :disabled="submitting">取消</el-button>
          <el-button type="primary" @click="submitForm(formRef)" :loading="submitting">
            确定
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import request from '../utils/request'

interface Server {
  id?: number
  name: string
  ip: string
  port: number
  username: string
  password?: string
}

const loading = ref(false)
const submitting = ref(false)
const serverList = ref<Server[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()

// Pagination and search
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const searchKeyword = ref('')

const initialFormState: Server = {
  name: '',
  ip: '',
  port: 22,
  username: 'root',
  password: ''
}

const form = reactive<Server>({ ...initialFormState })

const rules = reactive<FormRules>({
  name: [{ required: true, message: '请输入服务器名称', trigger: 'blur' }],
  ip: [{ required: true, message: '请输入IP地址', trigger: 'blur' }],
  port: [{ required: true, message: '请输入端口', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
})

const fetchServers = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/servers', {
      params: {
        page: currentPage.value,
        size: pageSize.value,
        search: searchKeyword.value
      }
    })
    if (res.data && res.data.list !== undefined) {
      serverList.value = res.data.list
      total.value = res.data.total
    } else if (res.list !== undefined) {
      serverList.value = res.list
      total.value = res.total
    } else {
      serverList.value = res.data || res || []
      total.value = serverList.value.length
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取服务器列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchServers()
}

const handleSizeChange = (val: number) => {
  pageSize.value = val
  fetchServers()
}

const handleCurrentChange = (val: number) => {
  currentPage.value = val
  fetchServers()
}

const handleCreate = () => {
  isEdit.value = false
  Object.assign(form, initialFormState)
  // For create, password is required
  rules.password = [{ required: true, message: '请输入密码', trigger: 'blur' }]
  dialogVisible.value = true
  // Reset form validation after dialog opens
  setTimeout(() => formRef.value?.clearValidate(), 0)
}

const handleEdit = (row: Server) => {
  isEdit.value = true
  Object.assign(form, row)
  // For edit, password is not required (only if changing)
  rules.password = []
  form.password = '' // Clear password field, only send if user types a new one
  dialogVisible.value = true
  setTimeout(() => formRef.value?.clearValidate(), 0)
}

const handleDelete = (row: Server) => {
  ElMessageBox.confirm(`确认删除服务器 ${row.name} 吗?`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await request.delete(`/servers/${row.id}`)
      ElMessage.success('删除成功')
      fetchServers()
    } catch (error: any) {
      ElMessage.error(error.message || '删除失败')
    }
  }).catch(() => {})
}

const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return
  await formEl.validate(async (valid) => {
    if (valid) {
      submitting.value = true
      try {
        if (isEdit.value) {
          const payload = { ...form }
          if (!payload.password) delete payload.password
          await request.put(`/servers/${form.id}`, payload)
          ElMessage.success('更新成功')
        } else {
          await request.post('/servers', form)
          ElMessage.success('创建成功')
        }
        dialogVisible.value = false
        fetchServers()
      } catch (error: any) {
        // 错误已由 request 拦截器统一展示
        console.error('保存失败:', error)
      } finally {
        submitting.value = false
      }
    }
  })
}

onMounted(() => {
  fetchServers()
})
</script>

<style scoped>
.server-management {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0;
}

.page-title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: #111827;
  letter-spacing: -0.5px;
}

.page-subtitle {
  margin: 8px 0 0 0;
  font-size: 14px;
  color: #6B7280;
}

.action-btn {
  border-radius: 8px;
  padding: 10px 20px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.search-input {
  width: 280px;
}

.search-input :deep(.el-input__wrapper) {
  border-radius: 8px;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
}

.table-container {
  background: white;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #E5E7EB;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06);
}

.pagination-container {
  padding: 16px 0;
  display: flex;
  justify-content: flex-end;
}

.font-medium {
  font-weight: 600;
  color: #374151;
}

.mono-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  background-color: #F3F4F6;
  padding: 2px 6px;
  border-radius: 4px;
  color: #4B5563;
  font-size: 13px;
}

.soft-btn {
  border-radius: 6px;
  font-weight: 500;
}

:deep(.el-table) {
  --el-table-border-color: #F3F4F6;
  --el-table-header-bg-color: #F9FAFB;
}

:deep(.el-table th.el-table__cell) {
  font-weight: 600;
  color: #4B5563;
  background-color: #F9FAFB !important;
}

:deep(.el-table td.el-table__cell) {
  padding: 12px 0;
}
</style>
