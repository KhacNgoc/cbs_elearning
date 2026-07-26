<template>
  <div v-if="lesson" class="learning-page" :class="{ 'sidebar-visible': sidebarOpen }">
    <LessonLearningHeader :topic-name="topic?.name" :lesson-title="lesson.title" :completed-count="completedCount"
      :total-lessons="topic?.lessons.length || 0" :completed="isCompleted" @complete="markAsCompleted" @open-content="sidebarOpen = true" />
    <div class="learning-stage" :class="{ 'sidebar-hidden': !sidebarOpen }">
      <LessonMediaStage ref="mediaStage" :lesson="lesson" :selected-document="selectedDocument"
        :current-index="currentLessonIndex" :total-lessons="topic?.lessons.length || 0"
        @adjacent="goToAdjacentLesson" @close-document="selectedDocument = null"
        @download-document="downloadDocument" @time-update="currentVideoTime = $event"
        @add-note="openNoteComposer" />
      <LessonCourseSidebar :topic="topic" :active-lesson-id="lesson.id" :completed-lessons="userStore.completedLessons"
        :completed-count="completedCount" :progress="courseProgress" :open="sidebarOpen" :selected-document-id="selectedDocument?.id"
        @close="sidebarOpen = false" @select="switchLesson" @select-document="showDocument" @quiz="startQuiz" />
      <button v-if="!sidebarOpen" class="sidebar-reopen" @click="sidebarOpen = true" aria-label="Mở nội dung khóa học"><ChevronLeft :size="22" /></button>
      <div v-if="sidebarOpen" class="mobile-overlay" @click="sidebarOpen = false"></div>
    </div>

    <section class="lesson-information">
      <LessonOverview :lesson="lesson" :topic-name="topic?.name" :completed="isCompleted">
        <LessonVideoNotes ref="videoNotesPanel" :notes="videoNotes" :current-time="currentVideoTime" :loading="notesLoading"
          :saving="noteSaving" @create="createVideoNote" @update="updateVideoNote"
          @delete="deleteVideoNote" @seek="seekToNote" />
        <LessonDiscussion :comments="lesson.comments" v-model:rating="userRating" v-model:comment="commentText" @submit="submitFeedback" />
      </LessonOverview>
    </section>
  </div>
  <div v-else class="loading-state"><span></span>Đang tải bài học...</div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ChevronLeft } from 'lucide-vue-next';
import { useCourseStore } from '../stores/courseStore';
import { useUserStore } from '../stores/userStore';
import { axiosClient } from '../api/axiosClient';
import { courseApi } from '../api/courseApi';
import type { LessonDocument, VideoNote } from '../types';
import LessonLearningHeader from '../components/lesson/LessonLearningHeader.vue';
import LessonCourseSidebar from '../components/lesson/LessonCourseSidebar.vue';
import LessonMediaStage from '../components/lesson/LessonMediaStage.vue';
import LessonOverview from '../components/lesson/LessonOverview.vue';
import LessonDiscussion from '../components/lesson/LessonDiscussion.vue';
import LessonVideoNotes from '../components/lesson/LessonVideoNotes.vue';

const props = defineProps<{ id: string }>();
const router = useRouter();
const courseStore = useCourseStore();
const userStore = useUserStore();
const mediaStage = ref<InstanceType<typeof LessonMediaStage> | null>(null);
const videoNotesPanel = ref<InstanceType<typeof LessonVideoNotes> | null>(null);
const selectedDocument = ref<LessonDocument | null>(null);
const pendingDocument = ref<LessonDocument | null>(null);
const userRating = ref(5);
const commentText = ref('');
const sidebarOpen = ref(true);
const currentVideoTime = ref(0);
const videoNotes = ref<VideoNote[]>([]);
const notesLoading = ref(false);
const noteSaving = ref(false);

const lesson = computed(() => courseStore.currentLesson);
const topic = computed(() => courseStore.currentTopic);
const isCompleted = computed(() => lesson.value ? userStore.completedLessons.includes(lesson.value.id) : false);
const completedCount = computed(() => topic.value?.lessons.filter(item => userStore.completedLessons.includes(item.id)).length || 0);
const courseProgress = computed(() => topic.value?.lessons.length ? Math.round(completedCount.value / topic.value.lessons.length * 100) : 0);
const currentLessonIndex = computed(() => topic.value?.lessons.findIndex(item => item.id === lesson.value?.id) ?? -1);

