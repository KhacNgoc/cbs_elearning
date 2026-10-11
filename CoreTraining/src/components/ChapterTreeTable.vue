<template>
  <div class="chapter-tree">
    <p class="tree-help">Nhấn nút mũi tên ở đầu mỗi chương để hiển thị hoặc ẩn các bài học.</p>
    <div class="tree-scroll">
      <table class="tree-table">
        <caption class="sr-only">Danh sách chương và các bài học thuộc chương</caption>
        <thead><tr><th class="toggle-column"><span class="sr-only">Mở / thu gọn chương</span></th><th class="order-column">STT</th><th>Chương / Bài học</th><th>Bài học / Kiểm tra</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
        <tbody>
          <template v-for="chapter in chapters" :key="chapter.id">
            <tr class="chapter-row" :class="{ expanded: expandedIds.has(chapter.id) }">
              <td class="toggle-column">
                  <button type="button" class="chapter-toggle"
                    :aria-expanded="expandedIds.has(chapter.id)"
                    :aria-controls="chapter.lessons.length ? chapter.lessons.map(lesson => `chapter-lesson-${chapter.id}-${lesson.id}`).join(' ') : `chapter-empty-${chapter.id}`"
                    :aria-label="`${expandedIds.has(chapter.id) ? 'Ẩn' : 'Hiển thị'} bài học của chương ${chapter.title}`"
                    :title="expandedIds.has(chapter.id) ? 'Ẩn các bài học' : 'Hiển thị các bài học'"
                    @click="$emit('toggle', chapter.id)">
                    <ChevronRight :size="20" aria-hidden="true" />
                  </button>
              </td>
              <td class="order-column">{{ orderOf(chapter) + 1 }}</td>
              <td><button class="name-link chapter-name" :aria-expanded="expandedIds.has(chapter.id)" @click="$emit('openChapter', chapter)">{{ chapter.title }}</button><small>{{ chapter.code }}</small></td>
              <td><span class="lesson-count">{{ chapter.lessons.length }} bài học</span></td>
              <td><span class="status">{{ statusLabel(chapter.status) }}</span></td>
              <td>
                <div v-if="canEdit" class="row-actions">
                  <button class="row-edit" :disabled="busy" :aria-label="`Thêm bài học vào chương ${chapter.title}`" @click="$emit('addLesson', chapter)"><Plus :size="14" /> Thêm bài</button>
                  <button class="order-button" :disabled="busy || reorderDisabled || orderOf(chapter) === 0" @click="$emit('moveChapter', chapter, -1)" :aria-label="`Di chuyển chương ${chapter.title} lên`"><ArrowUp :size="14" /></button>
                  <button class="order-button" :disabled="busy || reorderDisabled || orderOf(chapter) === totalChapters - 1" @click="$emit('moveChapter', chapter, 1)" :aria-label="`Di chuyển chương ${chapter.title} xuống`"><ArrowDown :size="14" /></button>
                  <button class="row-edit" @click="$emit('editChapter', chapter)">Sửa</button>
                  <button class="row-delete" :disabled="busy" @click="$emit('deleteChapter', chapter)">Xóa</button>
                </div>
                <span v-else class="readonly-label">Chỉ xem</span>
              </td>
            </tr>
          <template v-if="expandedIds.has(chapter.id)">
            <tr v-for="lesson in displayedLessons(chapter)" :id="`chapter-lesson-${chapter.id}-${lesson.id}`" :key="lesson.id" class="tree-lesson-row">
              <td class="toggle-column"></td>
              <td class="order-column lesson-order"><span class="lesson-number">{{ orderOf(chapter) + 1 }}.{{ lessonIndex(chapter, lesson) + 1 }}</span></td>
              <td>
                <div class="lesson-branch"><FileText :size="17" aria-hidden="true" /><div>
                  <button class="name-link lesson-name" @click="$emit('openLesson', lesson)">{{ lesson.title }}</button>
                  <small>{{ lesson.id }}</small>
                </div></div>
              </td>
              <td><span class="quiz-label" :class="{ required: lesson.hasQuiz && lesson.quizRequired }">{{ !lesson.hasQuiz ? 'Chưa có kiểm tra' : lesson.quizRequired ? 'Kiểm tra bắt buộc' : 'Kiểm tra tùy chọn' }}</span></td>
              <td><span class="status">{{ statusLabel(lesson.status) }}</span></td>
              <td><div class="row-actions">
                <template v-if="canEdit">
                  <button class="order-button" :disabled="busy || reorderDisabled || lessonIndex(chapter, lesson) === 0" :aria-label="`Di chuyển bài học ${lesson.title} lên`" @click="$emit('moveLesson', chapter, lesson, -1)"><ArrowUp :size="14" /></button>
                  <button class="order-button" :disabled="busy || reorderDisabled || lessonIndex(chapter, lesson) === chapter.lessons.length - 1" :aria-label="`Di chuyển bài học ${lesson.title} xuống`" @click="$emit('moveLesson', chapter, lesson, 1)"><ArrowDown :size="14" /></button>
                </template>
                <button class="row-edit" :disabled="busy" @click="$emit('openLesson', lesson)">{{ canEdit ? 'Soạn bài' : 'Xem bài' }}</button>
                <button v-if="canEdit" class="row-delete" :disabled="busy" :aria-label="`Xóa bài học ${lesson.title}`" @click="$emit('deleteLesson', lesson)">Xóa</button>
              </div></td>
            </tr>
            <tr v-if="!chapter.lessons.length" :id="`chapter-empty-${chapter.id}`" class="tree-lesson-row"><td class="toggle-column"></td><td class="order-column"></td><td colspan="4" class="empty-chapter">Chương chưa có bài học. <button v-if="canEdit" class="name-link" :disabled="busy" @click="$emit('addLesson', chapter)">Thêm bài học đầu tiên</button></td></tr>
            <tr v-else-if="!displayedLessons(chapter).length" class="tree-lesson-row"><td class="toggle-column"></td><td class="order-column"></td><td colspan="4" class="empty-chapter">Không có bài học phù hợp với bộ lọc.</td></tr>
          </template>
          </template>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ArrowDown, ArrowUp, ChevronRight, FileText, Plus } from 'lucide-vue-next';
