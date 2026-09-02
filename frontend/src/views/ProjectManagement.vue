<template>
  <div class="project-management">
    <div class="page-header">
      <div>
        <h1 class="page-title">
          <el-icon class="title-icon"><Folder /></el-icon>
          项目管理
        </h1>
        <p class="page-subtitle">配置部署工程、关联 GitLab 仓库并定制多套发布环境</p>
      </div>
      <div class="header-actions">
        <el-input
          v-model="searchQuery"
          placeholder="搜索项目名称..."
          class="search-input"
          clearable
          @input="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="handleCreate" class="action-btn">
          <el-icon><Plus /></el-icon> 新增项目
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="content-area">
      <el-row :gutter="20" v-if="projectList.length > 0">
        <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="(project, index) in projectList" :key="project.id" class="card-col">
          <div class="project-item-card">
            <div class="card-top-bar" :style="{ backgroundColor: getBrandColor(index) }"></div>
            
            <div class="card-main">
              <div class="card-head">
                <div class="project-title-group">
                  <span class="project-idx" :style="{ color: getBrandColor(index), background: getBrandBg(index) }">
                    {{ String(index + 1).padStart(2, '0') }}
                  </span>
                  <span class="project-name" :title="project.name">{{ project.name }}</span>
                </div>
                <span class="gitlab-id-badge" v-if="project.gitlabProjectId">
                  GitLab #{{ project.gitlabProjectId }}
                </span>
              </div>

              <div class="card-meta">
                <div class="meta-row">
                  <span class="meta-label">产物路径</span>
                  <span class="meta-val font-mono">{{ project.buildOutputDir || '未指定' }}</span>
                </div>
                <div class="meta-row">
                  <span class="meta-label">部署目录</span>
                  <span class="meta-val font-mono text-ellipsis" :title="project.deployDir">{{ project.deployDir || '未配置' }}</span>
                </div>
              </div>

              <div class="card-actions">
                <el-button type="primary" plain class="primary-card-btn" @click="handleManageProfiles(project)">
                  <el-icon><Setting /></el-icon> 环境配置
                </el-button>
                <div class="action-sub-group">
                  <el-button size="small" plain class="sub-btn" @click="handleEdit(project)">
                    <el-icon><Edit /></el-icon> 编辑
                  </el-button>
                  <el-button size="small" type="danger" plain class="sub-btn" @click="handleDelete(project)">
                    <el-icon><Delete /></el-icon> 删除
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
      <el-empty v-else description="暂无项目数据，请点击上方按钮新建项目" />

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
    </div>

    <!-- Create/Edit Project Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑项目' : '新增项目'"
      width="640px"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="custom-form"
      >
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="项目名称" prop="name">
              <el-input v-model="form.name" placeholder="自定义项目标识/名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联 GitLab 仓库" prop="gitlabProjectId">
              <el-select
                v-model="form.gitlabProjectId"
                placeholder="搜索并选择 GitLab 仓库"
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
          </el-col>
        </el-row>

        <div class="form-section-title">
          <el-icon><Setting /></el-icon> 默认部署参数
        </div>
        
        <el-form-item label="构建脚本 (Build Script)" prop="buildScript">
          <el-input 
            v-model="form.buildScript" 
            type="textarea" 
            :rows="3" 
            placeholder="例如: npm install && npm run build" 
            class="code-textarea"
          />
          <div class="form-tip">
            如需切换 Node/JDK/Maven 版本，在脚本前添加 <code>. /usr/local/nvm/nvm.sh && nvm use 22 && sdk use java 17.0.8-tem &&</code>
          </div>
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="14">
            <el-form-item label="构建后产物路径" prop="buildOutputDir">
              <el-input v-model="form.buildOutputDir" placeholder="例如: dist 或 target/app.jar" />
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="同步到部署目录" prop="syncToDeployDir">
              <el-switch v-model="form.syncToDeployDir" active-text="开启同步" inactive-text="仅构建" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="服务器部署目录" prop="deployDir" v-if="form.syncToDeployDir !== false">
          <el-input v-model="form.deployDir" placeholder="例如: /www/wwwroot/my-app" />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="前置脚本 (Pre-deploy)" prop="preScript">
              <el-input v-model="form.preScript" type="textarea" :rows="2" placeholder="部署同步前在目标服务器执行" class="code-textarea" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="后置脚本 (Post-deploy)" prop="postScript">
              <el-input v-model="form.postScript" type="textarea" :rows="2" placeholder="部署同步后在目标服务器执行 (如: pm2 restart app)" class="code-textarea" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm(formRef)">确定保存</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- Manage Profiles Dialog -->
    <el-dialog
      v-model="profileDialogVisible"
      :title="`环境配置 - ${currentProject?.name}`"
      width="820px"
      destroy-on-close
    >
      <div class="dialog-content">
        <div class="dialog-actions">
          <el-button type="primary" size="small" @click="handleCreateProfile">
            <el-icon><Plus /></el-icon> 新增环境
          </el-button>
        </div>
        
        <el-table 
          :data="profileList" 
          v-loading="profileLoading" 
          class="custom-table"
        >
          <el-table-column prop="name" label="环境名称" width="160">
            <template #default="scope">
              <span class="profile-name-tag">{{ scope.row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="serverId" label="关联目标服务器" min-width="200">
            <template #default="scope">
              <div class="server-info-cell" v-if="getServerName(scope.row.serverId)">
                <el-icon><Monitor /></el-icon>
                <span>{{ getServerName(scope.row.serverId) }}</span>
              </div>
              <span v-else class="text-muted">未绑定服务器 (仅本地构建)</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" align="right">
            <template #default="scope">
              <el-button size="small" link type="primary" @click="handleEditProfile(scope.row)">
                编辑
              </el-button>
              <el-button size="small" link type="danger" @click="handleDeleteProfile(scope.row)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <!-- Create/Edit Profile Dialog -->
    <el-dialog
      v-model="profileFormDialogVisible"
      :title="isProfileEdit ? '编辑环境配置' : '新增环境配置'"
      width="680px"
    >
      <el-form
        ref="profileFormRef"
        :model="profileForm"
        :rules="profileRules"
        label-position="top"
        class="custom-form"
      >
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="环境名称" prop="name">
              <el-input v-model="profileForm.name" placeholder="例如: 生产环境, 测试环境" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="目标服务器" prop="serverId">
              <el-select v-model="profileForm.serverId" placeholder="请选择目标服务器(可选)" style="width: 100%" clearable>
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

        <div class="form-section-title">
          <el-icon><Setting /></el-icon> 环境覆写参数 (留空则继承项目默认配置)
        </div>
        
        <el-form-item label="构建脚本" prop="buildScript">
          <el-input 
            v-model="profileForm.buildScript" 
            type="textarea" 
            :rows="3" 
            placeholder="例如: npm install && npm run build:prod" 
            class="code-textarea"
          />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="14">
            <el-form-item label="构建后产物" prop="buildOutputDir">
              <el-input v-model="profileForm.buildOutputDir" placeholder="例如: dist (覆盖默认)" />
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="同步到部署目录" prop="syncToDeployDir">
              <el-switch v-model="profileForm.syncToDeployDir" active-text="开启" inactive-text="关闭" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="部署目录" prop="deployDir" v-if="profileForm.syncToDeployDir !== false">
          <el-input v-model="profileForm.deployDir" placeholder="例如: /www/wwwroot/prod-app" />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="前置脚本" prop="preScript">
              <el-input v-model="profileForm.preScript" type="textarea" :rows="2" placeholder="部署前在服务器执行" class="code-textarea" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="后置脚本" prop="postScript">
              <el-input v-model="profileForm.postScript" type="textarea" :rows="2" placeholder="部署后执行(如: pm2 restart app)" class="code-textarea" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="profileFormDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitProfileForm(profileFormRef)">确定保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Plus, Setting, Edit, Delete, Search, Monitor, Folder } from '@element-plus/icons-vue'
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
const pageSize = ref(12)
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

const themeColors = ['#4f46e5', '#10b981', '#f59e0b', '#06b6d4', '#8b5cf6', '#ec4899', '#f97316']
const themeBgs = ['#eef2ff', '#ecfdf5', '#fffbeb', '#ecfeff', '#f5f3ff', '#fdf2f8', '#fff7ed']

const getBrandColor = (index: number) => themeColors[index % themeColors.length]
const getBrandBg = (index: number) => themeBgs[index % themeBgs.length]

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
    if (res && res.list) {
      gitlabProjects.value = res.list
    } else {
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
  delete form.id
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
    confirmButtonText: '确定删除',
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
  delete profileForm.id
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
    confirmButtonText: '确定删除',
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

.card-col {
  margin-bottom: 20px;
}

/* Project Item Card */
.project-item-card {
  background: #ffffff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  box-shadow: var(--shadow-xs);
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  position: relative;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.project-item-card:hover {
  border-color: #cbd5e1;
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.card-top-bar {
  height: 3px;
  width: 100%;
}

.card-main {
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.project-title-group {
  display: flex;
  align-items: center;
  gap: 10px;
  overflow: hidden;
}

.project-idx {
  font-family: var(--font-mono);
  font-size: 11.5px;
  font-weight: 700;
  padding: 3px 6px;
  border-radius: 6px;
  flex-shrink: 0;
}

.project-name {
  font-weight: 700;
  font-size: 15px;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gitlab-id-badge {
  font-size: 11px;
  color: #64748b;
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: var(--font-mono);
}

.card-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: #f8fafc;
  border: 1px solid #f1f5f9;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 18px;
}

.meta-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12.5px;
}

.meta-label {
  color: #64748b;
  font-size: 12px;
}

.meta-val {
  color: #334155;
  font-weight: 500;
}

.text-ellipsis {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: auto;
}

.primary-card-btn {
  width: 100%;
}

.action-sub-group {
  display: flex;
  gap: 8px;
}

.sub-btn {
  flex: 1;
}

/* Forms & Dialog */
.custom-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: #334155;
  margin-bottom: 4px;
}

.form-section-title {
  font-size: 13.5px;
  font-weight: 700;
  color: #0f172a;
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 16px 0 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f1f5f9;
}

.code-textarea :deep(.el-textarea__inner) {
  font-family: var(--font-mono);
  font-size: 12.5px;
  background-color: #0f172a;
  color: #e2e8f0;
}

.form-tip {
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
  margin-top: 6px;
}

.form-tip code {
  background: #f1f5f9;
  color: #4f46e5;
  padding: 2px 4px;
  border-radius: 4px;
  font-size: 11.5px;
}

.profile-name-tag {
  font-weight: 600;
  color: #4f46e5;
  background: #eef2ff;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 12.5px;
}

.server-info-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #475569;
  font-size: 13px;
}

.text-muted {
  color: #94a3b8;
  font-size: 12.5px;
}
</style>
