<script setup lang="ts">
import { ref, onMounted } from 'vue'
import bgImage from '@/assets/png/hero/herobanner_bg.png'

const today = ref('')
const fishIndex = ref(78)
const imgUrl = bgImage

onMounted(() => {
  const now = new Date()
  today.value = `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日`
})
</script>

<template>
  <section class="hero">
    <div class="hero-inner" :style="{ backgroundColor: '#f0f2f5', backgroundImage: `url(${imgUrl})` }">
      <div class="hero-content">
        <div class="hero-text">
          <h1 class="hero-title">摸鱼啦～<br />FishingTime</h1>
          <p class="hero-subtitle">
            钓鱼爱好者的聚集地，聊聊钓鱼、分享生活、发现乐趣
          </p>
          <div class="hero-info">
            <div class="hero-info-item">
              <span class="hero-info-label">📅 今天</span>
              <span class="hero-info-value">{{ today }}</span>
            </div>
            <div class="hero-info-item">
              <span class="hero-info-label">🐟 今日摸鱼指数</span>
              <span class="hero-info-value">{{ fishIndex }}%</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
