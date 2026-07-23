<template>
  <el-container class="layout-container">
    <el-header class="header">
      <div class="logo">轻量部署平台</div>
      
      <!-- Desktop Navigation -->
      <div class="nav-links desktop-only">
        <router-link to="/dashboard" active-class="active"><el-button link>概览</el-button></router-link>
        <router-link to="/gitlab" active-class="active"><el-button link>GitLab 项目</el-button></router-link>
        <router-link to="/projects" active-class="active"><el-button link>项目管理</el-button></router-link>
        <router-link to="/servers" active-class="active"><el-button link>服务器管理</el-button></router-link>
        <router-link to="/tasks" active-class="active"><el-button link>部署任务</el-button></router-link>
      </div>

      <!-- Desktop User Profile -->
      <div class="user-actions desktop-only">
        <el-dropdown trigger="click" @command="handleCommand">
          <div class="user-profile">
            <el-avatar :size="32" :src="userStore.userInfo?.avatarUrl" class="user-avatar">
              {{ userStore.userInfo?.username?.charAt(0)?.toUpperCase() || 'U' }}
            </el-avatar>
            <span class="username">{{ userStore.userInfo?.username || 'Loading...' }}</span>
            <el-icon class="el-icon--right"><arrow-down /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout" class="logout-item">
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <!-- Mobile Hamburger Menu Icon -->
      <div class="mobile-only mobile-menu-icon" @click="drawerVisible = true">
        <el-icon :size="24"><Menu /></el-icon>
      </div>
    </el-header>

    <!-- Mobile Navigation Drawer -->
    <el-drawer
      v-model="drawerVisible"
      title="菜单"
      direction="rtl"
      size="65%"
      :with-header="false"
      class="mobile-drawer"
    >
      <div class="drawer-content">
        <div class="drawer-header">
          <el-avatar :size="48" :src="userStore.userInfo?.avatarUrl" class="user-avatar">
            {{ userStore.userInfo?.username?.charAt(0)?.toUpperCase() || 'U' }}
          </el-avatar>
          <span class="drawer-username">{{ userStore.userInfo?.username || 'Loading...' }}</span>
        </div>
        
        <el-divider />
        
        <div class="drawer-nav">
          <router-link to="/dashboard" active-class="drawer-active" @click="drawerVisible = false">
            <el-icon><HomeFilled /></el-icon> 概览
          </router-link>
          <router-link to="/gitlab" active-class="drawer-active" @click="drawerVisible = false">
            <el-icon><Platform /></el-icon> GitLab 项目
          </router-link>
          <router-link to="/projects" active-class="drawer-active" @click="drawerVisible = false">
            <el-icon><Folder /></el-icon> 项目管理
          </router-link>
          <router-link to="/servers" active-class="drawer-active" @click="drawerVisible = false">
            <el-icon><Monitor /></el-icon> 服务器管理
          </router-link>
          <router-link to="/tasks" active-class="drawer-active" @click="drawerVisible = false">
            <el-icon><List /></el-icon> 部署任务
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
      <div class="footer">
        <span class="footer-text">Light Deploy © 2026</span>
        <a href="https://github.com/craneding/" target="_blank" class="footer-link">@craneding</a>
        <span class="version">v{{ version }}</span>
      </div>
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
  HomeFilled, 
  Platform,
  Folder, 
  Monitor, 
  List 
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
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
.layout-container {
  min-height: 100vh;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  box-shadow: var(--el-box-shadow-light);
  padding: 0 40px;
  height: 64px;
  position: sticky;
  top: 0;
  z-index: 100;
}

.logo {
  font-size: 22px;
  font-weight: 800;
  color: var(--el-color-primary);
  letter-spacing: -0.5px;
}

.nav-links {
  display: flex;
  gap: 24px;
}

.nav-links a {
  text-decoration: none;
}

.nav-links .el-button {
  font-size: 15px;
  color: #6B7280;
}

.nav-links a.active .el-button {
  color: var(--el-color-primary);
  font-weight: 600;
}

.main-content {
  padding: 32px;
  max-width: 1440px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}

.user-profile {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: var(--el-border-radius-round);
  transition: background-color 0.2s ease;
}

.user-profile:hover {
  background-color: #F3F4F6;
}

.user-avatar {
  background-color: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
  font-weight: 600;
}

.username {
  font-size: 14px;
  font-weight: 500;
  color: #374151;
}

.logout-item {
  color: var(--el-color-danger);
}
.logout-item:hover {
  background-color: var(--el-color-danger-light-9) !important;
  color: var(--el-color-danger) !important;
}

.footer {
  text-align: center;
  padding: 24px 0 0;
  margin-top: auto;
  color: #9CA3AF;
  font-size: 13px;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
}

.footer-link {
  color: #9CA3AF;
  text-decoration: none;
  transition: color 0.2s;
}

.footer-link:hover {
  color: var(--el-color-primary);
}

.version {
  margin-left: auto;
  font-size: 12px;
  color: #D1D5DB;
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
  
  .logo {
    font-size: 18px;
  }
  
  .mobile-menu-icon {
    cursor: pointer;
    color: var(--el-color-primary);
    padding: 8px;
    border-radius: 8px;
    transition: background-color 0.2s ease;
  }
  
  .mobile-menu-icon:active {
    background-color: #F3F4F6;
  }
  
  .layout-container {
    padding-bottom: env(safe-area-inset-bottom);
  }
  
  .main-content {
    padding: 16px;
  }

  /* Drawer Styles */
  .drawer-content {
    display: flex;
    flex-direction: column;
    height: 100%;
    padding: 20px;
  }

  .drawer-header {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12px;
    padding-top: 20px;
  }

  .drawer-username {
    font-size: 18px;
    font-weight: 600;
    color: #111827;
  }

  .drawer-nav {
    display: flex;
    flex-direction: column;
    gap: 8px;
    flex: 1;
  }

  .drawer-nav a {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 16px;
    border-radius: 12px;
    text-decoration: none;
    color: #4B5563;
    font-size: 16px;
    font-weight: 500;
    transition: all 0.2s ease;
  }

  .drawer-nav a .el-icon {
    font-size: 20px;
  }

  .drawer-nav a.drawer-active {
    background-color: var(--el-color-primary-light-9);
    color: var(--el-color-primary);
  }

  .drawer-nav a:active {
    background-color: #F3F4F6;
  }

  .drawer-footer {
    margin-top: auto;
    padding-bottom: env(safe-area-inset-bottom);
  }

  .logout-btn-full {
    width: 100%;
    height: 48px;
    font-size: 16px;
    border-radius: 12px;
  }
}
</style>