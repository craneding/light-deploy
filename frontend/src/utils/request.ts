import axios, { type AxiosInstance, type InternalAxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '../store/user'

/** GitLab 授权过期确认框防重复 */
let gitlabReauthDialogOpen = false

const redirectToGitlabOAuth = () => {
  const baseURL = request.defaults.baseURL || '/light-deploy/api'
  window.location.href = `${baseURL}/oauth2/authorization/gitlab`
}

const promptGitlabReauth = (detail?: string) => {
  if (gitlabReauthDialogOpen) return
  gitlabReauthDialogOpen = true
  ElMessageBox.confirm(
    `GitLab 授权已过期${detail ? `（${detail}）` : ''}，需要重新登录 GitLab 才能继续。`,
    'GitLab 授权过期',
    {
      confirmButtonText: '重新登录 GitLab',
      cancelButtonText: '稍后再说',
      type: 'warning'
    }
  ).then(() => {
    redirectToGitlabOAuth()
  }).catch(() => {
    // 用户取消：停留当前页，保留表单状态
  }).finally(() => {
    gitlabReauthDialogOpen = false
  })
}

const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/light-deploy/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// Request Interceptor
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const userStore = useUserStore()
    if (userStore.token && config.headers) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error: any) => {
    return Promise.reject(error)
  }
)

// Response Interceptor
request.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data
    // Handle successful responses based on your API structure
    // 仅业务 code 401 视为应用会话过期；GitLab 相关失败走 HTTP 440，不在此分支
    if (res.code && res.code !== 200) {
      if (res.code === 401) {
        console.error('401 Error Intercepted in response! URL:', response.config.url, 'Data:', res);
        const userStore = useUserStore()
        userStore.logout()
        window.location.href = '/light-deploy/login'
        return Promise.reject(new Error(res.message || 'Unauthorized'))
      }
      ElMessage.error(res.message || 'Error')
      return Promise.reject(new Error(res.message || 'Error'))
    }
    return res
  },
  (error: any) => {
    const responseData = error.response?.data;
    const status = error.response?.status;

    // 应用自身会话过期（后端唯一返回 HTTP 401 的场景）：清 token 并跳登录
    if (status === 401) {
      console.error('401 Error Intercepted! URL:', error.response?.config?.url, 'Data:', responseData);
      const userStore = useUserStore()
      userStore.logout()
      window.location.href = '/light-deploy/login'
      return Promise.reject(new Error(typeof responseData === 'string' ? responseData : (responseData?.message || 'Unauthorized')))
    }

    // GitLab OAuth 授权过期：与应用会话严格区分，只引导重登 GitLab，不清 token、不跳登录
    if (status === 440 || responseData?.message === 'GITLAB_TOKEN_EXPIRED') {
      promptGitlabReauth(responseData?.detail)
      return Promise.reject(new Error(responseData?.detail || 'GitLab 授权已过期，请重新登录 GitLab'))
    }

    const errorMsg = typeof responseData === 'string' ? responseData : (responseData?.message || error.message || 'Request Error');
    ElMessage.error(errorMsg)
    return Promise.reject(error)
  }
)

export default request
