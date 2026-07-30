<script setup lang="ts">
import { ref, onMounted } from 'vue'
import Header from '@/components/Header.vue'
import HeroBanner from '@/components/HeroBanner.vue'
import HotRanking from '@/components/HotRanking.vue'
import CommonHot from '@/components/CommonHot.vue'
import CommunityRecommend from '@/components/CommunityRecommend.vue'
import GameCard from '@/components/GameCard.vue'
import Footer from '@/components/Footer.vue'

const leftCol = ref<HTMLElement | null>(null)
const rightCol = ref<HTMLElement | null>(null)
const leftStyle = ref<{ minHeight?: string }>({})

onMounted(() => {
  // 等 DOM 渲染完，量右侧实际高度，赋给左侧
  setTimeout(() => {
    if (leftCol.value && rightCol.value) {
      const rh = rightCol.value.offsetHeight
      const lh = leftCol.value.offsetHeight
      if (rh > lh) {
        leftStyle.value = { minHeight: rh + 'px' }
      }
    }
  }, 100)
})
</script>

<template>
  <Header />
  <HeroBanner />
  <div class="main-content">
    <div ref="leftCol" class="main-left" :style="leftStyle">
      <HotRanking />
    </div>
    <aside ref="rightCol" class="main-right">
      <CommonHot />
      <CommunityRecommend />
      <GameCard />
    </aside>
  </div>
  <Footer />
</template>
