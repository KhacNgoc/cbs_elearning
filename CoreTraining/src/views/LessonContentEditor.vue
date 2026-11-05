<template>
  <section class="card embedded-content">
    <form class="lesson-modal-form" @submit.prevent="saveLesson">
      <div ref="lessonModalBodyRef" class="lesson-modal-body lesson-authoring-fields">
                <div class="form-group">
          <label>Khóa học trực thuộc <span class="required-mark" aria-hidden="true">*</span></label>
          <select
            v-model="lessonForm.courseId"
            class="form-control"
            disabled
            required
          >
            <option
              v-for="t in courseStore.courses"
              :key="t.id"
              :value="t.id"
            >
              {{ t.name }}
            </option>
          </select>
        </div>

        <div class="form-group">
          <label>Tiêu đề bài học <span class="required-mark" aria-hidden="true">*</span></label>
          <input
            v-model="lessonForm.title"
            type="text"
            class="form-control"
            required
          />
        </div>

        <div class="form-group">
          <label>Gợi ý nghiệp vụ nhanh (Hint) <span class="required-mark" aria-hidden="true">*</span></label>
          <input
            v-model="lessonForm.hint"
            type="text"
            class="form-control"
            required
          />
        </div>

        <LessonContentSelection v-model="selectedContentTypes" v-model:documents="lessonForm.documents" :read-only="readOnly || loading" />
        <div v-if="selectedContentTypes.includes('VIDEO')" class="form-group video-source-group">
          <label id="video-source-label">Nguồn video bài học <span class="required-mark" aria-hidden="true">*</span></label>
          <div
            class="video-source-options"
            role="radiogroup"
            aria-labelledby="video-source-label"
          >
            <label
              class="video-source-option"
              :class="{ active: lessonForm.videoSource === 'UPLOAD' }"
            >
              <input
                v-model="lessonForm.videoSource"
                type="radio"
                value="UPLOAD"
                name="video-source"
              />
              <Upload :size="19" />
              <span>
                <strong>Tải video lên</strong>
                <small>Chọn file có sẵn trên máy</small>
              </span>
            </label>
            <label
              class="video-source-option"
              :class="{ active: lessonForm.videoSource === 'URL' }"
            >
              <input
                v-model="lessonForm.videoSource"
                type="radio"
                value="URL"
                name="video-source"
              />
              <Link2 :size="19" />
              <span>
                <strong>Dán đường link</strong>
                <small>Dùng link YouTube hoặc video trực tiếp</small>
              </span>
            </label>
          </div>

          <div
            v-if="lessonForm.videoSource === 'UPLOAD'"
            class="video-source-panel"
          >
            <div class="file-upload-control">
              <label v-if="!readOnly" class="file-picker">
                <Upload :size="18" />
                <span>{{
                  selectedVideoFileName || "Chọn file video từ máy"
                }}</span>
                <input
                  type="file"
                  accept="video/mp4,video/webm,.m4v"
                  :disabled="loading"
                  @change="handleVideoFileChange"
                />
              </label>
              <button
                v-if="!readOnly && selectedVideoFileName"
                type="button"
                class="file-clear-btn"
                title="Gỡ video"
                aria-label="Gỡ video đã chọn"
                @click="clearVideoFile"
              >
                <X :size="16" />
              </button>
            </div>
            <small class="video-source-help">
              Tối đa {{ Math.floor(VIDEO_UPLOAD_MAX_BYTES / 1024 / 1024) }} MiB.
              MP4/M4V: H.264/AAC; WebM: VP8/VP9, Opus/Vorbis.
            </small>
            <div v-if="videoUploadProgress !== null" role="status">
              <progress
                :value="videoUploadProgress"
                max="100"
                style="width: 100%"
              ></progress>
              <small>{{
                videoUploadProgress < 100
                  ? `Đang tải video: ${videoUploadProgress}%`
                  : "Đã gửi video, đang kiểm tra định dạng…"
              }}</small>
            </div>
            <small v-if="lessonForm.videoFile" class="selected-video-note">
              Đã chọn: {{ lessonForm.videoFile.name }}
            </small>
          </div>

          <div v-else class="video-source-panel">
            <input
              v-model="lessonForm.videoUrl"
              type="url"
              class="form-control"
              style="width: 100%"
              placeholder="Ví dụ: https://www.youtube.com/watch?v=..."
            />
            <small class="video-source-help">
              Dán link YouTube hoặc đường dẫn trực tiếp đến video.
            </small>
          </div>
        </div>

        <LessonTextEditor
          v-if="selectedContentTypes.includes('TEXT')"
          v-model="lessonForm.textBody"
          :read-only="readOnly || loading"
          @attach="addLessonDocument"
        />
        <div class="form-group supporting-section">
          <h2>Quy trình thực hiện</h2>
          <p class="section-description">
            Chia hướng dẫn thành từng quy trình. Mỗi dòng tương ứng với một bước
            thực hiện.
          </p>
          <div
            v-for="(section, index) in lessonForm.procedureSteps"
            :key="index"
            class="document-form-card"
          >
            <div class="document-form-fields procedure-form-fields">
              <input
                v-model="section.title"
                class="form-control"
                placeholder="Tên quy trình"
              />
              <textarea
                class="form-control"
                :value="section.steps.join('\n')"
                @input="
                  section.steps = (
                    $event.target as HTMLTextAreaElement
                  ).value.split('\n')
                "
                rows="4"
                placeholder="Mỗi dòng là một bước"
              />
            </div>
            <button
              v-if="!readOnly"
              type="button"
              class="action-btn delete document-remove"
              :disabled="loading"
              :aria-label="`Xóa quy trình ${index + 1}`"
              title="Xóa quy trình"
              @click="lessonForm.procedureSteps.splice(index, 1)"
            >
              <Trash2 :size="18" />
            </button>
          </div>
          <button
            v-if="!readOnly"
            type="button"
            class="btn btn-secondary"
            @click="lessonForm.procedureSteps.push({ title: '', steps: [] })"
          >
            <Plus :size="15" /> Thêm quy trình
          </button>
        </div>
        <!-- Lesson documents -->
        <div class="form-group supporting-section">
          <div
            class="card-header-row"
            style="display: flex; align-items: center; gap: 10px"
          >
            <div>
              <h2>Tài liệu đính kèm</h2>
              <p class="section-description">
                Bổ sung tài liệu PDF, Word hoặc PowerPoint để học viên xem và
                tải xuống.
              </p>
            </div>
            <button
              type="button"
              class="btn btn-secondary btn-sm"
              v-if="!readOnly"
              @click="addLessonDocument"
              style="padding: 0.5rem 1rem !important"
            >
              <Plus :size="15" /> Thêm tài liệu
            </button>
          </div>
          <div v-if="!lessonForm.documents.some(document => document.purpose !== 'SLIDE')" class="empty-state-box">
            Bài học chưa có tài liệu đính kèm.
          </div>
          <div
            v-for="{ document, index } in lessonForm.documents.map((document, index) => ({ document, index })).filter(({ document }) => document.purpose !== 'SLIDE')"
            :key="index"
            class="document-form-card"
          >
            <div class="document-form-index">
              <FileText :size="19" /><span>Tài liệu {{ index + 1 }}</span>
            </div>
            <div class="document-form-fields">
              <div class="form-group">
                <label>Tên hiển thị <span class="required-mark" aria-hidden="true">*</span></label>
                <input
                  v-model="document.name"
                  class="form-control"
                  placeholder="Ví dụ: Quy trình nghiệp vụ"
                  required
                />
              </div>
              <div class="form-group document-file-field">
                <label>Chọn file PDF, Word hoặc PowerPoint <span class="required-mark" aria-hidden="true">*</span></label>
                <div class="file-upload-control">
                  <label v-if="!readOnly" class="file-picker">
                    <Upload :size="18" />
                    <span>{{
                      document.file?.name ||
                      document.originalFileName ||
                      "Chọn file từ máy"
                    }}</span>
                    <input
                      type="file"
                      accept=".pdf,.doc,.docx,.ppt,.pptx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.ms-powerpoint,application/vnd.openxmlformats-officedocument.presentationml.presentation"
                      :required="!document.fileUrl"
                      @change="handleDocumentFileChange($event, index)"
                    />
                  </label>
                  <button
                    v-if="!readOnly && (document.file || document.fileUrl)"
                    type="button"
                    class="file-clear-btn"
                    title="Gỡ file"
                    :aria-label="`Gỡ file của tài liệu ${index + 1}`"
                    @click="clearDocumentFile(index, $event)"
                  >
                    <X :size="16" />
                  </button>
                </div>
                <small
                  v-if="document.file || document.fileSize"
                  class="selected-file-meta"
                >
                  {{ document.documentType }} ·
                  {{ formatFileSize(document.file?.size || document.fileSize) }}
                </small>
              </div>
            </div>
            <button
              type="button"
              class="action-btn delete document-remove"
              v-if="!readOnly"
              title="Xóa tài liệu"
              @click="removeLessonDocument(index)"
            >
              <Trash2 />
            </button>
          </div>
        </div>
      </div>
              <!-- KHỐI GHI NHẬN NỘI DUNG THAY ĐỔI & THÔNG BÁO HỌC VIÊN -->
        <div v-if="!readOnly && isPreviouslyComposed" class="change-notify-card">
          <div class="summary-label-row">
            <label>Tóm tắt nội dung thay đổi:</label>
            <span class="auto-diff-badge" title="Tự động ghi nhận các mục đã chỉnh sửa vào đây">
              <CheckCheck :size="12" />
              <span>Tự động ghi nhận thay đổi</span>
            </span>
          </div>
          <textarea
            v-model="changeSummary"
            @input="onUserManualEditSummary"
            rows="3"
            class="form-control notify-summary-input"
            placeholder="Mô tả nội dung cập nhật cho bài học..."
          ></textarea>

          <div class="change-notify-header" style="margin-top: 0.75rem;">
            <label class="notify-checkbox-label">
              <input type="checkbox" v-model="notifyLearners" :disabled="completedLearnersCount === 0" />
              <span class="notify-checkbox-title">Bắn thông báo cập nhật cho cán bộ đã học xong bài này</span>
            </label>
            <span v-if="completedLearnersCount > 0" class="badge-learners-warning">
              <ShieldAlert :size="13" />
              {{ completedLearnersCount }} cán bộ đã hoàn thành
            </span>
            <span v-else class="badge-learners-empty">
              (Chưa có cán bộ nào hoàn thành bài này)
            </span>
          </div>
          <small v-if="completedLearnersCount > 0 && notifyLearners" class="summary-hint">
            Thông báo sẽ gửi đến quả chuông và popup của các cán bộ đã hoàn thành bài học này.
          </small>
        </div>
        <footer v-if="!readOnly" class="modal-buttons lesson-modal-footer">
        <button type="button" class="btn btn-secondary" :disabled="loading" @click="emit('cancel')">
          Hủy
        </button>
        <button type="submit" class="btn btn-primary" :disabled="loading">
          {{ loading ? "Đang lưu..." : "Lưu nội dung" }}
        </button>
      </footer>
    </form>

    <!-- CHỈ HIỆN KHI TẢI TỆP LÊN (TÁC VỤ LÂU); LƯU THÔNG THƯỜNG CHỈ DÙNG TOAST -->
    <Teleport to="body">
      <div v-if="videoUploadProgress !== null || uploadingDocs" class="fullscreen-loading-overlay">
        <div class="loading-modal-card">
          <Loader2 :size="42" class="spin-icon text-primary loading-spinner" />
          <h4 v-if="videoUploadProgress !== null">
            Đang tải lên video ({{ videoUploadProgress }}%)...
          </h4>
          <h4 v-else>
            Đang tải lên và xử lý tài liệu đính kèm...
          </h4>

          <div v-if="videoUploadProgress !== null" class="upload-progress-track">
            <div
              class="upload-progress-bar"
              :style="{ width: videoUploadProgress + '%' }"
            ></div>
          </div>

          <p>
            Vui lòng chờ trong giây lát, hệ thống đang xử lý và chuyển thẳng sang màn hình xem trước.
          </p>
        </div>
      </div>
    </Teleport>
  </section>
