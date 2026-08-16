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
    {
      path: '/games/collision',
      name: 'gameCollision',
      component: () => import('@/games/collision/components/CollisionGameView.vue'),
    },
    {
      path: '/games/fish-breakout',
      name: 'gameFishBreakout',
      component: () => import('@/games/fish-breakout/components/FishBreakoutGameView.vue'),
    },
    {
      path: '/games/extreme-fishing',
      name: 'gameExtremeFishing',
      component: () => import('@/games/extreme-fishing/components/ExtremeFishingGameView.vue'),
    },
    {
      path: '/games/color-focus',
      name: 'gameColorFocus',
      component: () => import('@/games/color-focus/components/ColorFocusGameView.vue'),
    },
    {
      path: '/games/direction-trap',
      name: 'gameDirectionTrap',
      component: () => import('@/games/direction-trap/components/DirectionTrapGameView.vue'),
    },
    {
      path: '/games/color-hunter',
      name: 'gameColorHunter',
      component: () => import('@/games/color-hunter/components/ColorHunterGameView.vue'),
    },
    {
      path: '/games/detail',
      name: 'gameDetail',
      component: () => import('@/games/detail/DetailGameView.vue'),
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('@/views/ProfileView.vue'),
    },
  ],
})

export default router
