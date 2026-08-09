<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { CollisionEngine, type EngineEvent } from '../engine/CollisionEngine'
import { CollisionGameCanvas } from '../render/CollisionGameCanvas'
import { buildLevel, collisionLevels } from '../config/levels'
import { collisionStore } from '../stores/collisionStore'
import Header from '@/components/Header.vue'
import Footer from '@/components/Footer.vue'

const router = useRouter()
const canvasRef = ref<HTMLCanvasElement | null>(null)
let engine: CollisionEngine | null = null
let renderer: CollisionGameCanvas | null = null

const levelId = ref(collisionStore.getLastLevel())
const count = ref(0)
const score = ref(0)
const best = ref(0)
const showRule = ref(false)
const showPause = ref(false)
const showResult = ref(false)
const result = ref({ success: false, reason: '', score: 0, count: 0 })

function handleEvent(e: EngineEvent): void {
  if (e.type === 'count-change' || e.type === 'collision' || e.type === 'enemy-battle') {
    count.value = e.count
  } else if (e.type === 'level-complete') {
    collisionStore.setBestScore(levelId.value, e.score)
    collisionStore.setUnlockedLevel(levelId.value + 1)
    best.value = collisionStore.getBestScore(levelId.value)
    result.value = { success: true, reason: '', score: e.score, count: e.count }
    showResult.value = true
  } else if (e.type === 'level-failed') {
    result.value = { success: false, reason: e.reason, score: 0, count: e.count }
    showResult.value = true
  }
}

function loadLevel(id: number): void {
  const level = buildLevel(id)
  if (!level) return
  destroyGame()
  engine = new CollisionEngine(level)
  count.value = level.initialCount
  best.value = collisionStore.getBestScore(level.id)
  score.value = 0
  showResult.value = false
  engine.onEvent(handleEvent)
  renderer = new CollisionGameCanvas(canvasRef.value!, engine)
  renderer.setScoreCallback((s) => (score.value = s))
  renderer.start()
}

function destroyGame(): void {
  renderer?.destroy()
  renderer = null
  engine?.destroy()
  engine = null
}

function togglePause(): void {
  if (!engine) return
  if (engine.isPaused()) {
    engine.resume()
    showPause.value = false
  } else {
    engine.pause()
    showPause.value = true
  }
}

function nextLevel(): void {
  const next = levelId.value + 1
  if (next > collisionLevels.length) return
  levelId.value = next
  collisionStore.setLastLevel(next)
  loadLevel(next)
}

function retry(): void {
  showResult.value = false
  loadLevel(levelId.value)
}

function backToHall(): void {
  destroyGame()
  router.push('/games')
}

function onKeyDown(e: KeyboardEvent): void {
  if (e.key === 'p' || e.key === ' ') {
    e.preventDefault()
    togglePause()
  } else if (e.key === 'Escape') {
    showRule.value = false
    showPause.value = false
  }
}

function onBlur(): void {
  if (engine && !engine.isPaused()) {
    engine.pause()
    showPause.value = true
  }
}

onMounted(() => {
  collisionStore.setLastLevel(levelId.value)
  loadLevel(levelId.value)
  window.addEventListener('keydown', onKeyDown)
  window.addEventListener('blur', onBlur)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeyDown)
  window.removeEventListener('blur', onBlur)
  destroyGame()
})
</script>

