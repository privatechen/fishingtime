<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { GameItem } from '@/types'
import { fetchGameList } from '@/api'

const games = ref<GameItem[]>([])

onMounted(async () => {
  games.value = await fetchGameList()
})
</script>

<template>
  <div class="card">
    <div class="sidebar-card-header">🎮 小游戏</div>
    <div class="sidebar-card-body">
      <div class="game-grid">
        <div
          v-for="g in games"
          :key="g.id"
          class="game-card-item"
        >
          <span class="game-card-icon">{{ g.icon }}</span>
          <span class="game-card-name">{{ g.name }}</span>
          <span class="game-card-desc">{{ g.description }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
