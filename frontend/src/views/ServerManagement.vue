<template>
  <div class="server-management">
    <div class="page-header">
      <div>
        <h1 class="page-title">
          <el-icon class="title-icon"><Monitor /></el-icon>
          服务器管理
        </h1>
        <p class="page-subtitle">配置与维护用于接收构建产物和运行服务的 SSH 目标服务器集群</p>
      </div>
      <div class="header-actions">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索服务器名称或 IP..."
          class="search-input"
          clearable
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="handleCreate" class="action-btn">
          <el-icon><Plus /></el-icon> 新增服务器
        </el-button>
      </div>
    </div>

    <!-- Table Container -->
    <div class="table-container">
      <el-table :data="serverList" v-loading="loading" style="width: 100%" class="server-table">
        <el-table-column prop="id" label="ID" width="70" align="center">
          <template #default="scope">
            <span class="mono-id">#{{ scope.row.id }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="name" label="服务器名称" min-width="160">
          <template #default="scope">
            <div class="server-name-cell">
              <span class="server-status-dot"></span>
              <span class="server-name">{{ scope.row.name }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="ip" label="IP 地址 / 端口" min-width="200">
          <template #default="scope">
            <div class="ip-port-cell">
              <span class="mono-ip" @click="copyIp(scope.row.ip)" title="点击复制 IP">
                {{ scope.row.ip }}
              </span>
              <span class="port-tag font-mono">:{{ scope.row.port || 22 }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="username" label="SSH 用户名" width="140">
          <template #default="scope">
            <span class="user-tag font-mono">{{ scope.row.username }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="160" fixed="right" align="right">
          <template #default="scope">
            <el-button size="small" type="primary" link @click="handleEdit(scope.row)">
              编辑
            </el-button>
            <el-button size="small" type="danger" link @click="handleDelete(scope.row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    
    <div class="pagination-wrapper" v-if="total > 0">
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
      :title="isEdit ? '编辑服务器' : '新增服务器节点'"
      width="560px"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        v-loading="submitting"
        element-loading-text="正在验证 SSH 连通性并保存..."
        class="server-form"
      >
        <el-form-item label="服务器名称" prop="name">
          <el-input v-model="form.name" placeholder="例如: 生产主服务器 (Prod-Node-01)" />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="16">
            <el-form-item label="IP 地址 / 域名" prop="ip">
              <el-input v-model="form.ip" placeholder="例如: 192.168.1.100" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="SSH 端口" prop="port">
              <el-input-number v-model="form.port" :min="1" :max="65535" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="SSH 用户名" prop="username">
          <el-input v-model="form.username" placeholder="例如: root 或 deploy" />
        </el-form-item>

        <el-form-item label="SSH 密码 / 凭据" prop="password">
          <el-input 
            v-model="form.password" 
            type="password" 
            show-password 
            :placeholder="isEdit ? '留空表示不修改原密码' : '请输入 SSH 登录密码'" 
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false" :disabled="submitting">取消</el-button>
          <el-button type="primary" @click="submitForm(formRef)" :loading="submitting">
            测试连接并保存
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Plus, Search, Monitor } from '@element-plus/icons-vue'
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
  delete form.id
  rules.password = [{ required: true, message: '请输入密码', trigger: 'blur' }]
  dialogVisible.value = true
  setTimeout(() => formRef.value?.clearValidate(), 0)
}

const handleEdit = (row: Server) => {
  isEdit.value = true
  Object.assign(form, row)
  rules.password = []
  form.password = ''
  dialogVisible.value = true
  setTimeout(() => formRef.value?.clearValidate(), 0)
}

const handleDelete = (row: Server) => {
  ElMessageBox.confirm(`确认删除服务器 ${row.name} 吗?`, '删除警告', {
    confirmButtonText: '确定删除',
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

const copyIp = (ip: string) => {
  navigator.clipboard.writeText(ip)
  ElMessage.success(`IP 地址 ${ip} 已复制`)
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
  gap: 20px;
}

.title-icon {
  color: var(--el-color-primary);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-input {
  width: 280px;
}

.table-container {
  background: #ffffff;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  box-shadow: var(--shadow-xs);
}

.mono-id {
  font-family: var(--font-mono);
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
}

.server-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.server-status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background-color: #10b981;
}

.server-name {
  font-weight: 600;
  color: #0f172a;
  font-size: 13.5px;
}

.ip-port-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}

.mono-ip {
  font-family: var(--font-mono);
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
  color: #334155;
  font-size: 12.5px;
  cursor: pointer;
  border: 1px solid #e2e8f0;
  transition: all 0.2s ease;
}

.mono-ip:hover {
  background: #e2e8f0;
  color: var(--el-color-primary);
}

.port-tag {
  color: #64748b;
  font-size: 12.5px;
}

.user-tag {
  background: #f8fafc;
  border: 1px solid #f1f5f9;
  color: #475569;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}

.server-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: #334155;
  margin-bottom: 4px;
}
</style>
