<script setup>
import CommerceShell from '../../components/CommerceShell.vue'
import AccountNav from '../../components/AccountNav.vue'
</script>

<template>
  <CommerceShell>
    <main class="commerce-page account-page">
      <div class="page-width account-layout">
        <AccountNav />
        <section class="account-content"><RouterView /></section>
      </div>
    </main>
  </CommerceShell>
</template>
