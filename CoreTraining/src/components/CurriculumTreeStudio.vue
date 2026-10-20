<template>
  <div class="curriculum-tree-studio">
    <!-- Header Controls -->
    <div class="tree-header">
      <div class="header-left">
        <h3 class="tree-title">
          <Layers :size="18" class="title-icon" />
          <span>Cây cấu trúc đào tạo (7 Cấp)</span>
        </h3>
        <span class="tree-badge">Interactive Mode</span>
      </div>
      <div class="header-actions">
        <button
          type="button"
          class="btn-studio btn-primary"
          @click="openAddModal('domain')"
          title="Thêm Lĩnh vực đào tạo mới"
        >
          <Plus :size="15" />
          <span>Thêm Lĩnh vực</span>
        </button>
        <button
          type="button"
          class="btn-studio btn-ghost"
          @click="expandAll = !expandAll; toggleAllNodes(expandAll)"
          :title="expandAll ? 'Thu gọn tất cả' : 'Mở rộng tất cả'"
        >
          <component :is="expandAll ? Minimize2 : Maximize2" :size="15" />
          <span>{{ expandAll ? "Thu gọn" : "Mở rộng" }}</span>
        </button>
      </div>
    </div>

    <!-- Search box -->
    <div class="tree-search-wrapper">
      <Search :size="15" class="search-icon" />
      <input
        v-model="searchQuery"
        type="text"
        placeholder="Tìm kiếm lĩnh vực, chương trình, khóa học hoặc bài giảng..."
        class="tree-search-input"
      />
      <button
        v-if="searchQuery"
        type="button"
        class="clear-search-btn"
        @click="searchQuery = ''"
      >
        <X :size="13" />
      </button>
    </div>

    <!-- Tree Body -->
    <div class="tree-scroll-container">
      <div v-if="loading" class="tree-empty-state">
        <Loader2 :size="24" class="spin-icon" />
        <p>Đang tải cấu trúc chương trình...</p>
      </div>

      <div v-else-if="!filteredDomains.length" class="tree-empty-state">
        <FolderTree :size="36" class="empty-icon" />
        <p v-if="searchQuery">Không tìm thấy mục nào khớp với "{{ searchQuery }}".</p>
        <p v-else>Chưa có cấu trúc đào tạo nào. Hãy bấm "Thêm Lĩnh vực" để bắt đầu.</p>
      </div>

      <div v-else class="tree-root">
        <!-- LEVEL 1: DOMAIN (LĨNH VỰC) -->
        <div
          v-for="domain in filteredDomains"
          :key="domain.id"
          class="tree-branch domain-branch"
        >
          <div
            class="tree-node domain-node"
            :class="{ active: selectedId === domain.id, expanded: isExpanded(domain.id) }"
            @click="selectNode('domain', domain)"
          >
            <button
              type="button"
              class="toggle-chevron"
              @click.stop="toggleNode(domain.id)"
            >
              <ChevronRight
                :size="15"
                :class="{ rotated: isExpanded(domain.id) }"
              />
            </button>

            <span class="node-icon domain-icon">
              <Globe :size="16" />
            </span>

            <div class="node-info">
              <span class="node-title">{{ domain.name }}</span>
              <span class="node-code">{{ domain.code }}</span>
            </div>

            <div class="node-badge-group">
              <span class="count-tag">{{ getProgramsForDomain(domain.id).length }} CT</span>
            </div>

            <!-- Hover Action Buttons (như hình mẫu) -->
            <div class="node-hover-actions" @click.stop>
              <button
                type="button"
                class="hover-action-btn add-btn"
                title="Thêm Chương trình đào tạo mới vào Lĩnh vực này"
                @click="openAddModal('program', domain)"
              >
                <Plus :size="14" />
                <span class="btn-tooltip">Thêm CT</span>
              </button>
              <button
                type="button"
                class="hover-action-btn menu-btn"
                title="Tùy chọn"
                @click="openMenu('domain', domain)"
              >
                <MoreHorizontal :size="14" />
              </button>
            </div>
          </div>

          <!-- LEVEL 2: PROGRAM (CHƯƠNG TRÌNH ĐÀO TẠO) -->
          <div v-if="isExpanded(domain.id)" class="tree-sub-tree">
            <div
              v-for="program in getProgramsForDomain(domain.id)"
              :key="program.id"
              class="tree-branch program-branch"
            >
              <div
                class="tree-node program-node"
                :class="{ active: selectedId === program.id, expanded: isExpanded(program.id) }"
                @click="selectNode('program', program)"
              >
                <button
                  type="button"
                  class="toggle-chevron"
                  @click.stop="toggleNode(program.id)"
                >
                  <ChevronRight
                    :size="14"
                    :class="{ rotated: isExpanded(program.id) }"
                  />
                </button>

                <span class="node-icon program-icon">
                  <Folder :size="15" />
                </span>

                <div class="node-info">
                  <span class="node-title">{{ program.name }}</span>
                  <span class="node-code">{{ program.code }}</span>
                </div>

                <div class="node-badge-group">
                  <span class="count-tag">{{ getCoursesForProgram(program.id).length }} Khóa</span>
                </div>

                <!-- Hover Actions -->
                <div class="node-hover-actions" @click.stop>
                  <button
                    type="button"
                    class="hover-action-btn add-btn"
                    title="Thêm Khóa học vào Chương trình này"
                    @click="openAddModal('course', program)"
                  >
                    <Plus :size="14" />
                    <span class="btn-tooltip">Thêm Khóa</span>
                  </button>
                  <button
                    type="button"
                    class="hover-action-btn menu-btn"
                    title="Tùy chọn"
                    @click="openMenu('program', program)"
                  >
                    <MoreHorizontal :size="14" />
                  </button>
                </div>
              </div>

              <!-- LEVEL 3: COURSE (KHÓA HỌC) -->
              <div v-if="isExpanded(program.id)" class="tree-sub-tree">
                <div
                  v-for="course in getCoursesForProgram(program.id)"
                  :key="course.id"
                  class="tree-branch course-branch"
                >
                  <div
                    class="tree-node course-node"
                    :class="{ active: selectedId === course.id, expanded: isExpanded(course.id) }"
                    @click="selectNode('course', course)"
                  >
                    <button
                      type="button"
                      class="toggle-chevron"
                      @click.stop="toggleNode(course.id)"
                    >
                      <ChevronRight
                        :size="13"
                        :class="{ rotated: isExpanded(course.id) }"
                      />
                    </button>

                    <span class="node-icon course-icon">
                      <BookOpen :size="14" />
                    </span>

                    <div class="node-info">
                      <span class="node-title">{{ course.name }}</span>
                      <span class="node-status-dot" :class="course.status?.toLowerCase()"></span>
                    </div>

                    <div class="node-badge-group">
                      <span class="status-pill" :class="course.status?.toLowerCase()">
                        {{ course.status === 'PUBLISHED' ? 'Đã xuất bản' : 'Bản nháp' }}
                      </span>
                    </div>

                    <!-- Hover Actions -->
                    <div class="node-hover-actions" @click.stop>
                      <button
                        type="button"
                        class="hover-action-btn add-btn"
                        title="Thêm Chương vào Khóa học này"
                        @click="openAddModal('chapter', course)"
                      >
                        <Plus :size="14" />
                        <span class="btn-tooltip">Thêm Chương</span>
                      </button>
                      <button
                        type="button"
                        class="hover-action-btn menu-btn"
                        title="Tùy chọn"
                        @click="openMenu('course', course)"
                      >
                        <MoreHorizontal :size="14" />
                      </button>
                    </div>
                  </div>

                  <!-- LEVEL 4: CHAPTER (CHƯƠNG HỌC) -->
                  <div v-if="isExpanded(course.id)" class="tree-sub-tree">
                    <div
                      v-for="(chapter, cIdx) in getChaptersForCourse(course.id)"
                      :key="chapter.id"
                      class="tree-branch chapter-branch"
                    >
                      <div
                        class="tree-node chapter-node"
                        :class="{ active: selectedId === chapter.id, expanded: isExpanded(chapter.id) }"
                        @click="selectNode('chapter', chapter, course)"
                      >
                        <button
                          type="button"
                          class="toggle-chevron"
                          @click.stop="toggleNode(chapter.id)"
                        >
                          <ChevronRight
                            :size="13"
                            :class="{ rotated: isExpanded(chapter.id) }"
                          />
                        </button>

                        <span class="node-icon chapter-icon">
                          <Bookmark :size="13" />
                        </span>

                        <div class="node-info">
                          <span class="node-index">Chương {{ cIdx + 1 }}:</span>
                          <span class="node-title">{{ chapter.title }}</span>
                        </div>

                        <!-- Hover Actions -->
                        <div class="node-hover-actions" @click.stop>
                          <button
                            type="button"
                            class="hover-action-btn add-btn"
                            title="Thêm Bài học vào Chương này"
                            @click="openAddModal('lesson', chapter, course)"
                          >
                            <Plus :size="14" />
                            <span class="btn-tooltip">Thêm Bài</span>
                          </button>
                          <button
                            type="button"
                            class="hover-action-btn menu-btn"
                            title="Tùy chọn"
                            @click="openMenu('chapter', chapter, course)"
                          >
                            <MoreHorizontal :size="14" />
                          </button>
                        </div>
                      </div>

                      <!-- LEVEL 5: LESSON (BÀI HỌC) -->
                      <div v-if="isExpanded(chapter.id)" class="tree-sub-tree">
                        <div
                          v-for="(lesson, lIdx) in chapter.lessons || []"
                          :key="lesson.id"
                          class="tree-branch lesson-branch"
                        >
                          <div
                            class="tree-node lesson-node"
                            :class="{ active: selectedId === lesson.id, expanded: isExpanded(lesson.id) }"
                            @click="selectNode('lesson', lesson, course, chapter)"
                          >
                            <button
                              type="button"
                              class="toggle-chevron"
                              @click.stop="toggleNode(lesson.id)"
                            >
                              <ChevronRight
                                :size="12"
                                :class="{ rotated: isExpanded(lesson.id) }"
                              />
                            </button>

                            <span class="node-icon lesson-icon">
                              <FileText :size="13" />
                            </span>

                            <div class="node-info">
                              <span class="node-index">{{ cIdx + 1 }}.{{ lIdx + 1 }}</span>
                              <span class="node-title">{{ lesson.title }}</span>
                            </div>

                            <!-- Hover Actions -->
                            <div class="node-hover-actions" @click.stop>
                              <button
                                type="button"
                                class="hover-action-btn add-btn"
                                title="Mở trang soạn thảo bài học"
                                @click="openLessonEditor(lesson.id)"
                              >
                                <Plus :size="14" />
                                <span class="btn-tooltip">+ Khối</span>
                              </button>
                              <button
                                type="button"
                                class="hover-action-btn open-editor-btn"
                                title="Mở trang soạn thảo bài học"
                                @click="openLessonEditor(lesson.id)"
                              >
                                <ExternalLink :size="13" />
                              </button>
                            </div>
                          </div>

                          <!-- LEVEL 6 & 7: CONTENT BLOCKS & QUIZ -->
                          <div v-if="isExpanded(lesson.id)" class="tree-sub-tree leaf-sub-tree">
                            <!-- Block 1: Video -->
                            <div
                              class="tree-node block-node video-block"
                              :class="{ active: !!lesson.videoUrl }"
                              @click="editLessonBlock(lesson, 'video')"
                            >
                              <Video :size="12" class="block-icon video-icon" />
                              <span class="block-title">
                                {{ lesson.videoUrl ? 'Video bài giảng' : '(Chưa có video - Click để thêm)' }}
                              </span>
                              <span v-if="lesson.videoUrl" class="block-check">✓</span>
                            </div>

                            <!-- Block 2: Documents -->
                            <div
                              class="tree-node block-node doc-block"
                              :class="{ active: !!(lesson.documents && lesson.documents.length) }"
                              @click="editLessonBlock(lesson, 'document')"
                            >
                              <FileUp :size="12" class="block-icon doc-icon" />
                              <span class="block-title">
                                {{ (lesson.documents && lesson.documents.length) ? `${lesson.documents.length} tài liệu đính kèm` : '(Chưa có tài liệu - Click để thêm)' }}
                              </span>
                              <span v-if="lesson.documents && lesson.documents.length" class="block-check">✓</span>
                            </div>

                            <!-- Block 3: Procedure Steps -->
                            <div
                              class="tree-node block-node procedure-block"
                              :class="{ active: !!(lesson.procedureSteps && lesson.procedureSteps.length) }"
                              @click="editLessonBlock(lesson, 'procedure')"
                            >
                              <ListOrdered :size="12" class="block-icon proc-icon" />
                              <span class="block-title">
                                {{ (lesson.procedureSteps && lesson.procedureSteps.length) ? `${lesson.procedureSteps.length} bước quy trình CBS` : '(Chưa có quy trình - Click để thêm)' }}
                              </span>
                              <span v-if="lesson.procedureSteps && lesson.procedureSteps.length" class="block-check">✓</span>
                            </div>

                            <!-- Block 4: Assessment / Quiz (Level 7) -->
                            <div
                              class="tree-node block-node quiz-block"
                              :class="{ active: !!lesson.hasQuiz }"
                              @click="editLessonBlock(lesson, 'quiz')"
                            >
                              <HelpCircle :size="12" class="block-icon quiz-icon" />
                              <span class="block-title">
                                {{ lesson.hasQuiz ? 'Bài kiểm tra đánh giá năng lực' : '(Chưa gắn Quiz - Click để thêm)' }}
                              </span>
                              <span v-if="lesson.hasQuiz" class="block-check">✓</span>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- INLINE ADD MODAL -->
    <div v-if="addModal.show" class="tree-modal-backdrop" @click.self="addModal.show = false">
      <div class="tree-quick-modal">
        <div class="modal-header">
          <h4>{{ modalTitle }}</h4>
          <button type="button" class="close-btn" @click="addModal.show = false">
            <X :size="16" />
          </button>
        </div>
        <form @submit.prevent="submitAddModal" class="modal-form">
          <div class="form-group">
            <label>Mã định danh (*):</label>
            <input
              v-model="addModal.code"
              type="text"
              placeholder="Ví dụ: GDV_01, CH_01..."
              required
              class="form-control"
              ref="codeInputRef"
            />
          </div>
          <div class="form-group">
            <label>Tên hiển thị (*):</label>
            <input
              v-model="addModal.name"
              type="text"
              placeholder="Nhập tên tiêu đề..."
              required
              class="form-control"
            />
          </div>
          <div class="modal-actions">
            <button type="button" class="btn btn-secondary" @click="addModal.show = false">
              Hủy
            </button>
            <button type="submit" class="btn btn-primary" :disabled="submitting">
              <Loader2 v-if="submitting" :size="14" class="spin-icon" />
              <span>Tạo mới</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from "vue";
