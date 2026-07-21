<template>
  <div class="login-container">
    <div class="brand-bg"></div>
    <el-card class="login-card" shadow="always">
      <div class="card-header">
        <h2 class="brand-title">轻量部署</h2>
        <p class="brand-subtitle">简易而强大的自动化部署平台</p>
      </div>
      <div class="login-actions">
        <el-button type="primary" size="large" class="login-btn" @click="handleGitlabLogin">
          <template #icon><i class="iconfont"></i></template>
          使用 GitLab 登录
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import { onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const route = useRoute()

onMounted(() => {
  const error = route.query.error as string
  if (error === 'oauth_failed') {
    ElMessage.error('GitLab 登录失败，请重试')
  } else if (error === 'user_not_found') {
    ElMessage.error('该 GitLab 账号未在系统中注册')
  }
})

const handleGitlabLogin = () => {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/light-deploy/api'
  window.location.href = `${baseUrl}/oauth2/authorization/gitlab`
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #F9FAFB 0%, #E0E7FF 100%);
  position: relative;
  overflow: hidden;
}

.brand-bg {
  position: absolute;
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, var(--el-color-primary-light-7) 0%, transparent 70%);
  top: -200px;
  right: -200px;
  opacity: 0.5;
  border-radius: 50%;
  pointer-events: none;
}

.login-card {
  width: 100%;
  max-width: 420px;
  margin: 0 20px;
  text-align: center;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04) !important;
  z-index: 1;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  box-sizing: border-box;
}

.card-header {
  margin-bottom: 32px;
}

.brand-title {
  margin: 0;
  font-size: 32px;
  font-weight: 800;
  color: #111827;
  letter-spacing: -1px;
}

.brand-subtitle {
  margin: 8px 0 0;
  font-size: 15px;
  color: #6B7280;
}

.login-actions {
  display: flex;
  justify-content: center;
  padding: 10px 0 20px;
}

.login-btn {
  width: 100%;
  border-radius: 8px;
  font-size: 16px;
  height: 48px;
  box-shadow: 0 4px 6px -1px var(--el-color-primary-light-5);
}
</style>
