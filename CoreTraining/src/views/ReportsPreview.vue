<template><div class="report-preview-shell"><aside><div class="preview-brand">B <span>BIDV Core Training<small>QUẢN TRỊ · MOCKUP</small></span></div><ReportingMenu base-path="/mockups/reports" show-overview/></aside><main><ReportsMockup/></main></div></template>
<script setup lang="ts">
import ReportingMenu from '../components/ReportingMenu.vue';
import ReportsMockup from './ReportsMockup.vue';
</script>
<style scoped>
.report-preview-shell{display:grid;grid-template-columns:230px minmax(0,1fr);min-height:100vh;margin:-2rem}.report-preview-shell>aside{background:var(--primary-color);padding:24px 14px}.report-preview-shell>main{padding:28px;min-width:0;background:var(--bg-main)}.preview-brand{display:flex;gap:12px;color:white;font-size:27px;align-items:center;padding:0 10px 28px}.preview-brand span{font-size:13px;font-weight:600}.preview-brand small{display:block;font-size:8px;letter-spacing:1px;margin-top:6px;opacity:.65}@media(max-width:850px){.report-preview-shell{grid-template-columns:190px minmax(0,1fr)}.report-preview-shell>main{padding:20px}}@media(max-width:600px){.report-preview-shell{display:block;margin:-1rem}.report-preview-shell>aside{padding:12px}.preview-brand{display:none}.report-preview-shell>main{padding:16px}}@media print{.report-preview-shell{display:block;margin:0}.report-preview-shell>aside{display:none}.report-preview-shell>main{padding:0}}
</style>
<style scoped>
@media(min-width:601px) and (max-width:768px){.report-preview-shell{margin:-1rem}}
</style>
