<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useAuth } from '@/stores/auth'

const props = defineProps<{
  /** 提交成绩接口 */
  scoreUrl: string
  /** 我的最佳成绩接口 */
  myBestUrl: string
  /** 本局成绩（各游戏类型不同） */
  result: object
  /** 构建 POST body（默认得分制：bestScore/accuracy/switchAccuracy/maxStreak） */
  submitBody?: (result: object) => Record<string, unknown>
  /** 最佳成绩展示文案（默认：我的最高分 X · 最高连对 Y） */
  bestDisplay?: (myBest: object | null) => string
}>()

const emit = defineEmits<{ (e: 'saved'): void }>()

const { isLoggedIn, register, login } = useAuth()

const recordState = ref<'idle' | 'saving' | 'saved' | 'error'>('idle')
const myBest = ref<object | null>(null)
const authMode = ref<'register' | 'login'>('register')
const authUsername = ref('')
const authPassword = ref('')
const authConfirm = ref('')
const authError = ref('')
const authSubmitting = ref(false)

/** 得分制默认提交体（选颜色/方向陷阱） */
function defaultSubmitBody(r: any): Record<string, unknown> {
  return {
    bestScore: r.score,
    bestAccuracy: r.accuracy,
    bestAvgReaction: r.avgReactionTime,
    bestSwitchAccuracy: r.switchTotal > 0 ? r.switchCorrect / r.switchTotal : null,
    maxStreak: r.maxStreak,
  }
}

/** 得分制默认最佳展示 */
function defaultBestDisplay(mb: any): string {
  const parts: string[] = []
  if (mb?.bestScore != null) parts.push(`我的最高分 ${mb.bestScore}`)
  if (mb?.maxStreak) parts.push(`最高连对 ${mb.maxStreak}`)
  return parts.join(' · ')
}

const bestText = computed(() =>
  props.bestDisplay ? props.bestDisplay(myBest.value) : defaultBestDisplay(myBest.value),
)

/** 结果页渲染即自动触发：已登录直接保存，未登录显示注册引导 */
onMounted(() => {
  if (isLoggedIn.value) {
    void save()
  } else {
    recordState.value = 'idle'
  }
})

async function save(): Promise<void> {
  recordState.value = 'saving'
  const ok = await submitScore()
  if (ok) {
    recordState.value = 'saved'
    emit('saved')
    myBest.value = await loadMyBest()
  } else {
    recordState.value = 'error'
  }
}

async function submitScore(): Promise<boolean> {
  try {
    const body = props.submitBody ? props.submitBody(props.result) : defaultSubmitBody(props.result)
    const res = await fetch(props.scoreUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify(body),
    })
    const json = await res.json()
    return json.code === 200
  } catch {
    return false
  }
}

async function loadMyBest(): Promise<object | null> {
  try {
    const res = await fetch(props.myBestUrl, { credentials: 'same-origin' })
    const json = await res.json()
    return json.code === 200 ? (json.data ?? null) : null
  } catch {
    return null
  }
}

function switchAuthMode(mode: 'register' | 'login'): void {
  authMode.value = mode
  authError.value = ''
}

/** 注册/登录成功后自动保存本局成绩 */
async function handleAuthSubmit(): Promise<void> {
  authError.value = ''
  if (!authUsername.value.trim()) {
    authError.value = '请输入账号'
    return
  }
  if (!authPassword.value) {
    authError.value = '请输入密码'
    return
  }
  if (authMode.value === 'register' && authPassword.value !== authConfirm.value) {
    authError.value = '两次输入的密码不一致'
    return
  }
  authSubmitting.value = true
  try {
    const err =
      authMode.value === 'register'
        ? await register({
            username: authUsername.value.trim(),
            password: authPassword.value,
            nickname: authUsername.value.trim(),
          })
        : await login({ username: authUsername.value.trim(), password: authPassword.value })
    if (err) {
      authError.value = err
      return
    }
    await save()
  } finally {
    authSubmitting.value = false
  }
}
</script>

<template>
  <div class="record-area">
    <template v-if="isLoggedIn">
      <p v-if="recordState === 'saving'" class="record-tip">正在自动保存成绩...</p>
      <p v-else-if="recordState === 'saved'" class="record-tip record-ok">
        ✓ 已自动保存 · {{ bestText }}
      </p>
      <p v-else-if="recordState === 'error'" class="record-tip record-error">
        成绩保存失败，请稍后重试
      </p>
    </template>
    <template v-else>
      <p class="record-tip">本局成绩未记录，注册后可保存成绩并参与排行。</p>
      <div class="auth-mode">
        <button :class="{ active: authMode === 'register' }" @click="switchAuthMode('register')">
          注册
        </button>
        <button :class="{ active: authMode === 'login' }" @click="switchAuthMode('login')">
          登录
        </button>
      </div>
      <input v-model="authUsername" type="text" placeholder="账号" />
      <input v-model="authPassword" type="password" placeholder="密码" />
      <input
        v-if="authMode === 'register'"
        v-model="authConfirm"
        type="password"
        placeholder="确认密码"
      />
      <p v-if="authError" class="auth-error">{{ authError }}</p>
      <button class="auth-submit" :disabled="authSubmitting" @click="handleAuthSubmit">
        {{ authSubmitting ? '提交中...' : authMode === 'register' ? '注册并保存' : '登录并保存' }}
      </button>
    </template>
  </div>
</template>

<style scoped>
.record-area {
  margin-bottom: 20px;
  padding: 16px;
  background: var(--color-bg);
  border-radius: 12px;
  text-align: left;
}
.record-tip {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 12px;
  text-align: center;
}
.record-ok {
  color: #27ae60;
}
.record-error {
  color: var(--color-danger);
}
.auth-mode {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}
.auth-mode button {
  flex: 1;
  padding: 6px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-card);
  font-size: 13px;
  cursor: pointer;
  color: var(--color-text-secondary);
}
.auth-mode button.active {
  border-color: var(--color-primary);
  color: var(--color-primary);
  font-weight: 600;
}
.record-area input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
  margin-bottom: 8px;
}
.record-area input:focus {
  border-color: var(--color-primary);
}
.auth-error {
  color: var(--color-danger);
  font-size: 13px;
  margin-bottom: 8px;
}
.auth-submit {
  width: 100%;
  padding: 9px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 18px;
  font-size: 14px;
  cursor: pointer;
}
.auth-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
