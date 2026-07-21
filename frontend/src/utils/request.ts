import axios, { type AxiosInstance, type InternalAxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../store/user'

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
    if (res.code && res.code !== 200) {
      if (res.code === 401 || String(res.message || '').includes('401')) {
        console.error('401 Error Intercepted in response! URL:', response.config.url, 'Data:', res);
        const userStore = useUserStore()
        userStore.logout()
        window.location.href = '/login'
        return Promise.reject(new Error(res.message || 'Unauthorized'))
      }
      ElMessage.error(res.message || 'Error')
      return Promise.reject(new Error(res.message || 'Error'))
    }
    return res
  },
  (error: any) => {
    const responseData = error.response?.data;
    
    // Check if the error indicates unauthorized access (401)
    let isUnauthorized = error.response?.status === 401;
    
    if (error.response?.status === 500 && responseData) {
      if (typeof responseData === 'object') {
        isUnauthorized = responseData.code === 401 || String(responseData.message || '').includes('401');
      } else if (typeof responseData === 'string') {
        isUnauthorized = responseData.includes('401');
      }
    }

    if (isUnauthorized) {
      console.error('401 Error Intercepted! URL:', error.response?.config?.url, 'Data:', responseData);
      const userStore = useUserStore()
      userStore.logout()
      window.location.href = '/login'
    } else {
      const errorMsg = typeof responseData === 'string' ? responseData : (responseData?.message || error.message || 'Request Error');
      ElMessage.error(errorMsg)
    }
    return Promise.reject(error)
  }
)

export default request
