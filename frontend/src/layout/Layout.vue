<template>
  <el-container class="layout-container">
    <el-header class="header">
      <!-- Brand Logo -->
      <div class="brand-section" @click="router.push('/dashboard')">
        <div class="brand-icon">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2L2 7L12 12L22 7L12 2Z" fill="white" fill-opacity="0.9"/>
            <path d="M2 17L12 22L22 17" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M2 12L12 17L22 12" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <div class="brand-text">
          <span class="brand-title">LightDeploy</span>
          <span class="brand-badge">PRO</span>
        </div>
      </div>
      
      <!-- Desktop Navigation -->
      <nav class="nav-links desktop-only">
        <router-link to="/dashboard" class="nav-item" active-class="active">
          <el-icon><DataAnalysis /></el-icon>
          <span>概览</span>
        </router-link>
        <router-link to="/gitlab" class="nav-item" active-class="active">
          <el-icon><Platform /></el-icon>
          <span>GitLab 仓库</span>
        </router-link>
        <router-link to="/projects" class="nav-item" active-class="active">
          <el-icon><Folder /></el-icon>
          <span>项目管理</span>
        </router-link>
        <router-link to="/servers" class="nav-item" active-class="active">
          <el-icon><Monitor /></el-icon>
          <span>服务器</span>
        </router-link>
        <router-link to="/tasks" class="nav-item" active-class="active">
          <el-icon><DocumentCopy /></el-icon>
          <span>部署任务</span>
        </router-link>
      </nav>

      <!-- Desktop User Profile -->
      <div class="user-actions desktop-only">
        <el-dropdown trigger="click" @command="handleCommand">
          <div class="user-profile-pill">
            <el-avatar :size="28" :src="userStore.userInfo?.avatarUrl" class="user-avatar">
              {{ userStore.userInfo?.username?.charAt(0)?.toUpperCase() || 'U' }}
            </el-avatar>
            <span class="username">{{ userStore.userInfo?.username || '用户' }}</span>
            <el-icon class="arrow-icon"><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu class="custom-dropdown-menu">
              <div class="dropdown-user-header">
                <div class="user-name-full">{{ userStore.userInfo?.name || userStore.userInfo?.username || '开发者' }}</div>
                <div class="user-sub">GitLab 认证用户</div>
              </div>
              <el-dropdown-item divided command="logout" class="logout-item">
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <!-- Mobile Hamburger Menu Icon -->
      <div class="mobile-only mobile-menu-icon" @click="drawerVisible = true">
        <el-icon :size="22"><Menu /></el-icon>
      </div>
    </el-header>

    <!-- Mobile Navigation Drawer -->
    <el-drawer
      v-model="drawerVisible"
      title="菜单"
      direction="rtl"
      size="280px"
      :with-header="false"
      class="mobile-drawer"
    >
      <div class="drawer-content">
        <div class="drawer-header">
          <el-avatar :size="48" :src="userStore.userInfo?.avatarUrl" class="user-avatar-lg">
            {{ userStore.userInfo?.username?.charAt(0)?.toUpperCase() || 'U' }}
          </el-avatar>
          <div class="drawer-user-info">
            <span class="drawer-username">{{ userStore.userInfo?.username || '开发者' }}</span>
            <span class="drawer-role">GitLab 已认证</span>
          </div>
        </div>
        
        <div class="drawer-nav">
          <router-link to="/dashboard" active-class="drawer-active" @click="drawerVisible = false">
            <el-icon><DataAnalysis /></el-icon> 概览仪表盘
          </router-link>
          <router-link to="/gitlab" active-class="drawer-active" @click="drawerVisible = false">
            <el-icon><Platform /></el-icon> GitLab 仓库
          </router-link>
          <router-link to="/projects" active-class="drawer-active" @click="drawerVisible = false">
            <el-icon><Folder /></el-icon> 项目管理
          </router-link>
          <router-link to="/servers" active-class="drawer-active" @click="drawerVisible = false">
            <el-icon><Monitor /></el-icon> 服务器管理
          </router-link>
          <router-link to="/tasks" active-class="drawer-active" @click="drawerVisible = false">
            <el-icon><DocumentCopy /></el-icon> 部署任务
          </router-link>
        </div>
        
        <div class="drawer-footer">
          <el-button type="danger" plain class="logout-btn-full" @click="handleLogout">
            <el-icon><SwitchButton /></el-icon>退出登录
          </el-button>
        </div>
      </div>
    </el-drawer>

    <el-main class="main-content">
      <router-view />
      
      <footer class="footer">
        <div class="footer-status">
          <span class="status-indicator"></span>
          <span>系统运行正常</span>
        </div>
        <div class="footer-center">
          <span>Light Deploy</span>
          <span>•</span>
          <a href="https://github.com/craneding/" target="_blank" class="footer-link">@craneding</a>
        </div>
        <div class="footer-version">v{{ version }}</div>
      </footer>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import { ElMessage } from 'element-plus'
import { 
  ArrowDown, 
  SwitchButton, 
  Menu, 
  DataAnalysis,
  Platform,
  Folder, 
  Monitor, 
  DocumentCopy
} from '@element-plus/icons-vue'
import { version } from '../../package.json'

