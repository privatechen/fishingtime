<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '@/stores/auth'
import fishLogo from '@/assets/svg/logo/fish-logo.svg'

const router = useRouter()
const { login } = useAuth()

const username = ref('')
const password = ref('')
const errorMsg = ref('')

async function handleLogin() {
  errorMsg.value = ''

  // 前端校验
  if (!username.value.trim()) {
    errorMsg.value = '请输入账号'
    return
  }
  if (!password.value) {
    errorMsg.value = '请输入密码'
    return
  }

  const err = await login({
    username: username.value.trim(),
    password: password.value,
  })

  if (err) {
    errorMsg.value = err
  } else {
    router.push('/')
  }
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
          <label>账号</label>
          <input v-model="username" type="text" placeholder="请输入用户名" />
        </div>
        <div class="form-group">
          <label>密码</label>
          <input v-model="password" type="password" placeholder="请输入密码" />
        </div>
        <!-- 错误提示 — 显示在密码与按钮之间 -->
        <p v-if="errorMsg" class="auth-error">{{ errorMsg }}</p>
        <button type="submit" class="btn btn-primary">登 录</button>
      </form>
      <div class="auth-footer">
        还没有账号？<router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </div>
</template>