const loadLessonData = async (lessonId: string) => {
  currentVideoTime.value = 0;
  videoNotes.value = [];
  await courseStore.fetchLessonDetail(lessonId);
  
  // Guard check: topic accessibility
  if (topic.value && !userStore.isTopicAccessible(topic.value.id)) {
    alert("Bạn cần đăng ký học chủ đề nghiệp vụ này trước!");
    router.push('/');
    return;
  }

  // Auto complete lesson when viewed
  if (lesson.value) {
    await userStore.completeLesson(lesson.value.id);
    await loadVideoNotes(lesson.value.id);
  }
};

const loadVideoNotes = async (lessonId: string) => {
  notesLoading.value = true;
  try {
    videoNotes.value = await courseApi.getVideoNotes(lessonId);
  } catch (error) {
    console.error('Failed to load video notes:', error);
    videoNotes.value = [];
  } finally {
    notesLoading.value = false;
  }
};

onMounted(async () => {
  if (userStore.mandatoryTopics.length === 0) {
    await userStore.fetchProgress();
  }
  if (courseStore.topics.length === 0) {
    await courseStore.fetchTopics();
  }
  await loadLessonData(props.id);
  sidebarOpen.value = window.innerWidth > 900;
});

watch(() => props.id, async newId => {
  selectedDocument.value = null;
  await loadLessonData(newId);
  if (pendingDocument.value) {
    selectedDocument.value = pendingDocument.value;
    pendingDocument.value = null;
  }
  if (window.innerWidth <= 900) sidebarOpen.value = false;
});

const switchLesson = (lessonId: string) => router.push(`/lesson/${lessonId}`);
const showDocument = (lessonId: string, document: LessonDocument) => {
  if (lessonId !== lesson.value?.id) {
    pendingDocument.value = document;
    router.push(`/lesson/${lessonId}`);
    return;
  }
  selectedDocument.value = document;
};
const downloadDocument = async (document: import('../types').LessonDocument) => {
  try {
    const apiBaseUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080';
    const url = /^https?:\/\//i.test(document.fileUrl) || document.fileUrl.startsWith('blob:')
      ? document.fileUrl
      : `${apiBaseUrl}${document.fileUrl.startsWith('/') ? document.fileUrl : `/${document.fileUrl}`}`;
    const response = await axiosClient.get<Blob>(url, { responseType: 'blob' });
    const objectUrl = URL.createObjectURL(response.data);
    const link = window.document.createElement('a');
    link.href = objectUrl;
    link.download = document.originalFileName || document.name;
    window.document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(objectUrl);
  } catch (cause) {
    console.error('Failed to download document:', cause);
    alert('Không thể tải tài liệu. Vui lòng thử lại.');
  }
};
const goToAdjacentLesson = (offset: number) => {
  const nextLesson = topic.value?.lessons[currentLessonIndex.value + offset];
  if (nextLesson) switchLesson(nextLesson.id);
};
const markAsCompleted = async () => { if (lesson.value) await userStore.completeLesson(lesson.value.id); };
const startQuiz = () => { if (topic.value) router.push(`/quiz/${topic.value.id}`); };
const sortVideoNotes = () => {
  videoNotes.value.sort((a, b) =>
    a.timestampSeconds - b.timestampSeconds || a.createdAt.localeCompare(b.createdAt)
  );
};
const createVideoNote = async (content: string, timestampSeconds: number) => {
  if (!lesson.value) return;
  noteSaving.value = true;
  try {
    videoNotes.value.push(await courseApi.createVideoNote(lesson.value.id, timestampSeconds, content));
    sortVideoNotes();
  } catch (error) {
    console.error('Failed to create video note:', error);
    alert('Không thể lưu ghi chú. Vui lòng thử lại.');
  } finally {
    noteSaving.value = false;
  }
};
const updateVideoNote = async (note: VideoNote, content: string) => {
  if (!lesson.value) return;
  noteSaving.value = true;
  try {
    const updated = await courseApi.updateVideoNote(
      lesson.value.id, note.id, note.timestampSeconds, content
    );
    const index = videoNotes.value.findIndex(item => item.id === note.id);
    if (index >= 0) videoNotes.value[index] = updated;
    sortVideoNotes();
  } catch (error) {
    console.error('Failed to update video note:', error);
    alert('Không thể cập nhật ghi chú. Vui lòng thử lại.');
  } finally {
    noteSaving.value = false;
  }
};
const deleteVideoNote = async (note: VideoNote) => {
  if (!lesson.value) return;
  noteSaving.value = true;
  try {
    await courseApi.deleteVideoNote(lesson.value.id, note.id);
    videoNotes.value = videoNotes.value.filter(item => item.id !== note.id);
  } catch (error) {
    console.error('Failed to delete video note:', error);
    alert('Không thể xóa ghi chú. Vui lòng thử lại.');
  } finally {
    noteSaving.value = false;
  }
};
const seekToNote = async (seconds: number) => {
  if (selectedDocument.value) {
    selectedDocument.value = null;
    await nextTick();
  }
  mediaStage.value?.seekTo(seconds);
  window.scrollTo({ top: 0, behavior: 'smooth' });
};
const openNoteComposer = (seconds: number) => {
  videoNotesPanel.value?.openAt(seconds);
};
const submitFeedback = async () => {
  if (!commentText.value.trim() || !lesson.value) return;
  await courseStore.addComment(lesson.value.id, userRating.value, commentText.value);
  commentText.value = '';
  userRating.value = 5;
};
</script>