</template>
<script setup lang="ts">
import '../assets/styles/lesson-authoring.css';
import LessonContentSelection from '../components/lesson/LessonContentSelection.vue';
import { lessonContentTypes, selectedLessonContent, validateSelectedLessonContent, type LessonContentType } from '../utils/lessonContent';
import { scrollWithin } from "../utils/scrollWithin";
import { ref, computed, nextTick, watch, defineAsyncComponent } from "vue";
import { Upload, Link2, Plus, FileText, X, Trash2, CheckCheck, ShieldAlert, Loader2 } from "lucide-vue-next";
import { axiosClient } from "../api/axiosClient";
import { onMounted } from "vue";
import { useCourseStore } from "../stores/courseStore";
import { adminApi, VIDEO_UPLOAD_MAX_BYTES } from "../api/adminApi";
import { trainingApi } from "../api/trainingApi";
import { validateVideoUpload } from "../utils/videoAccess";
import { showToast } from "../utils/toast";
import type { Lesson, LessonDocument } from "../types";
const LessonTextEditor = defineAsyncComponent(
  () => import("../components/lesson/LessonTextEditor.vue"),
);
const props = defineProps<{ lesson: Lesson; readOnly?: boolean }>();
const emit = defineEmits<{
  saved: [];
  cancel: [];
  dirty: [value: boolean];
  'title-change': [title: string];
}>();
const courseStore = useCourseStore();
const loading = ref(false),
  videoUploadProgress = ref<number | null>(null),
  uploadingDocs = ref(false);
