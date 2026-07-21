<template>
  <div class="project-management">
    <div class="page-header">
      <div>
        <h1 class="page-title">项目管理</h1>
        <p class="page-subtitle">创建和配置您的部署项目与环境</p>
      </div>
      <div class="header-actions">
        <el-input
          v-model="searchQuery"
          placeholder="搜索项目..."
          class="search-input"
          clearable
          @input="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" size="large" @click="handleCreate" class="action-btn">
          <template #icon><el-icon><Plus /></el-icon></template>
          新增项目
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="content-area">
      <el-row :gutter="24" v-if="projectList.length > 0">
        <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="(project, index) in projectList" :key="project.id" style="margin-bottom: 24px;">
          <el-card class="project-card" :style="{ '--card-theme-color': getRandomColor(index) }">
            <div class="card-header-line"></div>
            <template #header>
              <div class="project-card-header">
                <div class="header-content">
                  <div class="title-index">{{ String(index + 1).padStart(2, '0') }}</div>
                  <span class="project-name" :title="project.name">{{ project.name }}</span>
                </div>
                <el-tag size="small" type="info" class="rounded-tag">GitLab ID: {{ project.gitlabProjectId }}</el-tag>
              </div>
            </template>
            <div class="project-actions">
              <el-button size="default" type="primary" plain class="full-width-btn" @click="handleManageProfiles(project)">
                <el-icon><Setting /></el-icon> 环境配置
              </el-button>
              <div class="action-group">
                <el-button size="small" class="flex-1" @click="handleEdit(project)"><el-icon><Edit /></el-icon> 编辑</el-button>
                <el-button size="small" type="danger" plain class="flex-1" @click="handleDelete(project)"><el-icon><Delete /></el-icon> 删除</el-button>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-else description="暂无项目数据"></el-empty>

      <div class="pagination-container" v-if="total > 0">
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
    </div>

    <!-- Create/Edit Project Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑项目' : '新增项目'"
      width="600px"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
      >
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="form.name" placeholder="自定义项目名称" />
        </el-form-item>
        <el-form-item label="Gitlab项目" prop="gitlabProjectId">
          <el-select
            v-model="form.gitlabProjectId"
            placeholder="请选择Gitlab项目"
            filterable
            remote
            :remote-method="searchGitlabProjects"
            :loading="gitlabLoading"
            style="width: 100%"
          >
            <el-option
              v-for="item in gitlabProjects"
              :key="item.id"
              :label="item.name_with_namespace"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-divider>默认部署配置</el-divider>
        
        <el-form-item label="构建脚本" prop="buildScript">
          <el-input v-model="form.buildScript" type="textarea" :rows="3" placeholder="例如: npm install && npm run build" />
          <div class="form-tip">如需切换 Node/JDK/Maven 版本，在脚本前添加 <code>. /usr/local/nvm/nvm.sh && nvm use 22 && source /usr/local/sdkman/bin/sdkman-init.sh && sdk use java 11.0.12-tem && sdk use maven 3.8.8 &&</code></div>
        </el-form-item>
        <el-form-item label="构建后产物" prop="buildOutputDir">
          <el-input v-model="form.buildOutputDir" placeholder="例如: dist 或者 target/app.jar (构建成功后供下载)" />
        </el-form-item>
        <el-form-item label="同步到部署目录" prop="syncToDeployDir">
          <el-switch v-model="form.syncToDeployDir" active-text="开启" inactive-text="关闭" />
        </el-form-item>
        <el-form-item label="部署目录" prop="deployDir" v-if="form.syncToDeployDir !== false">
          <el-input v-model="form.deployDir" placeholder="例如: /www/wwwroot/my-app" />
        </el-form-item>
        <el-form-item label="前置脚本" prop="preScript">
          <el-input v-model="form.preScript" type="textarea" :rows="3" placeholder="部署前执行" />
        </el-form-item>
        <el-form-item label="后置脚本" prop="postScript">
          <el-input v-model="form.postScript" type="textarea" :rows="3" placeholder="部署后执行(如: pm2 restart app)" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm(formRef)">
            确定
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- Manage Profiles Dialog -->
    <el-dialog
      v-model="profileDialogVisible"
      :title="`环境配置 - ${currentProject?.name}`"
      width="850px"
      class="custom-dialog"
      destroy-on-close
    >
      <div class="dialog-content">
        <div class="dialog-actions">
          <el-button type="primary" @click="handleCreateProfile" class="soft-btn">
            <template #icon><el-icon><Plus /></el-icon></template>
            新增环境
          </el-button>
        </div>
        
        <el-table 
          :data="profileList" 
          v-loading="profileLoading" 
          border 
          class="custom-table"
          :header-cell-style="{ background: '#F9FAFB', color: '#4B5563', fontWeight: 600 }"
        >
          <el-table-column prop="name" label="环境名称" width="180">
            <template #default="scope">
              <span class="profile-name-tag">{{ scope.row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="serverId" label="关联服务器" min-width="200">
            <template #default="scope">
              <div class="server-info-cell" v-if="getServerName(scope.row.serverId)">
                <el-icon><Monitor /></el-icon>
                <span>{{ getServerName(scope.row.serverId) }}</span>
              </div>
              <span v-else class="text-gray-400">未关联</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" align="center">
            <template #default="scope">
              <div class="table-actions">
                <el-button size="small" @click="handleEditProfile(scope.row)" class="soft-btn">
                  <el-icon><Edit /></el-icon> 编辑
                </el-button>
                <el-button size="small" type="danger" plain @click="handleDeleteProfile(scope.row)" class="soft-btn">
                  <el-icon><Delete /></el-icon> 删除
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <!-- Create/Edit Profile Dialog -->
    <el-dialog
      v-model="profileFormDialogVisible"
      :title="isProfileEdit ? '编辑环境' : '新增环境'"
      width="700px"
      class="custom-dialog"
    >
      <div class="dialog-form-container">
        <el-form
          ref="profileFormRef"
          :model="profileForm"
          :rules="profileRules"
          label-position="top"
          class="modern-form"
        >
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="环境名称" prop="name">
                <el-input v-model="profileForm.name" placeholder="例如: 生产环境, 测试环境" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="目标服务器" prop="serverId">
                <el-select v-model="profileForm.serverId" placeholder="请选择关联服务器(可选)" style="width: 100%" clearable>
                  <el-option
                    v-for="server in serverList"
                    :key="server.id"
                    :label="`${server.name} (${server.ip})`"
                    :value="server.id"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <div class="config-section">
            <div class="section-header">
              <el-icon><Setting /></el-icon>
              <span>部署脚本配置</span>
            </div>
            
            <el-row :gutter="20">
              <el-col :span="24">
                <el-form-item label="构建脚本" prop="buildScript">
                  <el-input 
                    v-model="profileForm.buildScript" 
                    type="textarea" 
                    :rows="3" 
                    placeholder="例如: npm install && npm run build" 
                    class="code-input"
                  />
                  <div class="form-tip">如需切换 Node/JDK/Maven 版本，在脚本前添加 <code>. /usr/local/nvm/nvm.sh && nvm use 22 && source /usr/local/sdkman/bin/sdkman-init.sh && sdk use java 11.0.12-tem && sdk use maven 3.8.8 &&</code></div>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="构建后产物" prop="buildOutputDir">
                  <el-input 
                    v-model="profileForm.buildOutputDir" 
                    placeholder="例如: dist 或者 target/app.jar (覆盖项目默认配置)" 
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="同步到部署目录" prop="syncToDeployDir">
                  <el-switch v-model="profileForm.syncToDeployDir" active-text="开启" inactive-text="关闭" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="部署目录" prop="deployDir" v-if="profileForm.syncToDeployDir !== false">
                  <el-input 
                    v-model="profileForm.deployDir" 
                    placeholder="例如: /www/wwwroot/my-app" 
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="前置脚本" prop="preScript">
                  <el-input 
                    v-model="profileForm.preScript" 
                    type="textarea" 
                    :rows="3" 
                    placeholder="部署前执行" 
                    class="code-input"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="后置脚本" prop="postScript">
                  <el-input 
                    v-model="profileForm.postScript" 
                    type="textarea" 
                    :rows="3" 
                    placeholder="部署后执行(如: pm2 restart app)" 
                    class="code-input"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </div>
        </el-form>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="profileFormDialogVisible = false" class="soft-btn">取消</el-button>
          <el-button type="primary" @click="submitProfileForm(profileFormRef)" class="soft-btn">
            确定
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Plus, Setting, Edit, Delete, Search, Monitor } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import request from '../utils/request'
import { fetchGitlabProjects, type GitlabProject } from '../api/gitlab'

interface Project {
  id?: number
  name: string
  gitlabProjectId: number | null
  buildScript?: string
  buildOutputDir?: string
  deployDir?: string
  syncToDeployDir?: boolean
  preScript?: string
  postScript?: string
}

interface DeployProfile {
  id?: number
  projectId: number
  serverId: number | null
  name: string
  buildScript?: string
  buildOutputDir?: string
  deployDir?: string
  syncToDeployDir?: boolean
  preScript?: string
  postScript?: string
}

const loading = ref(false)
const projectList = ref<Project[]>([])
const searchQuery = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
let searchTimeout: any = null

const handleSearch = () => {
  if (searchTimeout) clearTimeout(searchTimeout)
  searchTimeout = setTimeout(() => {
    fetchProjects()
  }, 300)
}

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()

const gitlabLoading = ref(false)
const gitlabProjects = ref<GitlabProject[]>([])

const initialFormState: Project = {
  name: '',
  gitlabProjectId: null,
  buildScript: '',
  buildOutputDir: '',
  deployDir: '',
  syncToDeployDir: true,
  preScript: '',
  postScript: ''
}

const form = reactive<Project>({ ...initialFormState })

const rules = reactive<FormRules>({
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  gitlabProjectId: [{ required: true, message: '请选择Gitlab项目', trigger: 'change' }]
})

// Profile related state
const profileDialogVisible = ref(false)
const currentProject = ref<Project | null>(null)
const profileList = ref<DeployProfile[]>([])
const profileLoading = ref(false)

const profileFormDialogVisible = ref(false)
const isProfileEdit = ref(false)
const profileFormRef = ref<FormInstance>()
const serverList = ref<any[]>([])

const initialProfileFormState: DeployProfile = {
  projectId: 0,
  serverId: null,
  name: '',
  buildScript: '',
  buildOutputDir: '',
  deployDir: '',
  syncToDeployDir: true,
  preScript: '',
  postScript: ''
}

const profileForm = reactive<DeployProfile>({ ...initialProfileFormState })

const profileRules = reactive<FormRules>({
  name: [{ required: true, message: '请输入环境名称', trigger: 'blur' }]
})

const themeColors = [
  '#4f46e5', // Indigo
  '#10b981', // Emerald
  '#f59e0b', // Amber
  '#ef4444', // Red
  '#8b5cf6', // Violet
  '#06b6d4', // Cyan
  '#ec4899', // Pink
  '#f97316'  // Orange
]

const getRandomColor = (index: number) => {
  return themeColors[index % themeColors.length]
}

const fetchProjects = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value,
      ...(searchQuery.value?.trim() ? { search: searchQuery.value.trim() } : {})
    }
    const res: any = await request.get('/projects', { params })
    
    const data = res.data || res
    // Support both paginated and non-paginated responses for backward compatibility
    if (data && typeof data === 'object' && 'list' in data) {
      projectList.value = data.list
      total.value = data.total || 0
    } else {
      projectList.value = data || []
      total.value = projectList.value.length
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取项目列表失败')
  } finally {
    loading.value = false
  }
}