<template>
  <Header />
  <div class="collision-page">
    <!-- 顶栏 -->
    <div class="game-topbar">
      <button class="top-btn" @click="showPause = true">← 返回大厅</button>
      <span class="level-title">第 {{ levelId }} 关</span>
      <button class="top-btn" @click="showRule = true">规则</button>
    </div>

    <!-- 信息栏 -->
    <div class="game-info">
      <span class="info-item">🐟 {{ count }}</span>
      <span class="info-item">得分 {{ score }}</span>
      <span class="info-item">最佳 {{ best }}</span>
    </div>

    <!-- 画布 -->
    <div class="canvas-wrap">
      <canvas ref="canvasRef"></canvas>
    </div>

    <!-- 底栏 -->
    <div class="game-controlbar">
      <span class="control-tip">PC：A/D 或 ←/→ 移动 · P 暂停</span>
      <span class="control-tip">手机：左右拖动</span>
      <div class="control-btns">
        <button class="ctrl-btn" @click="togglePause">{{ showPause ? '继续' : '暂停' }}</button>
        <button class="ctrl-btn" @click="retry">重新开始</button>
      </div>
    </div>
  </div>

  <!-- 规则弹窗 -->
  <div v-if="showRule" class="dialog-mask" @click.self="showRule = false">
    <div class="dialog">
      <h3>游戏规则</h3>
      <p>1. 鱼群自下往上游，你控制左右移动选车道。</p>
      <p>2. 数字门出现在任意两条车道（+5/+10/+20，×2/×3/×4），游到对应车道穿过。</p>
      <p>3. 礁石碰到直接失败；水母 -8；渔网 -20%；换道可躲避。</p>
      <p>4. 敌方鱼群：无法躲避，1:1 抵消。</p>
      <p>5. 剩余鱼群 ≥ 1 游到终点即通关，分数 = 剩余鱼的数量。</p>
      <button class="dialog-btn" @click="showRule = false">知道了</button>
    </div>
  </div>

  <!-- 暂停弹窗 -->
  <div v-if="showPause" class="dialog-mask">
    <div class="dialog">
      <h3>暂停</h3>
      <p>退出后本关进度不保存</p>
      <div class="dialog-actions">
        <button class="btn-secondary" @click="backToHall">返回大厅</button>
        <button class="btn-primary" @click="togglePause">继续游戏</button>
      </div>
    </div>
  </div>

  <!-- 结算弹窗 -->
  <div v-if="showResult" class="dialog-mask">
    <div class="dialog">
      <h3>{{ result.success ? '🎉 通关成功' : '💔 挑战失败' }}</h3>
      <p v-if="result.success">剩余鱼群 {{ result.count }} · 本关得分 {{ result.score }}</p>
      <p v-else>原因：{{ result.reason }}</p>
      <div class="dialog-actions">
        <button class="btn-secondary" @click="backToHall">返回大厅</button>
        <button class="btn-secondary" @click="retry">再试一次</button>
        <button v-if="result.success && levelId < collisionLevels.length" class="btn-primary" @click="nextLevel">
          下一关
        </button>
      </div>
    </div>
  </div>

  <Footer />
</template>

<style scoped>
.collision-page {
  max-width: var(--max-width);
  margin: 0 auto;
  padding: 16px 20px;
  min-height: calc(100vh - var(--header-height));
}

.game-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.top-btn {
  background: none;
  border: none;
  color: var(--color-primary);
  cursor: pointer;
  font-size: 14px;
  padding: 6px 10px;
}

.level-title {
  font-size: 18px;
  font-weight: 700;
}

.game-info {
  display: flex;
  gap: 32px;
  justify-content: center;
  padding: 10px 0;
  background: var(--color-card);
  border-radius: 12px;
  margin-bottom: 12px;
  box-shadow: var(--shadow);
}

.info-item {
  font-size: 15px;
  font-weight: 600;
}

.canvas-wrap {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--shadow);
  background: #e6f7ff;
}

.canvas-wrap canvas {
  display: block;
  width: 100%;
  touch-action: none;
}

.game-controlbar {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.control-tip {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.control-btns {
  display: flex;
  gap: 12px;
}

.ctrl-btn {
  padding: 8px 20px;
  border: 1px solid var(--color-border);
  border-radius: 20px;
  background: var(--color-card);
  cursor: pointer;
  font-size: 14px;
}

.ctrl-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

/* 弹窗 */
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
  width: 360px;
  max-width: 90%;
  text-align: center;
}

.dialog h3 {
  margin-bottom: 12px;
}

.dialog p {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin-bottom: 8px;
  line-height: 1.6;
}

.dialog-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-top: 16px;
  flex-wrap: wrap;
}

.dialog-btn {
  width: 100%;
  margin-top: 16px;
  padding: 10px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 20px;
  cursor: pointer;
}

.btn-primary {
  padding: 10px 16px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 20px;
  cursor: pointer;
}

.btn-secondary {
  padding: 10px 16px;
  background: var(--color-hover);
  border: none;
  border-radius: 20px;
  cursor: pointer;
}
</style>
