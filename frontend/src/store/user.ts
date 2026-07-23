import { defineStore } from 'pinia'
import request from '../utils/request'
import { getItem, setItem, removeItem } from '../utils/storage'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getItem<string>('jwt_token') || '',
    userInfo: null as any
  }),
  actions: {
    setToken(token: string) {
      this.token = token
      setItem('jwt_token', token)
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
      removeItem('jwt_token')
      this.userInfo = null
    }
  }
})