const isPreviouslyComposed = computed(() => {
  const l = props.lesson;
  if (!l) return false;
  if (l.status === 'PUBLISHED') return true;
  if (l.hasText || (l.textBody && l.textBody.trim().length > 0)) return true;
  if (l.hasVideo || (l.videoUrl && l.videoUrl.trim().length > 0)) return true;
  if (Array.isArray(l.documents) && l.documents.length > 0) return true;
  if (Array.isArray((l as any).procedureSteps) && (l as any).procedureSteps.length > 0) return true;
  if (l.hasQuiz) return true;
  return false;
});
const lessonModal = ref({ show: true, isEdit: true });
type LessonDocumentForm = LessonDocument & { file?: File };
const lessonForm = ref({
  ...props.lesson,
  textBody: props.lesson.textBody || "",
  videoFile: undefined as File | undefined,
  videoSource: props.lesson.videoSource || "URL",
  videoUrl: props.lesson.videoUrl || "",
  documents: props.lesson.documents.map((d) => ({
    ...d,
  })) as LessonDocumentForm[],
  procedureSteps: props.lesson.procedureSteps.map((s) => ({
    ...s,
    steps: [...s.steps],
  })),
});
const selectedContentTypes = ref<LessonContentType[]>(lessonContentTypes(props.lesson));
watch(selectedContentTypes, () => { emit("dirty", true); autoUpdateChangeSummary(); }, { deep: true });
watch(lessonForm, () => { emit("dirty", true); autoUpdateChangeSummary(); }, { deep: true });
watch(() => lessonForm.value.title, (t) => emit("title-change", t));
watch(
  () => props.lesson,
  (newLesson) => {
    if (newLesson) {
      lessonForm.value.title = newLesson.title;
      lessonForm.value.hint = newLesson.hint;
      lessonForm.value.version = newLesson.version;
    }
  },
  { deep: true }
);
const lessonModalBodyRef = ref<HTMLElement | null>(null);

const selectedVideoFileName = computed(() => {
  if (lessonForm.value.videoFile) return lessonForm.value.videoFile.name;
  if (lessonForm.value.videoSource === "UPLOAD" && lessonForm.value.videoUrl) {
    return decodeURIComponent(
      lessonForm.value.videoUrl.split("/").pop() || "Video đã tải lên",
    );
  }
  return "";
});

const handleVideoFileChange = (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;

  const error = validateVideoUpload(file, VIDEO_UPLOAD_MAX_BYTES);
  if (error) {
    showToast(error, "error");
    input.value = "";
    return;
  }
  lessonForm.value.videoFile = file;
};

const clearVideoFile = (event: MouseEvent) => {
  lessonForm.value.videoFile = undefined;
  lessonForm.value.videoUrl = "";
  const control = (event.currentTarget as HTMLElement).closest(
    ".file-upload-control",
  );
  const input = control?.querySelector<HTMLInputElement>('input[type="file"]');
  if (input) input.value = "";
};

const addLessonDocument = async () => {
  lessonForm.value.documents.push({
    purpose: "SUPPORTING",
    name: "",
    documentType: "PDF",
    fileUrl: "",
    originalFileName: "",
  });

  await nextTick();
  const cards = lessonModalBodyRef.value?.querySelectorAll<HTMLElement>(
    ".document-form-card",
  );
  const newCard = cards?.[cards.length - 1];
  if (!newCard) return;

  scrollWithin(newCard, "nearest");
  window.setTimeout(() => {
    newCard.querySelector<HTMLInputElement>("input.form-control")?.focus({
      preventScroll: true,
    });
  }, 250);
};

const handleDocumentFileChange = (event: Event, index: number) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;

  const extension = file.name.split(".").pop()?.toLowerCase();
  if (!["pdf", "doc", "docx", "ppt", "pptx"].includes(extension || "")) {
    showToast("Chỉ hỗ trợ file PDF, DOC, DOCX, PPT hoặc PPTX.", "warning");
    input.value = "";
    return;
  }

  const document = lessonForm.value.documents[index];
  document.file = file;
  document.originalFileName = file.name;
  document.fileSize = file.size;
  document.documentType =
    extension === "pdf"
      ? "PDF"
      : ["ppt", "pptx"].includes(extension || "")
        ? "POWERPOINT"
        : "WORD";
  if (!document.name.trim()) document.name = file.name.replace(/\.[^.]+$/, "");
};

const formatFileSize = (size?: number) => {
  if (!size) return "";
  return size < 1024 * 1024
    ? `${Math.ceil(size / 1024)} KB`
    : `${(size / 1024 / 1024).toFixed(1)} MB`;
};

const clearDocumentFile = (index: number, event: MouseEvent) => {
  const document = lessonForm.value.documents[index];
  document.file = undefined;
  document.fileUrl = "";
  document.originalFileName = "";
  document.fileSize = undefined;
  const control = (event.currentTarget as HTMLElement).closest(
    ".file-upload-control",
  );
  const input = control?.querySelector<HTMLInputElement>('input[type="file"]');
  if (input) input.value = "";
};

const removeLessonDocument = (index: number) => {
  lessonForm.value.documents.splice(index, 1);
};

const notifyLearners = ref(false);
const changeSummary = ref("");
const completedLearnersCount = ref(0);
const isSummaryManuallyEdited = ref(false);

function computeDetectedChanges(): string {
  const diffs: string[] = [];
  const original = props.lesson;
  if (!original) return "- Cập nhật nội dung bài học.";

  // 1. Tiêu đề bài học
  const origTitle = (original.title || "").trim();
  const currentTitle = (lessonForm.value.title || "").trim();
  if (currentTitle && origTitle && currentTitle !== origTitle) {
    diffs.push(`- Đổi tiêu đề: "${currentTitle}"`);
  }

  // 2. Gợi ý / Mô tả tóm tắt (Hint)
  const origHint = (original.hint || "").trim();
  const currentHint = (lessonForm.value.hint || "").trim();
  if (currentHint !== origHint) {
    diffs.push("- Cập nhật gợi ý nghiệp vụ bài học");
  }

  // 3. Định dạng nội dung bài học (Video, Soạn thảo văn bản, Slide)
  const origTypes = lessonContentTypes(original).sort();
  const currentTypes = [...selectedContentTypes.value].sort();
  if (JSON.stringify(origTypes) !== JSON.stringify(currentTypes)) {
    const typeLabels: Record<string, string> = {
      VIDEO: "Video",
      TEXT: "Bài viết soạn thảo",
      SLIDE: "Slide bài giảng",
    };
    const activeNames = currentTypes.map((t) => typeLabels[t] || t).join(", ");
    diffs.push(`- Điều chỉnh định dạng bài học (${activeNames || "Chưa chọn"})`);
  }

  // 4. Video bài giảng
  if (selectedContentTypes.value.includes("VIDEO")) {
    if (lessonForm.value.videoFile) {
      diffs.push(`- Tải lên video bài giảng mới: ${lessonForm.value.videoFile.name}`);
    } else {
      const origVid = (original.videoUrl || "").trim();
      const currentVid = (lessonForm.value.videoUrl || "").trim();
      if (origVid !== currentVid) {
        if (!currentVid && origVid) {
          diffs.push("- Xóa video bài giảng");
        } else if (currentVid && !origVid) {
          diffs.push("- Bổ sung video bài giảng mới");
        } else if (currentVid) {
          diffs.push("- Cập nhật liên kết video bài giảng");
        }
      }
    }
  }

  // 5. Tài liệu đính kèm
  const origDocs = original.documents || [];
  const currentDocs = lessonForm.value.documents || [];
  const newUploaded = currentDocs.filter((d) => !d.id || !!d.file);

  if (newUploaded.length > 0) {
    const names = newUploaded.map((d) => d.file?.name || d.name).filter(Boolean);
    diffs.push(`- Tải lên ${newUploaded.length} tài liệu đính kèm mới (${names.slice(0, 2).join(", ")}${names.length > 2 ? "..." : ""})`);
  } else if (currentDocs.length !== origDocs.length) {
    if (currentDocs.length > origDocs.length) {
      diffs.push(`- Bổ sung tài liệu đính kèm (tổng ${currentDocs.length} tài liệu)`);
    } else {
      diffs.push(`- Giảm bớt tài liệu đính kèm (còn ${currentDocs.length} tài liệu)`);
    }
  } else {
    const nameChanged = currentDocs.some((d, idx) => (d.name || "").trim() !== (origDocs[idx]?.name || "").trim());
    if (nameChanged) {
      diffs.push("- Cập nhật thông tin tài liệu đính kèm");
    }
  }

  // 6. Soạn thảo văn bản (TextBody / Rich Text)
  if (selectedContentTypes.value.includes("TEXT")) {
    const origText = (original.textBody || "").trim();
    const currentText = (lessonForm.value.textBody || "").trim();
    if (origText !== currentText) {
      diffs.push("- Chỉnh sửa nội dung bài viết / bài giảng");
    }
  }

  // 7. Quy trình nghiệp vụ
  const origProc = JSON.stringify(original.procedureSteps || []);
  const currentProc = JSON.stringify(lessonForm.value.procedureSteps || []);
  if (origProc !== currentProc) {
    const stepCount = lessonForm.value.procedureSteps.reduce((acc, p) => acc + (p.steps?.length || 0), 0);
    diffs.push(`- Cập nhật các bước quy trình tác nghiệp (${stepCount} bước)`);
  }

  if (diffs.length === 0) {
    return "- Cập nhật nội dung bài học.";
  }

  return diffs.join("\n");
}

