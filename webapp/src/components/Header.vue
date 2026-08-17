<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuth } from '@/stores/auth'
import { useAdminAuth } from '@/stores/adminAuth'
import fishLogo from '@/assets/svg/logo/fish-logo.svg'

const router = useRouter()
const route = useRoute()
const scrolled = ref(false)
const { user, isLoggedIn, logout } = useAuth()
const { isAdmin } = useAdminAuth()

const navItems = [
  { name: '首页', path: '/', key: 'home' },
  { name: '热榜', path: '/', key: 'hot' },
  { name: '社区', path: '/', key: 'community' },
  { name: '小游戏', path: '/games', key: 'game' },
]

function handleScroll() {
  scrolled.value = window.scrollY > 10
}

async function handleLogout() {
  await logout()
  router.push('/')
}

onMounted(() => window.addEventListener('scroll', handleScroll))
onUnmounted(() => window.removeEventListener('scroll', handleScroll))
</script>

<template>
  <header class="header" :class="{ scrolled }">
    <div class="header-inner">
      <!-- Logo -->
      <router-link to="/" class="logo">
        <img :src="fishLogo" alt="FishingTime" class="logo-img" />
        <span>FishingTime</span>
      </router-link>

      <!-- Navigation -->
      <nav class="nav">
        <router-link
          v-for="item in navItems"
          :key="item.key"
          :to="item.path"
          class="nav-link"
          :class="{ active: route.path === item.path && item.key === 'home' }"
        >
          {{ item.name }}
        </router-link>
        <!-- 管理入口：管理后台登录后显示 -->
        <router-link v-if="isAdmin" to="/admin/game" class="nav-link">管理</router-link>
      </nav>

      <!-- Right -->
      <div class="header-right">
        <div class="search-box">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"/>
            <path d="m21 21-4.3-4.3"/>
          </svg>
          <input type="text" placeholder="搜索感兴趣的内容..." />
        </div>

        <!-- 已登录 -->
        <template v-if="isLoggedIn">
          <router-link to="/profile" class="header-nickname">{{ user?.nickname }}</router-link>
          <button class="btn btn-outline" @click="handleLogout">退出</button>
        </template>

        <!-- 未登录 -->
        <template v-else>
          <router-link to="/login" class="btn btn-outline">登录</router-link>
          <router-link to="/register" class="btn btn-primary">注册</router-link>
        </template>
      </div>
    </div>
  </header>
</template>

<style scoped>
.header-nickname {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text);
  text-decoration: none;
  cursor: pointer;
}

.header-nickname:hover {
  color: var(--color-primary);
}
</style>
