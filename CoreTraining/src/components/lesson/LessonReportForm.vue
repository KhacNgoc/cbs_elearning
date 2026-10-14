<template>
  <div class="report-entry"><button type="button" @click="open=true"><Flag :size="16" /> Báo cáo nội dung không phù hợp</button><small>Gửi phản ánh riêng đến quản trị viên để xem xét.</small></div>
  <AdminModal :open="open" title="Báo cáo nội dung bài học" :busy="sending" submit-label="Gửi báo cáo" @close="open=false" @submit="submit">
    <p class="report-lesson">{{ lessonTitle }}</p>
    <label class="report-field">Nội dung báo cáo<textarea v-model="content" rows="6" maxlength="5000" required placeholder="Mô tả nội dung không phù hợp, vị trí trong bài học và lý do…"></textarea><small>{{ content.length }}/5.000 ký tự</small></label>
    <label class="report-field">Ảnh minh chứng (không bắt buộc)<input type="file" accept="image/png,image/jpeg" multiple @change="choose" /><small>Tối đa 3 ảnh PNG/JPEG, mỗi ảnh không quá 5 MB.</small></label>
    <div class="evidence-list"><div v-for="(file,index) in images" :key="file.name+index"><img :src="previews[index]" :alt="file.name" /><span>{{ file.name }}</span><button type="button" :disabled="sending" @click="remove(index)" :aria-label="`Gỡ ảnh ${file.name}`"><X :size="16" /></button></div></div>
    <p v-if="error" class="report-error" role="alert">{{ error }}</p>
  </AdminModal>
</template>
<script setup lang="ts">
import {ref,watch,onBeforeUnmount} from 'vue';
import {Flag,X} from 'lucide-vue-next';
import AdminModal from '../AdminModal.vue';
import {contentToolsApi,contentToolsError} from '../../api/contentToolsApi';
import {showToast} from '../../utils/toast';
const props=defineProps<{lessonId:string;lessonTitle:string}>();
const open=ref(false),sending=ref(false),content=ref(''),error=ref(''),images=ref<File[]>([]),previews=ref<string[]>([]);
function clear(){content.value='';error.value='';images.value=[];previews.value.forEach(URL.revokeObjectURL);previews.value=[];}
watch(open,value=>{if(!value)clear();});
function choose(event:Event){const input=event.target as HTMLInputElement;const files=[...(input.files||[])];input.value='';if(images.value.length+files.length>3 || files.some(f=>!['image/png','image/jpeg'].includes(f.type)||f.size>5*1024*1024)){error.value='Chọn tối đa 3 ảnh PNG/JPEG, mỗi ảnh không quá 5 MB.';return;}error.value='';images.value.push(...files);previews.value.push(...files.map(f=>URL.createObjectURL(f)));}
function remove(index:number){URL.revokeObjectURL(previews.value[index]!);previews.value.splice(index,1);images.value.splice(index,1);}
async function submit(){if(sending.value)return;if(!content.value.trim()){error.value='Vui lòng nhập nội dung báo cáo.';return;}sending.value=true;error.value='';try{await contentToolsApi.submitReport(props.lessonId,content.value,images.value);open.value=false;showToast('Đã gửi báo cáo đến quản trị viên.');}catch(e){error.value=contentToolsError(e);}finally{sending.value=false;}}
onBeforeUnmount(clear);
</script>
<style scoped>
.report-entry{display:flex;align-items:center;flex-wrap:wrap;gap:.75rem;margin:1rem 0}.report-entry>button{display:flex;align-items:center;gap:.5rem;border:1px solid #e3c1b9;border-radius:8px;background:white;padding:.65rem .85rem;color:#9b3c2a;cursor:pointer}.report-entry small,.report-field small{color:var(--text-muted);font-size:.8rem}.report-field{display:grid;gap:.5rem;margin:1rem 0}.report-lesson{font-weight:700;color:var(--primary-color)}.report-error{color:#b91c1c;margin-top:1rem}.evidence-list{display:grid;gap:.5rem}.evidence-list>div{display:flex;align-items:center;gap:.75rem;padding:.5rem;border:1px solid var(--border-color);border-radius:8px}.evidence-list img{width:64px;height:64px;object-fit:cover}.evidence-list span{flex:1;overflow-wrap:anywhere}.evidence-list button{background:none;border:0;cursor:pointer}
</style>