const handleSizeChange = (val: number) => {
  pageSize.value = val
  currentPage.value = 1
  fetchProjects()
}

const handleCurrentChange = (val: number) => {
  currentPage.value = val
  fetchProjects()
}

const searchGitlabProjects = async (query: string) => {
  gitlabLoading.value = true
  try {
    const res: any = await fetchGitlabProjects(query)
    // The backend now returns { list: [...], total: ... }
    if (res && res.list) {
      gitlabProjects.value = res.list
    } else {
      // Ensure we always have an array
      gitlabProjects.value = Array.isArray(res) ? res : (res.data || [])
    }
  } catch (error: any) {
    ElMessage.error('获取Gitlab项目失败')
  } finally {
    gitlabLoading.value = false
  }
}

const handleCreate = () => {
  isEdit.value = false
  Object.assign(form, initialFormState)
  searchGitlabProjects('')
  dialogVisible.value = true
  setTimeout(() => formRef.value?.clearValidate(), 0)
}

const handleEdit = (row: Project) => {
  isEdit.value = true
  Object.assign(form, row)
  searchGitlabProjects('')
  dialogVisible.value = true
  setTimeout(() => formRef.value?.clearValidate(), 0)
}

const handleDelete = (row: Project) => {
  ElMessageBox.confirm(`确认删除项目 ${row.name} 吗?`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await request.delete(`/projects/${row.id}`)
      ElMessage.success('删除成功')
      fetchProjects()
    } catch (error: any) {
      ElMessage.error(error.message || '删除失败')
    }
  }).catch(() => {})
}