<style>
.learning-page { margin: -2rem; background: var(--bg-main); min-height: calc(100vh - 73px); color: var(--text-main); transition: padding-right .25s ease; }
.learning-page.sidebar-visible { padding-right: 370px; }
.learning-header { min-height: 68px; padding: 0 1.25rem; display: flex; align-items: center; gap: 1rem; background: #10251f; color: white; border-bottom: 1px solid rgba(255,255,255,.12); }
.back-button { width: 40px; height: 40px; display: grid; place-items: center; border-radius: 8px; border: 1px solid rgba(255,255,255,.18); transition: .2s ease; }
.back-button:hover { background: rgba(255,255,255,.1); transform: translateX(-2px); }
.header-copy { min-width: 0; flex: 1; display: flex; align-items: center; gap: .9rem; }
.course-name { color: #e4c760; font-weight: 700; white-space: nowrap; }
.header-separator { width: 1px; height: 24px; background: rgba(255,255,255,.25); }
.header-copy h1 { font-size: 1rem; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.header-actions, .progress-summary, .outline-action, .mobile-content-button { display: flex; align-items: center; gap: .5rem; }
.progress-summary { color: #dbe7e3; font-size: .82rem; }
.outline-action, .mobile-content-button { min-height: 38px; padding: 0 .85rem; color: white; background: transparent; border: 1px solid rgba(255,255,255,.3); border-radius: 7px; cursor: pointer; font-weight: 600; }
.outline-action:disabled { color: #8ae2c2; border-color: rgba(138,226,194,.35); cursor: default; }
.mobile-content-button { display: none; }
.learning-stage { display: block; background: #17201e; height: clamp(480px, calc(100vh - 193px), 720px); position: relative; overflow: hidden; }
.viewer-area { width: 100%; height: 100%; min-width: 0; min-height: 0; padding: 14px 18px 0; display: flex; flex-direction: column; overflow: hidden; }
.viewer-shell { flex: 1; min-height: 0; display: grid; place-items: stretch; overflow: hidden; background: #080b0a; box-shadow: 0 10px 30px rgba(0,0,0,.22); }
.viewer-shell .card { border: 0; border-radius: 0; box-shadow: none; }
.viewer-shell .video-container, .viewer-shell .slide-viewer { width: 100%; height: 100%; min-height: 0; padding: 0; }
.viewer-shell .video-title-bar, .viewer-shell .video-footer-info, .viewer-shell .viewer-header { margin: 0; padding: .7rem 1rem; }
.viewer-shell .video-player-wrapper { flex: 1 1 auto; width: 100%; min-height: 0; max-height: none; aspect-ratio: auto; border-radius: 0; }
.viewer-shell .video-element { width: 100%; height: 100%; object-fit: contain; }
.viewer-shell .slide-content { border-radius: 0; min-height: 450px; }
.viewer-navigation { height: 70px; display: grid; grid-template-columns: 150px 1fr 150px; align-items: center; color: white; }
.viewer-navigation button { display: flex; align-items: center; justify-content: center; gap: .4rem; background: transparent; border: 0; color: #d5ddda; cursor: pointer; }
.viewer-navigation button:hover:not(:disabled) { color: #e4c760; }
.viewer-navigation button:disabled { opacity: .3; cursor: not-allowed; }
.viewer-lesson-title { min-width: 0; text-align: center; display: flex; flex-direction: column; }
.viewer-lesson-title span { color: #8ba29a; font-size: .72rem; text-transform: uppercase; letter-spacing: .08em; }
.viewer-lesson-title strong { font-size: .9rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.course-sidebar { position: fixed; top: 73px; right: 0; bottom: 0; width: 370px; min-width: 370px; background: #fff; border-left: 1px solid #dce5e1; display: flex; flex-direction: column; overflow: hidden; box-shadow: -8px 0 24px rgba(9,31,24,.08); transition: transform .25s ease; z-index: 90; }
.sidebar-hidden .course-sidebar { transform: translateX(100%); }
.sidebar-header { padding: 1rem 1.1rem .75rem; display: flex; justify-content: space-between; align-items: flex-start; }
.sidebar-header h2 { font-size: 1rem; }
.sidebar-header p { color: var(--text-muted); font-size: .75rem; margin-top: .15rem; }
.sidebar-close { border: 0; background: transparent; color: var(--text-muted); cursor: pointer; padding: .2rem; }
.sidebar-progress { height: 3px; background: #e4eae8; }
.sidebar-progress span { display: block; height: 100%; background: var(--primary-color); transition: width .4s ease; }
.lesson-list { overflow-y: auto; flex: 1; }
.lesson-row { width: 100%; padding: .85rem 1rem; display: flex; align-items: flex-start; gap: .7rem; background: #fff; color: var(--text-main); border: 0; border-bottom: 1px solid #edf1ef; text-align: left; cursor: pointer; }
.lesson-row:hover { background: #f4f8f6; }
.lesson-row.active { background: var(--primary-light); box-shadow: inset 4px 0 var(--primary-color); }
.lesson-check { width: 18px; height: 18px; flex: 0 0 18px; margin-top: 1px; display: grid; place-items: center; border: 1.5px solid #94a3b8; border-radius: 3px; }
.lesson-row.completed .lesson-check { background: var(--primary-color); border-color: var(--primary-color); color: white; }
.lesson-row-copy { display: flex; flex-direction: column; gap: .35rem; min-width: 0; }
.lesson-row-copy strong { font-size: .85rem; font-weight: 600; line-height: 1.35; }
.lesson-row-copy small { display: flex; align-items: center; gap: .3rem; color: var(--text-muted); }
.quiz-box { margin: .8rem; padding: .9rem; border-radius: 9px; background: #f5f0dc; border: 1px solid #e7d692; }
.quiz-box > div { display: flex; align-items: center; gap: .5rem; color: #6a5610; }
.quiz-box p { margin: .4rem 0 .7rem; font-size: .75rem; color: #71683f; }
.quiz-box button { border: 0; background: transparent; color: var(--primary-color); font-weight: 700; display: flex; gap: .35rem; align-items: center; cursor: pointer; }
.sidebar-reopen { position: fixed; right: 0; top: 45%; width: 35px; height: 52px; border: 0; border-radius: 8px 0 0 8px; background: var(--primary-color); color: white; cursor: pointer; z-index: 89; }
.mobile-overlay { display: none; }
.procedure-viewer { overflow-y: auto; padding: 2rem; background: #e9efed; }
.procedure-document { max-width: 900px; margin: auto; padding: 2.5rem; background: white; min-height: 100%; }
.procedure-heading { border-bottom: 3px solid var(--primary-color); padding-bottom: 1rem; margin-bottom: 1.5rem; }
.procedure-heading span, .eyebrow { color: var(--primary-color); font-size: .75rem; text-transform: uppercase; letter-spacing: .1em; font-weight: 700; }
.procedure-heading h2 { margin-top: .35rem; }
.procedure-section { display: grid; grid-template-columns: 42px 1fr; gap: 1rem; margin: 1.5rem 0; }
.section-number { font-weight: 800; color: #c5a539; font-size: 1.15rem; }
.procedure-section h3 { font-size: 1rem; margin-bottom: .5rem; }
.procedure-section ul { padding-left: 1rem; color: #46564f; }
.procedure-section li { margin: .4rem 0; }
.discussion-viewer { height: 100%; display: grid; place-content: center; justify-items: center; padding: 2rem; text-align: center; color: #c5d5cf; background: radial-gradient(circle at center, #173c31 0%, #07110e 72%); }
.discussion-viewer svg { color: #e4c760; margin-bottom: 1rem; }
.discussion-viewer span { color: #79bfa6; text-transform: uppercase; font-size: .75rem; font-weight: 700; letter-spacing: .1em; }
.discussion-viewer h2 { color: white; margin: .4rem 0; }
.discussion-viewer p { max-width: 520px; color: #9db3ab; }

/* Multi-file document workspace */
.document-workspace { height: 100%; min-height: 0; display: grid; grid-template-columns: 280px minmax(0, 1fr); position: relative; background: #dfe8e5; overflow: hidden; }
.document-library { min-width: 0; height: 100%; display: flex; flex-direction: column; background: #f8faf9; border-right: 1px solid #d3ddd9; z-index: 3; }
.document-library-header { min-height: 65px; padding: .8rem 1rem; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #dbe4e0; }
.document-library-header > div { display: flex; flex-direction: column; }
.document-library-header span { color: var(--text-muted); font-size: .72rem; text-transform: uppercase; letter-spacing: .06em; }
.document-library-header strong { font-size: .9rem; }
.document-library-close { display: none; border: 0; background: transparent; color: var(--text-muted); }
.document-list { flex: 1; overflow-y: auto; padding: .55rem; }
.document-list button { width: 100%; display: grid; grid-template-columns: 38px minmax(0, 1fr) 16px; align-items: center; gap: .6rem; padding: .7rem; border: 1px solid transparent; border-radius: 8px; background: transparent; color: var(--text-main); text-align: left; cursor: pointer; }
.document-list button:hover { background: #eef4f1; }
.document-list button.active { background: var(--primary-light); border-color: rgba(0,91,65,.18); color: var(--primary-color); }
.document-type-icon { width: 36px; height: 40px; display: grid; place-items: center; border-radius: 6px; background: white; border: 1px solid #d7e0dc; color: var(--primary-color); }
.document-list button > span:nth-child(2) { min-width: 0; display: flex; flex-direction: column; }
.document-list button strong { font-size: .8rem; line-height: 1.35; }
.document-list button small { margin-top: .25rem; color: var(--text-muted); font-size: .7rem; }
.document-preview { min-width: 0; min-height: 0; display: flex; flex-direction: column; overflow: hidden; }
.document-preview-header { min-height: 65px; padding: .55rem .9rem; display: flex; align-items: center; gap: .8rem; background: white; border-bottom: 1px solid #d3ddd9; }
.document-library-toggle { display: none; align-items: center; gap: .4rem; border: 1px solid #ced9d5; background: white; border-radius: 6px; padding: .45rem .6rem; color: var(--primary-color); cursor: pointer; }
.selected-file { min-width: 0; display: flex; align-items: center; gap: .55rem; color: var(--primary-color); }
.selected-file > span { min-width: 0; display: flex; flex-direction: column; }
.selected-file strong { color: var(--text-main); font-size: .85rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.selected-file small { color: var(--text-muted); font-size: .7rem; }
.open-document { margin-left: auto; display: flex; align-items: center; gap: .35rem; color: var(--primary-color); font-size: .8rem; font-weight: 700; white-space: nowrap; }
.document-preview-body { flex: 1; min-height: 0; overflow: auto; }
.document-preview-body > .slide-viewer { min-height: 100%; height: max-content; }
.document-preview-body iframe { width: 100%; height: 100%; min-height: 450px; border: 0; background: white; }
.external-document { min-height: 100%; display: grid; place-content: center; justify-items: center; text-align: center; background: white; color: var(--text-muted); }
.external-document h3 { margin-top: .8rem; color: var(--text-main); }
.external-document a { margin-top: 1rem; display: flex; align-items: center; gap: .4rem; padding: .6rem .9rem; border-radius: 7px; background: var(--primary-color); color: white; }
.document-library-overlay { display: none; }
.lesson-information { background: white; }
.content-tabs { z-index: 80; min-height: 52px; padding: 6px 1.1rem; display: flex; align-items: center; justify-content: flex-start; gap: .35rem; background: #132b24; border-top: 1px solid rgba(255,255,255,.06); border-bottom: 1px solid rgba(255,255,255,.1); box-shadow: 0 4px 12px rgba(5,18,14,.14); overflow-x: auto; }
.content-tabs button { position: relative; min-height: 38px; padding: 0 .95rem; display: flex; align-items: center; gap: .45rem; background: transparent; border: 1px solid transparent; border-radius: 7px; color: #aebdb8; white-space: nowrap; font-size: .86rem; font-weight: 600; cursor: pointer; transition: .2s ease; }
.content-tabs button:hover { color: white; background: rgba(255,255,255,.06); }
.content-tabs button::after { content: ''; position: absolute; left: .9rem; right: .9rem; bottom: 1px; height: 2px; border-radius: 2px; background: transparent; }
.content-tabs button.active { color: white; background: rgba(0,91,65,.45); border-color: rgba(126,205,177,.22); }
.content-tabs button.active svg { color: #e4c760; }
.content-tabs button.active::after { background: #e4c760; }
.info-layout { max-width: 1280px; margin: auto; padding: 2.5rem 2rem 4rem; display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 3.5rem; }
.info-main { min-width: 0; }
.lesson-overview h2, .section-title h2 { font-size: 1.55rem; margin: .35rem 0 1rem; }
.hint-box { padding: 1rem 1.1rem; display: flex; gap: .8rem; background: var(--primary-light); border-left: 4px solid var(--primary-color); color: var(--primary-color); }
.hint-box svg { flex: 0 0 auto; }
.hint-box p { color: #3f5f54; margin-top: .2rem; }
.lesson-facts { border-left: 1px solid var(--border-color); padding-left: 1.5rem; }
.lesson-facts h3 { font-size: 1rem; margin-bottom: 1rem; }
.lesson-facts > div { display: flex; gap: .7rem; color: var(--primary-color); margin: 1rem 0; }
.lesson-facts span { display: flex; flex-direction: column; }
.lesson-facts small { color: var(--text-muted); }
.lesson-facts strong { color: var(--text-main); font-size: .85rem; }
.discussion-section { margin-top: 3rem; padding-top: 2rem; border-top: 1px solid var(--border-color); }
.section-title { display: flex; align-items: center; justify-content: space-between; }
.section-title > svg { color: var(--primary-color); }
.feedback-form { padding: 1.1rem; background: #f8faf9; border: 1px solid var(--border-color); border-radius: 10px; }
.rating-row { display: flex; align-items: center; gap: 1rem; margin-bottom: .8rem; font-weight: 600; }
.feedback-form textarea { width: 100%; resize: vertical; border: 1px solid #cfd9d5; border-radius: 7px; padding: .8rem; outline: none; }
.feedback-form textarea:focus { border-color: var(--primary-color); box-shadow: 0 0 0 3px rgba(0,91,65,.1); }
.submit-button { margin: .75rem 0 0 auto; padding: .65rem 1rem; display: flex; align-items: center; gap: .4rem; border: 0; border-radius: 7px; color: white; background: var(--primary-color); cursor: pointer; font-weight: 700; }
.submit-button:disabled { opacity: .5; cursor: not-allowed; }
.comments-list { margin-top: 1rem; }
.empty-comments { padding: 2rem; text-align: center; color: var(--text-muted); }
.comment { padding: 1rem 0; display: grid; grid-template-columns: 40px 1fr; gap: .8rem; border-bottom: 1px solid var(--border-color); }
.avatar { width: 40px; height: 40px; display: grid; place-items: center; border-radius: 50%; background: var(--primary-color); color: white; font-weight: 700; }
.comment-meta { display: flex; justify-content: space-between; }
.comment-meta span { font-size: .75rem; color: var(--text-muted); }
.comment p { margin-top: .35rem; color: #43534d; }
.loading-state { min-height: 60vh; display: grid; place-content: center; gap: .7rem; color: var(--text-muted); text-align: center; }
.loading-state span { width: 30px; height: 30px; margin: auto; border: 3px solid var(--border-color); border-top-color: var(--primary-color); border-radius: 50%; animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: 1100px) {
  .learning-page.sidebar-visible { padding-right: 320px; }
  .course-sidebar { width: 320px; min-width: 320px; }
  .progress-summary { display: none; }
}
@media (max-width: 900px) {
  .learning-page { margin: -1rem; }
  .learning-page.sidebar-visible { padding-right: 0; }
  .learning-header { position: sticky; top: 65px; z-index: 82; }
  .header-separator, .course-name, .outline-action { display: none; }
  .mobile-content-button { display: flex; }
  .mobile-content-button span { display: none; }
  .content-tabs { top: 133px; }
  .learning-stage, .learning-stage.sidebar-hidden { display: block; height: clamp(420px, calc(100vh - 185px), 620px); }
  .viewer-area { padding: 10px 10px 0; height: 100%; }
  .course-sidebar { position: fixed; top: 0; right: 0; bottom: 0; width: min(400px, 88vw); min-width: 0; max-height: none; transform: translateX(100%); z-index: 102; box-shadow: -12px 0 30px rgba(0,0,0,.2); }
  .course-sidebar.open { transform: translateX(0); }
  .mobile-overlay { display: block; position: fixed; inset: 0; background: rgba(7,20,16,.58); z-index: 101; }
  .sidebar-reopen { display: none; }
  .info-layout { grid-template-columns: 1fr; padding: 2rem 1.25rem 3rem; gap: 2rem; }
  .lesson-facts { border-left: 0; border-top: 1px solid var(--border-color); padding: 1.5rem 0 0; }
  .content-tabs { padding: 0 .75rem; }
}
@media (max-width: 800px) {
  .document-workspace { grid-template-columns: 1fr; }
  .document-library { position: absolute; inset: 0 auto 0 0; width: min(320px, 86%); transform: translateX(-100%); transition: transform .22s ease; box-shadow: 10px 0 28px rgba(0,0,0,.2); }
  .document-library.open { transform: translateX(0); }
  .document-library-close, .document-library-toggle { display: flex; }
  .document-library-overlay { display: block; position: absolute; inset: 0; background: rgba(5,18,14,.45); z-index: 2; }
}
@media (max-width: 600px) {
  .learning-header { min-height: 58px; padding: 0 .7rem; top: 64px; }
  .content-tabs { top: 122px; min-height: 48px; padding: 5px .6rem; }
  .content-tabs button { min-height: 36px; padding: 0 .7rem; font-size: .78rem; }
  .header-copy h1 { font-size: .85rem; }
  .learning-stage, .learning-stage.sidebar-hidden { height: min(440px, calc(100vh - 170px)); min-height: 340px; }
  .viewer-area { padding: 0; min-height: 0; }
  .viewer-shell .video-container, .viewer-shell .slide-viewer { min-height: 300px; }
  .viewer-shell .video-title-bar, .viewer-shell .video-footer-info, .viewer-shell .viewer-header { display: none; }
  .viewer-shell .slide-content { min-height: 300px; padding: .5rem; }
  .viewer-shell .slide-card { padding: .75rem; }
  .document-preview-body { padding: 6px; }
  .document-preview-header { min-height: 56px; }
  .document-library-toggle span { display: none; }
  .viewer-navigation { height: 60px; grid-template-columns: 70px 1fr 70px; }
  .viewer-navigation button span { display: none; }
  .procedure-viewer { padding: .7rem; }
  .procedure-document { padding: 1rem; }
  .content-tabs button { padding: 0 .75rem; font-size: .8rem; }
  .info-layout { padding: 1.75rem 1rem 2.5rem; }
  .lesson-overview h2, .section-title h2 { font-size: 1.25rem; }
  .rating-row { align-items: flex-start; flex-direction: column; gap: .4rem; }
}

/* Sidebar resources layout */
.learning-stage { height: clamp(540px, calc(100vh - 141px), 780px); }
.lesson-entry { position: relative; background: #fff; border-bottom: 1px solid #edf1ef; transition: background .2s ease; }
.lesson-entry.active { background: var(--primary-light); box-shadow: inset 4px 0 var(--primary-color); }
.lesson-entry .lesson-row { padding: .85rem 1rem .55rem; background: transparent; border-bottom: 0; }
.lesson-entry .lesson-row:hover { background: rgba(239,246,243,.8); }
.lesson-entry.completed .lesson-check { background: var(--primary-color); border-color: var(--primary-color); color: white; }
.lesson-resources-toggle { margin: 0 .75rem .65rem auto; padding: .35rem .5rem; display: flex; align-items: center; gap: .35rem; border: 1px solid rgba(0,91,65,.22); border-radius: 6px; background: #fff; color: var(--primary-color); font-size: .72rem; font-weight: 700; cursor: pointer; }
.lesson-resources-toggle:hover, .lesson-resources-toggle.expanded { background: #e7f2ee; border-color: rgba(0,91,65,.4); }
.lesson-resources-toggle > svg:last-child { transition: transform .2s ease; }
.lesson-resources-toggle.expanded > svg:last-child { transform: rotate(180deg); }
.resource-count { min-width: 17px; height: 17px; display: grid; place-items: center; border-radius: 50%; background: var(--primary-color); color: #fff; font-size: .62rem; }
.lesson-documents { margin: 0 .75rem .7rem 2.7rem; padding: .25rem 0 .25rem .7rem; display: flex; flex-direction: column; gap: .2rem; border-left: 2px solid #b8d4ca; }
.lesson-documents button { width: 100%; padding: .5rem .55rem; display: grid; grid-template-columns: 17px minmax(0,1fr); gap: .45rem; align-items: flex-start; border: 0; border-radius: 6px; background: transparent; color: #42534c; text-align: left; cursor: pointer; }
.lesson-documents button:hover, .lesson-documents button.selected { background: #fff; color: var(--primary-color); box-shadow: 0 1px 4px rgba(11,42,32,.09); }
.lesson-documents button > span { min-width: 0; display: flex; flex-direction: column; }
.lesson-documents strong { font-size: .75rem; line-height: 1.3; }
.lesson-documents small { margin-top: .15rem; color: var(--text-muted); font-size: .65rem; }

/* Document preview selected from the course sidebar */
.selected-document-preview { width: 100%; height: 100%; min-height: 0; display: flex; flex-direction: column; overflow: hidden; background: #0f172a; }
.selected-document-toolbar { min-height: 54px; padding: .55rem .9rem; display: flex; align-items: center; justify-content: space-between; gap: 1rem; background: #1d211f; color: #fff; border-bottom: 1px solid #343b38; }
.selected-document-toolbar > div { min-width: 0; display: flex; align-items: center; gap: .55rem; }
.selected-document-toolbar > div > svg { color: #e4c760; flex: 0 0 auto; }
.selected-document-toolbar span { min-width: 0; display: flex; flex-direction: column; }
.selected-document-toolbar strong { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; font-size: .85rem; }
.selected-document-toolbar small { color: #92a39d; font-size: .68rem; }
.selected-document-toolbar button { padding: .45rem .7rem; display: flex; align-items: center; gap: .4rem; border: 1px solid #52605b; border-radius: 6px; background: transparent; color: #dce7e3; cursor: pointer; white-space: nowrap; }
.selected-document-toolbar button:hover { color: #fff; border-color: #e4c760; }
.selected-document-actions { flex: 0 0 auto; display: flex; align-items: center; gap: .5rem; }
.selected-document-toolbar .download-document-button { background: var(--primary-color); border-color: var(--primary-color); color: #fff; }
.selected-document-toolbar .download-document-button:hover { background: #087657; border-color: #e4c760; }
.selected-document-preview > .slide-viewer { flex: 1; min-height: 0; height: auto; }
.selected-document-preview > iframe { flex: 1; width: 100%; min-height: 0; border: 0; background: #fff; }
.selected-document-preview > .word-preview,
.selected-document-preview > .powerpoint-preview { flex: 1; min-height: 0; }

@media (max-width: 900px) {
  .learning-stage, .learning-stage.sidebar-hidden { height: clamp(480px, calc(100vh - 133px), 680px); }
}
@media (max-width: 600px) {
  .learning-stage, .learning-stage.sidebar-hidden { height: min(500px, calc(100vh - 122px)); min-height: 360px; }
  .selected-document-toolbar button { font-size: 0; }
  .selected-document-toolbar button svg { width: 18px; height: 18px; }
}
</style>