import { useRouter } from "vue-router";
import {
  Layers,
  Plus,
  Minimize2,
  Maximize2,
  Search,
  X,
  ChevronRight,
  Globe,
  Folder,
  BookOpen,
  Bookmark,
  FileText,
  Video,
  FileUp,
  ListOrdered,
  HelpCircle,
  MoreHorizontal,
  ExternalLink,
  FolderTree,
  Loader2,
} from "lucide-vue-next";
import { trainingApi } from "../api/trainingApi";
import { adminApi } from "../api/adminApi";
import { showToast } from "../utils/toast";
import type { TrainingDomain, TrainingProgram, Course, Chapter, Lesson } from "../types";

const router = useRouter();
const emit = defineEmits<{
  (e: "select", item: { type: string; data: any; parent?: any }): void;
  (e: "refresh"): void;
}>();

// State
const loading = ref(false);
const submitting = ref(false);
const searchQuery = ref("");
const expandAll = ref(true);
const selectedId = ref<string>("");
const expandedIds = ref<Set<string>>(new Set());

// Data storage
const domains = ref<TrainingDomain[]>([]);
const programs = ref<TrainingProgram[]>([]);
const courses = ref<Course[]>([]);
const chaptersMap = ref<Record<string, Chapter[]>>({});

// Inline Add Modal State
const addModal = ref<{
  show: boolean;
  type: "domain" | "program" | "course" | "chapter" | "lesson";
  parentId?: string;
  parentObj?: any;
  code: string;
  name: string;
}>({
  show: false,
  type: "domain",
  code: "",
  name: "",
});
const codeInputRef = ref<HTMLInputElement | null>(null);

