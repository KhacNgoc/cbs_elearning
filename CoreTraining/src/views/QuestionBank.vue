<template>
  <main class="bank-page"><QuestionBankPanel /></main>
</template>
<script setup lang="ts">
import QuestionBankPanel from "../components/QuestionBankPanel.vue";
</script>
<style scoped>
.bank-page {
  padding: 2rem;
  min-width: 0;
}
@media (max-width: 650px) {
  .bank-page {
    padding: 1rem;
  }
}
</style>
