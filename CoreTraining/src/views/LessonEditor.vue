<template>
  <main class="lesson-editor-page">
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <template v-if="lesson && chapter">
      <header><div class="heading-with-back"><AdminBackButton :to="chapterUrl" label="Quay lại danh sách bài học trong chương" /><div class="heading-copy"><nav class="breadcrumb"><RouterLink :to="`/admin/courses/${chapter.courseId}`">{{ course?.name }}</RouterLink> / <RouterLink :to="chapterUrl">{{ chapter.title }}</RouterLink> / {{ lesson.id }}</nav><h1>{{ lesson.title }}</h1></div></div></header>
      <p v-if="course?.status !== 'DRAFT'" class="readonly-notice">Khóa đang xuất bản hoặc lưu trữ. <RouterLink :to="`/admin/courses/${chapter.courseId}`">Mở chi tiết khóa và chuyển về nháp</RouterLink> để chỉnh sửa bài.</p>
      <div class="editor-tabs" role="tablist"><button id="content-tab" role="tab" :aria-selected="tab === 'content'" aria-controls="content-panel" :class="{active:tab === 'content'}" @click="tab='content'">Nội dung bài học</button><button id="quiz-tab" role="tab" :aria-selected="tab === 'quiz'" aria-controls="quiz-panel" :class="{active:tab === 'quiz'}" @click="tab='quiz'">Bài kiểm tra <span v-if="lesson.hasQuiz">{{ lesson.quizRequired ? 'Bắt buộc' : 'Tùy chọn' }}</span></button></div>
      <fieldset :disabled="course?.status !== 'DRAFT'" class="editor-fieldset">
      <section id="content-panel" v-show="tab === 'content'" role="tabpanel" aria-labelledby="content-tab"><LessonContentEditor :lesson="lesson" :read-only="course?.status !== 'DRAFT'" @saved="savedContent" @dirty="dirty=$event" @cancel="router.push(chapterUrl)" /></section>
      <section id="quiz-panel" v-show="tab === 'quiz'" role="tabpanel" aria-labelledby="quiz-tab"><div v-if="!lesson.hasQuiz" class="card empty-quiz"><h2>Bài học chưa có bài kiểm tra</h2><p>Thêm bài kiểm tra đính kèm. Có thể chọn bắt buộc đạt để mở bài tiếp theo hoặc dùng để tự luyện.</p><button v-if="course?.status === 'DRAFT'" class="btn btn-primary" :disabled="creating" @click="createQuiz">Tạo bài kiểm tra</button></div><AdminQuizDetail v-else :key="lesson.id" embedded :lesson="lesson" :read-only="course?.status !== 'DRAFT'" @saved="refreshLesson" /></section>
      </fieldset>
    </template><p v-else-if="!error">Đang tải bài học...</p>
  </main>
</template>
<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue';
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router';
import { trainingApi } from '../api/trainingApi';
import { adminApi } from '../api/adminApi';
import { useCourseStore } from '../stores/courseStore';
import type { Lesson, Chapter, Course } from '../types';
import LessonContentEditor from './LessonContentEditor.vue';
import AdminQuizDetail from './AdminQuizDetail.vue';
import AdminBackButton from '../components/AdminBackButton.vue';
import { showToast } from '../utils/toast';
const route=useRoute(),router=useRouter(),courseStore=useCourseStore();
const lesson=ref<Lesson|null>(null),chapter=ref<Chapter|null>(null),course=ref<Course|null>(null),error=ref(''),dirty=ref(false),creating=ref(false),tab=ref('content');
const chapterUrl=computed(()=>`/admin/courses/${chapter.value?.courseId}/chapters/${chapter.value?.id}`);
async function refreshLesson(){lesson.value=await trainingApi.lesson(String(route.params.lessonId));}
async function load(){try{lesson.value=null;await courseStore.fetchCourses(true);await refreshLesson();chapter.value=await trainingApi.chapter(lesson.value!.chapterId!);course.value=courseStore.courses.find(c=>c.id===chapter.value?.courseId)||null;}catch(e){error.value=(e as {response?:{data?:{message?:string}}}).response?.data?.message || 'Không tải được bài học.';}}
watch(()=>route.params.lessonId,()=>{dirty.value=false;void load();},{immediate:true});
async function savedContent(){dirty.value=false;await refreshLesson();}
async function createQuiz(){creating.value=true;try{await adminApi.updateQuizSettings(lesson.value!.courseId,lesson.value!.id,80,true);showToast('Đã tạo bài kiểm tra.');await refreshLesson();}catch(e){error.value=(e as {response?:{data?:{message?:string}}}).response?.data?.message || 'Không tạo được kiểm tra.';showToast(error.value,'error');}finally{creating.value=false;}}
onBeforeRouteLeave(()=>!dirty.value || window.confirm('Nội dung chưa lưu. Bạn có muốn rời trang và bỏ các thay đổi?'));
function beforeUnload(e:BeforeUnloadEvent){if(dirty.value){e.preventDefault();e.returnValue='';}}
onMounted(()=>window.addEventListener('beforeunload',beforeUnload));onBeforeUnmount(()=>window.removeEventListener('beforeunload',beforeUnload));
</script>
<style scoped>
.editor-fieldset{border:0;padding:0;margin:0;min-width:0}.readonly-notice{padding:1rem;background:#fff5de;color:#805b00;border-radius:8px;margin-bottom:1rem}.readonly-notice a{text-decoration:underline}
.lesson-editor-page{padding:2rem;min-width:0}.lesson-editor-page header{display:flex;align-items:center;justify-content:space-between;gap:1rem;margin-bottom:1.5rem}.breadcrumb{font-size:.85rem;color:var(--text-muted)}.breadcrumb a{color:var(--primary-color)}h1{font-size:1.5rem;margin-top:.75rem}.editor-tabs{display:flex;gap:2rem;border-bottom:1px solid var(--border-color);margin-bottom:1.5rem}.editor-tabs button{border:0;border-bottom:3px solid transparent;background:none;padding:1rem 0;color:var(--text-muted);font-size:1rem;cursor:pointer}.editor-tabs button.active{color:var(--primary-color);border-color:var(--primary-color);font-weight:700}.editor-tabs span{font-size:.75rem;margin-left:.5rem}.empty-quiz{padding:2rem;text-align:center}.empty-quiz p{margin:1rem;color:var(--text-muted)}.error{color:#b91c1c}@media(max-width:768px){.lesson-editor-page{padding:1rem}.lesson-editor-page header{flex-direction:column;align-items:flex-start}}
.heading-with-back{display:flex;align-items:flex-start;gap:1rem;min-width:0;width:100%}
.heading-copy{min-width:0}
.heading-copy h1,.heading-copy .breadcrumb{overflow-wrap:anywhere}
@media(max-width:560px){.heading-with-back{gap:.75rem}}
</style>
