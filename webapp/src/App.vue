<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterView } from 'vue-router'
import { useAuth } from '@/stores/auth'

const { checkAuth } = useAuth()
onMounted(() => checkAuth())
</script>

<template>
  <RouterView />
</template>
