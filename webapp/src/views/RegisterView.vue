<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '@/stores/auth'
import fishLogo from '@/assets/svg/logo/fish-logo.svg'

const router = useRouter()
const { register } = useAuth()

const username = ref('')
const nickname = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const errorMsg = ref('')

async function handleRegister() {
  errorMsg.value = ''

  // 前端校验
  if (!username.value.trim()) {
    errorMsg.value = '请输入账号'
    return
  }
  if (!nickname.value.trim()) {
    errorMsg.value = '请输入昵称'
    return
  }
  if (!password.value) {
    errorMsg.value = '请输入密码'
    return
  }
  if (password.value !== confirmPassword.value) {
    errorMsg.value = '两次输入的密码不一致'
    return
  }

  const err = await register({
    username: username.value.trim(),
    password: password.value,
    nickname: nickname.value.trim(),
    email: email.value.trim() || undefined,
  })

  if (err) {
    errorMsg.value = err
  } else {
    // 注册成功 → 自动登录已完成 → 跳首页
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
      <h2 class="auth-title">注册</h2>
      <form class="auth-form" @submit.prevent="handleRegister">
        <div class="form-group">
          <label>账号</label>
          <input v-model="username" type="text" placeholder="3~32个字符" />
        </div>
        <div class="form-group">
          <label>昵称</label>
          <input v-model="nickname" type="text" placeholder="输入昵称" />
        </div>
        <div class="form-group">
          <label>邮箱（选填）</label>
          <input v-model="email" type="email" placeholder="email@example.com" />
        </div>
        <div class="form-group">
          <label>密码</label>
          <input v-model="password" type="password" placeholder="至少6位" />
        </div>
        <div class="form-group">
          <label>确认密码</label>
          <input v-model="confirmPassword" type="password" placeholder="再次输入密码" />
        </div>
        <!-- 错误提示 — 显示在密码与按钮之间 -->
        <p v-if="errorMsg" class="auth-error">{{ errorMsg }}</p>
        <button type="submit" class="btn btn-primary">注 册</button>
      </form>
      <div class="auth-footer">
        已有账号？<router-link to="/login">立即登录</router-link>
      </div>
    </div>
  </div>
</template>
