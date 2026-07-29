<script setup>
import { computed } from 'vue'
import { statusLabel } from '../utils/format'

const props = defineProps({
  value: { type: String, default: '' },
})

const tone = computed(() => {
  if (['paid', 'completed', 'delivered'].includes(props.value)) return 'success'
  if (['picking', 'processing', 'shipped'].includes(props.value)) return 'active'
  if (['cancelled', 'closed', 'returned', 'not-required'].includes(props.value)) return 'muted'
  return 'warning'
})
</script>

<template>
  <span class="status-badge" :data-tone="tone">
    <i aria-hidden="true"></i>
    {{ statusLabel(value) }}
  </span>
</template>
