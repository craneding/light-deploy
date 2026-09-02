<template>
  <div class="gitlab-projects-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">
          <el-icon class="title-icon"><Platform /></el-icon>
          GitLab 仓库
        </h1>
        <p class="page-subtitle">查看与检索您的 GitLab 组织及个人授权项目仓库</p>
      </div>
      <div class="header-actions">
        <el-input
          v-model="searchQuery"
          placeholder="搜索仓库名称或路径..."
          class="search-input"
          clearable
          @input="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button @click="loadProjects" :loading="loading" class="action-btn">
          <el-icon><Refresh /></el-icon> 刷新列表
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="content-area">
      <el-row :gutter="20" v-if="projects.length > 0">
        <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="project in projects" :key="project.id" class="card-col">
          <div class="repo-card">
            <div class="repo-head">
              <div class="repo-icon">
                <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
                  <path fill="#FC6D26" d="m23.6 9.6-1.5-4.6a.9.9 0 0 0-1.7 0L18.9 9.6H5.1L3.6 5a.9.9 0 0 0-1.7 0L.4 9.6a.9.9 0 0 0 .3 1l11.3 8.2 11.3-8.2a.9.9 0 0 0 .3-1Z"/>
                  <path fill="#E24329" d="M12 18.8 .4 9.6a.9.9 0 0 0 .3 1l11.3 8.2Z"/>
                  <path fill="#FCA326" d="M12 18.8 5.1 9.6h13.8L12 18.8Z"/>
                  <path fill="#E24329" d="m12 18.8 11.3-8.2a.9.9 0 0 0 .3-1L12 18.8Z"/>
                </svg>
              </div>
              <div class="repo-name-wrap">
                <span class="repo-name" :title="project.name">{{ project.name }}</span>
                <span class="repo-id font-mono">#{{ project.id }}</span>
              </div>
            </div>

            <div class="repo-body">
              <div class="repo-path font-mono" :title="project.name_with_namespace">
                {{ project.name_with_namespace }}
              </div>
            </div>

            <div class="repo-footer">
              <el-link :href="project.web_url" target="_blank" type="primary" :underline="false" class="gitlab-link">
                <span>在 GitLab 中查看</span>
                <el-icon><TopRight /></el-icon>
              </el-link>
            </div>
          </div>
        </el-col>
      </el-row>
      <el-empty v-else description="未检索到任何 GitLab 仓库数据" />
    </div>

    <div class="pagination-wrapper" v-if="totalCount > pageSize">
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
import { Refresh, TopRight, Search, Platform } from '@element-plus/icons-vue'
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

const loadProjects = async () => {
  loading.value = true
  try {
    const res: any = await fetchGitlabProjects(searchQuery.value?.trim(), currentPage.value, pageSize.value)
    if (res && res.list) {
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
.gitlab-projects-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.title-icon {
  color: var(--el-color-primary);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-input {
  width: 280px;
}

.card-col {
  margin-bottom: 20px;
}

.repo-card {
  background: #ffffff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  box-shadow: var(--shadow-xs);
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  height: 100%;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.repo-card:hover {
  border-color: #cbd5e1;
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.repo-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.repo-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #fff7ed;
  border: 1px solid #ffedd5;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.repo-name-wrap {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.repo-name {
  font-weight: 700;
  font-size: 15px;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.repo-id {
  font-size: 11px;
  color: #94a3b8;
}

.repo-body {
  margin-bottom: 16px;
}

.repo-path {
  font-size: 12.5px;
  color: #64748b;
  background: #f8fafc;
  padding: 6px 10px;
  border-radius: 6px;
  border: 1px solid #f1f5f9;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.repo-footer {
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid #f8fafc;
  display: flex;
  justify-content: flex-end;
}

.gitlab-link {
  font-size: 13px;
  font-weight: 500;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>
