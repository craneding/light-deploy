<template>
  <div class="deployment-console">
    <!-- Header -->
    <div class="page-header">
      <div class="header-left">
        <el-button @click="goBack" class="back-btn" plain>
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
        <div>
          <h1 class="page-title">
            <el-icon class="title-icon"><Terminal /></el-icon>
            部署控制台
            <span class="mono-id">#{{ taskId }}</span>
          </h1>
          <p class="page-subtitle" v-if="taskInfo">
            项目：<strong>{{ taskInfo.projectName || taskInfo.projectId }}</strong>
            <span class="divider-dot">•</span>
            环境：<strong>{{ taskInfo.profileName || taskInfo.profileId }}</strong>
            <span class="divider-dot">•</span>
            引用：<code>{{ taskInfo.gitRef }}</code>
          </p>
          <p class="page-subtitle" v-else>
            实时捕获构建编译、依赖安装与远程发布输出流
          </p>
        </div>
      </div>
      
      <div class="header-right">
        <el-tag :type="statusType" effect="light" round size="large" class="status-tag">
          <span v-if="status === 'Connected' || status === 'Connecting'" class="pulse-dot"></span>
          {{ getStatusLabel() }}
        </el-tag>
      </div>
    </div>

    <!-- Terminal Window -->
    <div class="terminal-card">
      <div class="terminal-header">
        <div class="terminal-dots">
          <span class="dot red"></span>
          <span class="dot yellow"></span>
          <span class="dot green"></span>
        </div>
        
        <div class="terminal-title">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="4 17 10 11 4 5"></polyline>
            <line x1="12" y1="19" x2="20" y2="19"></line>
          </svg>
          bash - light-deploy-runner #{{ taskId }}
        </div>

        <div class="terminal-actions">
          <el-tooltip :content="autoScroll ? '自动滚动已开启' : '自动滚动已暂停'" placement="top">
            <el-button 
              size="small" 
              :type="autoScroll ? 'primary' : 'info'" 
              link 
              @click="toggleAutoScroll"
              class="term-btn"
            >
              <el-icon><Bottom /></el-icon> 自动滚动
            </el-button>
          </el-tooltip>
          
          <el-button size="small" link type="primary" @click="copyLogs" class="term-btn">
            <el-icon><CopyDocument /></el-icon> 复制日志
          </el-button>
          
          <el-button size="small" link type="info" @click="clearLogs" class="term-btn">
            <el-icon><Delete /></el-icon> 清屏
          </el-button>
        </div>
      </div>

      <div class="terminal-container" ref="terminalContainer" @scroll="handleUserScroll">
        <div class="terminal-output">
          <div v-if="logs.length === 0" class="terminal-loading">
            <span class="cursor-blink">></span> 正在初始化终端并建立 WebSocket 管道...
          </div>
          <div 
            v-for="(log, index) in logs" 
            :key="index" 
            class="log-line"
            :class="getLogClass(log)"
          >
            <span class="line-num">{{ index + 1 }}</span>
            <span class="line-text">{{ log }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import { ElMessage } from 'element-plus'
import { ArrowLeft, CopyDocument, Delete, Bottom } from '@element-plus/icons-vue'
import request from '../utils/request'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const taskId = route.params.id as string
const logs = ref<string[]>([])
const status = ref<string>('Connecting')
const taskInfo = ref<any>(null)
const autoScroll = ref(true)
const terminalContainer = ref<HTMLElement | null>(null)
let ws: WebSocket | null = null
let isProgrammaticScroll = false

const statusType = computed(() => {
  if (status.value === 'Connected' || status.value === 'Success') return 'success'
  if (status.value === 'Connecting') return 'warning'
  if (status.value === 'Disconnected' || status.value === 'Error' || status.value === 'Failed') return 'danger'
  return 'info'
})

const getStatusLabel = () => {
  switch (status.value) {
    case 'Connected': return '实时传输中'
    case 'Connecting': return '正在连接...'
    case 'Success': return '部署成功'
    case 'Failed': return '部署失败'
    case 'Disconnected': return '已断开连接'
    case 'Error': return '连接异常'
    default: return status.value
  }
}

const getLogClass = (log: string) => {
  const lower = log.toLowerCase()
  if (lower.includes('error') || lower.includes('failed') || lower.includes('fatal:')) {
    return 'log-error'
  }
  if (lower.includes('success') || lower.includes('done') || lower.includes('completed successfully')) {
    return 'log-success'
  }
  if (lower.includes('warn') || lower.includes('warning')) {
    return 'log-warning'
  }
  if (log.startsWith('===>') || log.startsWith('>>>') || log.startsWith('[LIGHT-DEPLOY]')) {
    return 'log-header'
  }
  return 'log-normal'
}

const scrollToBottom = () => {
  if (!autoScroll.value) return
  nextTick(() => {
    if (terminalContainer.value) {
      isProgrammaticScroll = true
      terminalContainer.value.scrollTop = terminalContainer.value.scrollHeight
    }
  })
}

const handleUserScroll = () => {
  if (!terminalContainer.value) return
  if (isProgrammaticScroll) {
    isProgrammaticScroll = false
    return
  }
  const { scrollTop, scrollHeight, clientHeight } = terminalContainer.value
  const distanceToBottom = scrollHeight - scrollTop - clientHeight
  // If user scrolled up noticeably (> 50px), pause autoScroll
  if (distanceToBottom > 50) {
    autoScroll.value = false
  } else {
    autoScroll.value = true
  }
}

const toggleAutoScroll = () => {
  autoScroll.value = !autoScroll.value
  if (autoScroll.value) {
    scrollToBottom()
  }
}

const copyLogs = () => {
  navigator.clipboard.writeText(logs.value.join('\n'))
  ElMessage.success('全部日志已复制到剪贴板')
}

const clearLogs = () => {
  logs.value = []
  ElMessage.info('控制台显示已清空')
}

const checkTaskStatusAndLogs = async () => {
  try {
    const res: any = await request.get(`/deploy-tasks/${taskId}`)
    const task = res.data || res
    taskInfo.value = task
    
    if (task && (task.status === 'SUCCESS' || task.status === 'FAILED' || task.status === 'success' || task.status === 'failed')) {
      status.value = task.status.toUpperCase() === 'SUCCESS' ? 'Success' : 'Failed'
      if (task.logs) {
        logs.value = task.logs.split('\n').filter((line: string) => line.trim() !== '')
        scrollToBottom()
      } else {
        logs.value = ['> 任务已结束，但未找到历史日志记录。']
      }
      return true // Already finished
    }
    return false // Still running or pending
  } catch (error) {
    console.error('Failed to fetch task status', error)
    return false
  }
}

const connectWebSocket = async () => {
  const isFinished = await checkTaskStatusAndLogs()
  if (isFinished) return

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  let host = window.location.host
  let basePath = import.meta.env.VITE_API_BASE_URL || '/light-deploy/api'

  if (import.meta.env.VITE_API_BASE_URL) {
    const apiBaseUrl = new URL(import.meta.env.VITE_API_BASE_URL, window.location.origin)
    host = apiBaseUrl.host
    basePath = apiBaseUrl.pathname === '/' ? '' : apiBaseUrl.pathname
  } else if (import.meta.env.DEV) {
    host = 'localhost:8080'
    basePath = '/api'
  }
  
  basePath = basePath.replace(/\/$/, '')
  const wsUrl = `${protocol}//${host}${basePath}/ws/deploy?taskId=${taskId}&token=${userStore.token}`
  
  try {
    ws = new WebSocket(wsUrl)
    
    ws.onopen = () => {
      status.value = 'Connected'
      logs.value.push(`> Connected to deployment stream for task #${taskId}`)
      scrollToBottom()
    }
    
    ws.onmessage = (event) => {
      logs.value.push(event.data)
      scrollToBottom()
      
      if (event.data.includes('Deployment Completed Successfully')) {
        status.value = 'Success'
      } else if (event.data.includes('Deployment Failed')) {
        status.value = 'Failed'
      }
    }
    
    ws.onclose = () => {
      if (status.value !== 'Success' && status.value !== 'Failed') {
        status.value = 'Disconnected'
      }
      logs.value.push(`> Stream connection closed.`)
      scrollToBottom()
    }
    
    ws.onerror = () => {
      status.value = 'Error'
      logs.value.push(`> WebSocket connection error.`)
      scrollToBottom()
    }
  } catch (err: any) {
    ElMessage.error('无法连接到控制台 WebSocket')
    status.value = 'Error'
  }
}

const goBack = () => {
  router.push('/tasks')
}

onMounted(() => {
  connectWebSocket()
})

onUnmounted(() => {
  if (ws) {
    ws.close()
  }
})
</script>

<style scoped>
.deployment-console {
  display: flex;
  flex-direction: column;
  gap: 18px;
  height: calc(100vh - 190px);
  min-height: 480px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0 12px 0;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.back-btn {
  border-radius: 8px;
  font-weight: 500;
}

.mono-id {
  font-family: var(--font-mono);
  font-size: 16px;
  color: var(--el-color-primary);
  background: #eef2ff;
  padding: 2px 8px;
  border-radius: 6px;
  border: 1px solid #c7d2fe;
}

.divider-dot {
  margin: 0 6px;
  color: #cbd5e1;
}

.status-tag {
  font-weight: 600;
  padding: 0 14px;
}

/* Terminal Card */
.terminal-card {
  flex: 1;
  min-height: 0;
  border-radius: 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background-color: #0b0f19;
  border: 1px solid #1e293b;
  box-shadow: var(--shadow-xl) !important;
}

.terminal-header {
  height: 42px;
  background-color: #111827;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-bottom: 1px solid #1f2937;
  user-select: none;
}

.terminal-dots {
  display: flex;
  gap: 7px;
  width: 70px;
}

.dot {
  width: 11px;
  height: 11px;
  border-radius: 50%;
}

.dot.red { background-color: #ef4444; }
.dot.yellow { background-color: #f59e0b; }
.dot.green { background-color: #10b981; }

.terminal-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #94a3b8;
  font-size: 12.5px;
  font-family: var(--font-mono);
}

.terminal-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.term-btn {
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.terminal-container {
  flex: 1;
  min-height: 0;
  padding: 16px 20px;
  overflow-y: auto;
  font-family: var(--font-mono);
  background-color: #0b0f19;
}

.terminal-output {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.terminal-loading {
  color: #94a3b8;
  font-size: 13.5px;
  padding: 10px 0;
}

.cursor-blink {
  animation: blink 1s step-start infinite;
  color: #6366f1;
  font-weight: bold;
}

@keyframes blink {
  50% { opacity: 0; }
}

.log-line {
  display: flex;
  gap: 16px;
  line-height: 1.6;
  font-size: 13px;
  word-break: break-all;
}

.line-num {
  width: 32px;
  text-align: right;
  color: #334155;
  user-select: none;
  font-size: 11.5px;
  flex-shrink: 0;
  padding-top: 1px;
}

.line-text {
  flex: 1;
  white-space: pre-wrap;
}

.log-normal { color: #cbd5e1; }
.log-error { color: #f87171; background: rgba(239, 68, 68, 0.1); border-radius: 2px; padding: 0 4px; }
.log-success { color: #34d399; font-weight: 500; }
.log-warning { color: #fbbf24; }
.log-header { color: #818cf8; font-weight: 600; }

/* Custom scrollbar for dark terminal */
.terminal-container::-webkit-scrollbar {
  width: 8px;
}
.terminal-container::-webkit-scrollbar-track {
  background: #0b0f19;
}
.terminal-container::-webkit-scrollbar-thumb {
  background: #1e293b;
  border-radius: 4px;
}
.terminal-container::-webkit-scrollbar-thumb:hover {
  background: #334155;
}
</style>
