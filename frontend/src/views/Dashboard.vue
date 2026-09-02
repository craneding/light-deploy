<template>
  <div class="dashboard-container">
    <!-- Page Header -->
    <div class="page-header">
      <div>
        <h1 class="page-title">
          <el-icon class="title-icon"><DataAnalysis /></el-icon>
          系统概览
        </h1>
        <p class="page-subtitle">实时掌控集群服务状态、部署效能与最近执行轨迹</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" size="large" @click="goToCreateTask" class="header-btn">
          <el-icon><Plus /></el-icon> 新建部署任务
        </el-button>
      </div>
    </div>

    <!-- Statistics Cards -->
    <el-row :gutter="20" class="stat-cards-row">
      <el-col :xs="24" :sm="8" :md="8">
        <div class="metric-card" v-loading="loading">
          <div class="metric-icon-wrap icon-indigo">
            <el-icon><Monitor /></el-icon>
          </div>
          <div class="metric-body">
            <div class="metric-label">已接入服务器</div>
            <div class="metric-value">{{ stats.serverCount }}</div>
            <div class="metric-sub">承载应用部署节点</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="8" :md="8">
        <div class="metric-card" v-loading="loading">
          <div class="metric-icon-wrap icon-emerald">
            <el-icon><Folder /></el-icon>
          </div>
          <div class="metric-body">
            <div class="metric-label">活跃部署项目</div>
            <div class="metric-value">{{ stats.projectCount }}</div>
            <div class="metric-sub">已绑定 GitLab 仓库</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="8" :md="8">
        <div class="metric-card" v-loading="loading">
          <div class="metric-icon-wrap icon-amber">
            <el-icon><DocumentCopy /></el-icon>
          </div>
          <div class="metric-body">
            <div class="metric-label">累计部署次数</div>
            <div class="metric-value">{{ stats.taskCount }}</div>
            <div class="metric-sub">全生命周期发布任务</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- Main Content Row -->
    <el-row :gutter="20" class="dashboard-main-row">
      <!-- Task Status Breakdown -->
      <el-col :xs="24" :lg="8" class="dashboard-col">
        <el-card shadow="never" class="dashboard-card status-card" v-loading="loading">
          <template #header>
            <div class="card-header-inner">
              <span class="card-header-title">任务状态分布</span>
              <span class="success-rate-tag" v-if="stats.taskCount > 0">
                成功率 {{ successRate }}%
              </span>
            </div>
          </template>

          <div class="status-summary">
            <!-- Progress distribution bar -->
            <div class="distribution-bar" v-if="stats.taskCount > 0">
              <div 
                class="bar-segment seg-success" 
                :style="{ width: `${(stats.successTasks / stats.taskCount) * 100}%` }"
                title="成功"
              ></div>
              <div 
                class="bar-segment seg-running" 
                :style="{ width: `${(stats.runningTasks / stats.taskCount) * 100}%` }"
                title="运行中"
              ></div>
              <div 
                class="bar-segment seg-failed" 
                :style="{ width: `${(stats.failedTasks / stats.taskCount) * 100}%` }"
                title="失败"
              ></div>
            </div>

            <div class="status-list">
              <div class="status-row">
                <div class="status-item-left">
                  <span class="status-badge-dot dot-success"></span>
                  <span class="status-name">部署成功 (SUCCESS)</span>
                </div>
                <div class="status-item-right">
                  <span class="count-num text-success">{{ stats.successTasks }}</span>
                  <span class="count-pct" v-if="stats.taskCount > 0">{{ Math.round((stats.successTasks / stats.taskCount) * 100) }}%</span>
                </div>
              </div>

              <div class="status-row">
                <div class="status-item-left">
                  <span class="status-badge-dot dot-running"></span>
                  <span class="status-name">正在执行 (RUNNING)</span>
                </div>
                <div class="status-item-right">
                  <span class="count-num text-primary">{{ stats.runningTasks }}</span>
                  <span class="count-pct" v-if="stats.taskCount > 0">{{ Math.round((stats.runningTasks / stats.taskCount) * 100) }}%</span>
                </div>
              </div>

              <div class="status-row">
                <div class="status-item-left">
                  <span class="status-badge-dot dot-failed"></span>
                  <span class="status-name">部署失败 (FAILED)</span>
                </div>
                <div class="status-item-right">
                  <span class="count-num text-danger">{{ stats.failedTasks }}</span>
                  <span class="count-pct" v-if="stats.taskCount > 0">{{ Math.round((stats.failedTasks / stats.taskCount) * 100) }}%</span>
                </div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- Recent Deployments -->
      <el-col :xs="24" :lg="16" class="dashboard-col">
        <el-card shadow="never" class="dashboard-card recent-card" v-loading="loading">
          <template #header>
            <div class="card-header-inner">
              <span class="card-header-title">最近部署动态</span>
              <el-button link type="primary" @click="goToTasks" class="view-all-link">
                查看全部任务 <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>
          </template>

          <el-table :data="stats.recentTasks" style="width: 100%" class="recent-table">
            <el-table-column prop="id" label="ID" width="70" align="center">
              <template #default="scope">
                <span class="mono-id">#{{ scope.row.id }}</span>
              </template>
            </el-table-column>
            
            <el-table-column prop="projectName" label="项目 / 环境" min-width="170">
              <template #default="scope">
                <div class="project-cell">
                  <span class="proj-name">{{ scope.row.projectName || '-' }}</span>
                  <span class="profile-tag" v-if="scope.row.profileName">{{ scope.row.profileName }}</span>
                </div>
              </template>
            </el-table-column>

            <el-table-column prop="gitRef" label="Git 引用" min-width="160">
              <template #default="scope">
                <div class="git-ref-badge">
                  <span class="ref-type">{{ scope.row.gitRefType || 'branch' }}</span>
                  <span class="ref-value" :title="scope.row.gitRef">
                    {{ scope.row.gitRefType === 'commit' && scope.row.gitRef?.length > 8 ? scope.row.gitRef.substring(0, 8) : scope.row.gitRef || '-' }}
                  </span>
                </div>
              </template>
            </el-table-column>

            <el-table-column prop="status" label="状态" width="110">
              <template #default="scope">
                <el-tag :type="getStatusType(scope.row.status)" effect="light" round class="status-tag">
                  <span v-if="scope.row.status === 'RUNNING'" class="pulse-dot"></span>
                  {{ getStatusText(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="耗时" width="110">
              <template #default="scope">
                <span class="duration-text">{{ calculateDuration(scope.row.startTime, scope.row.endTime) }}</span>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="80" align="center">
              <template #default="scope">
                <el-button size="small" link type="primary" @click="viewConsole(scope.row.id)">
                  控制台
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { DataAnalysis, Monitor, Folder, DocumentCopy, ArrowRight, Plus } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'

const router = useRouter()
const loading = ref(false)
const stats = ref({
  projectCount: 0,
  serverCount: 0,
  taskCount: 0,
  successTasks: 0,
  failedTasks: 0,
  runningTasks: 0,
  recentTasks: [] as any[]
})

const successRate = computed(() => {
  if (!stats.value.taskCount) return 100
  const rate = (stats.value.successTasks / stats.value.taskCount) * 100
  return rate % 1 === 0 ? rate : rate.toFixed(1)
})

const fetchStats = async () => {
  loading.value = true
  try {
    const data = await request.get('/dashboard/stats')
    stats.value = data as any
  } catch (error) {
    console.error('Failed to fetch stats:', error)
  } finally {
    loading.value = false
  }
}

const goToTasks = () => {
  router.push('/tasks')
}

const goToCreateTask = () => {
  router.push('/tasks')
}

const viewConsole = (taskId: number) => {
  router.push(`/tasks/${taskId}/console`)
}

const getStatusType = (status: string) => {
  switch (status?.toUpperCase()) {
    case 'SUCCESS': return 'success'
    case 'FAILED': return 'danger'
    case 'RUNNING': return 'primary'
    case 'PENDING': return 'info'
    default: return 'info'
  }
}

const getStatusText = (status: string) => {
  switch (status?.toUpperCase()) {
    case 'SUCCESS': return '成功'
    case 'FAILED': return '失败'
    case 'RUNNING': return '执行中'
    case 'PENDING': return '等待中'
    default: return status || '未知'
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

onMounted(() => {
  fetchStats()
})
</script>

<style scoped>
.dashboard-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.title-icon {
  color: var(--el-color-primary);
}

.header-btn {
  padding: 10px 18px;
}

/* Stat Cards */
.stat-cards-row {
  margin-bottom: 4px;
}

.metric-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 20px 22px;
  display: flex;
  align-items: center;
  gap: 18px;
  box-shadow: var(--shadow-xs);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  margin-bottom: 16px;
}

.metric-card:hover {
  border-color: #cbd5e1;
  box-shadow: var(--shadow-sm);
  transform: translateY(-2px);
}

.metric-icon-wrap {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
}

.icon-indigo {
  background: #eef2ff;
  color: #4f46e5;
  border: 1px solid #c7d2fe;
}

.icon-emerald {
  background: #ecfdf5;
  color: #10b981;
  border: 1px solid #a7f3d0;
}

.icon-amber {
  background: #fffbeb;
  color: #f59e0b;
  border: 1px solid #fde68a;
}

.metric-body {
  flex: 1;
}

.metric-label {
  font-size: 13px;
  font-weight: 500;
  color: #64748b;
  margin-bottom: 4px;
}

.metric-value {
  font-size: 26px;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: -0.03em;
  line-height: 1.1;
}

.metric-sub {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 4px;
}

/* Dashboard Cards */
.dashboard-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.dashboard-card :deep(.el-card__header) {
  padding: 16px 20px;
  border-bottom: 1px solid #f1f5f9;
}

.card-header-inner {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.success-rate-tag {
  font-size: 12px;
  font-weight: 600;
  color: #10b981;
  background: #ecfdf5;
  padding: 3px 8px;
  border-radius: 6px;
  border: 1px solid #a7f3d0;
}

.view-all-link {
  font-size: 13px;
  font-weight: 600;
}

/* Status Distribution */
.status-summary {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.distribution-bar {
  height: 8px;
  background: #f1f5f9;
  border-radius: 4px;
  overflow: hidden;
  display: flex;
}

.bar-segment {
  height: 100%;
  transition: width 0.3s ease;
}

.seg-success { background-color: #10b981; }
.seg-running { background-color: #4f46e5; }
.seg-failed { background-color: #ef4444; }

.status-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.status-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 14px;
  background-color: #f8fafc;
  border-radius: 8px;
  border: 1px solid #f1f5f9;
}

.status-item-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.status-badge-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.dot-success { background-color: #10b981; }
.dot-running { background-color: #4f46e5; }
.dot-failed { background-color: #ef4444; }

.status-name {
  font-size: 13.5px;
  font-weight: 500;
  color: #334155;
}

.status-item-right {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.count-num {
  font-size: 16px;
  font-weight: 700;
}

.text-success { color: #10b981; }
.text-primary { color: #4f46e5; }
.text-danger { color: #ef4444; }

.count-pct {
  font-size: 12px;
  color: #94a3b8;
}

/* Recent Deployments Table */
.recent-table :deep(.el-table__cell) {
  padding: 10px 0;
}

.mono-id {
  font-family: var(--font-mono);
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
}

.project-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.proj-name {
  font-weight: 600;
  color: #0f172a;
  font-size: 13.5px;
}

.profile-tag {
  font-size: 11.5px;
  color: #64748b;
}

.git-ref-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  padding: 3px 8px;
  border-radius: 6px;
  font-family: var(--font-mono);
  font-size: 12px;
  max-width: 100%;
}

.ref-type {
  color: #94a3b8;
  font-size: 11px;
}

.ref-value {
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

.duration-text {
  font-size: 13px;
  color: #64748b;
}

@media (max-width: 992px) {
  .dashboard-main-row {
    flex-direction: column;
  }
  .dashboard-col {
    margin-bottom: 20px;
  }
}
</style>
