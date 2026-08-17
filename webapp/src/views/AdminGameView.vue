<script setup lang="ts">
import { ref } from 'vue'
import Header from '@/components/Header.vue'
import Footer from '@/components/Footer.vue'
import { useAdminAuth } from '@/stores/adminAuth'

const { isAdmin, token, login, logout } = useAdminAuth()

const username = ref('')
const password = ref('')
const loginError = ref('')
const loginSubmitting = ref(false)

const file = ref<File | null>(null)
const text = ref('')
const submitting = ref(false)
const result = ref<{ imageKey: string; action: string; questionCount: number } | null>(null)
const uploadError = ref('')

async function handleLogin() {
  loginError.value = ''
  if (!username.value.trim() || !password.value) {
    loginError.value = '请输入账号和密码'
    return
  }
  loginSubmitting.value = true
  try {
    const err = await login(username.value.trim(), password.value)
    if (err) loginError.value = err
  } finally {
    loginSubmitting.value = false
  }
}

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  file.value = input.files?.[0] || null
}

async function handleUpload() {
  if (submitting.value) return
  uploadError.value = ''
  result.value = null
  if (!file.value) {
    uploadError.value = '请选择图片'
    return
  }
  if (!text.value.trim()) {
    uploadError.value = '请输入题目文本'
    return
  }
  const form = new FormData()
  form.append('file', file.value)
  form.append('text', text.value)
  submitting.value = true
  try {
    const res = await fetch('/api/games/detail/admin/upload', {
      method: 'POST',
      headers: { 'X-Admin-Token': token.value },
      credentials: 'same-origin',
      body: form,
    })
    const json = await res.json()
    if (json.code === 200 && json.data) {
      result.value = json.data
    } else {
      uploadError.value = json.message || '上传失败'
    }
  } catch {
    uploadError.value = '网络异常，上传失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <Header />
  <div class="admin-page">
    <!-- 未登录：登录框 -->
    <div v-if="!isAdmin" class="card login-card">
      <h1 class="title">细节管理后台</h1>
      <p class="subtitle">请使用管理员账号登录</p>
      <input v-model="username" type="text" placeholder="账号" />
      <input v-model="password" type="password" placeholder="密码" @keyup.enter="handleLogin" />
      <p v-if="loginError" class="error">{{ loginError }}</p>
      <button class="btn-primary" :disabled="loginSubmitting" @click="handleLogin">
        {{ loginSubmitting ? '登录中...' : '登录' }}
      </button>
    </div>

    <!-- 已登录：上传区 -->
    <div v-else class="card upload-card">
      <div class="upload-head">
        <h1 class="title">细节管理后台</h1>
        <button class="btn-text" @click="logout">退出登录</button>
      </div>

      <div class="field">
        <label class="label">图片（jpg；文件名决定图片标识，如 PicA.jpg → pic_a）</label>
        <input class="file-input" type="file" accept="image/*" @change="onFileChange" />
      </div>

      <div class="field">
        <label class="label">题目文本（每题 3 行：问题 / 选项 / 答案，共 30 行 = 10 题）</label>
        <textarea v-model="text" class="textarea" rows="14" placeholder="示例：
女孩的围裙是什么颜色？
A. 蓝色　B. 粉色　C. 黄色　D. 绿色
答案：B
..." />
      </div>

      <p v-if="uploadError" class="error">{{ uploadError }}</p>

      <button class="btn-primary" :disabled="submitting" @click="handleUpload">
        {{ submitting ? '上传中...' : '上传并保存' }}
      </button>

      <div v-if="result" class="result">
        <p class="result-line">
          <b>{{ result.imageKey }}</b>
          <span class="action" :class="result.action === 'updated' ? 'text-updated' : 'text-created'">
            {{ result.action === 'updated' ? '已更新' : '已新增' }}
          </span>
          · {{ result.questionCount }} 道题
        </p>
        <img v-if="result.imageKey" class="preview" :src="`/games/detail/${result.imageKey}.jpg`" alt="预览" />
      </div>
    </div>
  </div>
  <Footer />
</template>

<style scoped>
.admin-page {
  max-width: 620px;
  margin: 0 auto;
  padding: 48px 20px;
  min-height: calc(100vh - var(--header-height));
}
.card {
  background: var(--color-card);
  border-radius: 16px;
  box-shadow: var(--shadow);
  padding: 32px;
}
.title {
  font-size: 26px;
  font-weight: 700;
  margin-bottom: 8px;
}
.subtitle {
  color: var(--color-text-secondary);
  font-size: 14px;
  margin-bottom: 24px;
}
.login-card input,
.upload-card input[type='password'] {
  width: 100%;
  box-sizing: border-box;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  font-size: 15px;
  margin-bottom: 12px;
}
.upload-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.btn-text {
  background: none;
  border: none;
  color: var(--color-text-secondary);
  cursor: pointer;
  font-size: 14px;
}
.field {
  margin-bottom: 18px;
}
.label {
  display: block;
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 8px;
}
.file-input {
  font-size: 14px;
}
.textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.6;
  font-family: monospace;
  resize: vertical;
}
.btn-primary {
  width: 100%;
  padding: 12px;
  border: none;
  border-radius: 20px;
  background: var(--color-primary);
  color: #fff;
  font-size: 15px;
  cursor: pointer;
}
.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.error {
  color: var(--color-danger);
  font-size: 13px;
  margin: 8px 0;
}
.result {
  margin-top: 20px;
  padding: 16px;
  background: var(--color-bg);
  border-radius: 12px;
}
.result-line {
  font-size: 15px;
  margin-bottom: 12px;
}
.action {
  margin: 0 6px;
}
.text-updated {
  color: #2d8cf0;
}
.text-created {
  color: #27ae60;
}
.preview {
  max-width: 100%;
  max-height: 280px;
  border-radius: 8px;
  display: block;
}
</style>
