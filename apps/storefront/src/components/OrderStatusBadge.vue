<script setup>
import { computed } from 'vue'
import { orderStatusMeta } from '../data/commerce'

const props = defineProps({ status: { type: String, required: true } })
const meta = computed(() => orderStatusMeta[props.status] || { label: props.status, tone: 'muted' })
</script>

<template>
  <span :class="['status-badge', `status-badge--${meta.tone}`]">{{ meta.label }}</span>
</template>