// Modal title helper
const modalTitle = computed(() => {
  switch (addModal.value.type) {
    case "domain":
      return "Thêm Lĩnh vực đào tạo mới";
    case "program":
      return `Thêm Chương trình vào [${addModal.value.parentObj?.name}]`;
    case "course":
      return `Thêm Khóa học vào [${addModal.value.parentObj?.name}]`;
    case "chapter":
      return `Thêm Chương vào [${addModal.value.parentObj?.name}]`;
    case "lesson":
      return `Thêm Bài học vào [${addModal.value.parentObj?.title || addModal.value.parentObj?.name}]`;
    default:
      return "Thêm mới";
  }
});

// Load full tree catalog
async function loadTreeData() {
  loading.value = true;
  try {
    const catalog = await trainingApi.catalog();
    domains.value = catalog.domains || [];
    programs.value = catalog.programs || [];

    // Load courses
    const allCourses = await trainingApi.courses();
    courses.value = allCourses || [];

    // Tự động mở rộng các domain & program
    domains.value.forEach((d) => expandedIds.value.add(d.id));
    programs.value.forEach((p) => expandedIds.value.add(p.id));

    // Load chapters cho từng khóa học
    for (const c of courses.value) {
      expandedIds.value.add(c.id);
      loadChaptersForCourse(c.id);
    }
  } catch (error) {
    console.error("Lỗi khi tải cấu trúc đào tạo:", error);
    showToast("Không thể tải cấu trúc cây đào tạo", "error");
  } finally {
    loading.value = false;
  }
}

