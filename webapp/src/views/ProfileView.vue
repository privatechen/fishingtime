<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '@/stores/auth'
import Header from '@/components/Header.vue'
import Footer from '@/components/Footer.vue'

const router = useRouter()
const { checkAuth } = useAuth()

const username = ref('')
const nickname = ref('')
const email = ref('')
const loading = ref(false)
const saving = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

async function loadProfile() {
  loading.value = true
  try {
    const res = await fetch('/api/users/me', { credentials: 'same-origin' })
    const json = await res.json()
    if (json.code === 200 && json.data) {
      username.value = json.data.username ?? ''
      nickname.value = json.data.nickname ?? ''
      email.value = json.data.email ?? ''
    } else {
      // 未登录或异常
      router.push('/login')
    }
  } catch {
    router.push('/login')
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  errorMsg.value = ''
  successMsg.value = ''

  if (!username.value.trim()) {
    errorMsg.value = '用户名不能为空'
    return
  }
  if (!nickname.value.trim()) {
    errorMsg.value = '昵称不能为空'
    return
  }

  saving.value = true
  try {
    const res = await fetch('/api/users/me', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify({
        username: username.value.trim(),
        nickname: nickname.value.trim(),
        email: email.value.trim() || null,
      }),
    })
    const json = await res.json()
    if (json.code === 200) {
      // 刷新登录态，Header 立即更新
      await checkAuth()
      successMsg.value = '保存成功'
    } else {
      errorMsg.value = json.message || '保存失败'
    }
  } catch {
    errorMsg.value = '网络异常，请稍后重试'
  } finally {
    saving.value = false
  }
}

onMounted(loadProfile)
</script>

<template>
  <Header />
  <div class="profile-page">
    <div class="profile-card">
      <h2 class="profile-title">个人资料</h2>

      <div v-if="loading" class="profile-state">加载中...</div>

      <form v-else class="profile-form" @submit.prevent="handleSave">
        <div class="form-group">
          <label>账号（用户名）</label>
          <input v-model="username" type="text" placeholder="请输入用户名" />
        </div>
        <div class="form-group">
          <label>昵称</label>
          <input v-model="nickname" type="text" placeholder="请输入昵称" />
        </div>
        <div class="form-group">
          <label>邮箱</label>
          <input v-model="email" type="email" placeholder="email@example.com" />
        </div>

        <p v-if="errorMsg" class="form-error">{{ errorMsg }}</p>
        <p v-if="successMsg" class="form-success">{{ successMsg }}</p>

        <div class="form-actions">
          <button type="button" class="btn-back" @click="router.push('/')">返回首页</button>
          <button type="submit" class="btn-save" :disabled="saving">
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </form>
    </div>
  </div>
  <Footer />
</template>

<style scoped>
.profile-page {
  max-width: var(--max-width);
  margin: 0 auto;
  padding: 40px 20px;
  display: flex;
  justify-content: center;
}

.profile-card {
  background: var(--color-card);
  border-radius: 16px;
  padding: 32px;
  width: 100%;
  max-width: 420px;
  box-shadow: var(--shadow);
}

.profile-title {
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 24px;
  text-align: center;
}

.profile-state {
  text-align: center;
  color: var(--color-text-muted);
  padding: 24px 0;
}

.profile-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-group label {
  display: block;
  font-size: 14px;
  color: var(--color-text-secondary);
  margin-bottom: 6px;
}

.form-group input {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
}

.form-group input:focus {
  border-color: var(--color-primary);
}

.form-error {
  color: #e74c3c;
  font-size: 13px;
}

.form-success {
  color: #52c41a;
  font-size: 13px;
}

.form-actions {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}

.btn-back,
.btn-save {
  flex: 1;
  padding: 10px;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  cursor: pointer;
}

.btn-back {
  background: var(--color-hover);
  color: var(--color-text);
}

.btn-save {
  background: var(--color-primary);
  color: #fff;
}

.btn-save:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
