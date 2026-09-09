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

/** 拦截器已处理 GitLab 重登的错误标记，调用处据此不再重复 ElMessage */
export const GITLAB_REAUTH_FLAG = 'gitlabReauthHandled'

export const isGitlabReauthError = (error: any) =>
  !!error && (error as any)[GITLAB_REAUTH_FLAG] === true

const toGitlabReauthError = (detail?: string) => {
  const err = new Error(detail || 'GitLab 授权已过期，请重新登录 GitLab')
  ;(err as any)[GITLAB_REAUTH_FLAG] = true
  return err
}

/** 兼容旧后端/漏网 500：正文里还带着 GitLab 401 原文时同样视为授权过期 */
const isLegacyGitlabTokenError = (msg: string) =>
  /invalid_token/i.test(msg) ||
  /Error fetching (branches|projects|tags|commits)/i.test(msg)

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
      return Promise.reject(toGitlabReauthError(responseData?.detail))
    }

    const errorMsg = typeof responseData === 'string' ? responseData : (responseData?.message || error.message || 'Request Error');
    // 兜底：旧后端把 GitLab 401 原文透成 500 字符串时，不裸显英文，同样弹重登框
    if (typeof errorMsg === 'string' && isLegacyGitlabTokenError(errorMsg)) {
      promptGitlabReauth()
      return Promise.reject(toGitlabReauthError())
    }
    ElMessage.error(errorMsg)
    return Promise.reject(error)
  }
)

export default request
