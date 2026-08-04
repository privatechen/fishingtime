<script setup lang="ts">
import { ref } from 'vue'
import { useAuth } from '@/stores/auth'

defineProps<{ visible: boolean }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'registered'): void }>()

const { register } = useAuth()
const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const errorMsg = ref('')
const submitting = ref(false)

async function handleSubmit() {
  errorMsg.value = ''

  if (!username.value.trim()) {
    errorMsg.value = '请输入账号'
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

  submitting.value = true
  try {
    const err = await register({
      username: username.value.trim(),
      password: password.value,
      nickname: username.value.trim(), // 昵称直接用账号
    })
    if (err) {
      errorMsg.value = err
      return
    }
    emit('registered')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div v-if="visible" class="dialog-mask" @click.self="emit('close')">
    <div class="dialog">
      <h3 class="dialog-title">保存游戏记录</h3>
      <p class="dialog-tip">保存分数前需要注册账号，昵称将自动生成。</p>

      <div class="form-group">
        <label>账号</label>
        <input v-model="username" type="text" placeholder="请输入账号" />
      </div>
      <div class="form-group">
        <label>密码</label>
        <input v-model="password" type="password" placeholder="请输入密码" />
      </div>
      <div class="form-group">
        <label>确认密码</label>
        <input v-model="confirmPassword" type="password" placeholder="请再次输入密码" />
      </div>

      <p v-if="errorMsg" class="error-msg">{{ errorMsg }}</p>

      <div class="dialog-actions">
        <button class="btn-cancel" @click="emit('close')">取消</button>
        <button class="btn-submit" :disabled="submitting" @click="handleSubmit">
          {{ submitting ? '注册中...' : '注册并保存' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dialog-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.dialog {
  background: #fff;
  border-radius: 16px;
  padding: 28px;
  width: 340px;
  max-width: 90%;
}

.dialog-title {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 8px;
}

.dialog-tip {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 16px;
}

.form-group {
  margin-bottom: 12px;
}

.form-group label {
  display: block;
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 4px;
}

.form-group input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
}

.form-group input:focus {
  border-color: var(--color-primary);
}

.error-msg {
  color: #e74c3c;
  font-size: 13px;
  margin-bottom: 12px;
}

.dialog-actions {
  display: flex;
  gap: 12px;
}

.btn-cancel,
.btn-submit {
  flex: 1;
  padding: 10px;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  cursor: pointer;
}

.btn-cancel {
  background: var(--color-hover);
  color: var(--color-text);
}

.btn-submit {
  background: var(--color-primary);
  color: #fff;
}

.btn-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
