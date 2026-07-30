<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { fetchCommonHot } from '@/api'

const data = ref({ title: '', weiboRank: 0, baiduRank: 0, zhihuRank: 0, hot: '' })

onMounted(async () => {
  data.value = await fetchCommonHot()
})
</script>

<template>
  <div class="card">
    <div class="sidebar-card-header">🌐 全网共同热点</div>
    <div class="sidebar-card-body">
      <div class="common-hot-item">
        <div class="common-hot-title">{{ data.title }}</div>
        <div class="common-hot-ranks">
          <div class="common-hot-rank">
            <span class="common-hot-rank-name">微博</span>
            <span class="common-hot-rank-value">#{{ data.weiboRank }}</span>
          </div>
          <div class="common-hot-rank">
            <span class="common-hot-rank-name">百度</span>
            <span class="common-hot-rank-value">#{{ data.baiduRank }}</span>
          </div>
          <div class="common-hot-rank">
            <span class="common-hot-rank-name">知乎</span>
            <span class="common-hot-rank-value">#{{ data.zhihuRank }}</span>
          </div>
        </div>
        <div style="margin-top:8px;font-size:13px;color:var(--color-text-muted)">
          热度 🔥 {{ data.hot }}
        </div>
      </div>
    </div>
  </div>
</template>
