<template>
  <section class="course-rating-form" aria-label="Đánh giá khóa học">
    <header><div><span class="eyebrow">TRẢI NGHIỆM SAU KHÓA HỌC</span><h2>{{ state?.mine?'Đánh giá của bạn':'Bạn thấy khóa học này thế nào?' }}</h2><p>Chia sẻ mức độ hài lòng để chúng tôi cải thiện khóa học.</p></div><Star :size="24"/></header>
    <SkeletonLoader v-if="loading" :rows="3" label="Đang tải đánh giá" />
    <div v-if="error" role="alert" class="rating-error">{{ error }} <button type="button" :disabled="saving||loading" @click="load">{{ state?'Tải lại đánh giá':'Thử lại' }}</button></div>
    <template v-if="state&&!loading">
      <p class="rating-total">{{ state.summary.average===null?'Chưa có đánh giá':`${state.summary.average.toFixed(1)}/5 sao · ${state.summary.count} đánh giá` }} · Từ học viên đã hoàn thành khóa</p>
      <form v-if="state.canRate" @submit.prevent="save">
        <fieldset :disabled="saving"><legend>Mức độ hài lòng của bạn</legend><div class="rating-choices"><label v-for="star in 5" :key="star" :class="{selected:stars===star}"><input v-model="stars" type="radio" name="course-stars" :value="star" :aria-label="`${star} sao`" required/><span>{{ star }} ★</span></label></div>
          <label class="rating-feedback">Nhận xét (không bắt buộc)<textarea v-model="content" maxlength="2000" rows="4" placeholder="Điều hữu ích nhất hoặc nội dung bạn muốn cải thiện..."/></label><small>{{ content.length }}/2000 ký tự</small>
          <button class="btn btn-primary" :disabled="!stars||saving" type="submit">{{ saving?'Đang lưu...':state.mine?'Cập nhật đánh giá':'Gửi đánh giá khóa học' }}</button>
        </fieldset>
      </form>
      <p v-else>{{ state.message }}</p>
      <p v-if="success" class="rating-success" role="status">{{ success }}</p>
    </template>
  </section>
</template>
<script setup lang="ts">
import SkeletonLoader from "./SkeletonLoader.vue";
import { ref,watch,onBeforeUnmount } from 'vue';
import { Star } from 'lucide-vue-next';
import { courseRatingApi } from '../api/courseRatingApi';
import type { CourseRatingState } from '../api/courseRatingApi';
const props=defineProps<{courseId:string}>();
const state=ref<CourseRatingState|null>(null),stars=ref(0),content=ref(''),loading=ref(false),saving=ref(false),error=ref(''),success=ref('');
let sequence=0;
const message=(e:unknown)=>(e as {response?:{data?:{message?:string}}}).response?.data?.message||'Không thể lưu hoặc tải đánh giá. Vui lòng thử lại.';
async function load(){const request=++sequence;loading.value=true;error.value='';state.value=null;stars.value=0;content.value='';success.value='';try{const data=await courseRatingApi.state(props.courseId);if(request!==sequence)return;state.value=data;stars.value=data.mine?.stars||0;content.value=data.mine?.content||'';}catch(e){if(request===sequence)error.value=message(e);}finally{if(request===sequence)loading.value=false;}}
async function save(){if(!state.value?.canRate||!stars.value||saving.value)return;const request=sequence;const id=props.courseId;saving.value=true;error.value='';success.value='';try{const data=await courseRatingApi.save(id,{stars:stars.value,content:content.value,version:state.value.mine?.version??null});if(request!==sequence)return;state.value=data;success.value='Cảm ơn bạn! Đánh giá khóa học đã được lưu.';}catch(e){if(request===sequence)error.value=message(e);}finally{saving.value=false;}}
watch(()=>props.courseId,load,{immediate:true});onBeforeUnmount(()=>{sequence++;});
</script>
<style scoped>
.course-rating-form{background:var(--bg-card);border:1px solid var(--success-border);border-radius:12px;padding:24px}header{display:flex;justify-content:space-between;gap:15px}header>svg{color:#b88723;flex-shrink:0}.eyebrow{font-size:10px;letter-spacing:1px;color:var(--primary-ink)}h2{font-size:19px;margin:10px 0}p{font-size:13px;color:var(--text-muted);line-height:1.7}.rating-total{margin:16px 0}fieldset{border:0;padding:0;min-width:0}legend{font-size:13px;margin-bottom:12px;font-weight:600}.rating-choices{display:flex;gap:10px;flex-wrap:wrap;margin-bottom:20px}.rating-choices label{display:flex;gap:7px;align-items:center;border:1px solid var(--border-color);border-radius:7px;padding:10px 13px;font-size:14px;cursor:pointer}.rating-choices label.selected{background:var(--warning-surface);color:var(--warning-ink);border-color:var(--warning-border)}.rating-choices input{accent-color:var(--primary-color)}.rating-feedback{display:flex;flex-direction:column;gap:9px;font-size:13px}.rating-feedback textarea{width:100%;border:1px solid var(--border-color);border-radius:7px;padding:12px;resize:vertical;min-height:90px;font:inherit;color:var(--text-main);background:var(--bg-card)}small{display:block;text-align:right;color:var(--text-muted);font-size:11px;margin:8px 0 14px}.rating-error{background:var(--danger-surface);color:var(--danger-ink);padding:12px;margin:14px 0;border-radius:6px;font-size:13px}.rating-success{color:var(--success-ink);margin-top:14px}button:focus-visible,input:focus-visible,textarea:focus-visible{outline:2px solid var(--primary-ink);outline-offset:3px}@media(max-width:500px){.course-rating-form{padding:16px}.rating-choices{gap:7px}.rating-choices label{padding:9px}h2{font-size:17px}.btn{width:100%}}
</style>
