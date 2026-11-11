<template>
  <AdminExamList v-if="route.query.tab === 'exams'" />
  <EnrollmentAdmin v-else-if="route.query.tab === 'enrollments'" />
  <DocumentAdmin v-else-if="route.query.tab === 'documents'" />
  <div v-else class="admin-reports-overview"><ReportsMockup /></div>
</template>
<script setup lang="ts">
import { defineAsyncComponent } from 'vue';
import { useRoute } from 'vue-router';
const ReportsMockup = defineAsyncComponent(() => import('./ReportsMockup.vue'));
const EnrollmentAdmin = defineAsyncComponent(() => import('./EnrollmentAdmin.vue'));
const AdminExamList = defineAsyncComponent(() => import('./AdminExamList.vue'));
const DocumentAdmin = defineAsyncComponent(() => import('./DocumentAdmin.vue'));
// Exam, enrollment and AI-document pages keep their /admin?tab= URLs; any other tab shows the overview.
const route = useRoute();
</script>
<style scoped>
.admin-reports-overview{padding:clamp(16px,2.5vw,32px);background:var(--bg-main);min-height:100%;min-width:0}
@media print{.admin-reports-overview{padding:0}}
</style>
