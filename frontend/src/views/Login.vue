<template>
  <div class="login-container">
    <!-- Ambient Background Lighting -->
    <div class="ambient-glow glow-top"></div>
    <div class="ambient-glow glow-bottom"></div>
    <div class="grid-overlay"></div>

    <div class="login-card-wrapper">
      <div class="login-card">
        <!-- Logo & Header -->
        <div class="brand-header">
          <div class="brand-badge-icon">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 2L2 7L12 12L22 7L12 2Z" fill="white" fill-opacity="0.95"/>
              <path d="M2 17L12 22L22 17" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M2 12L12 17L22 12" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
          <h1 class="brand-title">LightDeploy</h1>
          <p class="brand-subtitle">轻量、极速的现代前端与服务自动化部署平台</p>
        </div>

        <!-- Features Showcase -->
        <div class="feature-pills">
          <div class="feature-item">
            <span class="feature-dot"></span>
            <span>GitLab 仓库多分支与 Tag 同步</span>
          </div>
          <div class="feature-item">
            <span class="feature-dot"></span>
            <span>多环境配置与 Rsync 极速增量发布</span>
          </div>
          <div class="feature-item">
            <span class="feature-dot"></span>
            <span>WebSocket 实时终端构建与错误诊断</span>
          </div>
        </div>

        <!-- Action Button -->
        <div class="login-actions">
          <button class="gitlab-btn" @click="handleGitlabLogin">
            <svg class="gitlab-icon" viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
              <path fill="#FC6D26" d="m23.6 9.6-1.5-4.6a.9.9 0 0 0-1.7 0L18.9 9.6H5.1L3.6 5a.9.9 0 0 0-1.7 0L.4 9.6a.9.9 0 0 0 .3 1l11.3 8.2 11.3-8.2a.9.9 0 0 0 .3-1Z"/>
              <path fill="#E24329" d="M12 18.8 .4 9.6a.9.9 0 0 0 .3 1l11.3 8.2Z"/>
              <path fill="#FCA326" d="M12 18.8 5.1 9.6h13.8L12 18.8Z"/>
              <path fill="#E24329" d="m12 18.8 11.3-8.2a.9.9 0 0 0 .3-1L12 18.8Z"/>
            </svg>
            <span>使用 GitLab 账号快捷登录</span>
          </button>
        </div>

        <!-- Security & Version Info -->
        <div class="card-footer">
          <span class="secure-tag">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
              <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
            </svg>
            OAuth 2.0 安全认证
          </span>
        </div>
      </div>
    </div>
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
  background-color: #0b0f19;
  position: relative;
  overflow: hidden;
  padding: 20px;
}

/* Ambient glow effects */
.ambient-glow {
  position: absolute;
  width: 550px;
  height: 550px;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(120px);
  opacity: 0.35;
}

.glow-top {
  background: #6366f1;
  top: -150px;
  right: -100px;
}

.glow-bottom {
  background: #4f46e5;
  bottom: -150px;
  left: -100px;
}

.grid-overlay {
  position: absolute;
  inset: 0;
  background-image: 
    linear-gradient(to right, rgba(255, 255, 255, 0.03) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(255, 255, 255, 0.03) 1px, transparent 1px);
  background-size: 32px 32px;
  pointer-events: none;
}

.login-card-wrapper {
  position: relative;
  z-index: 10;
  width: 100%;
  max-width: 440px;
}

.login-card {
  background: rgba(17, 24, 39, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 20px;
  padding: 40px 32px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.6);
  text-align: center;
}

.brand-header {
  margin-bottom: 28px;
}

.brand-badge-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10px 25px -5px rgba(99, 102, 241, 0.5);
  margin-bottom: 18px;
}

.brand-title {
  margin: 0;
  font-size: 26px;
  font-weight: 800;
  color: #f8fafc;
  letter-spacing: -0.03em;
}

.brand-subtitle {
  margin: 10px 0 0;
  font-size: 13.5px;
  color: #94a3b8;
  line-height: 1.5;
}

.feature-pills {
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  padding: 14px 16px;
  margin-bottom: 28px;
  text-align: left;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: #cbd5e1;
}

.feature-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: #10b981;
  flex-shrink: 0;
}

.login-actions {
  margin-bottom: 20px;
}

.gitlab-btn {
  width: 100%;
  height: 48px;
  background: #ffffff;
  color: #0f172a;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 10px;
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.gitlab-btn:hover {
  background: #f8fafc;
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.3);
}

.gitlab-btn:active {
  transform: translateY(0);
}

.gitlab-icon {
  flex-shrink: 0;
}

.card-footer {
  display: flex;
  justify-content: center;
}

.secure-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #64748b;
}
</style>
