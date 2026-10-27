<template>
  <AdminExamList v-if="route.query.tab === 'exams'" />
  <AdminPortal v-else-if="legacySection" />
  <div v-else class="admin-reports-overview"><ReportsMockup /></div>
</template>
<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue';
import { useRoute } from 'vue-router';
const AdminPortal = defineAsyncComponent(() => import('./AdminPortal.vue'));
const ReportsMockup = defineAsyncComponent(() => import('./ReportsMockup.vue'));
const AdminExamList = defineAsyncComponent(() => import('./AdminExamList.vue'));
const route = useRoute();
// Preserve existing enrollment and exam management URLs while replacing overview.
const legacySection = computed(() => typeof route.query.tab === 'string' && route.query.tab !== 'overview');
</script>
<style scoped>
.admin-reports-overview{padding:clamp(16px,2.5vw,32px);background:var(--bg-main);min-height:100%;min-width:0}
@media print{.admin-reports-overview{padding:0}}
</style>
