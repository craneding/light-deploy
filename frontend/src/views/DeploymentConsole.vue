<template>
  <div class="deployment-console">
    <div class="page-header">
      <div>
        <h1 class="page-title">部署控制台</h1>
        <p class="page-subtitle">任务 #{{ taskId }} 的实时日志输出</p>
      </div>
      <div>
        <el-tag :type="statusType" effect="light" round size="large" class="status-tag">
          {{ status || '连接中...' }}
        </el-tag>
        <el-button @click="goBack" class="action-btn" size="large">返回列表</el-button>
      </div>
    </div>

    <div class="terminal-card">
      <div class="terminal-header">
        <div class="terminal-dots">
          <span class="dot red"></span>
          <span class="dot yellow"></span>
          <span class="dot green"></span>
        </div>
        <div class="terminal-title">bash - deployment-task-{{ taskId }}</div>
      </div>
      <div class="terminal-container" ref="terminalContainer">
        <pre class="terminal-output">
          <span v-for="(log, index) in logs" :key="index" :class="getLogClass(log)">{{ log }}</span>
        </pre>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const taskId = route.params.id as string
const logs = ref<string[]>([])
const status = ref<string>('Connecting')
const terminalContainer = ref<HTMLElement | null>(null)
let ws: WebSocket | null = null

const statusType = computed(() => {
  if (status.value === 'Connected' || status.value === 'Success') return 'success'
  if (status.value === 'Connecting') return 'warning'
  if (status.value === 'Disconnected' || status.value === 'Error' || status.value === 'Failed') return 'danger'
  return 'info'
})

const getLogClass = (log: string) => {
  if (log.toLowerCase().includes('error') || log.toLowerCase().includes('failed')) {
    return 'log-error'
  }
  if (log.toLowerCase().includes('success') || log.toLowerCase().includes('done')) {
    return 'log-success'
  }
  if (log.toLowerCase().includes('warning')) {
    return 'log-warning'
  }
  return 'log-normal'
}

const scrollToBottom = () => {
  nextTick(() => {
    if (terminalContainer.value) {
      terminalContainer.value.scrollTop = terminalContainer.value.scrollHeight
    }
  })
}

const checkTaskStatusAndLogs = async () => {
  try {
    const res: any = await request.get(`/deploy-tasks/${taskId}`)
    const task = res.data || res
    
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
  let basePath = '/api'
  
  if (import.meta.env.VITE_API_BASE_URL) {
    const apiBaseUrl = new URL(import.meta.env.VITE_API_BASE_URL, window.location.origin)
    host = apiBaseUrl.host
    basePath = apiBaseUrl.pathname === '/' ? '' : apiBaseUrl.pathname
  } else if (import.meta.env.DEV) {
    // If no VITE_API_BASE_URL is set but we are in DEV, fallback to local backend for WS
    host = 'localhost:8080'
    basePath = ''
  }
  
  // Clean up any trailing slashes from basePath
  basePath = basePath.replace(/\/$/, '')
  
  const wsUrl = `${protocol}//${host}${basePath}/ws/deploy?taskId=${taskId}&token=${userStore.token}`
  
  try {
    ws = new WebSocket(wsUrl)
    
    ws.onopen = () => {
      status.value = 'Connected'
      logs.value.push(`> Connected to deployment console for task ${taskId}`)
      scrollToBottom()
    }
    
    ws.onmessage = (event) => {
      // Assuming event.data is a string or JSON. We handle string here.
      logs.value.push(event.data)
      scrollToBottom()
      
      // Basic heuristic to detect end of deployment
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
      logs.value.push(`> Connection closed.`)
      scrollToBottom()
    }
    
    ws.onerror = (_error) => {
      status.value = 'Error'
      logs.value.push(`> WebSocket error occurred.`)
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
  gap: 24px;
  height: calc(100vh - 120px);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0;
  flex-shrink: 0;
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

.status-tag {
  margin-right: 16px;
  font-weight: 600;
}

.action-btn {
  border-radius: 8px;
  padding: 10px 20px;
}

.terminal-card {
  flex: 1;
  border-radius: 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background-color: #0f172a;
  border: 1px solid #334155;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25) !important;
}

.terminal-header {
  height: 40px;
  background-color: #1e293b;
  display: flex;
  align-items: center;
  padding: 0 16px;
  border-bottom: 1px solid #334155;
  position: relative;
}

.terminal-dots {
  display: flex;
  gap: 8px;
}

.dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.dot.red { background-color: #ef4444; }
.dot.yellow { background-color: #f59e0b; }
.dot.green { background-color: #10b981; }

.terminal-title {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  color: #94a3b8;
  font-size: 13px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.terminal-container {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  background-color: #0f172a;
}

.terminal-output {
  margin: 0;
  display: flex;
  flex-direction: column;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.terminal-output span {
  line-height: 1.6;
  font-size: 14px;
}

.log-normal { color: #e2e8f0; } /* Changed to a more standard terminal text color (slate-200) */
.log-error { color: #fca5a5; }
.log-success { color: #6ee7b7; font-weight: bold; }
.log-warning { color: #fcd34d; }

/* Custom scrollbar for terminal */
.terminal-container::-webkit-scrollbar {
  width: 10px;
}
.terminal-container::-webkit-scrollbar-track {
  background: #0f172a;
}
.terminal-container::-webkit-scrollbar-thumb {
  background: #334155;
  border-radius: 5px;
}
.terminal-container::-webkit-scrollbar-thumb:hover {
  background: #475569;
}
</style>