function hasAnyRealChanges(): boolean {
  const original = props.lesson;
  if (!original) return false;
  if ((lessonForm.value.title || "").trim() !== (original.title || "").trim()) return true;
  if ((lessonForm.value.hint || "").trim() !== (original.hint || "").trim()) return true;
  const origTypes = lessonContentTypes(original).sort();
  const currentTypes = [...selectedContentTypes.value].sort();
  if (JSON.stringify(origTypes) !== JSON.stringify(currentTypes)) return true;
  if (lessonForm.value.videoFile) return true;
  if ((lessonForm.value.videoUrl || "").trim() !== (original.videoUrl || "").trim()) return true;
  const origDocs = original.documents || [];
  const currentDocs = lessonForm.value.documents || [];
  if (currentDocs.some((d) => !d.id || !!d.file)) return true;
  if (currentDocs.length !== origDocs.length) return true;
  if (currentDocs.some((d, idx) => (d.name || "").trim() !== (origDocs[idx]?.name || "").trim())) return true;
  if ((lessonForm.value.textBody || "").trim() !== (original.textBody || "").trim()) return true;
  if (JSON.stringify(original.procedureSteps || []) !== JSON.stringify(lessonForm.value.procedureSteps || [])) return true;
  return false;
}

function autoUpdateChangeSummary() {
  if (isSummaryManuallyEdited.value) return;
  if (hasAnyRealChanges()) {
    changeSummary.value = computeDetectedChanges();
  }
}

function onUserManualEditSummary() {
  if (!changeSummary.value.trim()) {
    isSummaryManuallyEdited.value = false;
    autoUpdateChangeSummary();
  } else {
    isSummaryManuallyEdited.value = true;
  }
}

async function loadCompletedLearnersCount() {
  if (!props.lesson?.id) return;
  try {
    const res = await axiosClient.get(`/api/admin/lessons/${props.lesson.id}/completed-learners-count`);
    completedLearnersCount.value = res.data?.count || 0;
    if (completedLearnersCount.value > 0) {
      notifyLearners.value = true;
      autoUpdateChangeSummary();
    }
  } catch (e) {
    console.warn("Could not load completed learners count:", e);
  }
}

onMounted(() => {
  void loadCompletedLearnersCount();
});

const saveLesson = async () => {
  if (loading.value || props.readOnly) return;
  loading.value = true;
  videoUploadProgress.value = null;
  try {
    validateSelectedLessonContent(lessonForm.value, selectedContentTypes.value);
    const activeContent = selectedLessonContent(lessonForm.value, selectedContentTypes.value);
    const videoUrl = !selectedContentTypes.value.includes("VIDEO") ? "" :
      lessonForm.value.videoSource === "UPLOAD" && lessonForm.value.videoFile
        ? await adminApi.uploadVideo(lessonForm.value.videoFile, (percent) => {
            videoUploadProgress.value = percent;
          })
        : lessonForm.value.videoUrl.trim();
    videoUploadProgress.value = null;
    uploadingDocs.value = activeContent.documents.some((d) => !!d.file);
    const documents = await Promise.all(
      activeContent.documents.map(async (document, index) => {
        const uploaded = document.file
          ? await adminApi.uploadDocument(document.file)
          : document;
        return {
          id: document.file ? undefined : document.id,
          purpose: document.purpose || "SUPPORTING",
          name: document.name || uploaded.name,
          documentType: uploaded.documentType,
          fileUrl: uploaded.fileUrl,
          originalFileName: uploaded.originalFileName,
          fileSize: uploaded.fileSize,
          documentOrder: index,
        } satisfies LessonDocument;
      }),
    );
    uploadingDocs.value = false;

    const payload = {
      ...activeContent,
      videoUrl,
      documents,
      procedureSteps: lessonForm.value.procedureSteps,
    };
    delete (payload as Partial<typeof lessonForm.value>).videoFile;

    let saved: Lesson;
    try {
      saved = lessonModal.value.isEdit
        ? await adminApi.updateLesson(lessonForm.value.id, payload)
        : await adminApi.createLesson(payload);
    } catch (saveErr: any) {
      if (saveErr.response?.status === 409) {
        // Tự động đồng bộ version mới nhất và lưu lại để không gián đoạn người dùng
        const fresh = await trainingApi.lesson(lessonForm.value.id);
        payload.version = fresh.version;
        saved = await adminApi.updateLesson(lessonForm.value.id, payload);
      } else {
        throw saveErr;
      }
    }
    lessonForm.value = {
      ...lessonForm.value,
      ...saved,
      textBody: saved.textBody || "",
      videoSource: saved.videoSource || "URL",
      videoUrl: saved.videoUrl || "",
      videoFile: undefined,
      documents: saved.documents.map((d) => ({ ...d })),
      procedureSteps: saved.procedureSteps.map((s) => ({
        ...s,
        steps: [...s.steps],
      })),
    };
    await nextTick();
    let notifiedCount = 0;
        if (lessonModal.value.isEdit && isPreviouslyComposed.value && (notifyLearners.value || changeSummary.value.trim() || hasAnyRealChanges())) {
      try {
        const notifyRes = await axiosClient.post(`/api/admin/lessons/${lessonForm.value.id}/changelog-notify`, {
          changeSummary: changeSummary.value.trim() || computeDetectedChanges(),
          notifyLearners: notifyLearners.value,
        });
        if (notifyLearners.value) notifiedCount = notifyRes.data?.notifiedCount || 0;
      } catch (notifyErr) {
        console.warn("Could not send lesson change notification:", notifyErr);
      }
    }
    emit("saved");
    emit("dirty", false);
    const savedMessage = lessonModal.value.isEdit
      ? "Cập nhật bài học thành công."
      : "Tạo bài học thành công.";
    showToast(
      notifiedCount > 0
        ? `${savedMessage} Đã gửi thông báo cập nhật kiến thức tới ${notifiedCount} cán bộ đã hoàn thành bài.`
        : savedMessage,
    );
    await courseStore.fetchCourses(true);
  } catch (err) {
    const serverMessage = (
      err as { response?: { data?: { message?: string } } }
    ).response?.data?.message;
    showToast(
      serverMessage ||
        (err instanceof Error
          ? err.message
          : "Không thể lưu bài học. Vui lòng kiểm tra dữ liệu và thử lại."),
      "error",
    );
    console.error(err);
  } finally {
    videoUploadProgress.value = null;
    uploadingDocs.value = false;
    loading.value = false;
  }
};
</script>
<style scoped>
/* Redesigned Sidebar Layout */
.admin-portal-layout {
  display: flex;
  min-height: 100%;
  background-color: var(--surface-ffffff);
  font-family: var(--font-family);
}

