<script setup lang="ts">
defineProps<{ score: number; best: number }>()
</script>

<template>
  <div class="score-panel">
    <div class="score-box">
      <span class="score-label">当前分数</span>
      <span class="score-value">{{ score }}</span>
    </div>
    <div class="score-box">
      <span class="score-label">最高分</span>
      <span class="score-value">{{ best }}</span>
    </div>
  </div>
</template>

<style scoped>
.score-panel {
  display: flex;
  gap: 16px;
}

.score-box {
  flex: 1;
  background: var(--color-hover);
  border-radius: 12px;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.score-label {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.score-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text);
}
</style>