import type { Chapter, Lesson } from '../types';
const props = defineProps<{
  chapters: Chapter[];
  expandedIds: Set<string>;
  canEdit: boolean;
  busy: boolean;
  reorderDisabled: boolean;
  totalChapters: number;
  orderOf: (chapter: Chapter) => number;
  visibleLessons?: (chapter: Chapter) => Lesson[];
}>();
defineEmits<{
  toggle: [id: string];
  openChapter: [chapter: Chapter];
  openLesson: [lesson: Lesson];
  editChapter: [chapter: Chapter];
  deleteChapter: [chapter: Chapter];
  moveChapter: [chapter: Chapter, offset: number];
  addLesson: [chapter: Chapter];
  moveLesson: [chapter: Chapter, lesson: Lesson, offset: number];
  deleteLesson: [lesson: Lesson];
}>();
const orderedLessons = (chapter: Chapter) => [...chapter.lessons].sort((a, b) => (a.lessonOrder ?? 0) - (b.lessonOrder ?? 0) || a.id.localeCompare(b.id));
const displayedLessons = (chapter: Chapter) => props.visibleLessons ? props.visibleLessons(chapter) : orderedLessons(chapter);
const lessonIndex = (chapter: Chapter, lesson: Lesson) => orderedLessons(chapter).findIndex(item => item.id === lesson.id);
const statusLabel = (status?: string) => ({ DRAFT: 'Bản nháp', PUBLISHED: 'Đã xuất bản', ARCHIVED: 'Đã lưu trữ' }[status || 'DRAFT'] || status);
</script>

