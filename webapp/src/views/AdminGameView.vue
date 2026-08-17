<script setup lang="ts">
import { ref, onMounted } from 'vue'
import Header from '@/components/Header.vue'
import Footer from '@/components/Footer.vue'
import { useAdminAuth } from '@/stores/adminAuth'

interface AdminQuestion {
  questionText: string
  optionA: string
  optionB: string
  optionC: string
  optionD: string
  correctOption: string
}
interface AdminImage {
  imageKey: string
  questionCount: number
  questions: AdminQuestion[]
}

const { isAdmin, checkAdmin } = useAdminAuth()

// ── 列表 / 编辑状态 ──
const view = ref<'list' | 'edit'>('list')
const images = ref<AdminImage[]>([])
const loadingImages = ref(false)
const error = ref('')

const editingKey = ref('') // '' = 新增
const text = ref('')
const file = ref<File | null>(null)
const submitting = ref(false)
const brokenImages = ref<Set<string>>(new Set())

async function loadImages() {
  loadingImages.value = true
  error.value = ''
  try {
    const res = await fetch('/api/games/detail/admin/images', { credentials: 'same-origin' })
    const json = await res.json()
    if (json.code === 200 && json.data) {
      images.value = json.data
    } else {
      error.value = json.message || '加载失败'
    }
  } catch {
    error.value = '网络异常，加载失败'
  } finally {
    loadingImages.value = false
  }
}

function imageUrl(key: string) {
  return `/games/detail/${key}.jpg`
}

function onImgError(key: string) {
  const s = new Set(brokenImages.value)
  s.add(key)
  brokenImages.value = s
}

/** 题目 → 30 行标准文本 */
function questionsToText(qs: AdminQuestion[]): string {
  return qs
    .map(
      (q) =>
        `${q.questionText}\nA. ${q.optionA}　B. ${q.optionB}　C. ${q.optionC}　D. ${q.optionD}\n答案：${q.correctOption}`,
    )
    .join('\n')
}

function openNew() {
  editingKey.value = ''
  text.value = ''
  file.value = null
  error.value = ''
  view.value = 'edit'
}

function openEdit(img: AdminImage) {
  editingKey.value = img.imageKey
  text.value = questionsToText(img.questions)
  file.value = null
  error.value = ''
  view.value = 'edit'
}

function backToList() {
  view.value = 'list'
  void loadImages()
}

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  file.value = input.files?.[0] || null
}

async function save() {
  if (submitting.value) return
  error.value = ''
  if (!text.value.trim()) {
    error.value = '请输入题目文本'
    return
  }
  if (!editingKey.value && !file.value) {
    error.value = '新增图片必须选择图片文件'
    return
  }
  const form = new FormData()
  if (file.value) form.append('file', file.value)
  form.append('text', text.value)
  if (editingKey.value) form.append('imageKey', editingKey.value)
  submitting.value = true
  try {
    const res = await fetch('/api/games/detail/admin/upload', {
      method: 'POST',
      credentials: 'same-origin',
      body: form,
    })
    const json = await res.json()
    if (json.code === 200 && json.data) {
      backToList()
    } else {
      error.value = json.message || '保存失败'
    }
  } catch {
    error.value = '网络异常，保存失败'
  } finally {
    submitting.value = false
  }
}

async function removeImage(img: AdminImage) {
  if (!window.confirm(`确定删除图片「${img.imageKey}」及其 ${img.questionCount} 道题吗？`)) return
  try {
    const res = await fetch(`/api/games/detail/admin/images/${encodeURIComponent(img.imageKey)}`, {
      method: 'DELETE',
      credentials: 'same-origin',
    })
    const json = await res.json()
    if (json.code === 200) {
      await loadImages()
    } else {
      error.value = json.message || '删除失败'
    }
  } catch {
    error.value = '网络异常，删除失败'
  }
}

onMounted(async () => {
  await checkAdmin()
  if (isAdmin.value) void loadImages()
})
</script>