const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return
  await formEl.validate(async (valid) => {
    if (valid) {
      try {
        if (isEdit.value) {
          await request.put(`/projects/${form.id}`, form)
          ElMessage.success('更新成功')
        } else {
          await request.post('/projects', form)
          ElMessage.success('创建成功')
        }
        dialogVisible.value = false
        fetchProjects()
      } catch (error: any) {
        ElMessage.error(error.message || (isEdit.value ? '更新失败' : '创建失败'))
      }
    }
  })
}

// Profile Methods
const loadServers = async () => {
  try {
    const res: any = await request.get('/servers')
    serverList.value = res.data || res || []
  } catch (error: any) {
    console.error('Failed to load servers')
  }
}

const getServerName = (serverId: number | null) => {
  if (!serverId) return ''
  const server = serverList.value.find(s => s.id === serverId)
  return server ? `${server.name} (${server.ip})` : `ID: ${serverId}`
}

const fetchProfiles = async (projectId: number) => {
  profileLoading.value = true
  try {
    const res: any = await request.get(`/profiles?projectId=${projectId}`)
    profileList.value = res.data || res || []
  } catch (error: any) {
    ElMessage.error('获取环境列表失败')
  } finally {
    profileLoading.value = false
  }
}

const handleManageProfiles = (row: Project) => {
  currentProject.value = row
  profileDialogVisible.value = true
  fetchProfiles(row.id!)
  if (serverList.value.length === 0) {
    loadServers()
  }
}

