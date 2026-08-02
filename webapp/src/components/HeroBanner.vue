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
const sentence = ref('')
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

async function fetchSentence() {
  try {
    const res = await fetch('/api/daily-sentence/random', { credentials: 'same-origin' })
    const json = await res.json()
    sentence.value = (json.code === 200 && json.data && json.data.content) ? json.data.content : ''
  } catch {
    sentence.value = '' // 失败隐藏，不影响其他模块
  }
}

onMounted(() => {
  const now = new Date()
  today.value = `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日`

  // 今日一句
  fetchSentence()

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
          <h1 class="hero-title">Fishing...</h1>
          <p v-if="sentence" class="hero-sentence"> {{ sentence }}</p>

          <!-- 今日一句 -->
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
