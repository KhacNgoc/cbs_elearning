<template>
  <main class="course-outline">
    <RouterLink to="/" class="back-link"><ArrowLeft :size="18" /> Khóa học</RouterLink>
    <p v-if="error" class="error" role="alert">{{error}}</p>
    <template v-if="outline">
      <CourseThumbnail v-if="outline.course.thumbnail" class="outline-thumbnail" :thumbnail="outline.course.thumbnail" :name="outline.course.name" />
      <header><div><p>{{outline.course.domainName}} / {{outline.course.programName}}</p><h1>{{outline.course.name}}</h1><p>{{outline.chapters.length}} chương · {{totalLessons}} bài · {{completedCount}}/{{totalLessons}} bài hoàn thành</p></div><span v-if="outline.completed" class="complete-tag"><CheckCircle2 :size="18" /> Đã hoàn thành khóa học</span><button v-else-if="outline.resumeLessonId" class="btn btn-primary" @click="continueCourse">Tiếp tục học <ArrowRight :size="17" /></button></header>
      <p v-if="outline.course.description" class="description">{{outline.course.description}}</p>
      <div v-if="outline.completed" class="course-complete card"><h2>{{outline.chapters.length}}/{{outline.chapters.length}} chương đã hoàn thành</h2><p>Tất cả nội dung bài học và các kiểm tra bắt buộc đã hoàn thành. Kiểm tra tùy chọn vẫn có thể làm để ôn luyện.</p></div>
      <section v-for="(chapter,index) in outline.chapters" :key="chapter.id" class="card chapter-card">
        <div class="chapter-heading"><button class="chapter-title" :aria-expanded="expanded.has(chapter.id)" :aria-controls="`chapter-${chapter.id}`" @click="toggle(chapter.id)"><ChevronDown v-if="expanded.has(chapter.id)" :size="21"/><ChevronRight v-else :size="21"/><span>Chương {{index+1}} · {{chapter.title}}</span></button><div class="chapter-actions"><span class="chapter-status"><CheckCircle2 v-if="chapter.completed" :size="17"/><Lock v-else-if="chapter.locked" :size="17"/>{{chapter.completed?'Đã hoàn thành':chapter.locked?'Chưa mở khóa':`${chapterCompleted(chapter)}/${chapter.lessons.length} bài`}}</span><button class="btn" :class="chapter.completed?'btn-secondary':'btn-primary'" :disabled="chapter.locked || !chapter.lessons.length" @click="start(chapter)">{{chapter.locked?'Hoàn thành chương trước':chapter.completed?'Học lại chương':`Học chương ${index+1}`}}</button></div></div>
        <div v-show="expanded.has(chapter.id)" :id="`chapter-${chapter.id}`" class="chapter-lessons"><button v-for="(lesson,lessonIndex) in chapter.lessons" :key="lesson.id" class="outline-lesson" :disabled="chapter.locked || isLocked(chapter,lessonIndex)" @click="learn(lesson.id)"><CheckCircle2 v-if="completed(lesson.id)" :size="18"/><Lock v-else-if="chapter.locked || isLocked(chapter,lessonIndex)" :size="18"/><PlayCircle v-else :size="18"/><span>{{lessonIndex+1}}. {{lesson.title}}</span><small v-if="lesson.hasQuiz" :class="{required:lesson.quizRequired}">{{lesson.quizRequired?'Kiểm tra bắt buộc':'Kiểm tra tùy chọn'}} · {{passed(lesson)?'Đã đạt':'Chưa đạt'}}</small></button><p v-if="!chapter.lessons.length" class="empty">Chương chưa có bài học.</p></div>
      </section>
    </template><p v-else-if="!error">Đang tải khóa học...</p>
  </main>