/* Sidebar styling */
.admin-sidebar {
  display: none;
  width: 280px;
  background-color: var(--primary-color); /* Emerald Green */
  color: white;
  flex-direction: column;
  border-right: 1px solid var(--border-color);
  flex-shrink: 0;
  box-shadow: 2px 0 10px rgba(0, 0, 0, 0.05);
}

.sidebar-menu {
  padding: 2rem 1rem 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  flex: 1;
}

.sidebar-item {
  background: none;
  border: none;
  text-align: left;
  color: rgba(255, 255, 255, 0.75);
  font-size: 0.95rem;
  font-weight: 600;
  padding: 0.85rem 1.25rem;
  border-radius: var(--radius-md);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  transition: var(--transition);
}

.sidebar-item:hover {
  color: white;
  background-color: rgba(255, 255, 255, 0.08);
}

.sidebar-item.active {
  color: var(--primary-ink);
  background-color: var(--surface-ffffff); /* White tab bg for clean contrast */
  box-shadow: var(--shadow-sm);
}

.sidebar-icon {
  width: 18px;
  height: 18px;
}

.sidebar-footer {
  padding: 1.5rem;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.admin-role-badge {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background-color: rgba(255, 255, 255, 0.1);
  color: white;
  padding: 0.6rem 1rem;
  border-radius: var(--radius-full);
  font-size: 0.8rem;
  font-weight: 700;
}

.admin-badge-icon {
  width: 14px;
  height: 14px;
}

.spinning {
  animation: spin 8s linear infinite;
}

@keyframes spin {
  100% {
    transform: rotate(360deg);
  }
}

/* Main Content area */
.admin-main-content {
  flex: 1;
  padding: 2.2rem;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 1.8rem;
}

.admin-top-header {
  padding: 1.5rem 2rem;
  border-left: 5px solid var(--primary-color);
  background: var(--surface-ffffff);
  box-shadow: var(--shadow-sm);
}

.banner-tag {
  font-size: 0.7rem;
  font-weight: 700;
  color: var(--accent-secondary);
  letter-spacing: 1.2px;
}

.admin-top-header h1 {
  font-size: 1.6rem;
  color: var(--primary-ink);
  font-weight: 700;
  margin-top: 0.1rem;
}

.header-desc {
  font-size: 0.85rem;
  color: var(--text-muted);
}

/* Stats Cards Grid */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1.25rem;
  margin-bottom: 1.5rem;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 1.25rem;
  padding: 1.5rem;
  background: var(--surface-ffffff);
}

.stat-icon-box {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  position: relative;
}

.stat-icon {
  width: 22px;
  height: 22px;
}

.users-bg {
  background-color: #3b82f6;
}
.online-bg {
  background-color: #10b981;
}
.lessons-bg {
  background-color: var(--primary-color);
}
.questions-bg {
  background-color: var(--surface-8b5cf6);
}

.online-indicator-ping {
  position: absolute;
  top: -3px;
  right: -3px;
  width: 10px;
  height: 10px;
  background-color: var(--surface-ef4444);
  border: 2px solid var(--line-ffffff);
  border-radius: 50%;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0% {
    transform: scale(0.9);
    opacity: 1;
  }
  50% {
    transform: scale(1.3);
    opacity: 0.5;
  }
  100% {
    transform: scale(0.9);
    opacity: 1;
  }
}

.stat-details h3 {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--text-main);
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.active-badge-lbl {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--ink-10b981);
  background-color: var(--surface-dcfce7);
  padding: 0.1rem 0.4rem;
  border-radius: var(--radius-sm);
}

.stat-label {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

/* Charts Rows styling */
.charts-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
  gap: 1.5rem;
  margin-bottom: 1.5rem;
}

.charts-row.split-grid {
  grid-template-columns: 3fr 2fr;
}

@media (max-width: 1024px) {
  .charts-row.split-grid {
    grid-template-columns: 1fr;
  }
}

.chart-card {
  padding: 1.5rem;
  background: var(--surface-ffffff);
}

.chart-container {
  position: relative;
  height: 250px;
  width: 100%;
  margin-top: 1rem;
}

/* Real-time Sessions list Card */
.sessions-card {
  padding: 1.5rem;
  background: var(--surface-ffffff);
  display: flex;
  flex-direction: column;
}

.sessions-list {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  margin-top: 1.25rem;
  max-height: 250px;
  overflow-y: auto;
  padding-right: 0.25rem;
}

.session-row {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  padding: 0.75rem;
  background-color: var(--bg-main);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-color);
  transition: var(--transition);
}

.session-row:hover {
  transform: translateX(3px);
  border-color: var(--primary-light);
}

.session-user-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  color: white;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.85rem;
  box-shadow: var(--shadow-sm);
}

.session-info {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.session-name {
  font-size: 0.85rem;
  color: var(--text-main);
}

.session-action {
  color: var(--text-muted);
  font-size: 0.8rem;
  margin-left: 0.35rem;
}

.session-detail {
  font-size: 0.8rem;
  color: var(--primary-ink);
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.2rem;
  font-size: 0.75rem;
}

.session-course-tag {
  background-color: var(--primary-light);
  color: var(--primary-ink);
  font-weight: 700;
  padding: 0.1rem 0.4rem;
  border-radius: var(--radius-sm);
  font-size: 0.7rem;
}

.session-time {
  color: var(--text-muted);
}

/* Tables & CRUD styling */
.card-header-row {
  width: 100%;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 2rem;
  margin-bottom: 1.25rem;
}

.card-header-row .header-left {
  flex: 1;
  min-width: 0;
}

.card-header-row .card-title {
  margin: 0;
  line-height: 1.35;
}

.card-header-row .card-subtitle {
  margin: 0.4rem 0 0;
  line-height: 1.5;
  color: var(--text-muted);
}

.card-header-row > .btn {
  flex: 0 0 auto;
  margin-left: auto;
}

.course-selector-row {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  margin-bottom: 1rem;
}

.course-selector-row label {
  flex: 0 0 auto;
  font-weight: 500;
}

.course-selector-row .select-control {
  width: 170px;
}

.courses-summary,
.users-manager,
.lessons-manager,
.exams-manager {
  background: var(--surface-ffffff);
  padding: 1.8rem;
  box-shadow: var(--shadow-sm);
}

.admin-table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 1rem;
}