const handleCreateProfile = () => {
  isProfileEdit.value = false
  Object.assign(profileForm, initialProfileFormState)
  profileForm.projectId = currentProject.value!.id!
  profileFormDialogVisible.value = true
  setTimeout(() => profileFormRef.value?.clearValidate(), 0)
}

const handleEditProfile = (row: DeployProfile) => {
  isProfileEdit.value = true
  Object.assign(profileForm, row)
  profileFormDialogVisible.value = true
  setTimeout(() => profileFormRef.value?.clearValidate(), 0)
}

const handleDeleteProfile = (row: DeployProfile) => {
  ElMessageBox.confirm(`确认删除环境 ${row.name} 吗?`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await request.delete(`/profiles/${row.id}`)
      ElMessage.success('删除成功')
      fetchProfiles(currentProject.value!.id!)
    } catch (error: any) {
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

const submitProfileForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return
  await formEl.validate(async (valid) => {
    if (valid) {
      try {
        if (isProfileEdit.value) {
          await request.put(`/profiles/${profileForm.id}`, profileForm)
          ElMessage.success('更新成功')
        } else {
          await request.post('/profiles', profileForm)
          ElMessage.success('创建成功')
        }
        profileFormDialogVisible.value = false
        fetchProfiles(currentProject.value!.id!)
      } catch (error: any) {
        ElMessage.error(isProfileEdit.value ? '更新失败' : '创建失败')
      }
    }
  })
}

onMounted(() => {
  fetchProjects()
})
</script>

<style scoped>
.project-management {
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

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.search-input {
  width: 300px;
}

:deep(.search-input .el-input__wrapper) {
  border-radius: 8px;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
}

.action-btn {
  border-radius: 8px;
  padding: 10px 20px;
}

/* Custom Dialog & Form Styles */
.custom-dialog :deep(.el-dialog) {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
}

.custom-dialog :deep(.el-dialog__header) {
  margin: 0;
  padding: 20px 24px;
  border-bottom: 1px solid #F3F4F6;
}

.custom-dialog :deep(.el-dialog__title) {
  font-weight: 700;
  color: #111827;
}

.custom-dialog :deep(.el-dialog__body) {
  padding: 24px;
}

.dialog-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
}

.soft-btn {
  border-radius: 8px;
  font-weight: 500;
  transition: all 0.2s;
}

.soft-btn:hover {
  transform: translateY(-1px);
}

.custom-table {
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #F3F4F6;
}

.profile-name-tag {
  font-weight: 600;
  color: #4F46E5;
  background: #EEF2FF;
  padding: 4px 10px;
  border-radius: 6px;
}

.server-info-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #4B5563;
}

