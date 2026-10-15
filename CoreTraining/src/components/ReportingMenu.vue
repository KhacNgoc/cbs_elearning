<template>
  <nav class="reporting-menu" aria-label="Menu báo cáo đào tạo">
    <RouterLink v-if="showOverview" :to="{path:basePath,query:otherQuery}" class="report-menu-link" :class="{active:!selected}" :aria-current="!selected?'page':undefined"><LayoutDashboard :size="17"/> Tổng quan</RouterLink>
    <button class="report-menu-toggle" :aria-expanded="expanded" :aria-controls="menuId" @click="expanded=!expanded"><ChartNoAxesCombined :size="18"/><span>Thống kê & báo cáo</span><ChevronDown :size="15" :class="{rotated:expanded}"/></button>
    <div v-show="expanded" :id="menuId" class="report-menu-children">
      <RouterLink v-for="view in reportViews" :key="view.id" :to="{path:basePath,query:{...otherQuery,report:view.id}}" class="report-menu-link" :class="{active:selected===view.id}" :aria-current="selected===view.id?'page':undefined" @click="emit('navigate')"><span class="menu-dot"></span>{{ view.label }}</RouterLink>
    </div>
  </nav>
</template>
<script setup lang="ts">
import { computed, ref, watch, useId } from 'vue';
import { useRoute } from 'vue-router';
import { LayoutDashboard, ChartNoAxesCombined, ChevronDown } from 'lucide-vue-next';
import { reportMenuViews as reportViews } from '../utils/reportingViews';
withDefaults(defineProps<{basePath?:string;showOverview?:boolean}>(),{basePath:'/admin',showOverview:false});
const emit=defineEmits<{navigate:[]}>();
const route=useRoute();
const selected=computed(()=>reportViews.some(view=>view.id===route.query.report)?String(route.query.report):'');
const otherQuery=computed(()=>Object.fromEntries(Object.entries(route.query).filter(([key])=>!['report','tab'].includes(key))));
const expanded=ref(!!selected.value);
const menuId=`report-menu-${useId()}`;
watch(selected,value=>{if(value)expanded.value=true;});
</script>
<style scoped>
.reporting-menu{color:white;padding:4px 0}.report-menu-link,.report-menu-toggle{display:flex;align-items:center;gap:10px;width:100%;min-height:40px;border:0;border-radius:7px;background:transparent;color:#ffffffc7;padding:10px 12px;font:inherit;font-size:13px;text-align:left;text-decoration:none;cursor:pointer}.report-menu-toggle>span{flex:1}.report-menu-toggle>svg:last-child{transition:transform .2s}.rotated{transform:rotate(180deg)}.report-menu-link:hover,.report-menu-toggle:hover{background:#ffffff13;color:white}.report-menu-link.active{background:#ffffff25;color:white;font-weight:600}.report-menu-children{padding-left:12px}.report-menu-children .report-menu-link{font-size:12px;min-height:36px}.menu-dot{width:4px;height:4px;border-radius:50%;background:currentColor;opacity:.6;flex-shrink:0}a:focus-visible,button:focus-visible{outline:2px solid #f5d780;outline-offset:2px}@media print{.reporting-menu{display:none}}
</style>
