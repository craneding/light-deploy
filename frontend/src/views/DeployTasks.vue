<template>
  <div class="deploy-tasks">
    <div class="page-header">
      <div>
        <h1 class="page-title">
          <el-icon class="title-icon"><DocumentCopy /></el-icon>
          部署任务
        </h1>
        <p class="page-subtitle">监控全平台任务执行状态、产物分发与历史部署日志</p>
      </div>
      <div class="header-actions">
        <el-input
          v-model="searchQuery"
          placeholder="搜索项目名称、分支、Commit..."
          class="search-input"
          clearable
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button @click="loadTasks" :loading="loading" class="action-btn">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
        <el-button type="primary" @click="handleCreate" class="action-btn">
          <el-icon><Plus /></el-icon> 新建任务
        </el-button>
      </div>
    </div>

    <!-- Table Container -->
    <div class="table-container">
      <el-table :data="taskList" v-loading="loading" style="width: 100%" class="tasks-table">
        <el-table-column prop="id" label="ID" width="70" align="center">
          <template #default="scope">
            <span class="mono-id">#{{ scope.row.id }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="projectName" label="项目名称 / 环境" min-width="170">
          <template #default="scope">
            <div class="project-meta-cell">
              <span class="project-name">{{ scope.row.projectName || getProjectName(scope.row.projectId) }}</span>
              <span class="profile-name-tag">{{ scope.row.profileName || `环境ID: ${scope.row.profileId}` }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="gitRef" label="Git 引用" min-width="190">
          <template #default="scope">
            <div class="git-badge">
              <span class="git-type-pill" :class="`pill-${scope.row.gitRefType}`">
                {{ scope.row.gitRefType || 'branch' }}
              </span>
              <el-tooltip :content="scope.row.gitRef" placement="top" :disabled="scope.row.gitRefType !== 'commit'">
                <span class="git-ref-str">
                  {{ scope.row.gitRefType === 'commit' && scope.row.gitRef?.length > 8 ? scope.row.gitRef.substring(0, 8) : scope.row.gitRef }}
                </span>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="状态" width="110">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)" effect="light" round class="status-tag">
              <span v-if="scope.row.status?.toLowerCase() === 'running'" class="pulse-dot"></span>
              {{ getStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="operator" label="操作人" width="110">
          <template #default="scope">
            <div class="operator-cell">
              <el-icon><User /></el-icon>
              <span>{{ scope.row.operator || '系统' }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="耗时" width="100">
          <template #default="scope">
            <span class="duration-text">{{ calculateDuration(scope.row.startTime, scope.row.endTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="createdAt" label="创建时间" width="160">
          <template #default="scope">
            <span class="time-text">{{ scope.row.createdAt }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="280" fixed="right" align="right">
          <template #default="scope">
            <div class="row-actions">
              <el-button size="small" type="primary" @click="viewConsole(scope.row.id)" class="row-btn">
                控制台
              </el-button>
              <el-button size="small" plain @click="quickDeploy(scope.row)" class="row-btn">
                快速部署
              </el-button>
              <el-button v-if="scope.row.status === 'SUCCESS'" size="small" type="success" plain @click="viewArtifacts(scope.row)" class="row-btn">
                产物
              </el-button>
              <el-button v-if="scope.row.status === 'FAILED'" size="small" type="danger" plain @click="viewErrorLogs(scope.row)" class="row-btn">
                错误
              </el-button>
              <el-button v-if="scope.row.status === 'RUNNING'" size="small" type="danger" plain @click="handleStop(scope.row)" class="row-btn">
                停止
              </el-button>
            </div>
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

    <!-- Create Task Dialog -->
    <el-dialog
      v-model="dialogVisible"
      title="新建部署任务"
      width="580px"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="task-form"
      >
        <el-form-item label="目标项目" prop="projectId">
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
            placeholder="请选择目标部署环境"
            style="width: 100%"
            :loading="profileLoading"
            :disabled="!form.projectId"
          >
            <el-option
              v-for="item in profileList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="Git 引用类型" prop="gitRefType">
          <el-radio-group v-model="form.gitRefType" @change="handleRefTypeChange" class="custom-radio-group">
            <el-radio-button label="branch">分支 (Branch)</el-radio-button>
            <el-radio-button label="tag">标签 (Tag)</el-radio-button>
            <el-radio-button label="commit">提交 (Commit)</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="选择版本 / 引用" prop="gitRef">
          <el-select
            v-model="form.gitRef"
            :placeholder="'请选择 ' + form.gitRefType"
            style="width: 100%"
            :loading="gitLoading"
            :disabled="!form.projectId"
            filterable
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
            创建并执行部署
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- Error Logs Dialog -->
    <el-dialog
      v-model="errorDialogVisible"
      title="部署错误日志"
      width="720px"
    >
      <div class="error-window">
        <div class="error-window-header">
          <div class="window-dots">
            <span class="dot red"></span>
            <span class="dot yellow"></span>
            <span class="dot green"></span>
          </div>
          <span class="window-title">error.log</span>
          <el-button size="small" link type="primary" @click="copyErrorLogs" class="copy-btn">
            复制日志
          </el-button>
        </div>
        <div class="error-logs-container">
          <pre v-if="currentErrorLogs" class="error-logs-content">{{ currentErrorLogs }}</pre>
          <el-empty v-else description="暂无详细错误信息" />
        </div>
      </div>
      <template #footer>
        <el-button @click="errorDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- Artifacts Dialog -->
    <el-dialog
      v-model="artifactsDialogVisible"
      title="构建产物下载"
      width="680px"
    >
      <div v-loading="artifactsLoading" class="artifacts-container">
        <div v-if="scpInfo && artifactFiles.length > 0" class="scp-card">
          <div class="scp-header">
            <span class="scp-label">
              <el-icon><CopyDocument /></el-icon>
              SCP 远程下载命令
            </span>
            <el-button size="small" type="primary" plain @click="copyScpCommand">
              复制命令
            </el-button>
          </div>
          <pre class="scp-command">{{ scpCommand }}</pre>
        </div>

        <div v-if="artifactFiles.length > 0" class="artifacts-toolbar">
          <span class="file-count">共 {{ artifactFiles.length }} 个产物文件</span>
          <el-button type="primary" size="small" @click="downloadAllArtifacts">
            <el-icon><Download /></el-icon> 打包下载全部 (ZIP)
          </el-button>
        </div>

        <el-table
          v-if="artifactFiles.length > 0"
          :data="artifactTreeData"
          row-key="fullPath"
          :tree-props="{ children: 'children' }"
          default-expand-all
          style="width: 100%"
          class="artifact-table"
        >
          <el-table-column label="产物路径" prop="name" min-width="320">
            <template #default="scope">
              <div class="file-tree-node">
                <el-icon v-if="scope.row.isDir" class="folder-icon"><FolderOpened /></el-icon>
                <el-icon v-else class="file-icon"><Document /></el-icon>
                <span class="file-name">{{ scope.row.name }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" align="right">
            <template #default="scope">
              <el-button
                v-if="!scope.row.isDir"
                size="small"
                type="primary"
                link
                @click="downloadArtifact(scope.row.fullPath)"
              >
                下载
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无构建产物" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Document, Search, FolderOpened, CopyDocument, Refresh, DocumentCopy, User, Download } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import request from '../utils/request'
import { getItem } from '../utils/storage'
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

interface ScpInfo {
  host: string
  port: string
  hostDataDir: string
  artifactId: number
}

const scpInfo = ref<ScpInfo | null>(null)
const scpCommand = computed(() => {
  if (!scpInfo.value) return ''
  const { host, port, hostDataDir, artifactId } = scpInfo.value
  const remotePath = `${hostDataDir}/artifacts/${artifactId}/`
  const portFlag = port !== '22' ? ` -P ${port}` : ''
  return `scp${portFlag} -r ${host}:${remotePath} .`
})

interface TreeNode {
  name: string
  fullPath: string
  children?: TreeNode[]
  isDir?: boolean
}

function buildFileTree(files: {name: string}[]): TreeNode[] {
  const root: TreeNode[] = []
  for (const file of files) {
    const parts = file.name.split('/')
    let current = root
    let currentPath = ''
    for (let i = 0; i < parts.length; i++) {
      const part = parts[i]
      currentPath = currentPath ? currentPath + '/' + part : part
      const isDir = i < parts.length - 1
      let existing = current.find(n => n.name === part && n.isDir === isDir)
      if (!existing) {
        existing = { name: part, fullPath: currentPath, isDir, children: isDir ? [] : undefined }
        current.push(existing)
      }
      if (isDir && existing.children) {
        current = existing.children
      }
    }
  }
  return root
}

const artifactTreeData = computed(() => buildFileTree(artifactFiles.value))
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
  
  return `${hours}时${remainingMinutes}分`
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

const quickDeploy = (row: DeployTask) => {
  ElMessageBox.confirm(
    `确认使用项目「${row.projectName || row.projectId}」环境「${row.profileName || row.profileId}」(${row.gitRefType}: ${row.gitRef}) 快速发起部署？`,
    '快速部署确认',
    { confirmButtonText: '立即部署', cancelButtonText: '取消', type: 'info' }
  ).then(async () => {
    try {
      await createTask({
        projectId: row.projectId,
        profileId: row.profileId,
        gitRefType: row.gitRefType,
        gitRef: row.gitRef
      })
      ElMessage.success('快速部署任务已创建')
      loadTasks()
    } catch (error: any) {
      ElMessage.error(error.message || '快速部署失败')
    }
  }).catch(() => {})
}

const handleStop = (row: DeployTask) => {
  ElMessageBox.confirm(`确认停止部署任务 #${row.id} 吗?`, '停止任务', {
    confirmButtonText: '确定停止',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await stopTask(row.id!)
      ElMessage.success('已发送终止信号')
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

const copyErrorLogs = () => {
  navigator.clipboard.writeText(currentErrorLogs.value)
  ElMessage.success('错误日志已复制到剪贴板')
}

const viewArtifacts = async (row: DeployTask) => {
  currentRecordId.value = row.id!
  artifactsDialogVisible.value = true
  artifactsLoading.value = true
  artifactFiles.value = []
  scpInfo.value = null

  try {
    const res: any = await request.get(`/deploy-records/${row.id}/artifacts`)
    const data = res.data || res
    if (Array.isArray(data)) {
      artifactFiles.value = data.map((f: string) => ({ name: f }))
    } else {
      artifactFiles.value = (data.files || []).map((f: string) => ({ name: f }))
      if (data.scp) {
        scpInfo.value = data.scp as ScpInfo
      }
    }
  } catch (error: any) {
    ElMessage.error(error.message || '获取构建产物失败')
  } finally {
    artifactsLoading.value = false
  }
}

const downloadArtifact = (filename: string) => {
  if (!currentRecordId.value) return
  const token = getItem<string>('jwt_token') || ''
  const url = `/api/deploy-records/${currentRecordId.value}/artifacts/download?filePath=${encodeURIComponent(filename)}&token=${token}`
  window.open(url, '_blank')
}

const downloadAllArtifacts = () => {
  if (!currentRecordId.value) return
  const token = getItem<string>('jwt_token') || ''
  const url = `/api/deploy-records/${currentRecordId.value}/artifacts/download-all?token=${token}`
  window.open(url, '_blank')
}

const copyScpCommand = () => {
  navigator.clipboard.writeText(scpCommand.value)
  ElMessage.success('SCP 命令已复制到剪贴板')
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
  gap: 20px;
}

.title-icon {
  color: var(--el-color-primary);
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
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

.project-meta-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.project-name {
  font-weight: 600;
  color: #0f172a;
  font-size: 13.5px;
}

.profile-name-tag {
  font-size: 11.5px;
  color: #64748b;
}

.git-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  padding: 3px 8px;
  border-radius: 6px;
  font-family: var(--font-mono);
  font-size: 12px;
  max-width: 100%;
}

.git-type-pill {
  font-size: 10.5px;
  padding: 1px 5px;
  border-radius: 4px;
  font-weight: 600;
}

.pill-branch { background: #e0e7ff; color: #4338ca; }
.pill-tag { background: #dcfce7; color: #15803d; }
.pill-commit { background: #fef3c7; color: #b45309; }

.git-ref-str {
  color: #334155;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-tag {
  font-weight: 600;
  padding: 0 10px;
}

.operator-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #475569;
}

.duration-text {
  font-size: 12.5px;
  color: #64748b;
}

.time-text {
  font-size: 12.5px;
  color: #64748b;
}

.row-actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
}

.row-btn {
  padding: 4px 8px;
  font-size: 12px;
}

/* Form Styling */
.task-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: #334155;
  margin-bottom: 6px;
}

.custom-radio-group {
  width: 100%;
  display: flex;
}

.custom-radio-group :deep(.el-radio-button) {
  flex: 1;
}

.custom-radio-group :deep(.el-radio-button__inner) {
  width: 100%;
}

/* Error Window */
.error-window {
  background: #0f172a;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #334155;
}

.error-window-header {
  height: 38px;
  background: #1e293b;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-bottom: 1px solid #334155;
}

.window-dots {
  display: flex;
  gap: 6px;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.dot.red { background-color: #ef4444; }
.dot.yellow { background-color: #f59e0b; }
.dot.green { background-color: #10b981; }

.window-title {
  color: #94a3b8;
  font-size: 12px;
  font-family: var(--font-mono);
}

.copy-btn {
  font-size: 12px;
}

.error-logs-container {
  padding: 16px;
  max-height: 380px;
  overflow-y: auto;
}

.error-logs-content {
  color: #fca5a5;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-wrap: break-word;
  margin: 0;
}

/* Artifacts Window */
.artifacts-container {
  max-height: 520px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.scp-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 14px;
}

.scp-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.scp-label {
  font-weight: 600;
  font-size: 13px;
  color: #334155;
  display: flex;
  align-items: center;
  gap: 6px;
}

.scp-command {
  background: #0f172a;
  color: #e2e8f0;
  font-family: var(--font-mono);
  font-size: 12.5px;
  line-height: 1.5;
  padding: 12px;
  border-radius: 8px;
  margin: 0;
  word-break: break-all;
  user-select: all;
}

.artifacts-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 4px;
}

.file-count {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
}

.file-tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
}

.folder-icon {
  color: #f59e0b;
}

.file-icon {
  color: #64748b;
}

.file-name {
  font-family: var(--font-mono);
  font-size: 13px;
}
</style>
