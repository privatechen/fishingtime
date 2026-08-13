<script setup lang="ts">
import { computed } from 'vue'
import type { Direction, Fish } from '../engine/types'

/**
 * 鱼群突围棋盘
 *
 * 外层 fish-wrap 负责定位 + 离场平移动画（cell 热区），
 * 内层鱼 SVG 只负责方向旋转（PRD §17：wrapper 平移 / image 旋转分开）。
 * 基础鱼朝右，UP/DOWN/LEFT 通过 rotate 得到，不用四张图（PRD §16）。
 *
 * 高度用 padding-bottom 撑起（等宽正方形），不依赖 aspect-ratio——
 * 老浏览器不识别 aspect-ratio 会得到 0 高，导致整块棋盘隐形。
 * 棋盘背景画 6×6 网格线，鱼再少结构也始终可见。
 */
const props = defineProps<{
  rows: number
  cols: number
  fishes: Fish[]
  /** 正在播放离场动画的鱼 id */
  animatingIds: string[]
  /** 正在播放失误反馈的鱼 id */
  mistakeId: string | null
  /** debug：高亮当前可出鱼 */
  highlightFree?: boolean
  freeIds?: string[]
}>()

const emit = defineEmits<{ (e: 'tap', id: string): void }>()

const cellWidth = computed(() => `${100 / props.cols}%`)
const cellHeight = computed(() => `${100 / props.rows}%`)
/** padding-bottom 百分比 = 行数/列数，保证外框始终是正方形 */
const framePaddingBottom = computed(() => `${(props.rows / props.cols) * 100}%`)
/** 网格线间距随行列数变化（5×5 / 6×6 通用） */
const gridBgStyle = computed(() => ({
  backgroundSize: `calc(100% / ${props.cols}) calc(100% / ${props.rows})`,
}))

const ROTATE: Record<Direction, number> = { RIGHT: 0, DOWN: 90, LEFT: 180, UP: 270 }
</script>

<template>
  <!-- 外框：用 padding-bottom 保证正方形高度，随容器宽度自适应 -->
  <div class="board-frame" :style="{ paddingBottom: framePaddingBottom }">
    <div class="fish-board" :style="gridBgStyle">
      <div
        v-for="f in props.fishes"
        :key="f.id"
        class="fish-wrap"
        :class="[
          `dir-${f.direction}`,
          { exiting: props.animatingIds.includes(f.id) },
          { mistake: props.mistakeId === f.id },
          { highlight: props.highlightFree && props.freeIds?.includes(f.id) },
        ]"
        :style="{
          left: `${(f.col * 100) / props.cols}%`,
          top: `${(f.row * 100) / props.rows}%`,
          width: cellWidth,
          height: cellHeight,
        }"
        @click="emit('tap', f.id)"
      >
        <svg
          class="fish-svg"
          :style="{ transform: `rotate(${ROTATE[f.direction]}deg)` }"
          viewBox="0 0 64 32"
          aria-hidden="true"
        >
          <polygon points="2,7 13,16 2,25" fill="#8fc1f9" />
          <ellipse cx="36" cy="16" rx="22" ry="11" fill="#5aa0f8" />
          <circle cx="45" cy="10" r="2.6" fill="#fff" />
          <circle cx="45" cy="10" r="1.3" fill="#1d2129" />
        </svg>
      </div>
    </div>
  </div>
</template>

<style scoped>
.board-frame {
  position: relative;
  width: 100%;
  max-width: 520px;
  margin: 0 auto;
}
.fish-board {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  border: 2px solid #bcd8f7;
  border-radius: 16px;
  overflow: hidden;
  /* 网格线随行列数动态设置（inline style 提供 backgroundSize），即使鱼尚未渲染，格子结构也清晰可见 */
  background-color: #eef6ff;
  background-image:
    linear-gradient(to right, rgba(45, 140, 240, 0.16) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(45, 140, 240, 0.16) 1px, transparent 1px);
}

.fish-wrap {
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 6%;
  box-sizing: border-box;
  cursor: pointer;
  transition: transform 0.22s ease, opacity 0.22s ease;
  -webkit-tap-highlight-color: transparent;
}
/* 不用 filter: drop-shadow：高密度棋盘 + 离场动画时逐帧重绘滤镜是性能大头 */
.fish-svg {
  width: 100%;
  height: 100%;
}

/* 离场：沿方向平移 + 缩小淡出 */
.fish-wrap.exiting {
  opacity: 0;
}
.fish-wrap.dir-RIGHT.exiting {
  transform: translateX(260%) scale(0.6);
}
.fish-wrap.dir-LEFT.exiting {
  transform: translateX(-260%) scale(0.6);
}
.fish-wrap.dir-DOWN.exiting {
  transform: translateY(260%) scale(0.6);
}
.fish-wrap.dir-UP.exiting {
  transform: translateY(-260%) scale(0.6);
}

/* 失误：轻抖，不弹窗不提示 */
.fish-wrap.mistake {
  animation: fishShake 0.18s ease;
}
@keyframes fishShake {
  0%,
  100% {
    transform: translateX(0);
  }
  30% {
    transform: translateX(-6px);
  }
  70% {
    transform: translateX(6px);
  }
}

/* debug：可出鱼高亮 */
.fish-wrap.highlight .fish-svg {
  filter: drop-shadow(0 0 6px rgba(45, 140, 240, 0.95));
}
</style>
