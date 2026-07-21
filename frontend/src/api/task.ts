import request from '../utils/request'

export interface DeployTask {
  id?: number
  projectId: number
  projectName?: string
  profileId: number
  profileName?: string
  gitRefType: 'branch' | 'tag' | 'commit'
  gitRef: string
  status?: 'pending' | 'running' | 'success' | 'failed'
  logs?: string
  operator?: string
  startTime?: string
  endTime?: string
  createdAt?: string
}

export function fetchTasks(params?: { search?: string; page?: number; size?: number }) {
  return request.get('/deploy-tasks', { params })
}

export function getTaskById(id: number) {
  return request.get(`/deploy-tasks/${id}`)
}

export function createTask(data: DeployTask) {
  return request.post('/deploy-tasks', data)
}

export function stopTask(id: number) {
  return request.post(`/deploy-tasks/${id}/stop`)
}
