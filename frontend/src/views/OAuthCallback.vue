<template>
  <div class="callback-container">
    <div class="callback-card">
      <div class="spinner-ring"></div>
      <h3 class="callback-title">正在完成 GitLab 授权验证</h3>
      <p class="callback-sub">正在为您建立安全会话并获取项目权限，请稍候...</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

onMounted(() => {
  const token = route.query.token as string

  if (token) {
    userStore.setToken(token)
    ElMessage.success('登录成功，欢迎回来')
    router.replace('/dashboard')
  } else {
    ElMessage.error('登录失败，未获取到授权凭证')
    router.replace('/login')
  }
})
</script>

<style scoped>
.callback-container {
  height: 100vh;
  width: 100vw;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #0b0f19;
  padding: 20px;
}

.callback-card {
  background: rgba(17, 24, 39, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 16px;
  padding: 36px 32px;
  text-align: center;
  max-width: 400px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5);
  display: flex;
  flex-direction: column;
  align-items: center;
}

.spinner-ring {
  width: 44px;
  height: 44px;
  border: 3px solid rgba(99, 102, 241, 0.2);
  border-top-color: #6366f1;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 20px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.callback-title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: #f8fafc;
}

.callback-sub {
  margin: 8px 0 0;
  font-size: 13px;
  color: #94a3b8;
  line-height: 1.5;
}
</style>
