import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
    },
    {
      path: '/games',
      name: 'games',
      component: () => import('@/games/components/hall/GameHall.vue'),
    },
    {
      path: '/games/2048',
      name: 'game2048',
      component: () => import('@/games/components/2048/Game2048.vue'),
    },
  ],
})

export default router
