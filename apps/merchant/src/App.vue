<script setup>
import { onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

function handleUnauthorized() {
  router.replace({ name: 'login', query: { reason: 'expired' } })
}

onMounted(() => window.addEventListener('super-mall-merchant:unauthorized', handleUnauthorized))
onBeforeUnmount(() => window.removeEventListener('super-mall-merchant:unauthorized', handleUnauthorized))
</script>

<template>
  <RouterView />
</template>