.table-actions {
  display: flex;
  justify-content: center;
  gap: 8px;
}

.dialog-form-container {
  max-height: 60vh;
  overflow-y: auto;
  padding-right: 8px;
}

.modern-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: #374151;
  margin-bottom: 8px;
}

.modern-form :deep(.el-input__wrapper),
.modern-form :deep(.el-textarea__inner) {
  border-radius: 8px;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
}

.config-section {
  margin-top: 24px;
  padding: 20px;
  background: #F9FAFB;
  border-radius: 12px;
  border: 1px solid #F3F4F6;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  color: #111827;
  margin-bottom: 20px;
  font-size: 16px;
}

.code-input :deep(.el-textarea__inner) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  background-color: #ffffff;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 12px;
}

.project-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}

.card-header-line {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background-color: var(--card-theme-color, var(--el-color-primary));
  z-index: 2;
}

:deep(.el-card__header) {
  padding: 20px 24px;
  border-bottom: 1px solid rgba(243, 244, 246, 0.8);
  background: linear-gradient(to right, rgba(255, 255, 255, 0.9), rgba(249, 250, 251, 0.5));
  position: relative;
}

:deep(.el-card__body) {
  padding: 24px;
  flex: 1;
  display: flex;
  flex-direction: column;
  background-color: #ffffff;
}

.project-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-content {
  display: flex;
  align-items: center;
  gap: 12px;
}

.title-index {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 14px;
  font-weight: 700;
  color: var(--card-theme-color, var(--el-color-primary));
  background: color-mix(in srgb, var(--card-theme-color, var(--el-color-primary)) 15%, transparent);
  padding: 6px 10px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.project-name {
  font-weight: 700;
  font-size: 17px;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 140px;
  letter-spacing: -0.3px;
}

.rounded-tag {
  border-radius: 12px;
  padding: 4px 12px;
  font-weight: 600;
  border: none;
  background-color: #F3F4F6;
  color: #4B5563;
}

.project-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: auto;
}

.action-group {
  display: flex;
  gap: 12px;
}

.full-width-btn {
  width: 100%;
}

.form-tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
  margin-top: 4px;
}
.form-tip code {
  background: var(--el-fill-color-light);
  padding: 0 4px;
  border-radius: 3px;
  font-size: 12px;
}

.flex-1 {
  flex: 1;
}

.pagination-container {
  padding: 16px 0;
  display: flex;
  justify-content: flex-end;
}
</style>
