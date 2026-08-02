<script setup lang="ts">
defineProps<{ visible: boolean }>()
const emit = defineEmits<{ (e: 'confirm'): void; (e: 'cancel'): void }>()
</script>

<template>
  <div v-if="visible" class="dialog-mask" @click.self="emit('cancel')">
    <div class="dialog">
      <h3 class="dialog-title">重新开始？</h3>
      <p class="dialog-body">当前进度将被清空，确定重新开始吗？</p>
      <div class="dialog-actions">
        <button class="dialog-btn-cancel" @click="emit('cancel')">取消</button>
        <button class="dialog-btn-confirm" @click="emit('confirm')">确定</button>
      </div>
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
  width: 320px;
  max-width: 90%;
}

.dialog-title {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 12px;
}

.dialog-body {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin-bottom: 20px;
}

.dialog-actions {
  display: flex;
  gap: 12px;
}

.dialog-btn-cancel,
.dialog-btn-confirm {
  flex: 1;
  padding: 10px;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  cursor: pointer;
}

.dialog-btn-cancel {
  background: var(--color-hover);
  color: var(--color-text);
}

.dialog-btn-confirm {
  background: var(--color-danger);
  color: #fff;
}
</style>