</template>
<script setup lang="ts">
import { ref, computed, watch } from 'vue';import {useRoute,useRouter} from 'vue-router';
import {ArrowLeft,ArrowRight,CheckCircle2,ChevronDown,ChevronRight,Lock,PlayCircle} from 'lucide-vue-next';
import {trainingApi} from '../api/trainingApi';import {useUserStore} from '../stores/userStore';import type {CourseOutline,Chapter,Lesson} from '../types';
import CourseThumbnail from '../components/CourseThumbnail.vue';
import { resumeRoute } from '../utils/learning';
const route=useRoute(),router=useRouter(),user=useUserStore(),outline=ref<CourseOutline|null>(null),error=ref(''),expanded=ref(new Set<string>());
const totalLessons=computed(()=>outline.value?.chapters.reduce((n,c)=>n+c.lessons.length,0)||0);const completedCount=computed(()=>outline.value?.chapters.reduce((n,c)=>n+chapterCompleted(c),0)||0);
const completed=(id:string)=>user.completedLessons.includes(id);
const chapterCompleted=(chapter:Chapter)=>chapter.lessons.filter(l=>completed(l.id)).length;
const passed=(l:Lesson)=>(user.lessonQuizTotals[l.id] || 0)>0 && Math.round((user.lessonQuizScores[l.id] || 0)/(user.lessonQuizTotals[l.id] || 1)*100)>=(l.quizPassingScore || 80);
const isLocked=(c:Chapter,index:number)=>!outline.value?.course.canEdit && !user.systemAdmin && c.lessons.slice(0,index).some(l=>!completed(l.id));
function toggle(id:string){const set=new Set(expanded.value);set.has(id)?set.delete(id):set.add(id);expanded.value=set;}
function learn(id:string){router.push(`/lesson/${id}`);}
function continueCourse(){if(outline.value?.resume)router.push(resumeRoute(outline.value.resume));else if(outline.value?.resumeLessonId)learn(outline.value.resumeLessonId);}
function start(c:Chapter){const l=c.lessons.find(l=>!completed(l.id)) || c.lessons[0];if(l)learn(l.id);}
watch(()=>route.params.courseId,async()=>{error.value='';outline.value=null;try{await user.fetchProgress();outline.value=await trainingApi.outline(String(route.params.courseId));const first=outline.value.chapters.find(c=>!c.locked&&!c.completed)||outline.value.chapters[0];if(first)expanded.value=new Set([first.id]);}catch(e){error.value=(e as {response?:{data?:{message?:string}}}).response?.data?.message || 'Không thể tải khóa học.';}},{immediate:true});
</script>
<style scoped>
.outline-thumbnail{width:100%;max-height:220px;aspect-ratio:4/1;border-radius:12px;margin-bottom:1rem}
.course-outline{max-width:1200px;margin:auto;display:flex;flex-direction:column;gap:1.25rem}.back-link{display:flex;gap:.5rem;align-items:center;color:var(--primary-ink)}header,.chapter-heading,.chapter-actions{display:flex;justify-content:space-between;align-items:center;gap:1rem}header p,.description{color:var(--text-muted);font-size:.9rem}h1{font-size:2rem;margin:.6rem 0}.chapter-card{padding:1.5rem}.chapter-title{display:flex;align-items:center;gap:.75rem;border:0;background:none;text-align:left;font-size:1.15rem;font-weight:700;color:var(--text-main);cursor:pointer}.chapter-actions{flex-shrink:0;font-size:.8rem}.chapter-status,.complete-tag{display:flex;align-items:center;gap:.4rem;color:var(--primary-ink)}.chapter-lessons{margin-top:1.25rem;border-top:1px solid var(--border-color);padding-top:.5rem}.outline-lesson{width:100%;padding:1rem;border:0;border-bottom:1px solid var(--border-color);display:flex;align-items:center;gap:.75rem;background:var(--surface-ffffff);text-align:left;color:var(--text-main);cursor:pointer}.outline-lesson span{flex:1}.outline-lesson small{padding:.4rem .6rem;background:var(--primary-light);border-radius:6px;color:var(--primary-ink)}.outline-lesson small.required{background:var(--surface-fff5de);color:var(--ink-805b00)}.outline-lesson:disabled{opacity:.55;cursor:not-allowed}button:disabled{opacity:.5;cursor:not-allowed}.course-complete{padding:1.5rem;background:var(--primary-light);color:var(--primary-ink)}.course-complete p{margin-top:.75rem}.empty{padding:1rem;color:var(--text-muted)}.error{color:var(--ink-b91c1c)}@media(max-width:900px){header,.chapter-heading{align-items:flex-start;flex-direction:column}.chapter-actions{width:100%;justify-content:space-between}.outline-lesson{flex-wrap:wrap}.outline-lesson small{margin-left:2rem}}
</style>
