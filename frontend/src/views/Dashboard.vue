<template>
  <div class="dashboard-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">概览</h1>
        <p class="page-subtitle">欢迎回来，这是您的轻量部署数据概览</p>
      </div>
    </div>

    <!-- Statistics Cards -->
    <el-row :gutter="24" class="stat-cards">
      <el-col :xs="12" :sm="8" :md="8" :lg="8">
        <el-card shadow="hover" class="stat-card" v-loading="loading">
          <div class="stat-icon server-icon"><el-icon><Monitor /></el-icon></div>
          <div class="stat-content">
            <div class="stat-title">总服务器数</div>
            <div class="stat-value">{{ stats.serverCount }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="8" :lg="8">
        <el-card shadow="hover" class="stat-card" v-loading="loading">
          <div class="stat-icon project-icon"><el-icon><Folder /></el-icon></div>
          <div class="stat-content">
            <div class="stat-title">总项目数</div>
            <div class="stat-value">{{ stats.projectCount }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="8" :lg="8">
        <el-card shadow="hover" class="stat-card" v-loading="loading">
          <div class="stat-icon task-icon"><el-icon><List /></el-icon></div>
          <div class="stat-content">
            <div class="stat-title">总部署任务数</div>
            <div class="stat-value">{{ stats.taskCount }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="24" class="dashboard-main">
      <!-- Task Status Breakdown -->
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="chart-card" v-loading="loading">
          <template #header>
            <div class="card-header">
              <span>任务状态统计</span>
            </div>
          </template>
          <div class="status-stats">
            <div class="status-item">
              <div class="status-label"><el-tag type="success" effect="dark" round>SUCCESS</el-tag> 成功</div>
              <div class="status-count">{{ stats.successTasks }}</div>
            </div>
            <div class="status-item">
              <div class="status-label"><el-tag type="warning" effect="dark" round>FAILED</el-tag> 失败</div>
              <div class="status-count">{{ stats.failedTasks }}</div>
            </div>
            <div class="status-item">
              <div class="status-label"><el-tag type="primary" effect="dark" round>RUNNING</el-tag> 运行中</div>
              <div class="status-count">{{ stats.runningTasks }}</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- Recent Tasks -->
      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="recent-card" v-loading="loading">
          <template #header>
            <div class="card-header">
              <span>最近部署记录</span>
              <el-button link type="primary" @click="goToTasks">查看全部</el-button>
            </div>
          </template>
          <el-table :data="stats.recentTasks" style="width: 100%" :header-cell-style="{ background: '#F9FAFB', color: '#4B5563' }">
            <el-table-column prop="id" label="记录 ID" width="80" />
            <el-table-column prop="projectName" label="项目名称" min-width="120" show-overflow-tooltip />
            <el-table-column prop="profileName" label="环境名称" min-width="120" show-overflow-tooltip />
            <el-table-column prop="gitRefType" label="Git类型" width="120">
              <template #default="scope">
                <el-tag size="small" type="info" effect="plain" class="soft-tag">{{ scope.row.gitRefType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="gitRef" label="Git引用" min-width="200" show-overflow-tooltip>
              <template #default="scope">
                <el-tooltip :content="scope.row.gitRef" placement="top" :disabled="scope.row.gitRefType !== 'commit'">
                  <span class="git-ref-text">{{ scope.row.gitRefType === 'commit' && scope.row.gitRef?.length > 8 ? scope.row.gitRef.substring(0, 8) : scope.row.gitRef || '-' }}</span>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="scope">
                <el-tag :type="getStatusType(scope.row.status)" effect="light" round>
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
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Monitor, Folder, List } from '@element-plus/icons-vue'
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
  recentTasks: []
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

const getStatusType = (status: string) => {
  switch (status) {
    case 'SUCCESS': return 'success'
    case 'FAILED': return 'warning'
    case 'RUNNING': return 'primary'
    case 'PENDING': return 'info'
    default: return 'info'
  }
}

const getStatusText = (status: string) => {
  switch (status) {
    case 'SUCCESS': return '成功'
    case 'FAILED': return '失败'
    case 'RUNNING': return '运行中'
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
  
  return `${hours}小时${remainingMinutes}分${remainingSeconds}秒`
}

onMounted(() => {
  fetchStats()
})
</script>

<style scoped>
.dashboard-container {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.page-header {
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

.stat-cards {
  margin-bottom: 8px;
}

.stat-card {
  border-radius: 12px;
  border: 1px solid #E5E7EB;
}

:deep(.stat-card .el-card__body) {
  display: flex;
  align-items: center;
  padding: 24px;
  gap: 20px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
}

.server-icon {
  background-color: #E0E7FF;
  color: #4F46E5;
}

.project-icon {
  background-color: #D1FAE5;
  color: #10B981;
}

.task-icon {
  background-color: #FEF3C7;
  color: #F59E0B;
}

.stat-content {
  flex: 1;
}

.stat-title {
  font-size: 14px;
  color: #6B7280;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #111827;
}

.dashboard-main {
  display: flex;
}

.chart-card, .recent-card {
  border-radius: 12px;
  border: 1px solid #E5E7EB;
  height: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  color: #111827;
}

.status-stats {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.status-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background-color: #F9FAFB;
  border-radius: 8px;
}

.status-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  color: #4B5563;
}

.status-count {
  font-size: 20px;
  font-weight: 700;
  color: #111827;
}
</style>
