<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'

interface Category {
  id: number
  code: string
  name: string
  icon: string
  sortOrder: number
  status: number
  questionCount: number
}
interface Option {
  id?: number
  content: string
  icon: string
  sortOrder: number
  voteCount?: number
}
interface Question {
  id: number
  categoryId: number
  categoryName: string
  content: string
  description: string
  status: number
  answerCount: number
  recommendScore: number
  sortOrder: number
  options: Option[]
}

const categories = ref<Category[]>([])
const questions = ref<Question[]>([])
const loading = ref(false)
const error = ref('')
const activeCategoryId = ref<number | null>(null)

// 分类编辑态
const showCatForm = ref(false)
const catForm = ref({ id: 0, code: '', name: '', icon: '', sortOrder: 0, status: 1 })

// 题目编辑态
const showQForm = ref(false)
const editingQId = ref(0)
const qForm = ref({
  categoryId: 0,
  content: '',
  description: '',
  recommendScore: 1,
  sortOrder: 0,
  status: 1,
  options: [] as { content: string; icon: string; sortOrder: number }[],
})

async function api(path: string, method = 'GET', body?: unknown) {
  const res = await fetch(`/api/qa/admin${path}`, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    credentials: 'same-origin',
    body: body ? JSON.stringify(body) : undefined,
  })
  const json = await res.json()
  if (json.code !== 200) throw new Error(json.message || '请求失败')
  return json.data
}