async function loadChaptersForCourse(courseId: string) {
  try {
    const chapters = await trainingApi.chapters(courseId);
    chaptersMap.value[courseId] = chapters || [];
    chapters?.forEach((ch) => expandedIds.value.add(ch.id));
  } catch (err) {
    console.error(`Lỗi tải chương cho khóa ${courseId}:`, err);
  }
}

// Helpers for relationships
function getProgramsForDomain(domainId: string) {
  return programs.value.filter((p) => p.domainId === domainId);
}

function getCoursesForProgram(programId: string) {
  return courses.value.filter((c) => c.programId === programId);
}

function getChaptersForCourse(courseId: string) {
  return chaptersMap.value[courseId] || [];
}

// Expand / Collapse
function isExpanded(id: string) {
  return expandedIds.value.has(id);
}

function toggleNode(id: string) {
  if (expandedIds.value.has(id)) {
    expandedIds.value.delete(id);
  } else {
    expandedIds.value.add(id);
  }
}

function toggleAllNodes(expand: boolean) {
  if (!expand) {
    expandedIds.value.clear();
  } else {
    domains.value.forEach((d) => expandedIds.value.add(d.id));
    programs.value.forEach((p) => expandedIds.value.add(p.id));
    courses.value.forEach((c) => expandedIds.value.add(c.id));
    Object.values(chaptersMap.value)
      .flat()
      .forEach((ch) => expandedIds.value.add(ch.id));
  }
}

