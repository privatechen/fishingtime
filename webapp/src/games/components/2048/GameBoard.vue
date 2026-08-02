<script setup lang="ts">
import Tile from './Tile.vue'
import type { BoardTile } from '@/games/engine/2048/Game2048Engine'

defineProps<{
  tiles: BoardTile[]
  newTileIds: number[]
}>()
</script>

<template>
  <div class="board">
    <!-- 背景 4×4 网格 -->
    <div class="board-grid">
      <div v-for="i in 16" :key="i" class="cell" />
    </div>
    <!-- 活跃格子（绝对定位 + 动画） -->
    <Tile
      v-for="tile in tiles"
      :key="tile.id"
      :tile="tile"
      :is-new="newTileIds.includes(tile.id)"
    />
  </div>
</template>

<style scoped>
.board {
  --tile: 100px;
  --gap: 8px;
  position: relative;
  /* 内容 = 4列格子 + 3个间隔；padding 每边1个gap */
  width: calc(var(--tile) * 4 + var(--gap) * 3);
  height: calc(var(--tile) * 4 + var(--gap) * 3);
  padding: var(--gap);
  background: #bbada0;
  border-radius: 12px;
  box-sizing: content-box;
}

.board-grid {
  display: grid;
  grid-template-columns: repeat(4, var(--tile));
  grid-template-rows: repeat(4, var(--tile));
  gap: var(--gap);
}

.cell {
  background: rgba(238, 228, 218, 0.35);
  border-radius: 6px;
}
</style>
