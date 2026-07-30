<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import fishLogo from '@/assets/svg/logo/fish-logo.svg'

const router = useRouter()
const username = ref('')
const nickname = ref('')
const email = ref('')
const password = ref('')

function handleRegister() {
  // TODO: 接入后端 API
  router.push('/login')
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <router-link to="/" class="logo" style="justify-content:center;margin-bottom:24px">
        <img :src="fishLogo" alt="FishingTime" class="logo-img" />
        <span>FishingTime</span>
      </router-link>
      <h2 class="auth-title">注册</h2>
      <form class="auth-form" @submit.prevent="handleRegister">
        <div class="form-group">
          <label>用户名</label>
          <input v-model="username" type="text" placeholder="3~32个字符" required />
        </div>
        <div class="form-group">
          <label>昵称</label>
          <input v-model="nickname" type="text" placeholder="输入昵称" required />
        </div>
        <div class="form-group">
          <label>邮箱（选填）</label>
          <input v-model="email" type="email" placeholder="email@example.com" />
        </div>
        <div class="form-group">
          <label>密码</label>
          <input v-model="password" type="password" placeholder="至少6位" required />
        </div>
        <button type="submit" class="btn btn-primary" style="margin-top:8px">注 册</button>
      </form>
      <div class="auth-footer">
        已有账号？<router-link to="/login">立即登录</router-link>
      </div>
    </div>
  </div>
</template>