// Select node
function selectNode(type: string, data: any, parent?: any, grandparent?: any) {
  selectedId.value = data.id;
  emit("select", { type, data, parent: parent || grandparent });
}

// Open Inline Add Modal
function openAddModal(
  type: "domain" | "program" | "course" | "chapter" | "lesson",
  parentObj?: any,
  grandParentObj?: any
) {
  addModal.value = {
    show: true,
    type,
    parentId: parentObj?.id,
    parentObj: parentObj || grandParentObj,
    code: "",
    name: "",
  };
  nextTick(() => {
    codeInputRef.value?.focus();
  });
}

// Submit Create Node
async function submitAddModal() {
  const { type, parentId, code, name } = addModal.value;
  if (!code.trim() || !name.trim()) return;

  submitting.value = true;
  try {
    if (type === "domain") {
      await trainingApi.saveDomain({ code: code.trim(), name: name.trim() });
      showToast("Tạo lĩnh vực thành công!", "success");
    } else if (type === "program") {
      await trainingApi.saveProgram({
        domainId: parentId,
        code: code.trim(),
        name: name.trim(),
      });
      showToast("Tạo chương trình thành công!", "success");
    } else if (type === "course") {
      await trainingApi.saveCourse({
        programId: parentId,
        code: code.trim().toUpperCase(),
        name: name.trim(),
        iconName: "BookOpen",
      });
      showToast("Tạo khóa học thành công!", "success");
    } else if (type === "chapter") {
      await trainingApi.saveChapter(parentId!, {
        code: code.trim().toUpperCase(),
        title: name.trim(),
      });
      showToast("Tạo chương thành công!", "success");
    } else if (type === "lesson") {
      const courseId = addModal.value.parentObj?.courseId || addModal.value.parentObj?.course?.id || "";
      await adminApi.createLesson({
        id: code.trim().toUpperCase(),
        title: name.trim(),
        hint: name.trim(),
        chapterId: parentId!,
        courseId: courseId,
        videoUrl: "",
        videoSource: "URL",
        documents: [],
        procedureSteps: [],
      });
      showToast("Tạo bài học thành công!", "success");
    }

    addModal.value.show = false;
    await loadTreeData();
    emit("refresh");
  } catch (error: any) {
    const msg = error.response?.data?.message || "Lỗi khi tạo mục mới!";
    showToast(msg, "error");
  } finally {
    submitting.value = false;
  }
}

