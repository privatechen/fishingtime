<script setup lang="ts">
import Header from '@/components/Header.vue'
import HeroBanner from '@/components/HeroBanner.vue'
import HotRanking from '@/components/HotRanking.vue'
import CommonHot from '@/components/CommonHot.vue'
import CommunityRecommend from '@/components/CommunityRecommend.vue'
import GameCard from '@/components/GameCard.vue'
import Footer from '@/components/Footer.vue'
</script>

<template>
  <Header />
  <HeroBanner />
  <div class="main-content">
    <div class="main-left">
      <HotRanking />
    </div>
    <aside class="main-right">
      <CommonHot />
      <CommunityRecommend />
      <GameCard />
    </aside>
  </div>
  <Footer />
</template>
