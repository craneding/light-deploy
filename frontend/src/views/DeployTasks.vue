<template>
  <div class="deploy-tasks">
    <div class="page-header">
      <div>
        <h1 class="page-title">部署任务</h1>
        <p class="page-subtitle">管理并查看项目的部署任务与状态</p>
      </div>
      <div class="header-actions">
        <el-input
          v-model="searchQuery"
          placeholder="搜索项目名称、分支、Commit"
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
          新建任务
        </el-button>
      </div>
    </div>

    <div class="table-container">
      <el-table :data="taskList" v-loading="loading" style="width: 100%" :header-cell-style="{ background: '#F9FAFB', color: '#4B5563', fontWeight: 600 }">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="projectId" label="项目名称" min-width="150">
          <template #default="scope">
            <span class="font-medium">{{ scope.row.projectName || getProjectName(scope.row.projectId) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="profileId" label="环境名称" min-width="120">
          <template #default="scope">
            <span class="font-medium">{{ scope.row.profileName || `环境ID: ${scope.row.profileId}` }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="gitRefType" label="Git类型" width="120">
          <template #default="scope">
            <el-tag size="small" type="info" effect="plain" class="soft-tag">{{ scope.row.gitRefType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="gitRef" label="Git引用" min-width="200">
          <template #default="scope">
            <el-tooltip :content="scope.row.gitRef" placement="top" :disabled="scope.row.gitRefType !== 'commit'">
              <span class="git-ref-text">{{ scope.row.gitRefType === 'commit' && scope.row.gitRef?.length > 8 ? scope.row.gitRef.substring(0, 8) : scope.row.gitRef }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)" effect="light" round class="status-tag">
              {{ getStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" min-width="100">
          <template #default="scope">
            <span>{{ scope.row.operator || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" min-width="100">
          <template #default="scope">
            <span>{{ calculateDuration(scope.row.startTime, scope.row.endTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="180" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" plain class="soft-btn" @click="viewConsole(scope.row.id)">控制台</el-button>
            <el-button v-if="scope.row.status === 'SUCCESS'" size="small" type="success" plain class="soft-btn" @click="viewArtifacts(scope.row)">产物</el-button>
            <el-button v-if="scope.row.status === 'FAILED'" size="small" type="warning" plain class="soft-btn" @click="viewErrorLogs(scope.row)">错误</el-button>
            <el-button v-if="scope.row.status === 'RUNNING'" size="small" type="danger" plain class="soft-btn" @click="handleStop(scope.row)">停止</el-button>
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

    <!-- Create Task Dialog -->
    <el-dialog
      v-model="dialogVisible"
      title="新建部署任务"
      width="650px"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
      >
        <el-form-item label="选择项目" prop="projectId">
          <el-select
            v-model="form.projectId"
            placeholder="请选择要部署的项目"
            style="width: 100%"
            @change="handleProjectChange"
          >
            <el-option
              v-for="item in projectList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="目标环境" prop="profileId">
          <el-select
            v-model="form.profileId"
            placeholder="请选择目标环境"
            style="width: 100%"
            :loading="profileLoading"
          >
            <el-option
              v-for="item in profileList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="引用类型" prop="gitRefType">
          <el-radio-group v-model="form.gitRefType" @change="handleRefTypeChange">
            <el-radio label="branch">分支 (Branch)</el-radio>
            <el-radio label="tag">标签 (Tag)</el-radio>
            <el-radio label="commit">提交 (Commit)</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="选择引用" prop="gitRef">
          <el-select
            v-model="form.gitRef"
            :placeholder="'请选择' + form.gitRefType"
            style="width: 100%"
            :loading="gitLoading"
          >
            <el-option
              v-for="item in gitRefs"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm(formRef)" :loading="submitting">
            创建并执行
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- Error Logs Dialog -->
    <el-dialog
      v-model="errorDialogVisible"
      title="部署错误信息"
      width="700px"
    >
      <div class="error-logs-container">
        <pre v-if="currentErrorLogs" class="error-logs-content">{{ currentErrorLogs }}</pre>
        <el-empty v-else description="暂无详细错误信息" />
      </div>
      <template #footer>
        <el-button @click="errorDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- Artifacts Dialog -->
    <el-dialog
      v-model="artifactsDialogVisible"
      title="构建产物"
      width="600px"
    >
      <div v-loading="artifactsLoading" class="artifacts-container">
        <el-table :data="artifactFiles" style="width: 100%" v-if="artifactFiles.length > 0">
          <el-table-column label="文件名" prop="name">
            <template #default="scope">
              <el-icon><Document /></el-icon> {{ scope.row.name }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="scope">
              <el-button size="small" type="primary" link @click="downloadArtifact(scope.row.name)">下载</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无构建产物" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Document, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import request from '../utils/request'
import { fetchTasks, createTask, stopTask, type DeployTask } from '../api/task'
import { fetchGitlabBranches, fetchGitlabTags, fetchGitlabCommits } from '../api/gitlab'

const router = useRouter()

const loading = ref(false)
const taskList = ref<DeployTask[]>([])
const dialogVisible = ref(false)
const errorDialogVisible = ref(false)
const artifactsDialogVisible = ref(false)
const artifactsLoading = ref(false)
const artifactFiles = ref<{name: string}[]>([])
const currentRecordId = ref<number | null>(null)
const currentErrorLogs = ref<string>('')
const submitting = ref(false)
const formRef = ref<FormInstance>()

const searchQuery = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const projectList = ref<any[]>([])
const profileList = ref<any[]>([])
const profileLoading = ref(false)

const gitLoading = ref(false)
const gitRefs = ref<{label: string, value: string}[]>([])

const initialFormState = {
  projectId: undefined as number | undefined,
  profileId: undefined as number | undefined,
  gitRefType: 'branch' as 'branch' | 'tag' | 'commit',
  gitRef: ''
}

const form = reactive({ ...initialFormState })

const rules = reactive<FormRules>({
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  profileId: [{ required: true, message: '请选择目标环境', trigger: 'change' }],
  gitRefType: [{ required: true, message: '请选择引用类型', trigger: 'change' }],
  gitRef: [{ required: true, message: '请选择引用', trigger: 'change' }]
})

const getStatusType = (status?: string) => {
  switch (status?.toLowerCase()) {
    case 'success': return 'success'
    case 'running': return 'primary'
    case 'failed': return 'danger'
    default: return 'info'
  }
}

const getStatusText = (status?: string) => {
  switch (status?.toLowerCase()) {
    case 'success': return '成功'
    case 'running': return '执行中'
    case 'failed': return '失败'
    case 'pending': return '等待中'
    default: return status || '等待中'
  }
}

const calculateDuration = (startTime?: string, endTime?: string) => {
  if (!startTime) return '-'
  
  const start = new Date(startTime).getTime()
  const end = endTime ? new Date(endTime).getTime() : new Date().getTime()
  
  if (isNaN(start) || isNaN(end)) return '-'
  
  const diff = Math.max(0, end - start)
  const seconds = Math.floor(diff / 1000)
  
  if (seconds < 60) {
    return `${seconds}秒`
  }
  
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = seconds % 60
  
  if (minutes < 60) {
    return `${minutes}分${remainingSeconds}秒`
  }
  
  const hours = Math.floor(minutes / 60)
  const remainingMinutes = minutes % 60
  
  return `${hours}小时${remainingMinutes}分${remainingSeconds}秒`
}

const loadTasks = async () => {
  loading.value = true
  try {
    const res: any = await fetchTasks({
      search: searchQuery.value,
      page: currentPage.value,
      size: pageSize.value
    })
    
    if (res && res.list) {
      taskList.value = res.list
      total.value = res.total || 0
      currentPage.value = res.current || 1
      pageSize.value = res.size || 10
    } else {
      taskList.value = Array.isArray(res) ? res : (res.data || [])
      total.value = taskList.value.length
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取任务列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  loadTasks()
}

const handleSizeChange = (val: number) => {
  pageSize.value = val
  currentPage.value = 1
  loadTasks()
}

const handleCurrentChange = (val: number) => {
  currentPage.value = val
  loadTasks()
}

const loadProjects = async () => {
  try {
    const projRes = await request.get('/projects')
    projectList.value = Array.isArray(projRes) ? projRes : ((projRes as any).data || [])
  } catch (error: any) {
    ElMessage.error('加载项目列表失败')
  }
}

const getProjectName = (id: number) => {
  const project = projectList.value.find(p => p.id === id)
  return project ? project.name : `项目ID: ${id}`
}

const fetchProfiles = async () => {
  if (!form.projectId) return
  profileLoading.value = true
  try {
    const res: any = await request.get(`/profiles?projectId=${form.projectId}`)
    profileList.value = res.data || res || []
  } catch (error: any) {
    ElMessage.error('获取环境列表失败')
    profileList.value = []
  } finally {
    profileLoading.value = false
  }
}

const fetchGitRefs = async () => {
  if (!form.projectId) return
  
  // Find the gitlab project ID associated with this project
  const project = projectList.value.find(p => p.id === form.projectId)
  if (!project || !project.gitlabProjectId) {
    ElMessage.warning('该项目未绑定Gitlab项目')
    gitRefs.value = []
    return
  }

  gitLoading.value = true
  try {
    let res: any
    if (form.gitRefType === 'branch') {
      res = await fetchGitlabBranches(project.gitlabProjectId)
      gitRefs.value = (res.data || res || []).map((b: any) => ({ label: b.name, value: b.name }))
    } else if (form.gitRefType === 'tag') {
      res = await fetchGitlabTags(project.gitlabProjectId)
      gitRefs.value = (res.data || res || []).map((t: any) => ({ label: t.name, value: t.name }))
    } else if (form.gitRefType === 'commit') {
      res = await fetchGitlabCommits(project.gitlabProjectId)
      gitRefs.value = (res.data || res || []).map((c: any) => ({ label: `${c.short_id} - ${c.title}`, value: c.id }))
    }
  } catch (error: any) {
    ElMessage.error('获取Git引用失败')
    gitRefs.value = []
  } finally {
    gitLoading.value = false
  }
}

const handleProjectChange = () => {
  form.gitRef = ''
  form.profileId = undefined
  fetchGitRefs()
  fetchProfiles()
}

const handleRefTypeChange = () => {
  form.gitRef = ''
  fetchGitRefs()
}

const handleCreate = () => {
  Object.assign(form, initialFormState)
  gitRefs.value = []
  profileList.value = []
  dialogVisible.value = true
  if (projectList.value.length === 0) {
    loadProjects()
  }
  setTimeout(() => formRef.value?.clearValidate(), 0)
}

const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return
  await formEl.validate(async (valid) => {
    if (valid) {
      submitting.value = true
      try {
        await createTask({
          projectId: form.projectId!,
          profileId: form.profileId!,
          gitRefType: form.gitRefType,
          gitRef: form.gitRef
        })
        ElMessage.success('任务创建成功')
        dialogVisible.value = false
        loadTasks()
      } catch (error: any) {
        ElMessage.error(error.message || '任务创建失败')
      } finally {
        submitting.value = false
      }
    }
  })
}

const handleStop = (row: DeployTask) => {
  ElMessageBox.confirm(`确认停止任务 ${row.id} 吗?`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await stopTask(row.id!)
      ElMessage.success('任务已发送停止信号')
      loadTasks()
    } catch (error: any) {
      ElMessage.error(error.message || '停止失败')
    }
  }).catch(() => {})
}

const viewConsole = (taskId: number) => {
  router.push(`/tasks/${taskId}/console`)
}

const viewErrorLogs = (row: DeployTask) => {
  currentErrorLogs.value = row.logs || '没有记录详细错误信息。'
  errorDialogVisible.value = true
}

const viewArtifacts = async (row: DeployTask) => {
  currentRecordId.value = row.id!
  artifactsDialogVisible.value = true
  artifactsLoading.value = true
  artifactFiles.value = []
  
  try {
    const res: any = await request.get(`/deploy-records/${row.id}/artifacts`)
    const files = res.data || res || []
    artifactFiles.value = files.map((f: string) => ({ name: f }))
  } catch (error: any) {
    ElMessage.error(error.message || '获取构建产物失败')
  } finally {
    artifactsLoading.value = false
  }
}

const downloadArtifact = (filename: string) => {
  if (!currentRecordId.value) return
  const token = localStorage.getItem('jwt_token') || ''
  const url = `/api/deploy-records/${currentRecordId.value}/artifacts/download?filePath=${encodeURIComponent(filename)}&token=${token}`
  window.open(url, '_blank')
}

onMounted(() => {
  loadProjects()
  loadTasks()
})
</script>

<style scoped>
.deploy-tasks {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.error-logs-container {
  background-color: #1e1e1e;
  border-radius: 8px;
  padding: 16px;
  max-height: 400px;
  overflow-y: auto;
}

.error-logs-content {
  color: #f87171;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 14px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-wrap: break-word;
  margin: 0;
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
  gap: 16px;
  align-items: center;
}

.search-input {
  width: 300px;
}

.pagination-container {
  padding: 16px 0;
  display: flex;
  justify-content: flex-end;
}

.action-btn {
  border-radius: 8px;
  padding: 10px 20px;
}

.table-container {
  background: white;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #E5E7EB;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06);
}

.git-ref-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  background-color: #F3F4F6;
  padding: 4px 8px;
  border-radius: 6px;
  color: #4B5563;
  border: 1px solid #E5E7EB;
}

.soft-btn {
  border-radius: 6px;
  font-weight: 500;
}

.soft-tag {
  border-radius: 6px;
  font-weight: 500;
}

.status-tag {
  font-weight: 600;
  padding: 0 12px;
}
</style>
