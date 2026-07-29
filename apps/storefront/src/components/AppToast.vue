<script setup>
defineProps({ visible: { type: Boolean, default: false }, message: { type: String, default: '' } })
</script>

<template>
  <Transition name="toast">
    <div v-if="visible" class="app-toast" role="status"><span>✓</span>{{ message }}</div>
  </Transition>
</template>
