import { defineStore } from 'pinia'
import request from '../utils/request'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('jwt_token') || '',
    userInfo: null as any
  }),
  actions: {
    setToken(token: string) {
      this.token = token
      localStorage.setItem('jwt_token', token)
    },
    async fetchUserInfo() {
      try {
        const res = await request.get('/user/info')
        this.userInfo = res
      } catch (error) {
        console.error('Failed to fetch user info', error)
      }
    },
    logout() {
      this.token = ''
      localStorage.removeItem('jwt_token')
      this.userInfo = null
    }
  }
})