<style scoped>
.chapter-tree { display: flex; flex-direction: column; flex: 1; min-height: 0; min-width: 0; }
.tree-help { margin: 0 0 .75rem; color: var(--text-muted); font-size: .8rem; }
.tree-scroll { flex: 1; min-height: 0; overflow: auto; overscroll-behavior: contain; }
.tree-table { width: 100%; border-collapse: collapse; font-size: .875rem; }
.tree-table > tbody { display: table-row-group; }
th { position: sticky; top: 0; z-index: 1; padding: 1rem; background: var(--bg-main); color: var(--text-muted); font-size: .75rem; text-align: left; }
td { padding: 1rem; border-bottom: 1px solid var(--border-color); vertical-align: middle; overflow-wrap: anywhere; }
.toggle-column { width: 64px; padding-left: .75rem; padding-right: .75rem; }
.order-column { width: 72px; text-align: left; white-space: nowrap; }
.tree-table th:nth-child(3) { min-width: 240px; }
.chapter-toggle { display: grid; place-items: center; flex-shrink: 0; width: 40px; height: 40px; border: 1px solid #b8d5c8; border-radius: 9px; background: #edf6f1; color: var(--primary-color); cursor: pointer; }
.chapter-toggle:hover { background: #d7e9e3; border-color: var(--primary-color); }
.chapter-toggle svg { transition: transform .18s ease; }
.chapter-toggle[aria-expanded=true] svg { transform: rotate(90deg); }
.chapter-row.expanded { background: #f5faf7; }
.name-link { border: 0; background: none; cursor: pointer; text-align: left; color: var(--primary-color); font: inherit; font-weight: 700; }
.name-link:hover { text-decoration: underline; text-underline-offset: 3px; }
small { display: block; margin-top: .35rem; font-size: .75rem; color: var(--text-muted); }
.lesson-count { color: #52665e; font-size: .8rem; white-space: nowrap; }
.tree-lesson-row { display: table-row; background: #fafcfb; }
.tree-lesson-row:hover { background: #f0f6f3; }
.tree-lesson-row td { padding-top: .8rem; padding-bottom: .8rem; }
.lesson-order { color: var(--text-muted); font-size: .8rem; }
.lesson-number { display: inline-block; margin-left: 1.25rem; }
.lesson-branch { display: flex; align-items: center; gap: .75rem; padding-left: 3rem; }
.lesson-branch > svg { flex-shrink: 0; color: #789c8b; }
.lesson-branch > div { min-width: 0; }
.lesson-name { font-weight: 500; color: var(--text-main); }
.status { padding: .35rem .6rem; border-radius: 6px; font-size: .75rem; background: var(--primary-light); color: var(--primary-color); white-space: nowrap; }
.quiz-label { color: var(--text-muted); font-size: .75rem; }
.quiz-label.required { color: #805b00; }
.row-actions { display: flex; align-items: center; gap: .45rem; white-space: nowrap; }
.row-edit, .row-delete, .order-button { display: inline-flex; align-items: center; gap: .35rem; padding: .4rem .7rem; border: 1px solid var(--primary-color); border-radius: 6px; background: white; color: var(--primary-color); cursor: pointer; font-size: .8rem; white-space: nowrap; }
.order-button { display: grid; place-items: center; padding: .3rem; color: var(--text-muted); border-color: var(--border-color); }
.row-delete { border-color: #f0b7b7; color: #b91c1c; }
.row-edit:hover:not(:disabled), .order-button:hover:not(:disabled) { background: var(--primary-light); border-color: var(--primary-color); }
.row-delete:hover:not(:disabled) { background: #fff1f2; border-color: #b91c1c; }
button:disabled { opacity: .45; cursor: not-allowed; }
.readonly-label, .empty-chapter { color: var(--text-muted); font-size: .8rem; }
.empty-chapter .name-link { font-size: .8rem; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; }
@media(max-width:700px) { .tree-table { min-width: 680px; } td, th { padding: .75rem; } }
@media(prefers-reduced-motion:reduce) { .chapter-toggle svg { transition: none; } }
</style>
