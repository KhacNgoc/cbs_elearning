<template>
  <header class="learning-header">
    <router-link
      :to="backTo || '/'"
      class="back-button"
      aria-label="Quay lại trang chủ"
      ><ArrowLeft :size="20"
    /></router-link>
    <div class="header-copy">
      <span class="course-name">{{ courseName }}</span
      ><span class="header-separator"></span>
      <h1>{{ lessonTitle }}</h1>
    </div>
    <div class="header-actions">
      <div class="lesson-tools">
        <div
          v-if="hasText || hasVideo || hasSlides"
          class="lesson-content-switch"
          role="group"
          aria-label="Chọn nội dung bài học"
        >
          <button
            v-if="hasText"
            type="button"
            :aria-pressed="activeContent === 'TEXT'"
            @click="$emit('select-content', 'TEXT')"
          >
            <FileText :size="17" /><span>Văn bản</span>
            <small v-if="optionalContent?.includes('TEXT')" class="content-optional-tag">Tùy chọn</small>
          </button>
          <button
            v-if="hasVideo"
            type="button"
            :aria-pressed="activeContent === 'VIDEO'"
            @click="$emit('select-content', 'VIDEO')"
          >
            <PlayCircle :size="17" /><span>Video</span>
            <small v-if="optionalContent?.includes('VIDEO')" class="content-optional-tag">Tùy chọn</small>
          </button>
          <button v-if="hasSlides" type="button" :aria-pressed="activeContent === 'SLIDE'" @click="$emit('select-content', 'SLIDE')"><FileText :size="17" /><span>Slide</span><small v-if="optionalContent?.includes('SLIDE')" class="content-optional-tag">Tùy chọn</small></button>
        </div>
        <button
          v-if="flashcardsEnabled"
          type="button"
          class="flashcard-shortcut"
          :title="flashcardTotal ? `${flashcardTotal} flashcard` : 'Tạo flashcard của bạn'"
          @click="$emit('open-flashcards')"
        >
          <Layers3 :size="17" /><span>Flashcard</span>
          <strong v-if="flashcardTotal">{{ flashcardTotal }}</strong>
        </button>
      </div>
      <div
        class="header-completion"
        :class="{ visible: !sidebarOpen }"
        :style="{ width: sidebarOpen ? '0px' : `${completionWidth}px` }"
        :aria-hidden="sidebarOpen"
        :inert="sidebarOpen ? true : undefined"
      >
        <div ref="completionContent" class="header-completion-content">
          <div class="progress-summary">
            <Trophy :size="17" />
            <span>{{ completedCount }}/{{ totalLessons }} bài hoàn thành</span>
          </div>
          <button
            v-if="canComplete !== false"
            class="outline-action"
            :disabled="completed"
            @click="$emit('complete')"
          >
            <CheckCircle2 :size="17" />
            <span>{{ completed ? "Đã hoàn thành" : "Đánh dấu hoàn thành" }}</span>
          </button>
        </div>
      </div>
      <button
        class="mobile-content-button"
        aria-label="Mở nội dung chương"
        :aria-expanded="sidebarOpen"
        @click="$emit('open-content')"
      >
        <ListVideo :size="20" /><span>Nội dung</span>
      </button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";

// Measure the natural width so neither text nor buttons shrink during the animation.
const completionContent = ref<HTMLElement | null>(null);
const completionWidth = ref(0);
let completionObserver: ResizeObserver | undefined;
onMounted(() => {
  const content = completionContent.value;
  if (!content) return;
  const measure = () => {
    completionWidth.value = content.getBoundingClientRect().width;
  };
  measure();
  if (typeof ResizeObserver !== "undefined") {
    completionObserver = new ResizeObserver(measure);
    completionObserver.observe(content);
  }
});
onBeforeUnmount(() => completionObserver?.disconnect());

import {
  ArrowLeft,
  CheckCircle2,
  FileText,
  Layers3,
  ListVideo,
  PlayCircle,
  Trophy,
} from "lucide-vue-next";
defineProps<{
  sidebarOpen: boolean;
  activeContent: "VIDEO" | "TEXT" | "DOCUMENT" | "SLIDE";
  hasSlides: boolean;
  hasText: boolean;
  hasVideo: boolean;
  optionalContent?: ("TEXT" | "VIDEO" | "SLIDE")[];
  courseName?: string;
  backTo?: string;
  lessonTitle: string;
  completedCount: number;
  totalLessons: number;
  completed: boolean;
  canComplete?: boolean;
  flashcardTotal?: number;
  /** Shown even with no cards so learners can write their own. */
  flashcardsEnabled?: boolean;
}>();
defineEmits<{
  complete: [];
  "open-content": [];
  "open-flashcards": [];
  "select-content": [type: "VIDEO" | "TEXT" | "SLIDE"];
}>();
</script>
