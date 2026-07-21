import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '../store/user'

const routes: Array<RouteRecordRaw> = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/login/success',
    name: 'LoginSuccess',
    component: () => import('../views/OAuthCallback.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/oauth/callback',
    name: 'OAuthCallback',
    component: () => import('../views/OAuthCallback.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('../layout/Layout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue')
      },
      {
        path: 'gitlab',
        name: 'GitlabProjects',
        component: () => import('../views/GitlabProjects.vue')
      },
      {
        path: 'servers',
        name: 'ServerManagement',
        component: () => import('../views/ServerManagement.vue')
      },
      {
        path: 'projects',
        name: 'ProjectManagement',
        component: () => import('../views/ProjectManagement.vue')
      },
      {
        path: 'tasks',
        name: 'DeployTasks',
        component: () => import('../views/DeployTasks.vue')
      },
      {
        path: 'tasks/:id/console',
        name: 'DeploymentConsole',
        component: () => import('../views/DeploymentConsole.vue')
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory('/light-deploy/'),
  routes
})

// Navigation Guards
router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()
  const isAuthenticated = !!userStore.token

  if (to.meta.requiresAuth && !isAuthenticated) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
  } else if (to.name === 'Login' && isAuthenticated) {
    next({ name: 'Dashboard' })
  } else {
    next()
  }
})

export default router
