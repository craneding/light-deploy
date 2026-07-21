<template>
  <div class="dashboard-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">GitLab 项目</h1>
        <p class="page-subtitle">管理并查看您绑定的 GitLab 仓库</p>
      </div>
      <div class="header-actions">
        <el-input
          v-model="searchQuery"
          placeholder="搜索 GitLab 项目..."
          class="search-input"
          clearable
          @input="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" size="large" @click="loadProjects" :loading="loading" class="refresh-btn">
          <template #icon>
            <el-icon><Refresh /></el-icon>
          </template>
          刷新列表
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="content-area">
      <el-row :gutter="24" v-if="projects.length > 0">
        <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="(project, index) in projects" :key="project.id" style="margin-bottom: 24px;">
          <el-card class="project-card" :style="{ '--card-theme-color': getRandomColor(index) }">
            <div class="card-header-line"></div>
            <template #header>
              <div class="project-title">
                <div class="title-index">{{ String((currentPage - 1) * pageSize + index + 1).padStart(2, '0') }}</div>
                <span :title="project.name" class="text-ellipsis">{{ project.name }}</span>
              </div>
            </template>
            <div class="project-info">
              <div class="info-row">
                <span class="info-label">ID</span>
                <span class="info-value">{{ project.id }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">命名空间</span>
                <span class="info-value text-ellipsis" :title="project.name_with_namespace">{{ project.name_with_namespace }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">URL</span>
                <el-link type="primary" :href="project.web_url" target="_blank" class="text-ellipsis" :title="project.web_url">前往 GitLab <el-icon><TopRight /></el-icon></el-link>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-else description="暂无项目数据"></el-empty>
    </div>

    <div class="pagination-container" v-if="totalCount > pageSize">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[12, 24, 48, 96]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="totalCount"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        background
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Refresh, TopRight, Search } from '@element-plus/icons-vue'
import { fetchGitlabProjects, type GitlabProject } from '../api/gitlab'

const projects = ref<GitlabProject[]>([])
const loading = ref(false)
const searchQuery = ref('')
const currentPage = ref(1)
const pageSize = ref(12)
const totalCount = ref(0)
let searchTimeout: any = null

const handleSearch = () => {
  currentPage.value = 1
  if (searchTimeout) clearTimeout(searchTimeout)
  searchTimeout = setTimeout(() => {
    loadProjects()
  }, 300)
}

const handleSizeChange = (val: number) => {
  pageSize.value = val
  loadProjects()
}

const handleCurrentChange = (val: number) => {
  currentPage.value = val
  loadProjects()
}

const themeColors = [
  '#4f46e5', // Indigo
  '#10b981', // Emerald
  '#f59e0b', // Amber
  '#ef4444', // Red
  '#8b5cf6', // Violet
  '#06b6d4', // Cyan
  '#ec4899', // Pink
  '#f97316'  // Orange
]

const getRandomColor = (index: number) => {
  return themeColors[index % themeColors.length]
}

const loadProjects = async () => {
  loading.value = true
  try {
    const res: any = await fetchGitlabProjects(searchQuery.value?.trim(), currentPage.value, pageSize.value)
    // The backend now returns { list: [...], total: ... }
    if (res.list) {
      projects.value = res.list
      totalCount.value = res.total
    } else {
      projects.value = res.data || res || []
      totalCount.value = projects.value.length
    }
  } catch (error) {
    console.error('Failed to load projects', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadProjects()
})
</script>

<style scoped>
.dashboard-container {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.pagination-container {
  display: flex;
  justify-content: center;
  margin-top: 24px;
  padding: 20px 0;
  background-color: #ffffff;
  border-radius: 12px;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.05);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0;
}

.page-title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: #111827;
  letter-spacing: -0.5px;
}

.page-subtitle {
  margin: 8px 0 0 0;
  font-size: 14px;
  color: #6B7280;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.search-input {
  width: 300px;
}

:deep(.search-input .el-input__wrapper) {
  border-radius: 8px;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
}

.refresh-btn {
  border-radius: 8px;
  padding: 10px 20px;
}

.project-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}

.card-header-line {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background-color: var(--card-theme-color, var(--el-color-primary));
  z-index: 2;
}

:deep(.el-card__header) {
  padding: 20px 24px;
  border-bottom: 1px solid rgba(243, 244, 246, 0.8);
  background: linear-gradient(to right, rgba(255, 255, 255, 0.9), rgba(249, 250, 251, 0.5));
  position: relative;
}

:deep(.el-card__body) {
  padding: 24px;
  flex: 1;
  background-color: #ffffff;
}

.project-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-weight: 700;
  font-size: 17px;
  color: #111827;
  letter-spacing: -0.3px;
}

.title-index {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 14px;
  font-weight: 700;
  color: var(--card-theme-color, var(--el-color-primary));
  background: color-mix(in srgb, var(--card-theme-color, var(--el-color-primary)) 15%, transparent);
  padding: 6px 10px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.project-info {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  padding: 8px 12px;
  background: #F9FAFB;
  border-radius: 8px;
  transition: background-color 0.2s ease;
}

.info-row:hover {
  background: #F3F4F6;
}

.info-label {
  color: #6B7280;
  font-weight: 600;
  flex-shrink: 0;
  margin-right: 12px;
  font-size: 13px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.info-value {
  color: #374151;
  font-weight: 500;
  text-align: right;
}

.text-ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 180px;
  display: inline-block;
  vertical-align: bottom;
}

.el-link {
  font-size: 14px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
