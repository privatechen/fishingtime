<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import fishLogo from '@/assets/svg/logo/fish-logo.svg'

const router = useRouter()
const username = ref('')
const password = ref('')

function handleLogin() {
  // TODO: 接入后端 API
  router.push('/')
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <router-link to="/" class="logo" style="justify-content:center;margin-bottom:24px">
        <img :src="fishLogo" alt="FishingTime" class="logo-img" />
        <span>FishingTime</span>
      </router-link>
      <h2 class="auth-title">登录</h2>
      <form class="auth-form" @submit.prevent="handleLogin">
        <div class="form-group">
          <label>用户名</label>
          <input v-model="username" type="text" placeholder="请输入用户名" required />
        </div>
        <div class="form-group">
          <label>密码</label>
          <input v-model="password" type="password" placeholder="请输入密码" required />
        </div>
        <button type="submit" class="btn btn-primary" style="margin-top:8px">登 录</button>
      </form>
      <div class="auth-footer">
        还没有账号？<router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </div>
</template>
