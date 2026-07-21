<template>
  <div class="callback-container" v-loading="true" element-loading-text="正在处理登录回调，请稍候...">
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
    ElMessage.success('登录成功')
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
}
</style>