async function loadCategories() {
  loading.value = true
  error.value = ''
  try {
    categories.value = await api('/categories')
    if (activeCategoryId.value == null && categories.value.length) {
      activeCategoryId.value = categories.value[0].id
    }
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

async function loadQuestions() {
  if (activeCategoryId.value == null) return
  questions.value = await api(`/questions?categoryId=${activeCategoryId.value}`)
}

watch(activeCategoryId, () => void loadQuestions())
onMounted(() => void loadCategories())

// ── 分类 ──
function openCatForm(c?: Category) {
  catForm.value = c
    ? { id: c.id, code: c.code, name: c.name, icon: c.icon, sortOrder: c.sortOrder, status: c.status }
    : { id: 0, code: '', name: '', icon: '', sortOrder: categories.value.length + 1, status: 1 }
  showCatForm.value = true
}

async function saveCategory() {
  try {
    await api('/categories', catForm.value.id ? 'PUT' : 'POST', catForm.value)
    showCatForm.value = false
    await loadCategories()
  } catch (e) {
    error.value = (e as Error).message
  }
}

async function deleteCategory(c: Category) {
  if (!window.confirm(`删除分类「${c.name}」？（该分类下没有题目才能删）`)) return
  try {
    await api(`/categories/${c.id}`, 'DELETE')
    await loadCategories()
  } catch (e) {
    error.value = (e as Error).message
  }
}

// ── 题目 ──
function openQForm(q?: Question) {
  editingQId.value = q?.id || 0
  qForm.value = q
    ? {
        categoryId: q.categoryId,
        content: q.content,
        description: q.description,
        recommendScore: q.recommendScore,
        sortOrder: q.sortOrder,
        status: q.status,
        options: q.options.map((o) => ({ content: o.content, icon: o.icon, sortOrder: o.sortOrder })),
      }
    : {
        categoryId: activeCategoryId.value || 0,
        content: '',
        description: '',
        recommendScore: 1,
        sortOrder: questions.value.length + 1,
        status: 1,
        options: [
          { content: '', icon: '', sortOrder: 1 },
          { content: '', icon: '', sortOrder: 2 },
        ],
      }
  showQForm.value = true
}

function addOption() {
  qForm.value.options.push({ content: '', icon: '', sortOrder: qForm.value.options.length + 1 })
}

async function saveQuestion() {
  try {
    if (editingQId.value) {
      await api(`/questions/${editingQId.value}`, 'PUT', qForm.value)
    } else {
      await api('/questions', 'POST', qForm.value)
    }
    showQForm.value = false
    await loadQuestions()
  } catch (e) {
    error.value = (e as Error).message
  }
}

async function deleteQuestion(q: Question) {
  if (!window.confirm(`删除题目「${q.content.slice(0, 20)}」？`)) return
  try {
    await api(`/questions/${q.id}`, 'DELETE')
    await loadQuestions()
  } catch (e) {
    error.value = (e as Error).message
  }
}

async function toggleStatus(q: Question) {
  const next = q.status === 1 ? 2 : 1
  try {
    await api(`/questions/${q.id}/status?status=${next}`, 'PATCH')
    await loadQuestions()
  } catch (e) {
    error.value = (e as Error).message
  }
}
</script>

<template>
  <div class="qa-manage">
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="loading" class="hint">加载中...</p>

    <!-- 分类区 -->
    <div class="block">
      <div class="block-head">
        <h3 class="block-title">分类（{{ categories.length }}）</h3>
        <button class="btn-sm" @click="openCatForm()">＋ 新增分类</button>
      </div>
      <div class="cat-list">
        <div
          v-for="c in categories"
          :key="c.id"
          class="cat-item"
          :class="{ active: activeCategoryId === c.id }"
          @click="activeCategoryId = c.id"
        >
          <span class="cat-icon">{{ c.icon }}</span>
          <span class="cat-name">{{ c.name }}（{{ c.questionCount }}题）</span>
          <span class="cat-actions">
            <button class="link" @click.stop="openCatForm(c)">编辑</button>
            <button class="link danger" @click.stop="deleteCategory(c)">删除</button>
          </span>
        </div>
      </div>
    </div>

    <!-- 分类编辑弹层 -->
    <div v-if="showCatForm" class="mask" @click.self="showCatForm = false">
      <div class="dialog">
        <h4>{{ catForm.id ? '编辑分类' : '新增分类' }}</h4>
        <input v-model="catForm.name" placeholder="名称（日常）" />
        <input v-model="catForm.code" placeholder="编码（daily）" :disabled="!!catForm.id" />
        <input v-model="catForm.icon" placeholder="图标 emoji（🌱）" />
        <div class="row">
          <input v-model.number="catForm.sortOrder" type="number" placeholder="排序" />
          <select v-model.number="catForm.status">
            <option :value="1">启用</option>
            <option :value="0">停用</option>
          </select>
        </div>
        <div class="dialog-actions">
          <button class="btn-sm" @click="showCatForm = false">取消</button>
          <button class="btn-sm primary" @click="saveCategory">保存</button>
        </div>
      </div>
    </div>

    <!-- 题目区 -->
    <div class="block">
      <div class="block-head">
        <h3 class="block-title">
          题目{{ activeCategoryId ? '（' + categories.find((c) => c.id === activeCategoryId)?.name + '）' : '' }}
          （{{ questions.length }}）
        </h3>
        <button class="btn-sm" @click="openQForm()">＋ 新增题目</button>
      </div>
      <div v-if="questions.length === 0" class="hint">该分类暂无题目</div>
      <div v-else class="q-list">
        <div v-for="q in questions" :key="q.id" class="q-item">
          <div class="q-main">
            <span class="q-status" :class="q.status === 1 ? 'on' : 'off'">
              {{ q.status === 1 ? '上线' : q.status === 2 ? '下线' : '草稿' }}
            </span>
            <span class="q-content">{{ q.content }}</span>
            <span class="q-count">{{ q.answerCount }}人答</span>
          </div>
          <div class="q-opts">
            <span v-for="o in q.options" :key="o.id" class="q-opt">{{ o.icon }} {{ o.content }}</span>
          </div>
          <div class="q-actions">
            <button class="link" @click="openQForm(q)">编辑</button>
            <button class="link" @click="toggleStatus(q)">{{ q.status === 1 ? '下线' : '上线' }}</button>
            <button class="link danger" @click="deleteQuestion(q)">删除</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 题目编辑弹层 -->
    <div v-if="showQForm" class="mask" @click.self="showQForm = false">
      <div class="dialog wide">
        <h4>{{ editingQId ? '编辑题目' : '新增题目' }}</h4>
        <textarea v-model="qForm.content" placeholder="问题正文" rows="2"></textarea>
        <input v-model="qForm.description" placeholder="补充说明（可选）" />
        <div class="row">
          <select v-model.number="qForm.categoryId">
            <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
          <input v-model.number="qForm.recommendScore" type="number" step="0.1" placeholder="推荐权重" />
          <input v-model.number="qForm.sortOrder" type="number" placeholder="排序" />
          <select v-model.number="qForm.status">
            <option :value="0">草稿</option>
            <option :value="1">上线</option>
            <option :value="2">下线</option>
          </select>
        </div>
        <div class="opts-editor">
          <div v-for="(o, i) in qForm.options" :key="i" class="opt-row">
            <input v-model="o.icon" placeholder="图标" class="opt-icon" />
            <input v-model="o.content" placeholder="选项内容" class="opt-content" />
            <button class="link danger" @click="qForm.options.splice(i, 1)">删</button>
          </div>
          <button class="btn-sm" @click="addOption">＋ 选项</button>
        </div>
        <div class="dialog-actions">
          <button class="btn-sm" @click="showQForm = false">取消</button>
          <button class="btn-sm primary" @click="saveQuestion">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.error { color: var(--color-danger); font-size: 13px; margin: 8px 0; }
.hint { color: var(--color-text-muted); font-size: 14px; text-align: center; padding: 20px 0; }
.block { margin-top: 20px; }
.block-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.block-title { font-size: 16px; font-weight: 700; margin: 0; }
.btn-sm { padding: 7px 14px; border: 1px solid var(--color-border); border-radius: 12px; background: var(--color-card); color: var(--color-text); font-size: 13px; cursor: pointer; }
.btn-sm.primary { background: var(--color-primary); color: #fff; border-color: var(--color-primary); }
.link { background: none; border: none; color: var(--color-primary); cursor: pointer; font-size: 13px; padding: 2px 6px; }
.link.danger { color: var(--color-danger); }
.cat-list { display: flex; flex-wrap: wrap; gap: 8px; }
.cat-item { display: flex; align-items: center; gap: 8px; padding: 8px 12px; border: 1px solid var(--color-border); border-radius: 10px; cursor: pointer; }
.cat-item.active { border-color: var(--color-primary); background: #f0f7ff; }
.cat-actions { margin-left: auto; }
.q-list { display: flex; flex-direction: column; gap: 10px; }
.q-item { border: 1px solid var(--color-border); border-radius: 10px; padding: 10px 12px; }
.q-main { display: flex; align-items: center; gap: 10px; }
.q-status { font-size: 12px; padding: 2px 8px; border-radius: 8px; }
.q-status.on { background: #eafaf1; color: #27ae60; }
.q-status.off { background: var(--color-bg); color: var(--color-text-muted); }
.q-content { flex: 1; font-size: 14px; }
.q-count { font-size: 12px; color: var(--color-text-secondary); }
.q-opts { display: flex; flex-wrap: wrap; gap: 8px; margin: 8px 0; }
.q-opt { font-size: 12px; background: var(--color-bg); padding: 3px 8px; border-radius: 8px; }
.q-actions { display: flex; gap: 8px; }
.mask { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 1200; }
.dialog { background: #fff; border-radius: 14px; padding: 20px; width: 360px; max-width: 92%; max-height: 80vh; overflow-y: auto; }
.dialog.wide { width: 560px; }
.dialog h4 { margin: 0 0 12px; }
.dialog input, .dialog textarea, .dialog select { width: 100%; box-sizing: border-box; padding: 8px 10px; border: 1px solid var(--color-border); border-radius: 8px; font-size: 13px; margin-bottom: 8px; }
.row { display: flex; gap: 8px; }
.row > * { flex: 1; }
.dialog-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 8px; }
.opts-editor { margin: 8px 0; }
.opt-row { display: flex; gap: 6px; margin-bottom: 6px; align-items: center; }
.opt-icon { width: 60px !important; flex: none !important; margin: 0 !important; }
.opt-content { flex: 1 !important; margin: 0 !important; }
</style>
