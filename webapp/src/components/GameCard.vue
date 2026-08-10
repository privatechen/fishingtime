<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { games } from '@/games/config/games'

const router = useRouter()

/** 首页展示的 4 个小游戏 */
const HOME_GAME_IDS = ['2048', 'color-focus', 'direction-trap', 'color-hunter']

const homeGames = computed(() =>
  games.filter((g) => g.status === 'available' && g.path && HOME_GAME_IDS.includes(g.id)),
)

/** 封面是图片路径（import 后的 URL 以 / 开头）还是 emoji 文本 */
function isImage(cover: string): boolean {
  return cover.startsWith('/') || cover.startsWith('data:') || cover.startsWith('http')
}

function go(path: string): void {
  router.push(path)
}
</script>

<template>
  <div class="card">
    <div class="sidebar-card-header">🎮 小游戏</div>
    <div class="sidebar-card-body">
      <div class="game-grid">
        <div v-for="g in homeGames" :key="g.id" class="game-card-item" @click="go(g.path!)">
          <img v-if="isImage(g.cover)" :src="g.cover" class="game-card-icon" alt="" />
          <span v-else class="game-card-icon">{{ g.cover }}</span>
          <span class="game-card-name">{{ g.title }}</span>
          <span class="game-card-desc">{{ g.desc }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* img 自身带 .game-card-icon 类，需用 img.game-card-icon 匹配（原 1254px 大图压成小图标） */
img.game-card-icon {
  width: 48px;
  height: 48px;
  object-fit: contain;
}
</style>
