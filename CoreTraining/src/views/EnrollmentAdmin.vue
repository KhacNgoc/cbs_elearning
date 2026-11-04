<template>
 <section class="enrollments admin-page">
  <header><div><h1>Duyệt đăng ký</h1><p>Quản lý yêu cầu đăng ký khóa học.</p></div><button class="btn btn-secondary" :disabled="loading || busy" @click="load">Tải lại</button></header>
  <div class="filters"><label>Tìm kiếm<input v-model="query" placeholder="Học viên, tài khoản, khóa học..." /></label><label>Trạng thái<select v-model="status"><option value="">Tất cả</option><option value="PENDING">Chờ duyệt</option><option value="APPROVED">Đã duyệt</option><option value="REJECTED">Đã từ chối</option></select></label></div>
  <p v-if="loading" role="status">Đang tải yêu cầu đăng ký...</p>
  <p v-if="error" role="alert">{{ error }} <button @click="load">Thử lại</button></p>
  <div class="table-scroll"><table class="admin-data-table"><thead><tr><th>Học viên</th><th>Khóa học</th><th>Ngày đăng ký</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>
   <tr v-for="item in items" :key="item.id"><td><strong>{{ item.fullName }}</strong><br/><small>{{ item.username }}</small></td><td>{{ item.courseName }}</td><td>{{ date(item.requestedAt) }}</td><td>{{ labels[item.status] }}<p v-if="item.rejectionReason">{{ item.rejectionReason }}</p><small v-if="item.reviewedBy">{{ item.reviewedBy }} · {{ date(item.reviewedAt) }}</small></td><td><template v-if="item.status==='PENDING' && user.can('enrollments','update')"><button class="btn btn-primary" :disabled="busy || loading" @click="review(item,true)">Duyệt</button> <button class="btn btn-secondary" :disabled="busy || loading" @click="reject=item;reason=''">Từ chối</button></template></td></tr>
   <tr v-if="!items.length&&!loading&&!error"><td colspan="5">Không có yêu cầu phù hợp.</td></tr>
  </tbody></table></div>
  <footer><span>Trang {{ cursors.length }} · {{ items.length }} yêu cầu</span><label>Số yêu cầu / trang<select v-model.number="size"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select></label><button aria-label="Trang trước" :disabled="cursors.length<=1 || loading" @click="previous">Trước</button><button aria-label="Trang sau" :disabled="!hasMore || loading" @click="next">Sau</button></footer>
  <AdminModal :open="!!reject" title="Từ chối đăng ký" submit-label="Xác nhận từ chối" :busy="busy" :submit-disabled="!reason.trim()" danger @close="reject=null" @submit="reject && review(reject,false)"><p>{{ reject?.fullName }} · {{ reject?.courseName }}</p><label>Lý do từ chối<textarea v-model="reason" rows="4" required maxlength="1000" /></label></AdminModal>
 </section>
</template>
<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue';
import { adminListApi } from '../api/adminListApi';
import { adminApi } from '../api/adminApi';
import type { EnrollmentRequest } from '../api/adminApi';
import { useUserStore } from '../stores/userStore';
import { showToast } from '../utils/toast';
import AdminModal from '../components/AdminModal.vue';
const user=useUserStore(),items=ref<EnrollmentRequest[]>([]),query=ref(''),status=ref(''),size=ref(20),loading=ref(false),busy=ref(false),error=ref('');
const cursors=ref<(string|undefined)[]>([undefined]),nextCursor=ref<string|null>(null),hasMore=ref(false),reject=ref<EnrollmentRequest|null>(null),reason=ref('');
const labels={PENDING:'Chờ duyệt',APPROVED:'Đã duyệt',REJECTED:'Đã từ chối'};
const date=(value?:string)=>value?new Date(value).toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh'}):'—';
let sequence=0,timer:ReturnType<typeof setTimeout>|undefined;
async function load(){const request=++sequence;loading.value=true;error.value='';try{const data=await adminListApi.enrollments({query:query.value,status:status.value,size:size.value,cursor:cursors.value.at(-1)});if(request!==sequence)return;items.value=data.items;nextCursor.value=data.nextCursor;hasMore.value=data.hasMore;}catch{if(request===sequence){items.value=[];error.value='Không thể tải yêu cầu đăng ký. Vui lòng thử lại.';}}finally{if(request===sequence)loading.value=false;}}
function next(){if(hasMore.value&&nextCursor.value){cursors.value.push(nextCursor.value);void load();}}
function previous(){if(cursors.value.length>1){cursors.value.pop();void load();}}
watch([query,status,size],()=>{sequence++;clearTimeout(timer);cursors.value=[undefined];items.value=[];loading.value=true;timer=setTimeout(()=>void load(),250);});
async function review(item:EnrollmentRequest,approve:boolean){if(busy.value)return;busy.value=true;try{await adminApi.reviewEnrollmentRequest(item.id,approve,approve?undefined:reason.value.trim());reject.value=null;showToast(approve?'Đã duyệt đăng ký':'Đã từ chối đăng ký','success');await load();}catch(e){showToast((e as {response?:{data?:{message?:string}}}).response?.data?.message||'Không thể xử lý yêu cầu. Vui lòng thử lại.','error');}finally{busy.value=false;}}
onMounted(load);onBeforeUnmount(()=>{sequence++;clearTimeout(timer);});
</script>
<style scoped>
.enrollments{padding:2rem;display:grid;gap:1.2rem;min-width:0}header,footer{display:flex;align-items:center;justify-content:space-between;gap:1rem;flex-wrap:wrap}h1{margin:0}header p,small{color:var(--text-muted)}.filters{display:flex;gap:1rem;flex-wrap:wrap}label{display:grid;gap:.4rem}input,select,textarea{padding:.6rem;border:1px solid var(--border-color);border-radius:6px;color:var(--text-main);background:var(--bg-card)}input{min-width:260px}.table-scroll{overflow:auto}td{vertical-align:top}td p{max-width:350px;white-space:normal}textarea{width:100%}
</style>
