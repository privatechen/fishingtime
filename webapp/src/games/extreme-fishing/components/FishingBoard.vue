<script setup lang="ts">
import { ref, computed } from 'vue'
import type { Cell, Selection } from '../engine/types'

/**
 * 极限捞鱼棋盘
 *
 * 拖动撒网：pointerdown 记录起始格，pointermove 更新当前格形成轴对齐矩形，
 * pointerup 提交一次撒网（PRD §23：选区由格子坐标计算，一次 pointer 序列只结算一次）。
 * 棋盘高度用 padding-bottom 撑起保证正方形，不依赖 aspect-ratio。
 */
const props = defineProps<{
  board: Cell[]
  rows: number
  cols: number
  disabled?: boolean
}>()

const emit = defineEmits<{ (e: 'net', selection: Selection): void }>()

const boardEl = ref<HTMLElement | null>(null)
const dragStart = ref<{ row: number; col: number } | null>(null)
const dragCurrent = ref<{ row: number; col: number } | null>(null)

const selectedIds = computed(() => {
  if (!dragStart.value || !dragCurrent.value) return new Set<string>()
  const s = dragStart.value
  const c = dragCurrent.value
  const set = new Set<string>()
  for (let r = Math.min(s.row, c.row); r <= Math.max(s.row, c.row); r++) {
    for (let col = Math.min(s.col, c.col); col <= Math.max(s.col, c.col); col++) {
      set.add(`${r}-${col}`)
    }
  }
  return set
})

function cellFromClient(clientX: number, clientY: number): { row: number; col: number } {
  const el = boardEl.value
  if (!el) return { row: 0, col: 0 }
  const rect = el.getBoundingClientRect()
  const col = Math.floor(((clientX - rect.left) / rect.width) * props.cols)
  const row = Math.floor(((clientY - rect.top) / rect.height) * props.rows)
  return {
    row: Math.min(Math.max(row, 0), props.rows - 1),
    col: Math.min(Math.max(col, 0), props.cols - 1),
  }
}

function onDown(e: PointerEvent): void {
  if (props.disabled) return
  const cell = cellFromClient(e.clientX, e.clientY)
  dragStart.value = cell
  dragCurrent.value = cell
}

function onMove(e: PointerEvent): void {
  if (!dragStart.value) return
  dragCurrent.value = cellFromClient(e.clientX, e.clientY)
}

function onUp(): void {
  if (dragStart.value && dragCurrent.value) {
    const s = dragStart.value
    const c = dragCurrent.value
    emit('net', {
      startRow: s.row,
      startCol: s.col,
      endRow: c.row,
      endCol: c.col,
    })
  }
  dragStart.value = null
  dragCurrent.value = null
}
</script>

<template>
  <div class="board-frame" :style="{ paddingBottom: `${(props.rows / props.cols) * 100}%` }">
    <div
      ref="boardEl"
      class="fish-board"
      :style="{
        gridTemplateColumns: `repeat(${props.cols}, 1fr)`,
        gridTemplateRows: `repeat(${props.rows}, 1fr)`,
      }"
      @pointerdown="onDown"
      @pointermove="onMove"
      @pointerup="onUp"
      @pointercancel="onUp"
    >
      <div
        v-for="cell in props.board"
        :key="cell.id"
        :class="[
          'cell',
          `t-${cell.type}`,
          { selected: selectedIds.has(cell.id) },
        ]"
      >
        <span v-if="cell.type === 'FISH'" class="obj fish-obj" />
        <span v-else-if="cell.type === 'PUFFER'" class="obj puffer-obj" />
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
  display: grid;
  gap: 6px;
  border: 2px solid #bcd8f7;
  border-radius: 16px;
  background: #eef6ff;
  padding: 6px;
  box-sizing: border-box;
  touch-action: none;
  user-select: none;
  -webkit-user-select: none;
}
.cell {
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  transition: box-shadow 0.1s ease, background 0.1s ease;
}
.cell.selected {
  background: rgba(45, 140, 240, 0.22);
  box-shadow: inset 0 0 0 2px rgba(45, 140, 240, 0.6);
}
.obj {
  width: 78%;
  height: 78%;
  background-size: contain;
  background-repeat: no-repeat;
  background-position: center;
}
.fish-obj {
  background-image: url("../assets/fish.png");
}
.puffer-obj {
  background-image: url("../assets/puffer.png");
}

</style>