.admin-table th {
  padding: 1rem;
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--text-muted);
  border-bottom: 2px solid var(--border-color);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.admin-table td {
  padding: 1rem;
  border-bottom: 1px solid var(--border-color);
  font-size: 0.85rem;
}

.admin-table tr:hover td {
  background-color: var(--bg-main);
}

.code-badge {
  font-family: var(--font-family);
  background-color: var(--bg-main);
  color: var(--primary-ink);
  padding: 0.25rem 0.5rem;
  border-radius: var(--radius-sm);
  font-weight: 700;
  font-size: 0.8rem;
  border: 1px solid var(--border-color);
}

.actions-col {
  text-align: right;
  width: 110px;
}

.action-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 0.35rem;
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  transition: var(--transition);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.action-btn:hover {
  background-color: var(--bg-main);
  color: var(--primary-ink);
}

.action-btn.delete:hover {
  background-color: var(--surface-fee2e2);
  color: var(--ink-ef4444);
}

.action-btn svg {
  width: 16px;
  height: 16px;
}

/* User avatar helper */
.user-cell {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.user-avatar-circle {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: white;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.role-tag {
  font-size: 0.75rem;
  font-weight: 700;
  padding: 0.2rem 0.5rem;
  border-radius: var(--radius-full);
}

.role-tag.admin {
  background-color: var(--surface-fee2e2);
  color: var(--ink-ef4444);
}

.role-tag.user {
  background-color: var(--surface-e0f2fe);
  color: var(--ink-0369a1);
}

/* Modal details */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(15, 23, 42, 0.4);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 1.25rem;
  overflow-y: auto;
}

.modal-content {
  width: 100%;
  max-width: 480px;
  max-height: calc(100vh - 2.5rem);
  overflow-y: auto;
  padding: 2rem;
  background: var(--surface-ffffff);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
}

.modal-large {
  max-width: 650px;
}

.lesson-modal-content {
  width: min(960px, 100%);
  max-width: 960px;
  max-height: calc(100dvh - 2.5rem);
  padding: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.lesson-modal-header {
  flex: 0 0 auto;
  padding: 1.35rem 1.75rem 0;
  background: var(--surface-ffffff);
}

.lesson-modal-header h3 {
  margin-bottom: 0;
  padding-bottom: 0.75rem;
}

.lesson-modal-form {
  min-height: 0;
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
}

.lesson-modal-body {
  min-height: 0;
  flex: 1 1 auto;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
  padding: 1.25rem 1.75rem 0.5rem;
}

.lesson-modal-footer {
  flex: 0 0 auto;
  margin-top: 0;
  padding: 1rem 1.75rem;
  border-top: 1px solid var(--border-color);
  background: var(--surface-ffffff);
  box-shadow: 0 -8px 20px rgba(15, 35, 29, 0.05);
}

.modal-content h3 {
  font-size: 1.15rem;
  color: var(--primary-ink);
  margin-bottom: 1.25rem;
  border-bottom: 2px solid var(--primary-light);
  padding-bottom: 0.5rem;
  font-weight: 800;
}

.form-group {
  margin-bottom: 1.15rem;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.form-group label {
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
}

.form-control {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  padding: 0.6rem 0.8rem;
  font-size: 0.85rem;
  color: var(--text-main);
  background-color: var(--surface-ffffff);
  transition: var(--transition);
}

.form-control:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px var(--primary-light);
}

.modal-buttons {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 1.5rem;
}

.document-form-card {
  position: relative;
  margin-top: 0.75rem;
  padding: 0.9rem 3.25rem 0.9rem 0.9rem;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-main);
}

.document-form-index {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  margin-bottom: 0.7rem;
  color: var(--primary-ink);
  font-size: 0.8rem;
  font-weight: 700;
}

.document-form-fields {
  display: grid;
  grid-template-columns: minmax(180px, 0.8fr) minmax(260px, 1.2fr);
  gap: 0.8rem;
}

.document-form-fields .form-group {
  margin-bottom: 0;
}

.document-form-fields label {
  display: block;
  margin-bottom: 0.35rem;
  font-size: 0.72rem;
}

.file-picker {
  flex: 1;
  min-width: 0;
  min-height: 42px;
  padding: 0.55rem 0.75rem;
  display: flex !important;
  align-items: center;
  gap: 0.55rem;
  margin: 0 !important;
  border: 1px dashed var(--line-9ab9ae);
  border-radius: var(--radius-sm);
  background: var(--surface-ffffff);
  color: var(--primary-ink);
  cursor: pointer;
}

.exam-header-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.55rem;
}

.import-questions-btn {
  margin: 0 !important;
  cursor: pointer;
}

.import-questions-btn input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

.q-scope-tag {
  display: inline-flex;
  margin-left: 0.4rem;
  padding: 0.2rem 0.45rem;
  border-radius: 999px;
  background: var(--primary-light);
  color: var(--primary-ink);
  font-size: 0.66rem;
  font-weight: 700;
}

.field-help {
  display: block;
  margin-top: 0.4rem;
  color: var(--text-muted);
  font-size: 0.72rem;
  line-height: 1.45;
}

.quiz-list-toolbar {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  max-width: 520px;
  margin: 1rem 0;
  padding: 0 0.8rem;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--surface-ffffff);
  color: var(--text-muted);
}

.quiz-list-toolbar input {
  width: 100%;
  height: 42px;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text-color);
}

.quiz-definition-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 0.8rem;
}

.quiz-definition-card {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  padding: 1rem;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--surface-ffffff);
  color: var(--text-color);
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.2s,
    box-shadow 0.2s,
    transform 0.2s;
}

.quiz-definition-card:hover {
  border-color: var(--primary-color);
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}

.quiz-definition-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  flex: 0 0 44px;
  border-radius: 12px;
  background: var(--primary-light);
  color: var(--primary-ink);
}

.quiz-definition-copy {
  min-width: 0;
  flex: 1;
}

.quiz-definition-copy h4 {
  margin: 0.35rem 0 0.2rem;
  font-size: 0.9rem;
}

.quiz-definition-copy p {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.75rem;
}

