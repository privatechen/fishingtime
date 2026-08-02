<script setup lang="ts">
import { computed } from 'vue'
import type { BoardTile } from '@/games/engine/2048/Game2048Engine'

const props = defineProps<{ tile: BoardTile; isNew: boolean }>()

// 必须与 GameBoard.vue 的 --tile / --gap 保持一致
const CELL = 100
const GAP = 8
const STEP = CELL + GAP

/** 外层定位：JS 计算像素位移（CSS calc 不支持乘法） */
const wrapStyle = computed(() => ({
  transform: `translate(${props.tile.col * STEP}px, ${props.tile.row * STEP}px)`,
}))

/** 内层颜色 + 弹出动画 */
const tileClass = computed(() => {
  if (props.tile.value >= 2048) return 'tile-2048'
  return `tile-${props.tile.value}`
})

const fontSize = computed(() => {
  if (props.tile.value < 100) return '32px'
  if (props.tile.value < 1000) return '28px'
  return '22px'
})
</script>

<template>
  <!-- 外层：绝对定位 + translate，负责滑动 -->
  <div class="tile-wrap" :style="wrapStyle">
    <!-- 内层：颜色 + 弹出动画 -->
    <div
      class="tile"
      :class="[tileClass, { 'tile-pop': isNew }]"
      :style="{ fontSize }"
    >
      {{ tile.value }}
    </div>
  </div>
</template>

<style scoped>
.tile-wrap {
  position: absolute;
  /* 绝对定位原点是板子外边缘，需偏移一个 gap 对齐到网格内容区 */
  top: var(--gap);
  left: var(--gap);
  width: var(--tile);
  height: var(--tile);
  transition: transform 0.15s ease;
  will-change: transform;
}

.tile {
  width: 100%;
  height: 100%;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  user-select: none;
}

/* 新生成/合并格子的弹出动画 */
.tile-pop {
  animation: tilePop 0.2s ease;
}

@keyframes tilePop {
  0%   { transform: scale(0); }
  60%  { transform: scale(1.15); }
  100% { transform: scale(1); }
}

.tile-2    { background: #eee4da; color: #776e65; }
.tile-4    { background: #ede0c8; color: #776e65; }
.tile-8    { background: #f2b179; color: #f9f6f2; }
.tile-16   { background: #f59563; color: #f9f6f2; }
.tile-32   { background: #f67c5f; color: #f9f6f2; }
.tile-64   { background: #f65e3b; color: #f9f6f2; }
.tile-128  { background: #edcf72; color: #f9f6f2; }
.tile-256  { background: #edcc61; color: #f9f6f2; }
.tile-512  { background: #edc850; color: #f9f6f2; }
.tile-1024 { background: #edc53f; color: #f9f6f2; }
.tile-2048 { background: #edc22e; color: #f9f6f2; }
</style>