// Actions
function openMenu(type: string, item: any, parent?: any) {
  selectNode(type, item, parent);
}

function openLessonEditor(lessonId: string) {
  router.push(`/admin/lessons/${encodeURIComponent(lessonId)}/edit`);
}

function editLessonBlock(lesson: Lesson, blockType: string) {
  router.push({
    path: `/admin/lessons/${encodeURIComponent(lesson.id)}/edit`,
    query: { tab: blockType },
  });
}

// Search Filter
const filteredDomains = computed(() => {
  if (!searchQuery.value.trim()) return domains.value;
  const q = searchQuery.value.toLowerCase().trim();
  return domains.value.filter(
    (d) =>
      d.name.toLowerCase().includes(q) ||
      d.code.toLowerCase().includes(q) ||
      getProgramsForDomain(d.id).some(
        (p) =>
          p.name.toLowerCase().includes(q) ||
          p.code.toLowerCase().includes(q) ||
          getCoursesForProgram(p.id).some((c) => c.name.toLowerCase().includes(q))
      )
  );
});

onMounted(() => {
  loadTreeData();
});
</script>

<style scoped>
.curriculum-tree-studio {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-surface, #1e293b);
  border-radius: 8px;
  border: 1px solid var(--border-color, #334155);
  overflow: hidden;
  font-family: inherit;
}

/* Header */
.tree-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.85rem 1rem;
  border-bottom: 1px solid var(--border-color, #334155);
  background: rgba(15, 23, 42, 0.4);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.tree-title {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--text-primary, #f8fafc);
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.title-icon {
  color: var(--brand-color, #10b981);
}

.tree-badge {
  font-size: 0.7rem;
  padding: 0.15rem 0.45rem;
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
  border-radius: 4px;
  font-weight: 500;
}

.header-actions {
  display: flex;
  gap: 0.4rem;
}

.btn-studio {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.35rem 0.65rem;
  font-size: 0.8rem;
  font-weight: 500;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: none;
}

.btn-primary {
  background: var(--brand-color, #10b981);
  color: white;
}

.btn-primary:hover {
  background: #059669;
}

.btn-ghost {
  background: transparent;
  color: var(--text-secondary, #94a3b8);
  border: 1px solid var(--border-color, #334155);
}

.btn-ghost:hover {
  background: rgba(255, 255, 255, 0.05);
  color: var(--text-primary, #f8fafc);
}

/* Search */
.tree-search-wrapper {
  position: relative;
  padding: 0.5rem 0.85rem;
  border-bottom: 1px solid var(--border-color, #334155);
  display: flex;
  align-items: center;
}

.search-icon {
  position: absolute;
  left: 1.25rem;
  color: #64748b;
  pointer-events: none;
}

.tree-search-input {
  width: 100%;
  padding: 0.4rem 1.8rem 0.4rem 2rem;
  font-size: 0.82rem;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid var(--border-color, #334155);
  border-radius: 6px;
  color: var(--text-primary, #f8fafc);
  outline: none;
}

.tree-search-input:focus {
  border-color: var(--brand-color, #10b981);
}

.clear-search-btn {
  position: absolute;
  right: 1.25rem;
  background: transparent;
  border: none;
  color: #64748b;
  cursor: pointer;
}

/* Tree Scroll Container */
.tree-scroll-container {
  flex: 1;
  overflow-y: auto;
  padding: 0.5rem 0.4rem;
}

.tree-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3rem 1.5rem;
  color: #64748b;
  text-align: center;
  gap: 0.75rem;
  font-size: 0.85rem;
}

.spin-icon {
  animation: spin 1s linear infinite;
  color: var(--brand-color, #10b981);
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* Tree Node Styling */
.tree-root {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.tree-sub-tree {
  margin-left: 1.15rem;
  padding-left: 0.4rem;
  border-left: 1px dashed rgba(100, 116, 139, 0.25);
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.tree-node {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.35rem 0.55rem;
  border-radius: 6px;
  cursor: pointer;
  user-select: none;
  font-size: 0.83rem;
  transition: background 0.15s ease;
  color: var(--text-primary, #e2e8f0);
}

.tree-node:hover {
  background: rgba(255, 255, 255, 0.06);
}

.tree-node.active {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
}

.toggle-chevron {
  background: transparent;
  border: none;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #64748b;
  cursor: pointer;
  width: 16px;
  height: 16px;
}

.toggle-chevron .rotated {
  transform: rotate(90deg);
}

.node-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.domain-icon {
  color: #38bdf8;
}

.program-icon {
  color: #fbbf24;
}

.course-icon {
  color: #34d399;
}

.chapter-icon {
  color: #a78bfa;
}

.lesson-icon {
  color: #f472b6;
}

.node-info {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-title {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-code {
  font-size: 0.72rem;
  color: #64748b;
  background: rgba(100, 116, 139, 0.15);
  padding: 0.1rem 0.35rem;
  border-radius: 4px;
}

.node-index {
  font-size: 0.78rem;
  font-weight: 600;
  color: #94a3b8;
}

.count-tag {
  font-size: 0.7rem;
  color: #64748b;
}

.status-pill {
  font-size: 0.68rem;
  padding: 0.1rem 0.35rem;
  border-radius: 4px;
}

.status-pill.published {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
}

.status-pill.draft {
  background: rgba(245, 158, 11, 0.15);
  color: #f59e0b;
}

/* Hover Action Buttons */
.node-hover-actions {
  display: none;
  align-items: center;
  gap: 0.25rem;
  margin-left: auto;
}

.tree-node:hover .node-hover-actions {
  display: flex;
}

.hover-action-btn {
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid var(--border-color, #475569);
  color: #cbd5e1;
  padding: 0.2rem 0.35rem;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.72rem;
  gap: 0.2rem;
  transition: all 0.15s ease;
}

.hover-action-btn:hover {
  background: var(--brand-color, #10b981);
  color: white;
  border-color: #10b981;
}

/* Leaf Content Blocks (Level 6 & 7) */
.leaf-sub-tree {
  margin-top: 0.2rem;
  gap: 0.15rem;
}

.block-node {
  font-size: 0.78rem;
  padding: 0.25rem 0.5rem;
  color: #94a3b8;
}

.block-node:hover {
  background: rgba(255, 255, 255, 0.04);
}

.block-node.active {
  color: #e2e8f0;
}

.block-icon {
  flex-shrink: 0;
}

.video-icon {
  color: #ef4444;
}

.doc-icon {
  color: #3b82f6;
}

.proc-icon {
  color: #10b981;
}

.quiz-icon {
  color: #f59e0b;
}

.block-check {
  margin-left: auto;
  color: #10b981;
  font-weight: 700;
  font-size: 0.75rem;
}

/* Quick Modal */
.tree-modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.65);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  backdrop-filter: blur(2px);
}

.tree-quick-modal {
  width: 90%;
  max-width: 420px;
  background: #1e293b;
  border: 1px solid #334155;
  border-radius: 8px;
  padding: 1.25rem;
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.5);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.modal-header h4 {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: #f8fafc;
}

.modal-form {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.form-group label {
  font-size: 0.8rem;
  color: #94a3b8;
  font-weight: 500;
}

.form-control {
  padding: 0.5rem 0.75rem;
  font-size: 0.85rem;
  background: #0f172a;
  border: 1px solid #334155;
  border-radius: 6px;
  color: #f8fafc;
  outline: none;
}

.form-control:focus {
  border-color: #10b981;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 0.5rem;
}

.btn {
  padding: 0.45rem 0.85rem;
  font-size: 0.82rem;
  border-radius: 6px;
  border: none;
  cursor: pointer;
  font-weight: 500;
}

.btn-secondary {
  background: #334155;
  color: #cbd5e1;
}

.btn-secondary:hover {
  background: #475569;
}
</style>