const router = useRouter()
const userStore = useUserStore()
const drawerVisible = ref(false)

onMounted(() => {
  if (userStore.token && !userStore.userInfo) {
    userStore.fetchUserInfo()
  }
})

const handleCommand = (command: string) => {
  if (command === 'logout') {
    handleLogout()
  }
}

const handleLogout = () => {
  drawerVisible.value = false
  userStore.logout()
  ElMessage.success('已安全退出登录')
  router.push('/login')
}
</script>

<style scoped>
.layout-container {
  min-height: 100vh;
  background-color: var(--bg-app);
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(226, 232, 240, 0.8);
  padding: 0 32px;
  height: 60px;
  position: sticky;
  top: 0;
  z-index: 100;
}

.brand-section {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  user-select: none;
}

.brand-icon {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 10px rgba(79, 70, 229, 0.3);
}

.brand-text {
  display: flex;
  align-items: center;
  gap: 6px;
}

.brand-title {
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: -0.03em;
}

.brand-badge {
  font-size: 10px;
  font-weight: 700;
  background: #eef2ff;
  color: #4f46e5;
  padding: 1px 6px;
  border-radius: 4px;
  border: 1px solid #c7d2fe;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 6px;
  background: #f1f5f9;
  padding: 4px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  font-size: 13.5px;
  font-weight: 500;
  color: #64748b;
  text-decoration: none;
  border-radius: 7px;
  transition: all 0.2s ease;
}

.nav-item:hover {
  color: #0f172a;
}

.nav-item.active {
  background: #ffffff;
  color: #4f46e5;
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.main-content {
  padding: 28px 32px 20px;
  max-width: 1440px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 60px);
}

.user-profile-pill {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 10px 4px 5px;
  border-radius: 20px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  transition: all 0.2s ease;
}

.user-profile-pill:hover {
  border-color: #cbd5e1;
  background-color: #f8fafc;
}

.user-avatar {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #ffffff;
  font-weight: 600;
  font-size: 13px;
}

.username {
  font-size: 13.5px;
  font-weight: 500;
  color: #334155;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.arrow-icon {
  font-size: 12px;
  color: #94a3b8;
}

.dropdown-user-header {
  padding: 10px 16px 6px;
}

.user-name-full {
  font-weight: 600;
  color: #0f172a;
  font-size: 14px;
}

.user-sub {
  font-size: 12px;
  color: #64748b;
  margin-top: 2px;
}

.logout-item {
  color: #ef4444;
}

.logout-item:hover {
  background-color: #fef2f2 !important;
  color: #dc2626 !important;
}

.footer {
  margin-top: auto;
  padding-top: 36px;
  padding-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #94a3b8;
  font-size: 12.5px;
  border-top: 1px solid #f1f5f9;
}

.footer-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #64748b;
}

.status-indicator {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background-color: #10b981;
  box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.2);
}

.footer-center {
  display: flex;
  align-items: center;
  gap: 8px;
}

.footer-link {
  color: #64748b;
  text-decoration: none;
  font-weight: 500;
  transition: color 0.2s;
}

.footer-link:hover {
  color: #4f46e5;
}

.footer-version {
  font-family: var(--font-mono);
  font-size: 11.5px;
  color: #94a3b8;
}

/* Mobile Responsiveness */
.mobile-only {
  display: none;
}

@media (max-width: 768px) {
  .desktop-only {
    display: none !important;
  }
  
  .mobile-only {
    display: flex;
  }
  
  .header {
    padding: 0 16px;
  }
  
  .mobile-menu-icon {
    cursor: pointer;
    color: #334155;
    padding: 6px;
    border-radius: 8px;
    background: #f1f5f9;
  }
  
  .main-content {
    padding: 16px;
  }

  .footer {
    flex-direction: column;
    gap: 10px;
    text-align: center;
  }

  .drawer-content {
    display: flex;
    flex-direction: column;
    height: 100%;
    padding: 24px 16px;
  }

  .drawer-header {
    display: flex;
    align-items: center;
    gap: 14px;
    padding-bottom: 20px;
    border-bottom: 1px solid #f1f5f9;
  }

  .user-avatar-lg {
    background: linear-gradient(135deg, #6366f1, #8b5cf6);
    font-weight: 700;
    font-size: 18px;
  }

  .drawer-user-info {
    display: flex;
    flex-direction: column;
  }

  .drawer-username {
    font-size: 16px;
    font-weight: 700;
    color: #0f172a;
  }

  .drawer-role {
    font-size: 12px;
    color: #64748b;
  }

  .drawer-nav {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin-top: 20px;
    flex: 1;
  }

  .drawer-nav a {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 14px;
    border-radius: 9px;
    text-decoration: none;
    color: #475569;
    font-size: 14.5px;
    font-weight: 500;
    transition: all 0.2s ease;
  }

  .drawer-nav a.drawer-active {
    background-color: #eef2ff;
    color: #4f46e5;
    font-weight: 600;
  }

  .drawer-footer {
    margin-top: auto;
    padding-top: 16px;
  }

  .logout-btn-full {
    width: 100%;
    height: 42px;
    border-radius: 8px;
  }
}
</style>