<template>
  <Header />
  <div class="admin-page">
    <!-- 无权限 -->
    <div v-if="!isAdmin" class="card login-card">
      <h1 class="title">无访问权限</h1>
      <p class="subtitle">仅管理员账号（admin）可访问此页面。</p>
      <router-link to="/login" class="btn-primary link-btn">去登录</router-link>
    </div>

    <!-- 已登录 -->
    <template v-else>
      <!-- ── 列表视图 ── -->
      <div v-if="view === 'list'" class="card list-card">
        <div class="list-head">
          <div>
            <h1 class="title">细节图片管理</h1>
            <p class="subtitle">共 {{ images.length }} 张图 · 点击卡片可查看/编辑</p>
          </div>
          <button class="btn-add" @click="openNew">＋ 新增图片</button>
        </div>

        <p v-if="error" class="error">{{ error }}</p>
        <p v-if="loadingImages" class="hint">加载中...</p>

        <div v-else-if="images.length === 0" class="hint">还没有图片，点「新增图片」添加第一张</div>

        <div v-else class="img-grid">
          <div v-for="img in images" :key="img.imageKey" class="img-card">
            <div class="img-cover" @click="openEdit(img)">
              <img v-if="!brokenImages.has(img.imageKey)" :src="imageUrl(img.imageKey)" class="img-preview" alt="" @error="onImgError(img.imageKey)" />
              <div v-else class="img-broken">图片文件缺失</div>
            </div>
            <div class="img-info" @click="openEdit(img)">
              <div class="img-key">{{ img.imageKey }}</div>
              <div class="img-count">{{ img.questionCount }} 道题</div>
            </div>
            <button class="btn-delete" @click="removeImage(img)">删除</button>
          </div>
        </div>
      </div>

      <!-- ── 编辑视图 ── -->
      <div v-else class="card edit-card">
        <div class="list-head">
          <div>
            <h1 class="title">{{ editingKey ? `编辑：${editingKey}` : '新增图片' }}</h1>
            <p class="subtitle">每 3 行一题（问题 / 选项 / 答案）</p>
          </div>
          <button class="btn-text" @click="backToList">← 返回列表</button>
        </div>

        <!-- 预览 + 文件选择 -->
        <div class="edit-img-row">
          <img v-if="editingKey && !brokenImages.has(editingKey)" :src="imageUrl(editingKey)" class="edit-preview" alt="" @error="onImgError(editingKey)" />
          <div v-if="editingKey && brokenImages.has(editingKey)" class="edit-preview edit-broken">图片文件缺失</div>
          <div class="file-picker">
            <label class="file-btn">
              <input type="file" accept="image/*" class="file-input" @change="onFileChange" />
              选择图片{{ file ? '（已选）' : '' }}
            </label>
            <span class="file-name">{{ file ? file.name : '不选则保留原图 / 新增必须选择' }}</span>
          </div>
        </div>

        <textarea v-model="text" class="textarea" rows="16" placeholder="每题 3 行：问题 / 选项 / 答案" />

        <p v-if="error" class="error">{{ error }}</p>

        <div class="edit-actions">
          <button class="btn-secondary" :disabled="submitting" @click="backToList">取消</button>
          <button class="btn-primary" :disabled="submitting" @click="save">
            {{ submitting ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </template>
  </div>
  <Footer />
</template>

<style scoped>
.admin-page {
  max-width: 760px;
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
  margin-bottom: 20px;
}
.login-card input {
  width: 100%;
  box-sizing: border-box;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  font-size: 15px;
  margin-bottom: 12px;
}
.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.btn-text {
  background: none;
  border: none;
  color: var(--color-text-secondary);
  cursor: pointer;
  font-size: 14px;
  white-space: nowrap;
}
.error {
  color: var(--color-danger);
  font-size: 13px;
  margin: 8px 0;
}
.hint {
  color: var(--color-text-muted);
  font-size: 14px;
  text-align: center;
  padding: 24px 0;
}

/* ── 图片列表 ── */
.img-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 16px;
}
.img-card {
  border: 1px solid var(--color-border);
  border-radius: 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.img-cover {
  cursor: pointer;
}
.img-preview {
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  display: block;
  background: var(--color-bg);
}
.img-broken {
  width: 100%;
  aspect-ratio: 4 / 3;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-danger);
  font-size: 13px;
  background: var(--color-bg);
}
.img-info {
  padding: 10px 12px;
  cursor: pointer;
}
.img-key {
  font-size: 14px;
  font-weight: 600;
  word-break: break-all;
}
.img-count {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-top: 2px;
}
.btn-delete {
  margin: 0 12px 12px;
  padding: 6px;
  border: 1px solid var(--color-danger);
  border-radius: 8px;
  background: none;
  color: var(--color-danger);
  font-size: 13px;
  cursor: pointer;
}
.btn-delete:hover {
  background: #fdecea;
}

/* ── 编辑视图 ── */
.edit-img-row {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  margin-bottom: 18px;
}
.edit-preview {
  width: 220px;
  border-radius: 10px;
  display: block;
  flex-shrink: 0;
}
.edit-broken {
  height: 130px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg);
  color: var(--color-danger);
  font-size: 13px;
}
.file-picker {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}
.file-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 10px 18px;
  border: 1px dashed var(--color-primary);
  border-radius: 10px;
  color: var(--color-primary);
  font-size: 14px;
  cursor: pointer;
  background: #f0f7ff;
}
.file-btn:hover {
  background: #e4f0ff;
}
.file-input {
  display: none;
}
.file-name {
  font-size: 12px;
  color: var(--color-text-secondary);
  word-break: break-all;
}
.textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.7;
  font-family: monospace;
  resize: vertical;
  min-height: 240px;
}
.edit-actions {
  display: flex;
  gap: 12px;
  margin-top: 18px;
}
.btn-primary,
.btn-secondary {
  flex: 1;
  padding: 12px;
  border: none;
  border-radius: 20px;
  font-size: 15px;
  cursor: pointer;
}
.btn-primary {
  background: var(--color-primary);
  color: #fff;
}
.btn-secondary {
  background: var(--color-hover);
  color: var(--color-text);
}
.btn-primary:disabled,
.btn-secondary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.link-btn {
  display: block;
  text-align: center;
  text-decoration: none;
  margin-top: 12px;
}
.btn-add {
  padding: 10px 18px;
  border: none;
  border-radius: 18px;
  background: var(--color-primary);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  white-space: nowrap;
}
.btn-add:hover {
  opacity: 0.9;
}
</style>