.pending-count {
  padding: 0.45rem 0.7rem;
  border-radius: var(--radius-full);
  background: var(--surface-fff7d6);
  color: var(--ink-806710);
  font-weight: 800;
  font-size: 0.78rem;
}
.lesson-back-button {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: 0.75rem;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--primary-ink);
  font-weight: 700;
  cursor: pointer;
}
.lesson-back-button:hover {
  text-decoration: underline;
}
.lesson-course-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 1rem;
}
.lesson-course-card {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  width: 100%;
  padding: 1rem;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--surface-ffffff);
  color: var(--text-color);
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}
.lesson-course-card:hover {
  border-color: var(--primary-color);
  box-shadow: 0 6px 16px rgb(0 103 79 / 12%);
  transform: translateY(-2px);
}
.lesson-course-icon {
  display: grid;
  place-items: center;
  flex: 0 0 44px;
  height: 44px;
  border-radius: 10px;
  background: var(--primary-light);
  color: var(--primary-ink);
}
.lesson-course-copy {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.25rem;
}
.lesson-course-copy small {
  color: var(--text-muted);
}
.lesson-order-table tbody tr {
  transition:
    background-color 0.15s ease,
    opacity 0.15s ease;
}
.lesson-order-table tbody tr.dragging {
  opacity: 0.45;
}
.lesson-order-table tbody tr.drag-over td {
  border-top: 2px solid var(--primary-color);
  background: var(--primary-light);
}
.drag-column {
  width: 88px;
  text-align: center !important;
}
.drag-handle {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--text-muted);
  cursor: grab;
  user-select: none;
}
.drag-handle:active {
  cursor: grabbing;
}
.enrollment-status {
  display: inline-flex;
  padding: 0.3rem 0.55rem;
  border-radius: var(--radius-full);
  font-size: 0.72rem;
  font-weight: 800;
}
.enrollment-status.pending {
  background: var(--surface-fff7d6);
  color: var(--ink-806710);
}
.enrollment-status.approved {
  background: var(--surface-e8f5ef);
  color: var(--primary-ink);
}
.enrollment-status.rejected {
  background: var(--surface-feecec);
  color: var(--ink-b42318);
}
.enrollment-status-detail {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.45rem;
}
.enrollment-actions {
  display: flex;
  gap: 0.4rem;
}
.enrollment-table th,
.enrollment-table td {
  text-align: center;
  vertical-align: middle;
}
.enrollment-table .enrollment-actions {
  justify-content: center;
}
.enrollment-actions button {
  padding: 0.45rem 0.6rem;
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 700;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}
.approve-request {
  border: 1px solid var(--primary-color);
  background: var(--primary-color);
  color: white;
}
.approve-request:hover {
  background: #005a46;
  border-color: #005a46;
  box-shadow: 0 4px 10px rgb(0 103 79 / 22%);
  transform: translateY(-1px);
}
.reject-request {
  border: 1px solid #ef4444;
  background: var(--surface-ffffff);
  color: var(--ink-b42318);
}
.reject-request:hover {
  background: #dc2626;
  border-color: #dc2626;
  color: white;
  box-shadow: 0 4px 10px rgb(220 38 38 / 20%);
  transform: translateY(-1px);
}
.enrollment-actions button:active {
  box-shadow: none;
  transform: translateY(0);
}

.rejection-reason {
  display: inline-flex;
  max-width: 280px;
  gap: 0.25rem;
  color: var(--ink-b42318);
  line-height: 1.35;
  text-align: left;
}

.reject-enrollment-modal {
  width: min(520px, calc(100vw - 2rem));
  padding: 1.75rem;
}

.reject-modal-icon {
  width: 52px;
  height: 52px;
  margin-bottom: 1rem;
  display: grid;
  place-items: center;
  border-radius: 14px;
  background: var(--surface-feecec);
  color: var(--ink-b42318);
}

.reject-modal-heading > span {
  color: var(--ink-b42318);
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.reject-modal-heading h3 {
  margin: 0.3rem 0 0.45rem;
  color: var(--text-main);
  font-size: 1.25rem;
}

.reject-modal-heading p {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.86rem;
}

.reject-request-summary {
  margin: 1.25rem 0;
  padding: 0.9rem 1rem;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--surface-f8fafc);
}

.reject-request-summary > div {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.reject-request-summary small,
.reject-request-summary span {
  color: var(--text-muted);
  font-size: 0.72rem;
}

.reject-request-summary strong {
  color: var(--text-main);
  font-size: 0.88rem;
}

.reject-enrollment-modal textarea {
  width: 100%;
  padding: 0.75rem 0.85rem;
  resize: vertical;
  border: 1px solid var(--line-cbd5e1);
  border-radius: 9px;
  outline: none;
  color: var(--text-main);
  line-height: 1.5;
}

.reject-enrollment-modal textarea:focus {
  border-color: #dc2626;
  box-shadow: 0 0 0 3px rgb(220 38 38 / 10%);
}

.required-mark {
  color: var(--ink-dc2626);
}

.reason-counter {
  margin-top: 0.25rem;
  color: var(--text-muted);
  font-size: 0.7rem;
  text-align: right;
}

.reject-confirm-button {
  background: #dc2626;
  color: white;
}

.reject-confirm-button:hover:not(:disabled) {
  background: #b91c1c;
}

.reject-confirm-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.file-upload-control {
  display: flex;
  align-items: stretch;
  gap: 0.45rem;
}

.file-clear-btn {
  width: 38px;
  flex: 0 0 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--line-f0c5c5);
  border-radius: var(--radius-sm);
  background: var(--surface-fff7f7);
  color: var(--ink-b42318);
  cursor: pointer;
  transition:
    border-color 0.2s,
    background 0.2s;
}

.file-clear-btn:hover {
  border-color: #d92d20;
  background: var(--surface-fee4e2);
}

.file-clear-btn:focus-visible {
  outline: 2px solid #d92d20;
  outline-offset: 2px;
}

.file-picker:hover {
  border-color: var(--primary-color);
  background: var(--primary-light);
}

.file-picker span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.82rem;
  font-weight: 600;
}

.file-picker input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

.video-source-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.65rem;
}

.video-source-option {
  display: flex !important;
  align-items: center;
  gap: 0.65rem;
  min-height: 64px;
  margin: 0 !important;
  padding: 0.7rem 0.8rem;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--surface-ffffff);
  color: var(--text-muted);
  cursor: pointer;
  transition:
    border-color 0.2s,
    background 0.2s,
    color 0.2s;
}

.video-source-option:hover {
  border-color: var(--line-9ab9ae);
}

.video-source-option.active {
  border-color: var(--primary-color);
  background: var(--primary-light);
  color: var(--primary-ink);
  box-shadow: 0 0 0 1px var(--primary-color);
}

.video-source-option input {
  width: 16px;
  height: 16px;
  margin: 0;
  accent-color: var(--primary-color);
}

