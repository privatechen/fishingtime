<script setup lang="ts">
defineProps<{ visible: boolean }>()
const emit = defineEmits<{ (e: 'close'): void }>()
</script>

<template>
  <div v-if="visible" class="dialog-mask" @click.self="emit('close')">
    <div class="dialog">
      <h3 class="dialog-title">游戏规则</h3>
      <div class="dialog-body">
        <p>1. 使用方向键（↑↓←→）移动所有方块。</p>
        <p>2. 相同数字的方块碰撞时会合并成一个，数字翻倍。</p>
        <p>3. 每移动一次，会随机生成一个新的 2 或 4。</p>
        <p>4. 合成 2048 即胜利，可继续挑战更高分。</p>
        <p>5. 棋盘填满且无法合并时游戏结束。</p>
      </div>
      <button class="dialog-btn" @click="emit('close')">我知道了</button>
    </div>
  </div>
</template>

<style scoped>
.dialog-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.dialog {
  background: #fff;
  border-radius: 16px;
  padding: 28px;
  width: 360px;
  max-width: 90%;
}

.dialog-title {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 16px;
}

.dialog-body p {
  margin-bottom: 8px;
  font-size: 14px;
  color: var(--color-text-secondary);
  line-height: 1.6;
}

.dialog-btn {
  width: 100%;
  margin-top: 16px;
  padding: 10px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  cursor: pointer;
}
</style>
