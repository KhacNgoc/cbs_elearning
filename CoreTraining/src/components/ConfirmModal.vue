<template>
  <AdminModal :open="open" :title="title" :width="460" :busy="loading" :submit-label="confirmLabel" danger alert @close="$emit('cancel')" @submit="$emit('confirm')">
    <div class="confirm-modal-icon"><TriangleAlert :size="25" /></div><p class="confirm-message">{{ message }}</p>
    <template #footer><button type="button" class="btn btn-secondary" :disabled="loading" @click="$emit('cancel')">Đóng</button><button type="submit" class="btn btn-danger" :disabled="loading">{{ loading?'Đang xử lý…':confirmLabel }}</button></template>
  </AdminModal>
</template>
<script setup lang="ts">
import AdminModal from './AdminModal.vue';
import {TriangleAlert} from 'lucide-vue-next';
withDefaults(defineProps<{open:boolean;title:string;message:string;confirmLabel?:string;loading?:boolean}>(),{confirmLabel:'Xác nhận',loading:false});
defineEmits<{confirm:[];cancel:[]}>();
</script>
<style scoped>
.confirm-modal-icon{width:48px;height:48px;margin-bottom:1rem;display:grid;place-items:center;border-radius:12px;background:#feecec;color:#b42318}.confirm-message{margin:0;color:var(--text-muted);line-height:1.6;white-space:pre-line;overflow-wrap:anywhere}.btn-danger{background:#dc2626;color:white;border:1px solid #dc2626}
</style>