.video-source-option > span {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.video-source-option strong {
  font-size: 0.82rem;
}

.video-source-option small {
  color: var(--text-muted);
  font-size: 0.7rem;
  font-weight: 400;
}

.video-source-panel {
  margin-top: 0.75rem;
}

.video-source-help {
  display: block;
  margin-top: 0.4rem;
  color: var(--text-muted);
  font-size: 0.72rem;
}

.selected-video-note {
  display: block;
  margin-top: 0.5rem;
  color: var(--primary-ink);
}

.selected-file-meta {
  display: block;
  margin-top: 0.3rem;
  color: var(--text-muted);
  font-size: 0.7rem;
}

.document-remove {
  position: absolute;
  top: 50%;
  right: 0.8rem;
  transform: translateY(-50%);
}

@media (max-width: 700px) {
  .reject-request-summary {
    grid-template-columns: 1fr;
  }

  .video-source-options {
    grid-template-columns: 1fr;
  }

  .card-header-row {
    align-items: stretch;
    flex-direction: column;
    gap: 1rem;
  }

  .card-header-row > .btn {
    width: 100%;
    justify-content: center;
    margin-left: 0;
  }

  .course-selector-row {
    align-items: stretch;
    flex-direction: column;
  }

  .course-selector-row .select-control {
    width: 100%;
  }

  .modal-overlay {
    padding: 0.5rem;
  }

  .lesson-modal-content {
    max-height: calc(100dvh - 1rem);
  }

  .lesson-modal-header {
    padding: 1rem 1rem 0;
  }

  .lesson-modal-body {
    padding: 1rem 1rem 0.25rem;
    scrollbar-gutter: auto;
  }

  .lesson-modal-footer {
    padding: 0.8rem 1rem;
  }

  .document-form-fields {
    grid-template-columns: 1fr;
  }
}

.loading-overlay {
  width: 100%;
  min-height: 100%;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  color: var(--text-muted);
  text-align: center;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 3px solid var(--primary-light);
  border-top-color: var(--primary-color);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

select:disabled {
  background-color: var(--surface-f3f4f6);
  color: var(--ink-6b7280);
  border: 1px solid var(--line-d1d5db);
  cursor: not-allowed;
  opacity: 1;
}

.rag-documents-manager {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.rag-document-controls {
  display: grid;
  grid-template-columns: minmax(240px, 0.8fr) minmax(420px, 2fr);
  gap: 1rem;
  align-items: end;
}

.rag-upload-box {
  min-height: 82px;
  display: flex;
  align-items: center;
  gap: 0.85rem;
  padding: 1rem;
  border: 1px dashed var(--line-9db9ae);
  border-radius: var(--radius-md);
  background: var(--surface-f4f8f6);
  color: var(--primary-ink);
}

.rag-upload-box > div {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.rag-upload-box small {
  color: var(--text-muted);
}

.rag-upload-message {
  margin: -0.45rem 0 0;
  color: var(--primary-ink);
  font-weight: 600;
}

.rag-status {
  display: inline-flex;
  align-items: center;
  padding: 0.25rem 0.55rem;
  border-radius: 999px;
  background: var(--surface-eef2f7);
  color: var(--ink-475569);
  font-size: 0.75rem;
  font-weight: 700;
  white-space: nowrap;
}

.rag-status.status-indexing,
.rag-status.status-pending {
  background: var(--surface-fff7d6);
  color: var(--ink-806710);
}

.rag-status.status-indexed {
  background: var(--surface-dcfce7);
  color: var(--ink-166534);
}

.rag-status.status-failed {
  background: var(--surface-fee2e2);
  color: var(--ink-b91c1c);
}

.rag-error-cell {
  min-width: 0;
  color: var(--ink-b91c1c);
  white-space: normal;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.rag-error-cell span {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  line-clamp: 3;
}

.rag-document-table {
  table-layout: fixed;
}
.rag-document-table th:nth-child(1) {
  width: 24%;
}
.rag-document-table th:nth-child(2) {
  width: 11%;
}
.rag-document-table th:nth-child(3) {
  width: 12%;
}
.rag-document-table th:nth-child(4) {
  width: 9%;
}
.rag-document-table th:nth-child(5) {
  width: 15%;
}
.rag-document-table th:nth-child(6) {
  width: 29%;
}

.rag-empty-cell {
  padding: 2rem !important;
  color: var(--text-muted);
  text-align: center;
}

.spin-icon {
  animation: spin 0.9s linear infinite;
}

@media (max-width: 900px) {
  .rag-document-controls {
    grid-template-columns: 1fr;
  }

  .rag-upload-box {
    align-items: flex-start;
    flex-wrap: wrap;
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.embedded-content {
  padding: 1.5rem;
}
.lesson-modal-form {
  height: auto;
}
.lesson-modal-body {
  max-height: none;
  overflow: visible;
  padding: 0;
}
.lesson-modal-footer {
  position: static;
  margin-top: 1.5rem;
}
.failed {
  color: var(--ink-b91c1c);
}
textarea {
  width: 100%;
}
.procedure-form-fields {
  grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.2fr);
  align-items: start;
}
.procedure-form-fields > .form-control {
  min-width: 0;
  line-height: 1.5;
}
.procedure-form-fields + .document-remove {
  width: 32px;
  height: 36px;
}
@media (max-width: 700px) {
  .procedure-form-fields {
    grid-template-columns: 1fr;
  }
}
.embedded-content .form-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
}
.embedded-content .form-row > .col {
  min-width: 0;
}
.supporting-section {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 1.25rem;
  margin-top: 1.25rem;
}
.supporting-section h2 {
  font-size: 1.05rem;
  color: var(--primary-ink);
  margin: 0;
}
.section-description {
  font-size: 0.85rem;
  color: var(--text-muted);
  margin: 0.4rem 0 1rem;
}
.card-header-row {
  justify-content: space-between;
}
.embedded-content .lesson-modal-footer {
  position: sticky;
  bottom: 0;
  z-index: 5;
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  border-radius: 0 0 10px 10px;
}
.empty-state-box {
  padding: 1.5rem;
  border: 1px dashed var(--line-cbd5e1);
  border-radius: 8px;
  color: var(--text-muted);
  text-align: center;
}
@media (max-width: 700px) {
  .embedded-content {
    padding: 1rem;
  }
  .embedded-content .form-row {
    grid-template-columns: minmax(0, 1fr);
  }
  .supporting-section {
    padding: 1rem;
  }
}

/* CHANGELOG & NOTIFICATION CARD */
.change-notify-card {
  margin: 1.25rem 0;
  padding: 1rem 1.25rem;
  background: rgba(245, 158, 11, 0.05);
  border: 1px solid rgba(245, 158, 11, 0.3);
  border-radius: 10px;
}
.change-notify-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 0.5rem;
}
.notify-checkbox-label {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  cursor: pointer;
  font-weight: 600;
  color: var(--text-main);
  font-size: 0.88rem;
}
.badge-learners-warning {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.2rem 0.6rem;
  border-radius: 9999px;
  background: rgba(245, 158, 11, 0.16);
  color: #f59e0b;
  border: 1px solid rgba(245, 158, 11, 0.45);
  font-size: 0.75rem;
  font-weight: 700;
}
.badge-learners-empty {
  font-size: 0.75rem;
  color: var(--text-muted);
}
.change-notify-body {
  margin-top: 0.85rem;
  padding-top: 0.85rem;
  border-top: 1px dashed rgba(245, 158, 11, 0.25);
}
.summary-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.4rem;
}
.summary-label-row label {
  font-weight: 600;
  font-size: 0.82rem;
  color: var(--text-main);
}
.auto-diff-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.2rem 0.55rem;
  border-radius: 6px;
  background: var(--bg-subtle, rgba(16, 185, 129, 0.1));
  color: var(--primary-color, #10b981);
  border: 1px solid rgba(16, 185, 129, 0.25);
  font-size: 0.72rem;
  font-weight: 600;
}
.notify-summary-input {
  font-size: 0.85rem;
  line-height: 1.45;
}
.summary-hint {
  display: block;
  margin-top: 0.35rem;
  font-size: 0.72rem;
  color: var(--text-muted);
}


.fullscreen-loading-overlay {
  position: fixed;
  inset: 0;
  z-index: 999999;
  background: rgba(10, 16, 26, 0.78);
  backdrop-filter: blur(5px);
  display: flex;
  align-items: center;
  justify-content: center;
}
.loading-modal-card {
  background: var(--bg-surface, #1e293b);
  border: 1px solid var(--border-color, #334155);
  border-radius: 16px;
  padding: 2.2rem 3rem;
  text-align: center;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.65);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.9rem;
}
.loading-modal-card h4 {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 700;
  color: #fff;
}
.loading-modal-card p {
  margin: 0;
  font-size: 0.9rem;
  color: #94a3b8;
}
.spin-icon {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
