<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { CommunityPost } from '@/types'
import { fetchCommunityRecommend } from '@/api'

const list = ref<CommunityPost[]>([])

onMounted(async () => {
  list.value = await fetchCommunityRecommend()
})
</script>

<template>
  <div class="card">
    <div class="sidebar-card-header">💬 社区推荐</div>
    <div class="sidebar-card-body">
      <div
        v-for="item in list"
        :key="item.id"
        class="community-item"
      >
        <span class="community-title">{{ item.title }}</span>
        <span class="community-comments">💬 {{ item.commentCount }}</span>
      </div>
    </div>
  </div>
</template>
