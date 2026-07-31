<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import bgImage from '@/assets/png/hero/herobanner_bg.png'

interface WeatherData {
  province: string
  city: string
  weather: string
  temperature: number
  humidity: number
}

const today = ref('')
const fishIndex = ref(78)
const weather = ref<WeatherData | null>(null)
let weatherTimer: number | null = null

async function fetchWeather() {
  try {
    const res = await fetch('/api/weather', { credentials: 'same-origin' })
    const json = await res.json()
    weather.value = (json.code === 200 && json.data) ? json.data : null
  } catch {
    weather.value = null // 失败隐藏，不影响其他模块
  }
}

onMounted(() => {
  const now = new Date()
  today.value = `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日`

  // 立即获取天气，之后每 10 分钟刷新一次
  fetchWeather()
  weatherTimer = setInterval(fetchWeather, 10 * 60 * 1000)
})

onUnmounted(() => {
  if (weatherTimer) clearInterval(weatherTimer)
})
</script>

<template>
  <section class="hero">
    <div class="hero-inner" :style="{ backgroundColor: '#f0f2f5', backgroundImage: `url(${bgImage})` }">
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
            <!-- 天气信息：日期后面追加 -->
            <div v-if="weather" class="hero-info-item">
              <span class="hero-info-label">📍 {{ weather.province }}·{{ weather.city }}</span>
              <span class="hero-info-value">
                {{ weather.weather }} {{ weather.temperature }}℃ · 💧 {{ weather.humidity }}%
              </span>
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
