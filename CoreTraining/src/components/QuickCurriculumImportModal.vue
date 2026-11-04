<template>
  <div class="tree-modal-backdrop" @click.self="$emit('close')">
    <div class="quick-import-modal">
      <!-- Modal Header -->
      <div class="modal-header">
        <div class="header-titles">
          <div class="title-with-badge">
            <span class="badge-quick">
              <Zap :size="12" />
              <span>Quick Setup & Import</span>
            </span>
            <h3>Khởi tạo nhanh & Import Cây đào tạo</h3>
          </div>
          <p class="header-subtitle">
            Tạo trọn gói Lĩnh vực, Chương trình, Khóa học và Đề cương bài học trong một bước hoặc bằng file Excel / CSV.
          </p>
        </div>
        <button type="button" class="close-btn" @click="$emit('close')" aria-label="Đóng">
          <X :size="18" />
        </button>
      </div>

      <!-- Mode Tabs -->
      <div class="wizard-tabs">
        <button
          type="button"
          class="wizard-tab-btn"
          :class="{ active: activeTab === 'outline' }"
          @click="activeTab = 'outline'"
        >
          <Zap :size="16" />
          <span>Soạn nhanh Đề cương</span>
        </button>
        <button
          type="button"
          class="wizard-tab-btn"
          :class="{ active: activeTab === 'csv' }"
          @click="activeTab = 'csv'"
        >
          <FileSpreadsheet :size="16" />
          <span>Nhập từ File Excel / CSV</span>
        </button>
      </div>

      <!-- Modal Body -->
      <div class="modal-body-scroll">
        <!-- ============================================== -->
        <!-- TAB 1: SOẠN NHANH ĐỀ CƯƠNG (TEXT OUTLINE)       -->
        <!-- ============================================== -->
        <div v-if="activeTab === 'outline'" class="tab-content outline-mode">
          <!-- Step 1: Lĩnh vực & Chương trình -->
          <div class="wizard-grid-2">
            <!-- LĨNH VỰC -->
            <div class="form-group">
              <div class="label-with-action">
                <label>1. Lĩnh vực (*):</label>
                <button
                  type="button"
                  class="btn-text-toggle"
                  @click="isNewDomain = !isNewDomain"
                >
                  {{ isNewDomain ? "← Chọn từ danh sách" : "+ Tạo Lĩnh vực mới" }}
                </button>
              </div>

              <div v-if="!isNewDomain">
                <select v-model="selectedDomainId" class="form-control select-field" @change="onDomainChange">
                  <option value="">-- Chọn lĩnh vực hiện có --</option>
                  <option v-for="d in activeDomains" :key="d.id" :value="d.id">
                    {{ d.name }}
                  </option>
                </select>
              </div>
              <div v-else>
                <input
                  v-model="newDomainName"
                  type="text"
                  placeholder="Nhập tên Lĩnh vực mới..."
                  class="form-control input-field"
                />
              </div>
            </div>

            <!-- CHƯƠNG TRÌNH -->
            <div class="form-group">
              <div class="label-with-action">
                <label>2. Chương trình (*):</label>
                <button
                  type="button"
                  class="btn-text-toggle"
                  @click="isNewProgram = !isNewProgram"
                >
                  {{ isNewProgram ? "← Chọn từ danh sách" : "+ Tạo Chương trình mới" }}
                </button>
              </div>

              <div v-if="!isNewProgram">
                <select
                  v-model="selectedProgramId"
                  class="form-control select-field"
                  :disabled="!selectedDomainId && !availablePrograms.length"
                >
                  <option value="">-- Chọn chương trình hiện có --</option>
                  <option v-for="p in availablePrograms" :key="p.id" :value="p.id">
                    {{ p.name }}
                  </option>
                </select>
              </div>
              <div v-else>
                <input
                  v-model="newProgramName"
                  type="text"
                  placeholder="Nhập tên Chương trình mới..."
                  class="form-control input-field"
                />
              </div>
            </div>
          </div>

          <!-- Step 2: Khóa học -->
          <div class="wizard-grid-3">
            <div class="form-group">
              <label>3. Tên Khóa học (*):</label>
              <input
                v-model="courseName"
                type="text"
                placeholder="Nhập tên khóa học..."
                class="form-control input-field"
              />
            </div>
            <div class="form-group">
              <label>Cấp độ đào tạo:</label>
              <select v-model="courseLevel" class="form-control select-field">
                <option value="BASIC">Cơ bản (Nền tảng nghiệp vụ)</option>
                <option value="ADVANCED">Nâng cao (Xử lý tình huống & Tác nghiệp)</option>
                <option value="EXPERT">Chuyên gia (Thẩm định & Phê duyệt)</option>
              </select>
            </div>
            <div class="form-group">
              <label>Hạn hoàn thành khuyến nghị:</label>
              <input
                v-model="courseDeadline"
                type="date"
                class="form-control input-field date-field"
              />
            </div>
          </div>

          <!-- Step 3: Dàn ý Đề cương Chương & Bài học -->
          <div class="form-group outline-text-group">
            <div class="label-with-action">
              <label>
                4. Dàn ý Chương & Bài học (Soạn hoặc Paste text):
              </label>
              <button
                type="button"
                class="btn-sample-fill"
                :class="{ 'is-clear': outlineRawText.trim().length > 0 }"
                @click="toggleSampleOutline"
              >
                <component :is="outlineRawText.trim().length > 0 ? RotateCcw : Sparkles" :size="13" />
                <span>{{ outlineRawText.trim().length > 0 ? "Xóa dàn ý mẫu" : "Chèn dàn ý mẫu" }}</span>
              </button>
            </div>
            <div class="outline-hint">
              Mẹo: Mỗi dòng bắt đầu bằng <strong>Chương ...</strong> là một chương. Các dòng gạch đầu dòng <strong>- Bài ...</strong> bên dưới là các bài học của chương đó.
            </div>
            <textarea
              v-model="outlineRawText"
              rows="6"
              class="form-control outline-textarea"
              placeholder="Chương 1: Tổng quan quy trình&#10;- Bài 1: Giới thiệu & Văn bản pháp lý&#10;- Bài 2: Đối tượng & Điều kiện áp dụng&#10;Chương 2: Hướng dẫn thao tác nghiệp vụ&#10;- Bài 1: Tiếp nhận hồ sơ trên hệ thống&#10;- Bài 2: Phê duyệt và hạch toán"
            ></textarea>
          </div>

          <!-- Preview Realtime -->
          <div v-if="parsedOutline.length" class="outline-preview-box">
            <div class="preview-header">
              <span class="preview-title">
                <Layers :size="14" />
                <span>Xem trước cấu trúc sẽ sinh:</span>
              </span>
              <span class="preview-stats">
                {{ parsedOutline.length }} chương · {{ totalParsedLessons }} bài học
              </span>
            </div>
            <div class="preview-tree-list">
              <div
                v-for="(chap, cIdx) in parsedOutline"
                :key="cIdx"
                class="preview-chapter-item"
              >
                <div class="preview-chapter-title">
                  <Bookmark :size="13" class="icon-chap" />
                  <strong>Chương {{ cIdx + 1 }}:</strong> {{ chap.title }}
                </div>
                <div class="preview-lesson-list">
                  <div
                    v-for="(les, lIdx) in chap.lessons"
                    :key="lIdx"
                    class="preview-lesson-item"
                  >
                    <FileText :size="12" class="icon-les" />
                    <span>Bài {{ lIdx + 1 }}: {{ les.title }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Option Direct to Lesson Editor -->
          <div class="form-check-row">
            <label class="checkbox-label">
              <input v-model="openFirstLessonAfterCreate" type="checkbox" />
              <span>Tự động chuyển vào màn Soạn bài học đầu tiên ngay sau khi tạo</span>
            </label>
          </div>
        </div>

        <!-- ============================================== -->
        <!-- TAB 2: IMPORT EXCEL / CSV                       -->
        <!-- ============================================== -->
        <div v-else class="tab-content csv-mode">
          <!-- Guide Banner -->
          <div class="csv-guide-card">
            <div class="guide-left">
              <div class="guide-title">
                <FileSpreadsheet :size="18" class="text-primary" />
                <span>Tải file mẫu nhập liệu chuẩn Excel / CSV</span>
              </div>
              <p class="guide-desc">
                Cột bao gồm: <code>Lĩnh vực</code>, <code>Chương trình</code>, <code>Khóa học</code>, <code>Cấp độ</code>, <code>Hạn hoàn thành</code>, <code>Chương</code>, <code>Bài học</code>, <code>Mô tả bài học</code>.
                Header có định dạng màu nổi bật, đóng băng hàng đầu và căn lề sẵn. Hệ thống tự động gom nhánh thông minh theo phân cấp đã có, không tạo trùng lặp.
              </p>
            </div>
            <div class="guide-actions-btn">
              <button
                type="button"
                class="btn btn-primary btn-download-template"
                @click="downloadExcelTemplate"
              >
                <Download :size="14" />
                <span>Tải file mẫu Excel (.xlsx)</span>
              </button>
              <button
                type="button"
                class="btn btn-outline-secondary btn-download-csv"
                @click="downloadCsvTemplate"
              >
                <Download :size="13" />
                <span>Tải CSV (.csv)</span>
              </button>
            </div>
          </div>

          <!-- Dropzone -->
          <div
            class="csv-dropzone"
            :class="{ 'is-dragover': isDragOver, 'has-file': !!csvFile }"
            @dragover.prevent="isDragOver = true"
            @dragleave.prevent="isDragOver = false"
            @drop.prevent="handleFileDrop"
            @click="triggerFileInput"
          >
            <input
              ref="fileInputRef"
              type="file"
              accept=".xlsx,.xls,.csv,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              style="display: none"
              @change="handleFileSelect"
            />
            <div v-if="!csvFile && !csvFileInfo" class="dropzone-empty">
              <UploadCloud :size="36" class="dropzone-icon" />
              <p class="dropzone-main-text">
                Kéo thả file <strong>Excel (.xlsx)</strong> hoặc <strong>CSV (.csv)</strong> vào đây, hoặc <span class="browse-link">chọn từ máy tính</span>
              </p>
              <p class="dropzone-sub-text">Hỗ trợ Microsoft Excel (.xlsx), CSV UTF-8</p>
            </div>
            <div v-else class="dropzone-selected">
              <FileSpreadsheet :size="32" class="selected-icon" />
              <div class="selected-file-info">
                <span class="file-name">{{ (csvFile?.name) || csvFileInfo?.name }}</span>
                <span class="file-size">{{ (((csvFile?.size) || (csvFileInfo?.size) || 0) / 1024).toFixed(1) }} KB</span>
              </div>
              <button
                type="button"
                class="btn-remove-file"
                title="Chọn file khác"
                @click.stop="clearCsvFile"
              >
                <X :size="14" />
              </button>
            </div>
          </div>

          <!-- CSV Parse Error -->
          <div v-if="csvError" class="alert-box alert-error">
            <AlertCircle :size="15" />
            <span>{{ csvError }}</span>
          </div>

          <!-- CSV Preview Summary -->
          <div v-if="csvParsedRows.length" class="csv-preview-section">
            <div class="preview-metrics">
              <div class="metric-card">
                <span class="metric-num">{{ csvStats.domains }}</span>
                <span class="metric-label">Lĩnh vực</span>
              </div>
              <div class="metric-card">
                <span class="metric-num">{{ csvStats.programs }}</span>
                <span class="metric-label">Chương trình</span>
              </div>
              <div class="metric-card">
                <span class="metric-num">{{ csvStats.courses }}</span>
                <span class="metric-label">Khóa học</span>
              </div>
              <div class="metric-card">
                <span class="metric-num">{{ csvStats.chapters }}</span>
                <span class="metric-label">Chương</span>
              </div>
              <div class="metric-card highlight">
                <span class="metric-num">{{ csvStats.lessons }}</span>
                <span class="metric-label">Bài học</span>
              </div>
            </div>

            <!-- Preview Table (Up to 8 rows) -->
            <div class="csv-table-wrapper">
              <table class="csv-table">
                <thead>
                  <tr>
                    <th :style="{ width: colWidths.domain + 'px' }">
                      <div class="th-content">
                        <span>Lĩnh vực</span>
                        <span class="resize-handle" @mousedown.stop.prevent="startColResize($event, 'domain')"></span>
                      </div>
                    </th>
                    <th :style="{ width: colWidths.program + 'px' }">
                      <div class="th-content">
                        <span>Chương trình</span>
                        <span class="resize-handle" @mousedown.stop.prevent="startColResize($event, 'program')"></span>
                      </div>
                    </th>
                    <th :style="{ width: colWidths.course + 'px' }">
                      <div class="th-content">
                        <span>Khóa học</span>
                        <span class="resize-handle" @mousedown.stop.prevent="startColResize($event, 'course')"></span>
                      </div>
                    </th>
                    <th :style="{ width: colWidths.deadline + 'px' }">
                      <div class="th-content">
                        <span>Hạn hoàn thành</span>
                        <span class="resize-handle" @mousedown.stop.prevent="startColResize($event, 'deadline')"></span>
                      </div>
                    </th>
                    <th :style="{ width: colWidths.chapter + 'px' }">
                      <div class="th-content">
                        <span>Chương</span>
                        <span class="resize-handle" @mousedown.stop.prevent="startColResize($event, 'chapter')"></span>
                      </div>
                    </th>
                    <th :style="{ width: colWidths.lesson + 'px' }">
                      <div class="th-content">
                        <span>Bài học</span>
                        <span class="resize-handle" @mousedown.stop.prevent="startColResize($event, 'lesson')"></span>
                      </div>
                    </th>
                    <th :style="{ width: colWidths.description + 'px' }">
                      <div class="th-content">
                        <span>Mô tả bài học</span>
                        <span class="resize-handle" @mousedown.stop.prevent="startColResize($event, 'description')"></span>
                      </div>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, idx) in csvPreviewRows" :key="idx">
                    <td :style="{ maxWidth: colWidths.domain + 'px' }" :title="row.domain">{{ row.domain }}</td>
                    <td :style="{ maxWidth: colWidths.program + 'px' }" :title="row.program">{{ row.program }}</td>
                    <td :style="{ maxWidth: colWidths.course + 'px' }" :title="row.course">{{ row.course }}</td>
                    <td :style="{ maxWidth: colWidths.deadline + 'px' }" class="text-muted" :title="row.deadline || 'Không quy định'">{{ row.deadline || "—" }}</td>
                    <td :style="{ maxWidth: colWidths.chapter + 'px' }" :title="row.chapter">{{ row.chapter }}</td>
                    <td :style="{ maxWidth: colWidths.lesson + 'px' }" class="font-medium text-dark" :title="row.lesson">{{ row.lesson }}</td>
                    <td :style="{ maxWidth: colWidths.description + 'px' }" class="text-muted" :title="row.description || ''">{{ row.description || "—" }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p v-if="csvParsedRows.length > 8" class="table-more-hint">
              ...và {{ csvParsedRows.length - 8 }} dòng khác sẽ được import.
            </p>
          </div>
        </div>

        <!-- Processing Status Bar -->
        <div v-if="processing" class="progress-status-container">
          <div class="progress-label-row">
            <span class="status-text">
              <Loader2 :size="14" class="spin-icon" />
              <span>{{ progressStatusText }}</span>
            </span>
            <span class="status-percent">{{ progressPercent }}%</span>
          </div>
          <div class="progress-bar-track">
            <div class="progress-bar-fill" :style="{ width: progressPercent + '%' }"></div>
          </div>
        </div>
      </div>

      <!-- Modal Footer -->
      <div class="modal-footer">
        <button
          type="button"
          class="btn btn-secondary"
          :disabled="processing"
          @click="$emit('close')"
        >
          Hủy bỏ
        </button>

        <button
          v-if="activeTab === 'outline'"
          type="button"
          class="btn btn-primary btn-submit-wizard"
          :disabled="processing || !canSubmitOutline"
          @click="executeOutlineCreation"
        >
          <Loader2 v-if="processing" :size="15" class="spin-icon" />
          <Zap v-else :size="15" />
          <span>{{ processing ? "Đang khởi tạo cấu trúc..." : "Khởi tạo cấu trúc ngay" }}</span>
        </button>

        <button
          v-else
          type="button"
          class="btn btn-primary btn-submit-wizard"
          :disabled="processing || !csvParsedRows.length"
          @click="executeCsvImport"
        >
          <Loader2 v-if="processing" :size="15" class="spin-icon" />
          <UploadCloud v-else :size="15" />
          <span>{{ processing ? "Đang import..." : `Xác nhận Import (${csvParsedRows.length} bài học)` }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from "vue";
import {
  AlertCircle,
  Bookmark,
  Download,
  FileSpreadsheet,
  FileText,
  Layers,
  Loader2,
  RotateCcw,
  Sparkles,
  UploadCloud,
  X,
  Zap,
} from "lucide-vue-next";
import JSZip from "jszip";
import { trainingApi } from "../api/trainingApi";
import { adminApi } from "../api/adminApi";
import { showToast } from "../utils/toast";
import type { TrainingDomain, TrainingProgram, Course } from "../types";

const props = defineProps<{
  domains: TrainingDomain[];
  programs: TrainingProgram[];
}>();

const emit = defineEmits<{
  (e: "close"): void;
  (
    e: "completed",
    result: {
      expandedIds: string[];
      firstLessonId?: string;
      openLessonEditor?: boolean;
    }
  ): void;
}>();

const activeTab = ref<"outline" | "csv">("outline");

// ==========================================
// TAB 1: OUTLINE STATE
// ==========================================
const isNewDomain = ref(false);
const selectedDomainId = ref("");
const newDomainName = ref("");

const isNewProgram = ref(false);
const selectedProgramId = ref("");
const newProgramName = ref("");

const courseName = ref("");
const courseLevel = ref("BASIC");
const courseDeadline = ref("");
const outlineRawText = ref("");
const openFirstLessonAfterCreate = ref(true);

// ==========================================
// TAB 2: EXCEL / CSV STATE & PARSER
// ==========================================
const csvFile = ref<File | null>(null);
const fileInputRef = ref<HTMLInputElement | null>(null);
const isDragOver = ref(false);
const csvError = ref("");

interface CsvRow {
  domain: string;
  program: string;
  course: string;
  level: string;
  deadline?: string;
  chapter: string;
  lesson: string;
  description?: string;
}

const csvParsedRows = ref<CsvRow[]>([]);
const csvFileInfo = ref<{ name: string; size: number } | null>(null);

// Column resize state for preview table
const colWidths = ref<Record<string, number>>({
  domain: 130,
  program: 150,
  course: 200,
  deadline: 130,
  chapter: 170,
  lesson: 190,
  description: 220,
});

let isResizing = false;
let currentResizingCol = "";
let startX = 0;
let startWidth = 0;

function startColResize(e: MouseEvent, colKey: string) {
  isResizing = true;
  currentResizingCol = colKey;
  startX = e.clientX;
  startWidth = colWidths.value[colKey] || 150;
  window.addEventListener("mousemove", onColMouseMove);
  window.addEventListener("mouseup", stopColResize);
}

function onColMouseMove(e: MouseEvent) {
  if (!isResizing || !currentResizingCol) return;
  const delta = e.clientX - startX;
  colWidths.value[currentResizingCol] = Math.max(70, startWidth + delta);
}

function stopColResize() {
  isResizing = false;
  currentResizingCol = "";
  window.removeEventListener("mousemove", onColMouseMove);
  window.removeEventListener("mouseup", stopColResize);
}

const activeDomains = computed(() =>
  props.domains.filter((d) => d.status !== "DELETED")
);

const availablePrograms = computed(() => {
  if (!selectedDomainId.value) return [];
  return props.programs.filter(
    (p) => p.domainId === selectedDomainId.value && p.status !== "DELETED"
  );
});

function onDomainChange() {
  selectedProgramId.value = "";
}

// ==========================================
// PERSISTENT STORAGE KEY & AUTO-SAVE
// ==========================================
const STORAGE_KEY = "cbs_quick_curriculum_draft_v1";

function loadSavedDraft() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return;
    const data = JSON.parse(raw);
    if (data.activeTab) activeTab.value = data.activeTab;
    if (data.isNewDomain !== undefined) isNewDomain.value = data.isNewDomain;
    if (data.selectedDomainId !== undefined) selectedDomainId.value = data.selectedDomainId;
    if (data.newDomainName !== undefined) newDomainName.value = data.newDomainName;
    if (data.isNewProgram !== undefined) isNewProgram.value = data.isNewProgram;
    if (data.selectedProgramId !== undefined) selectedProgramId.value = data.selectedProgramId;
    if (data.newProgramName !== undefined) newProgramName.value = data.newProgramName;
    if (data.courseName !== undefined) courseName.value = data.courseName;
    if (data.courseLevel !== undefined) courseLevel.value = data.courseLevel;
    if (data.courseDeadline !== undefined) courseDeadline.value = data.courseDeadline;
    if (data.outlineRawText !== undefined) outlineRawText.value = data.outlineRawText;
    if (data.openFirstLessonAfterCreate !== undefined) {
      openFirstLessonAfterCreate.value = data.openFirstLessonAfterCreate;
    }
    if (Array.isArray(data.csvParsedRows) && data.csvParsedRows.length > 0) {
      csvParsedRows.value = data.csvParsedRows;
      if (data.csvFileInfo) {
        csvFileInfo.value = data.csvFileInfo;
      }
    }
    if (data.colWidths) {
      colWidths.value = { ...colWidths.value, ...data.colWidths };
    }
  } catch (e) {
    console.warn("Failed to load quick curriculum draft from localStorage", e);
  }
}

function saveDraft() {
  try {
    const data = {
      activeTab: activeTab.value,
      isNewDomain: isNewDomain.value,
      selectedDomainId: selectedDomainId.value,
      newDomainName: newDomainName.value,
      isNewProgram: isNewProgram.value,
      selectedProgramId: selectedProgramId.value,
      newProgramName: newProgramName.value,
      courseName: courseName.value,
      courseLevel: courseLevel.value,
      courseDeadline: courseDeadline.value,
      outlineRawText: outlineRawText.value,
      openFirstLessonAfterCreate: openFirstLessonAfterCreate.value,
      csvParsedRows: csvParsedRows.value,
      csvFileInfo: csvFile.value
        ? { name: csvFile.value.name, size: csvFile.value.size }
        : csvFileInfo.value,
      colWidths: colWidths.value,
    };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
  } catch (e) {
    // ignore
  }
}

function clearDraft() {
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch (e) {
    // ignore
  }
}

// Watch inputs and auto save
watch(
  [
    activeTab,
    isNewDomain,
    selectedDomainId,
    newDomainName,
    isNewProgram,
    selectedProgramId,
    newProgramName,
    courseName,
    courseLevel,
    courseDeadline,
    outlineRawText,
    openFirstLessonAfterCreate,
    csvParsedRows,
  ],
  () => {
    saveDraft();
  },
  { deep: true }
);

onMounted(() => {
  loadSavedDraft();
});

// Toggle sample outline: 1st click fills sample, 2nd click clears
function toggleSampleOutline() {
  if (outlineRawText.value.trim().length > 0) {
    outlineRawText.value = "";
  } else {
    outlineRawText.value = `Chương 1: Tổng quan quy chế & Điều kiện cho vay
- Bài 1: Căn cứ pháp lý và phạm vi điều chỉnh
- Bài 2: Đối tượng và tiêu chuẩn cấp tín dụng
Chương 2: Quy trình thẩm định và phê duyệt
- Bài 1: Tiếp nhận và kiểm tra hồ sơ khách hàng
- Bài 2: Thẩm định thực tế tại địa bàn
- Bài 3: Lập tờ trình thẩm định và trình phê duyệt
Chương 3: Giải ngân và Quản lý sau cho vay
- Bài 1: Thủ tục công chứng và đăng ký giao dịch bảo đảm
- Bài 2: Giải ngân và giám sát sử dụng vốn vay`;
  }
}

// Parse text outline into structured chapters & lessons
interface ParsedChapter {
  title: string;
  lessons: { title: string }[];
}

const parsedOutline = computed<ParsedChapter[]>(() => {
  const text = outlineRawText.value.trim();
  if (!text) {
    return [
      {
        title: "Chương 1: Tổng quan",
        lessons: [{ title: "Bài 1: Bài học mới" }],
      },
    ];
  }

  const lines = text.split("\n").map((l) => l.trim()).filter(Boolean);
  const chapters: ParsedChapter[] = [];
  let currentChapter: ParsedChapter | null = null;

  for (const line of lines) {
    // If line looks like a Chapter header
    const isChapter =
      /^(Chương|Phần|Module|Mục)\s*\d*[:.]?/i.test(line) ||
      (!line.startsWith("-") && !line.startsWith("*") && !line.startsWith("•") && !line.startsWith("Bài") && !/^\d+\.\d+/.test(line));

    if (isChapter && !line.startsWith("-") && !line.startsWith("*") && !line.startsWith("•")) {
      currentChapter = {
        title: line.replace(/^[#*-\s]+/, "").trim(),
        lessons: [],
      };
      chapters.push(currentChapter);
    } else {
      // It is a Lesson
      if (!currentChapter) {
        currentChapter = {
          title: "Chương 1: Tổng quan",
          lessons: [],
        };
        chapters.push(currentChapter);
      }
      const cleanTitle = line.replace(/^[-*•\d.\s]+/, "").trim();
      currentChapter.lessons.push({
        title: cleanTitle || "Bài học mới",
      });
    }
  }

  // Ensure each chapter has at least 1 lesson
  for (const ch of chapters) {
    if (!ch.lessons.length) {
      ch.lessons.push({ title: "Bài 1: Giới thiệu chương" });
    }
  }

  return chapters;
});

const totalParsedLessons = computed(() =>
  parsedOutline.value.reduce((sum, ch) => sum + ch.lessons.length, 0)
);

const canSubmitOutline = computed(() => {
  const hasDomain = isNewDomain.value ? !!newDomainName.value.trim() : !!selectedDomainId.value;
  const hasProg = isNewProgram.value ? !!newProgramName.value.trim() : !!selectedProgramId.value;
  const hasCourse = !!courseName.value.trim();
  return hasDomain && hasProg && hasCourse;
});

const csvPreviewRows = computed(() => csvParsedRows.value.slice(0, 8));

const csvStats = computed(() => {
  const dSet = new Set<string>();
  const pSet = new Set<string>();
  const cSet = new Set<string>();
  const chSet = new Set<string>();

  for (const r of csvParsedRows.value) {
    if (r.domain) dSet.add(r.domain);
    if (r.program) pSet.add(`${r.domain}::${r.program}`);
    if (r.course) cSet.add(`${r.domain}::${r.program}::${r.course}`);
    if (r.chapter) chSet.add(`${r.domain}::${r.program}::${r.course}::${r.chapter}`);
  }

  return {
    domains: dSet.size,
    programs: pSet.size,
    courses: cSet.size,
    chapters: chSet.size,
    lessons: csvParsedRows.value.length,
  };
});

function triggerFileInput() {
  fileInputRef.value?.click();
}

function clearCsvFile() {
  csvFile.value = null;
  csvFileInfo.value = null;
  csvParsedRows.value = [];
  csvError.value = "";
  if (fileInputRef.value) fileInputRef.value.value = "";
  saveDraft();
}

function handleFileSelect(e: Event) {
  const files = (e.target as HTMLInputElement).files;
  if (files && files[0]) {
    loadAndProcessFile(files[0]);
  }
}

function handleFileDrop(e: DragEvent) {
  isDragOver.value = false;
  const files = e.dataTransfer?.files;
  if (files && files[0]) {
    loadAndProcessFile(files[0]);
  }
}

function loadAndProcessFile(file: File) {
  csvFile.value = file;
  csvError.value = "";
  csvParsedRows.value = [];

  const lowerName = file.name.toLowerCase();
  if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) {
    parseXlsxFile(file);
  } else {
    parseCsvFile(file);
  }
}

// Download Excel Template with beautiful soft green header and frozen top row
async function downloadExcelTemplate() {
  const headers = [
    "Lĩnh vực",
    "Chương trình",
    "Khóa học",
    "Cấp độ",
    "Hạn hoàn thành",
    "Chương",
    "Bài học",
    "Mô tả bài học",
  ];
  const sampleRows = [
    [
      "Tín dụng",
      "Tín dụng Bán lẻ",
      "Nghiệp vụ Cho vay Khách hàng Cá nhân",
      "Cơ bản",
      "2026-12-31",
      "Chương 1: Tổng quan quy chế",
      "Bài 1: Căn cứ pháp lý & Phạm vi",
      "Tổng quan quy định và căn cứ pháp lý trong cấp tín dụng cá nhân",
    ],
    [
      "Tín dụng",
      "Tín dụng Bán lẻ",
      "Nghiệp vụ Cho vay Khách hàng Cá nhân",
      "Cơ bản",
      "2026-12-31",
      "Chương 1: Tổng quan quy chế",
      "Bài 2: Tiêu chuẩn và điều kiện",
      "Các điều kiện vay vốn bắt buộc",
    ],
    [
      "Tín dụng",
      "Tín dụng Bán lẻ",
      "Nghiệp vụ Cho vay Khách hàng Cá nhân",
      "Cơ bản",
      "2026-12-31",
      "Chương 2: Thẩm định hồ sơ",
      "Bài 1: Tiếp nhận và kiểm tra hồ sơ",
      "Quy trình kiểm tra hồ sơ pháp lý và tài chính",
    ],
    [
      "Tín dụng",
      "Tín dụng Bán lẻ",
      "Nghiệp vụ Cho vay Khách hàng Cá nhân",
      "Cơ bản",
      "2026-12-31",
      "Chương 2: Thẩm định hồ sơ",
      "Bài 2: Khảo sát thực địa tài sản",
      "Quy trình kiểm tra tài sản bảo đảm tại địa bàn",
    ],
    [
      "Kế toán & Tài chính",
      "Hạch toán Giao dịch Core",
      "Kế toán Tiền gửi & Tài khoản Thanh toán",
      "Cơ bản",
      "2026-11-30",
      "Chương 1: Mở và quản lý tài khoản",
      "Bài 1: Quy trình mở tài khoản tổ chức",
      "Các bước mở và kiểm soát thông tin tài khoản doanh nghiệp",
    ],
  ];

  try {
    const zip = new JSZip();

    // 1. [Content_Types].xml
    zip.file(
      "[Content_Types].xml",
      `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
</Types>`
    );

    // 2. _rels/.rels
    zip.file(
      "_rels/.rels",
      `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>`
    );

    // 3. xl/_rels/workbook.xml.rels
    zip.file(
      "xl/_rels/workbook.xml.rels",
      `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
</Relationships>`
    );

    // 4. xl/workbook.xml
    zip.file(
      "xl/workbook.xml",
      `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Mau_Dao_Tao" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>`
    );

    // 5. xl/styles.xml (Soft Green Header #D9EAD3 + Dark Green Bold text #1E4620 + Thin borders)
    zip.file(
      "xl/styles.xml",
      `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font>
      <sz val="11"/>
      <color rgb="FF0F172A"/>
      <name val="Calibri"/>
      <family val="2"/>
    </font>
    <font>
      <b/>
      <sz val="11"/>
      <color rgb="FF1E4620"/>
      <name val="Calibri"/>
      <family val="2"/>
    </font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill>
      <patternFill patternType="solid">
        <fgColor rgb="FFD9EAD3"/>
        <bgColor indexed="64"/>
      </patternFill>
    </fill>
  </fills>
  <borders count="3">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left style="thin"><color rgb="FFB7D7A8"/></left>
      <right style="thin"><color rgb="FFB7D7A8"/></right>
      <top style="thin"><color rgb="FFB7D7A8"/></top>
      <bottom style="thin"><color rgb="FFB7D7A8"/></bottom>
      <diagonal/>
    </border>
    <border>
      <left style="thin"><color rgb="FFE2E8F0"/></left>
      <right style="thin"><color rgb="FFE2E8F0"/></right>
      <top style="thin"><color rgb="FFE2E8F0"/></top>
      <bottom style="thin"><color rgb="FFE2E8F0"/></bottom>
      <diagonal/>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="3">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <!-- s=1: Header Row (Bold Green, D9EAD3 Background, Border 1) -->
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="left" vertical="center"/>
    </xf>
    <!-- s=2: Data Rows (Regular font, subtle border 2) -->
    <xf numFmtId="0" fontId="0" fillId="0" borderId="2" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="left" vertical="center"/>
    </xf>
  </cellXfs>
</styleSheet>`
    );

    const stringMap = new Map<string, number>();
    const stringList: string[] = [];
    function addString(str: any) {
      const s = String(str || "");
      if (stringMap.has(s)) return stringMap.get(s)!;
      const idx = stringList.length;
      stringList.push(s);
      stringMap.set(s, idx);
      return idx;
    }

    function colLetter(colIdx: number) {
      let temp = "";
      let letter = "";
      while (colIdx >= 0) {
        temp = String.fromCharCode((colIdx % 26) + 65);
        letter = temp + letter;
        colIdx = Math.floor(colIdx / 26) - 1;
      }
      return letter;
    }

    let sheetDataXml = `<row r="1" ht="28" customHeight="1">`;
    headers.forEach((h, colIdx) => {
      const sIdx = addString(h);
      const cellRef = `${colLetter(colIdx)}1`;
      sheetDataXml += `<c r="${cellRef}" s="1" t="s"><v>${sIdx}</v></c>`;
    });
    sheetDataXml += `</row>`;

    sampleRows.forEach((row, rowIdx) => {
      const rowNum = rowIdx + 2;
      sheetDataXml += `<row r="${rowNum}" ht="22" customHeight="1">`;
      row.forEach((cellVal, colIdx) => {
        const sIdx = addString(cellVal);
        const cellRef = `${colLetter(colIdx)}${rowNum}`;
        sheetDataXml += `<c r="${cellRef}" s="2" t="s"><v>${sIdx}</v></c>`;
      });
      sheetDataXml += `</row>`;
    });

    zip.file(
      "xl/worksheets/sheet1.xml",
      `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetViews>
    <sheetView tabSelected="1" workbookViewId="0">
      <pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>
    </sheetView>
  </sheetViews>
  <sheetFormatPr defaultRowHeight="20"/>
  <cols>
    <col min="1" max="1" width="22" customWidth="1"/>
    <col min="2" max="2" width="25" customWidth="1"/>
    <col min="3" max="3" width="38" customWidth="1"/>
    <col min="4" max="4" width="16" customWidth="1"/>
    <col min="5" max="5" width="20" customWidth="1"/>
    <col min="6" max="6" width="30" customWidth="1"/>
    <col min="7" max="7" width="34" customWidth="1"/>
    <col min="8" max="8" width="55" customWidth="1"/>
  </cols>
  <sheetData>${sheetDataXml}</sheetData>
</worksheet>`
    );

    let sharedStringsXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${stringList.length}" uniqueCount="${stringList.length}">`;
    stringList.forEach((s) => {
      const safe = s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
      sharedStringsXml += `<si><t>${safe}</t></si>`;
    });
    sharedStringsXml += `</sst>`;
    zip.file("xl/sharedStrings.xml", sharedStringsXml);

    const blob = await zip.generateAsync({
      type: "blob",
      mimeType:
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.setAttribute("href", url);
    link.setAttribute("download", "mau_nhap_chuong_trinh_dao_tao.xlsx");
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  } catch (err: any) {
    showToast("Không thể tạo file Excel: " + (err.message || String(err)), "error");
  }
}

// Download CSV Template fallback
function downloadCsvTemplate() {
  const headers = [
    "Lĩnh vực",
    "Chương trình",
    "Khóa học",
    "Cấp độ",
    "Hạn hoàn thành",
    "Chương",
    "Bài học",
    "Mô tả bài học",
  ];
  const sampleRows = [
    [
      "Tín dụng",
      "Tín dụng Bán lẻ",
      "Nghiệp vụ Cho vay Khách hàng Cá nhân",
      "Cơ bản",
      "2026-12-31",
      "Chương 1: Tổng quan quy chế",
      "Bài 1: Căn cứ pháp lý & Phạm vi",
      "Tổng quan quy định và căn cứ pháp lý trong cấp tín dụng cá nhân",
    ],
    [
      "Tín dụng",
      "Tín dụng Bán lẻ",
      "Nghiệp vụ Cho vay Khách hàng Cá nhân",
      "Cơ bản",
      "2026-12-31",
      "Chương 1: Tổng quan quy chế",
      "Bài 2: Tiêu chuẩn và điều kiện",
      "Các điều kiện vay vốn bắt buộc",
    ],
    [
      "Tín dụng",
      "Tín dụng Bán lẻ",
      "Nghiệp vụ Cho vay Khách hàng Cá nhân",
      "Cơ bản",
      "2026-12-31",
      "Chương 2: Thẩm định hồ sơ",
      "Bài 1: Tiếp nhận và kiểm tra hồ sơ",
      "Quy trình kiểm tra hồ sơ pháp lý và tài chính",
    ],
    [
      "Tín dụng",
      "Tín dụng Bán lẻ",
      "Nghiệp vụ Cho vay Khách hàng Cá nhân",
      "Cơ bản",
      "2026-12-31",
      "Chương 2: Thẩm định hồ sơ",
      "Bài 2: Khảo sát thực địa tài sản",
      "Quy trình kiểm tra tài sản bảo đảm tại địa bàn",
    ],
    [
      "Kế toán & Tài chính",
      "Hạch toán Giao dịch Core",
      "Kế toán Tiền gửi & Tài khoản Thanh toán",
      "Cơ bản",
      "2026-11-30",
      "Chương 1: Mở và quản lý tài khoản",
      "Bài 1: Quy trình mở tài khoản tổ chức",
      "Các bước mở và kiểm soát thông tin tài khoản doanh nghiệp",
    ],
  ];

  const csvContent =
    "\uFEFF" +
    [
      headers.join(","),
      ...sampleRows.map((row) =>
        row.map((cell) => `"${cell.replace(/"/g, '""')}"`).join(",")
      ),
    ].join("\r\n");

  const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.setAttribute("href", url);
  link.setAttribute("download", "mau_nhap_chuong_trinh_dao_tao.csv");
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

// Helper: Convert Excel column letter (A, B, ..., AA, etc.) to 0-based column index
function excelColToIndex(colStr: string): number {
  let index = 0;
  for (let i = 0; i < colStr.length; i++) {
    index = index * 26 + (colStr.charCodeAt(i) - 64);
  }
  return index - 1;
}

// Parse OpenXML XLSX files with DOMParser
async function parseXlsxFile(file: File) {
  try {
    const buffer = await file.arrayBuffer();
    const zip = await JSZip.loadAsync(buffer);

    // 1. Shared Strings
    const strings: string[] = [];
    const sstFile = zip.file("xl/sharedStrings.xml");
    if (sstFile) {
      const sstXml = await sstFile.async("text");
      const dom = new DOMParser().parseFromString(sstXml, "application/xml");
      dom.querySelectorAll("si").forEach((si) => {
        let str = "";
        si.querySelectorAll("t").forEach((t) => {
          str += t.textContent || "";
        });
        strings.push(str.trim());
      });
    }

    // 2. Sheet
    let sheetFile = zip.file("xl/worksheets/sheet1.xml");
    if (!sheetFile) {
      const sheetName = Object.keys(zip.files).find(
        (k) => k.startsWith("xl/worksheets/sheet") && k.endsWith(".xml")
      );
      if (sheetName) sheetFile = zip.file(sheetName);
    }
    if (!sheetFile) {
      csvError.value = "Không tìm thấy dữ liệu Sheet trong file Excel.";
      return;
    }

    const sheetXml = await sheetFile.async("text");
    const dom = new DOMParser().parseFromString(sheetXml, "application/xml");
    const rowElements = dom.querySelectorAll("sheetData > row");

    const grid: string[][] = [];
    rowElements.forEach((r) => {
      const rowData: string[] = [];
      const cells = r.querySelectorAll("c");
      cells.forEach((c) => {
        const rAttr = c.getAttribute("r") || "";
        const colLetters = rAttr.replace(/\d+/g, "").toUpperCase();
        const colIdx = colLetters ? excelColToIndex(colLetters) : rowData.length;

        const type = c.getAttribute("t");
        const vEl = c.querySelector("v");
        const isEl = c.querySelector("is t");
        let val = "";
        if (vEl) {
          const raw = (vEl.textContent || "").trim();
          if (type === "s") {
            const idx = parseInt(raw, 10);
            val = strings[idx] || "";
          } else {
            val = raw;
          }
        } else if (isEl) {
          val = (isEl.textContent || "").trim();
        }

        while (rowData.length < colIdx) {
          rowData.push("");
        }
        rowData[colIdx] = val;
      });
      if (rowData.some((cell) => (cell || "").length > 0)) {
        grid.push(rowData);
      }
    });

    processParsedGrid(grid);
  } catch (err: any) {
    csvError.value = "Lỗi khi đọc file Excel: " + (err.message || String(err));
  }
}

// Parse Raw CSV text
function parseRawCsvText(text: string): string[][] {
  const lines: string[][] = [];
  let row: string[] = [];
  let inQuotes = false;
  let currentToken = "";

  const cleanText = text.replace(/^\uFEFF/, "").replace(/\r\n/g, "\n").replace(/\r/g, "\n");
  const firstLine = cleanText.split("\n")[0] || "";
  const delimiter = firstLine.includes(";") && !firstLine.includes(",") ? ";" : ",";

  for (let i = 0; i < cleanText.length; i++) {
    const char = cleanText[i];
    const nextChar = cleanText[i + 1];

    if (char === '"') {
      if (inQuotes && nextChar === '"') {
        currentToken += '"';
        i++;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (char === delimiter && !inQuotes) {
      row.push(currentToken.trim());
      currentToken = "";
    } else if (char === "\n" && !inQuotes) {
      row.push(currentToken.trim());
      if (row.some((c) => c.length > 0)) {
        lines.push(row);
      }
      row = [];
      currentToken = "";
    } else {
      currentToken += char;
    }
  }

  if (currentToken.length > 0 || row.length > 0) {
    row.push(currentToken.trim());
    if (row.some((c) => c.length > 0)) {
      lines.push(row);
    }
  }

  return lines;
}

function parseCsvFile(file: File) {
  const reader = new FileReader();
  reader.onload = (e) => {
    try {
      const text = String(e.target?.result || "");
      const grid = parseRawCsvText(text);
      processParsedGrid(grid);
    } catch (err: any) {
      csvError.value = "Lỗi khi đọc file CSV: " + (err.message || String(err));
    }
  };
  reader.onerror = () => {
    csvError.value = "Không thể đọc file từ thiết bị của bạn.";
  };
  reader.readAsText(file, "utf-8");
}

// Helper: Normalize Date string to YYYY-MM-DD
function normalizeDate(raw?: string): string | null {
  if (!raw) return null;
  const trimmed = raw.trim();
  if (!trimmed) return null;

  // 1. Format YYYY-MM-DD
  if (/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) {
    return trimmed;
  }

  // 2. Format DD/MM/YYYY or DD-MM-YYYY
  const dmyMatch = trimmed.match(/^(\d{1,2})[\/\-](\d{1,2})[\/\-](\d{4})$/);
  if (dmyMatch) {
    const day = dmyMatch[1].padStart(2, "0");
    const month = dmyMatch[2].padStart(2, "0");
    const year = dmyMatch[3];
    return `${year}-${month}-${day}`;
  }

  // 3. Excel numeric date serial
  const num = Number(trimmed);
  if (!isNaN(num) && num > 30000 && num < 60000) {
    const d = new Date((num - 25569) * 86400 * 1000);
    if (!isNaN(d.getTime())) {
      return d.toISOString().split("T")[0];
    }
  }

  // 4. Fallback: try parsing with standard Date
  const parsed = new Date(trimmed);
  if (!isNaN(parsed.getTime())) {
    try {
      return parsed.toISOString().split("T")[0];
    } catch {
      // ignore
    }
  }

  return null;
}

function processParsedGrid(grid: string[][]) {
  if (!grid.length) {
    csvError.value = "File không có dữ liệu hợp lệ.";
    return;
  }

  // Header detection
  const firstRow = grid[0].map((c) => (c || "").trim().toLowerCase());
  let dIdx = 0,
    pIdx = 1,
    cIdx = 2,
    lvlIdx = 3,
    dlIdx = -1,
    chIdx = 4,
    lesIdx = 5,
    descIdx = 6;

  const hasHeader = firstRow.some((h) =>
    h.includes("lĩnh vực") ||
    h.includes("domain") ||
    h.includes("chương") ||
    h.includes("khóa") ||
    h.includes("hạn") ||
    h.includes("deadline") ||
    h.includes("bài") ||
    h.includes("mô tả")
  );

  let startIndex = 0;
  if (hasHeader) {
    startIndex = 1;
    firstRow.forEach((col, i) => {
      if (col.includes("lĩnh vực") || col.includes("domain")) {
        dIdx = i;
      } else if (col.includes("chương trình") || col.includes("program")) {
        pIdx = i;
      } else if (col.includes("khóa học") || col.includes("course")) {
        cIdx = i;
      } else if (col.includes("cấp độ") || col.includes("level")) {
        lvlIdx = i;
      } else if (col.includes("hạn") || col.includes("deadline") || col.includes("due")) {
        dlIdx = i;
      } else if (col.includes("chương") && !col.includes("chương trình")) {
        chIdx = i;
      } else if (col.includes("mô tả") || col.includes("description") || col.includes("desc")) {
        // Must match 'mô tả' BEFORE 'bài học' to prevent 'Mô tả bài học' being recognized as lesson title
        descIdx = i;
      } else if (col.includes("bài học") || col.includes("bài") || col.includes("lesson")) {
        lesIdx = i;
      }
    });
  } else if (grid[0].length >= 8) {
    dIdx = 0;
    pIdx = 1;
    cIdx = 2;
    lvlIdx = 3;
    dlIdx = 4;
    chIdx = 5;
    lesIdx = 6;
    descIdx = 7;
  }

  const rows: CsvRow[] = [];
  for (let i = startIndex; i < grid.length; i++) {
    const row = grid[i];
    const domain = (row[dIdx] || "").trim();
    const program = (row[pIdx] || "").trim();
    const course = (row[cIdx] || "").trim();
    const level = (row[lvlIdx] || "Cơ bản").trim();
    const rawDeadline = dlIdx >= 0 ? (row[dlIdx] || "").trim() : "";
    const deadline = normalizeDate(rawDeadline) || rawDeadline;
    const chapter = (row[chIdx] || "Chương 1: Tổng quan").trim();
    const lesson = (row[lesIdx] || "").trim();
    const description = (row[descIdx] || "").trim();

    if (domain && program && course && lesson) {
      rows.push({
        domain,
        program,
        course,
        level,
        deadline,
        chapter,
        lesson,
        description,
      });
    }
  }

  if (!rows.length) {
    csvError.value =
      "Không tìm thấy dòng dữ liệu bài học hợp lệ. Vui lòng đảm bảo các cột: Lĩnh vực, Chương trình, Khóa học, Bài học đều có dữ liệu.";
    return;
  }

  csvParsedRows.value = rows;
}

// ==========================================
// PROGRESS & EXECUTION
// ==========================================
const processing = ref(false);
const progressPercent = ref(0);
const progressStatusText = ref("");

// Helper: Normalize Level string to enum
function mapLevelValue(lvlStr?: string): "BASIC" | "ADVANCED" | "EXPERT" {
  const s = (lvlStr || "").toLowerCase();
  if (s.includes("nâng cao") || s.includes("advanced")) return "ADVANCED";
  if (s.includes("chuyên gia") || s.includes("expert")) return "EXPERT";
  return "BASIC";
}

// ==========================================
// EXECUTE TAB 1: OUTLINE CREATION
// ==========================================
async function executeOutlineCreation() {
  if (!canSubmitOutline.value || processing.value) return;

  processing.value = true;
  progressPercent.value = 5;
  progressStatusText.value = "Đang kiểm tra và khởi tạo Lĩnh vực...";

  const expandedIds = new Set<string>();
  let firstLessonId = "";

  try {
    // 1. Domain
    let domainId = selectedDomainId.value;
    if (isNewDomain.value) {
      const savedDomain = await trainingApi.saveDomain({
        name: newDomainName.value.trim(),
        status: "ACTIVE",
      });
      domainId = savedDomain.id;
    }
    expandedIds.add(domainId);
    progressPercent.value = 25;
    progressStatusText.value = "Đang khởi tạo Chương trình đào tạo...";

    // 2. Program
    let programId = selectedProgramId.value;
    if (isNewProgram.value) {
      const savedProg = await trainingApi.saveProgram({
        domainId,
        name: newProgramName.value.trim(),
        status: "ACTIVE",
      });
      programId = savedProg.id;
    }
    expandedIds.add(programId);
    progressPercent.value = 45;
    progressStatusText.value = `Đang tạo khóa học "${courseName.value}"...`;

    // 3. Course
    const savedCourse = await adminApi.createCourse({
      programId,
      name: courseName.value.trim(),
      level: courseLevel.value,
      completionDeadline: courseDeadline.value ? normalizeDate(courseDeadline.value) : null,
      iconName: "BookOpen",
      status: "DRAFT",
    } as any);
    const courseId = savedCourse.id;
    expandedIds.add(courseId);

    // 4. Chapters & Lessons
    const chaptersToCreate = parsedOutline.value;
    const totalSteps = chaptersToCreate.reduce((sum, ch) => sum + 1 + ch.lessons.length, 0);
    let stepCount = 0;

    for (let cIdx = 0; cIdx < chaptersToCreate.length; cIdx++) {
      const ch = chaptersToCreate[cIdx];
      stepCount++;
      progressPercent.value = Math.min(95, 50 + Math.round((stepCount / totalSteps) * 45));
      progressStatusText.value = `Đang tạo ${ch.title}...`;

      const savedChapter = await trainingApi.saveChapter(courseId, {
        title: ch.title,
        code: `CH${Date.now().toString(36).toUpperCase()}${cIdx + 1}`,
      });
      expandedIds.add(savedChapter.id);

      for (const les of ch.lessons) {
        stepCount++;
        progressPercent.value = Math.min(95, 50 + Math.round((stepCount / totalSteps) * 45));
        progressStatusText.value = `Đang tạo bài học: ${les.title}...`;

        const lessonId =
          typeof crypto !== "undefined" && crypto.randomUUID
            ? crypto.randomUUID()
            : Date.now().toString(36) + Math.random().toString(36).substring(2);

        const savedLesson = await adminApi.createLesson({
          id: lessonId,
          status: "DRAFT",
          title: les.title,
          hint: les.title,
          chapterId: savedChapter.id,
          courseId,
          videoUrl: "",
          videoSource: "URL",
          documents: [],
          procedureSteps: [],
        });

        if (!firstLessonId) {
          firstLessonId = savedLesson.id;
        }
      }
    }

    progressPercent.value = 100;
    progressStatusText.value = "Hoàn tất!";
    clearDraft();
    showToast(`Đã khởi tạo thành công khóa học "${courseName.value}" cùng ${chaptersToCreate.length} chương và ${totalParsedLessons.value} bài học!`, "success");

    emit("completed", {
      expandedIds: Array.from(expandedIds),
      firstLessonId,
      openLessonEditor: openFirstLessonAfterCreate.value,
    });
  } catch (err: any) {
    const msg = err.response?.data?.message || err.message || "Lỗi trong quá trình khởi tạo cấu trúc đào tạo.";
    showToast(msg, "error");
  } finally {
    processing.value = false;
  }
}

// ==========================================
// EXECUTE TAB 2: EXCEL / CSV BATCH IMPORT
// ==========================================
async function executeCsvImport() {
  if (!csvParsedRows.value.length || processing.value) return;

  processing.value = true;
  progressPercent.value = 5;
  progressStatusText.value = "Đang chuẩn bị danh mục dữ liệu...";

  const expandedIds = new Set<string>();
  let firstLessonId = "";

  try {
    // 1. Fetch fresh catalog to avoid duplicates
    const catalog = await trainingApi.catalog();
    const existingDomains = new Map<string, TrainingDomain>();
    for (const d of catalog.domains || []) {
      existingDomains.set(d.name.trim().toLowerCase(), d);
    }

    const existingPrograms = new Map<string, TrainingProgram>();
    for (const p of catalog.programs || []) {
      existingPrograms.set(`${p.domainId}::${p.name.trim().toLowerCase()}`, p);
    }

    const existingCourses = new Map<string, Course>();
    try {
      const allCourses = await trainingApi.courses();
      for (const c of allCourses || []) {
        if (c.programId) {
          existingCourses.set(`${c.programId}::${c.name.trim().toLowerCase()}`, c);
        }
      }
    } catch {
      // ignore
    }

    const existingChapters = new Map<string, any>();

    // 2. Process rows sequentially
    const totalRows = csvParsedRows.value.length;
    let rowIndex = 0;

    for (const row of csvParsedRows.value) {
      rowIndex++;
      progressPercent.value = Math.min(98, Math.round((rowIndex / totalRows) * 95));
      progressStatusText.value = `Đang xử lý ${rowIndex}/${totalRows}: ${row.lesson}...`;

      // A. Domain
      const dKey = row.domain.trim().toLowerCase();
      let domainObj = existingDomains.get(dKey);
      if (!domainObj) {
        domainObj = await trainingApi.saveDomain({
          name: row.domain.trim(),
          status: "ACTIVE",
        });
        existingDomains.set(dKey, domainObj);
      }
      expandedIds.add(domainObj.id);

      // B. Program
      const pKey = `${domainObj.id}::${row.program.trim().toLowerCase()}`;
      let programObj = existingPrograms.get(pKey);
      if (!programObj) {
        programObj = await trainingApi.saveProgram({
          domainId: domainObj.id,
          name: row.program.trim(),
          status: "ACTIVE",
        });
        existingPrograms.set(pKey, programObj);
      }
      expandedIds.add(programObj.id);

      // C. Course
      const cKey = `${programObj.id}::${row.course.trim().toLowerCase()}`;
      let courseObj = existingCourses.get(cKey);
      if (!courseObj) {
        const createdCourse = await adminApi.createCourse({
          programId: programObj.id,
          name: row.course.trim(),
          level: mapLevelValue(row.level),
          completionDeadline: row.deadline ? normalizeDate(row.deadline) : null,
          iconName: "BookOpen",
          status: "DRAFT",
        } as any);
        courseObj = createdCourse;
        existingCourses.set(cKey, createdCourse);
      } else if (!courseObj.completionDeadline && row.deadline) {
        const norm = normalizeDate(row.deadline);
        if (norm) {
          try {
            await adminApi.updateCourse(courseObj.id, {
              completionDeadline: norm,
            } as any);
            courseObj.completionDeadline = norm;
          } catch {
            // ignore
          }
        }
      }
      expandedIds.add(courseObj.id);

      // D. Chapter
      const chKey = `${courseObj.id}::${row.chapter.trim().toLowerCase()}`;
      let chapterObj = existingChapters.get(chKey);
      if (!chapterObj) {
        try {
          const chaps = await trainingApi.chapters(courseObj.id);
          for (const ch of chaps || []) {
            existingChapters.set(`${courseObj.id}::${ch.title.trim().toLowerCase()}`, ch);
          }
          chapterObj = existingChapters.get(chKey);
        } catch {
          // ignore
        }
      }

      if (!chapterObj) {
        const savedChapter = await trainingApi.saveChapter(courseObj.id, {
          title: row.chapter.trim(),
          code: `CH${Date.now().toString(36).toUpperCase()}${Math.random().toString(36).substring(2, 5)}`,
        });
        chapterObj = savedChapter;
        existingChapters.set(chKey, savedChapter);
      }
      expandedIds.add(chapterObj.id);

      // E. Lesson
      const lessonId =
        typeof crypto !== "undefined" && crypto.randomUUID
          ? crypto.randomUUID()
          : Date.now().toString(36) + Math.random().toString(36).substring(2);

      const savedLesson = await adminApi.createLesson({
        id: lessonId,
        status: "DRAFT",
        title: row.lesson.trim(),
        hint: row.description?.trim() || row.lesson.trim(),
        chapterId: chapterObj.id,
        courseId: courseObj.id,
        videoUrl: "",
        videoSource: "URL",
        documents: [],
        procedureSteps: [],
      });

      if (!firstLessonId) {
        firstLessonId = savedLesson.id;
      }
    }

    progressPercent.value = 100;
    progressStatusText.value = "Import hoàn tất!";
    showToast(
      `Import thành công ${totalRows} bài học vào cấu trúc đào tạo!`,
      "success"
    );

    emit("completed", {
      expandedIds: Array.from(expandedIds),
      firstLessonId,
      openLessonEditor: false,
    });
  } catch (err: any) {
    const msg =
      err.response?.data?.message || err.message || "Lỗi trong quá trình Import.";
    showToast(msg, "error");
  } finally {
    processing.value = false;
  }
}
</script>

<style scoped>
.quick-import-modal {
  background: var(--bg-card, #ffffff);
  border: 1px solid var(--border-color, #e2e8f0);
  border-radius: 16px;
  width: 92%;
  max-width: 860px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 45px -10px rgba(0, 0, 0, 0.4);
  overflow: hidden;
  animation: modalEnter 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes modalEnter {
  from {
    opacity: 0;
    transform: scale(0.97) translateY(8px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 1.25rem 1.5rem 1rem;
  border-bottom: 1px solid var(--border-color, #e2e8f0);
  background: var(--bg-subtle, rgba(248, 250, 252, 0.6));
}

.title-with-badge {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin-bottom: 0.35rem;
}

.badge-quick {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  padding: 0.18rem 0.58rem;
  border-radius: 20px;
  background: rgba(16, 185, 129, 0.15);
  color: var(--primary-color, #10b981);
  border: 1px solid rgba(16, 185, 129, 0.3);
}

.modal-header h3 {
  font-size: 1.22rem;
  font-weight: 700;
  margin: 0;
  color: var(--text-main, #0f172a);
}

.header-subtitle {
  font-size: 0.85rem;
  color: var(--text-muted, #64748b);
  margin: 0;
}

.close-btn {
  background: none;
  border: none;
  color: var(--text-muted, #64748b);
  cursor: pointer;
  padding: 0.4rem;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease;
}

.close-btn:hover {
  background: var(--bg-subtle, rgba(0, 0, 0, 0.05));
  color: var(--text-main, #0f172a);
}

/* WIZARD TABS */
.wizard-tabs {
  display: flex;
  gap: 0.5rem;
  padding: 0.75rem 1.5rem;
  background: var(--bg-card, #ffffff);
  border-bottom: 1px solid var(--border-color, #e2e8f0);
}

.wizard-tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.55rem 1.1rem;
  border-radius: 9px;
  border: 1px solid transparent;
  background: var(--bg-subtle, rgba(148, 163, 184, 0.1));
  color: var(--text-muted, #64748b);
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
}

.wizard-tab-btn:hover {
  background: var(--bg-subtle, rgba(148, 163, 184, 0.2));
  color: var(--text-main, #0f172a);
}

.wizard-tab-btn.active {
  background: var(--primary-color, #10b981);
  color: #ffffff;
  border-color: var(--primary-color, #10b981);
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.25);
}

/* MODAL BODY */
.modal-body-scroll {
  padding: 1.25rem 1.5rem;
  overflow-y: auto;
  flex: 1;
}

.wizard-grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.1rem;
  margin-bottom: 1.1rem;
}

@media (max-width: 680px) {
  .wizard-grid-2 {
    grid-template-columns: 1fr;
  }
}

.wizard-grid-3 {
  display: grid;
  grid-template-columns: 1.3fr 1fr 1fr;
  gap: 1.1rem;
  margin-bottom: 1.1rem;
}

@media (max-width: 768px) {
  .wizard-grid-3 {
    grid-template-columns: 1fr;
  }
}

.date-field {
  color-scheme: light dark;
}

.form-group {
  margin-bottom: 1.1rem;
}

.label-with-action {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.45rem;
}

.form-group label {
  font-size: 0.84rem;
  font-weight: 600;
  color: var(--text-main, #0f172a);
  margin-bottom: 0.35rem;
  display: block;
}

.btn-text-toggle {
  background: none;
  border: none;
  color: var(--primary-color, #10b981);
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
  text-decoration: underline;
}

.btn-text-toggle:hover {
  filter: brightness(0.9);
}

.btn-sample-fill {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  background: rgba(16, 185, 129, 0.1);
  border: 1px solid rgba(16, 185, 129, 0.3);
  color: var(--primary-color, #10b981);
  border-radius: 6px;
  padding: 0.25rem 0.55rem;
  font-size: 0.76rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-sample-fill:hover {
  background: rgba(16, 185, 129, 0.2);
}

.btn-sample-fill.is-clear {
  background: rgba(239, 68, 68, 0.1);
  border-color: rgba(239, 68, 68, 0.3);
  color: #ef4444;
}

.btn-sample-fill.is-clear:hover {
  background: rgba(239, 68, 68, 0.2);
}

.form-control {
  width: 100%;
  padding: 0.55rem 0.8rem;
  border-radius: 8px;
  border: 1px solid var(--border-color, #cbd5e1);
  background: var(--bg-card, #ffffff);
  color: var(--text-main, #0f172a);
  font-size: 0.88rem;
  box-sizing: border-box;
  transition: border-color 0.15s ease;
}

.form-control:focus {
  outline: none;
  border-color: var(--primary-color, #10b981);
  box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.15);
}

.outline-hint {
  font-size: 0.78rem;
  color: var(--text-muted, #64748b);
  margin-bottom: 0.45rem;
  line-height: 1.4;
}

.outline-textarea {
  font-family: monospace, ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas;
  font-size: 0.84rem;
  line-height: 1.5;
  resize: vertical;
}

/* OUTLINE PREVIEW */
.outline-preview-box {
  background: var(--bg-subtle, rgba(248, 250, 252, 0.8));
  border: 1px solid var(--border-color, #e2e8f0);
  border-radius: 10px;
  padding: 0.9rem 1.1rem;
  margin-bottom: 1.1rem;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.65rem;
  padding-bottom: 0.4rem;
  border-bottom: 1px dashed var(--border-color, #e2e8f0);
}

.preview-title {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--primary-color, #10b981);
}

.preview-stats {
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--text-muted, #64748b);
}

.preview-tree-list {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  max-height: 200px;
  overflow-y: auto;
}

.preview-chapter-title {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.84rem;
  color: var(--text-main, #0f172a);
}

.icon-chap {
  color: #3b82f6;
}

.preview-lesson-list {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding-left: 1.5rem;
  margin-top: 0.25rem;
}

.preview-lesson-item {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.8rem;
  color: var(--text-muted, #64748b);
}

.icon-les {
  color: #10b981;
}

.form-check-row {
  margin-top: 0.5rem;
  margin-bottom: 0.5rem;
}

.checkbox-label {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.84rem;
  color: var(--text-main, #0f172a);
  cursor: pointer;
}

/* CSV TAB STYLES */
.csv-guide-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1.25rem;
  padding: 0.95rem 1.15rem;
  border-radius: 12px;
  background: var(--bg-subtle, rgba(248, 250, 252, 0.8));
  border: 1px solid var(--border-color, #e2e8f0);
  margin-bottom: 1.1rem;
}

@media (max-width: 720px) {
  .csv-guide-card {
    flex-direction: column;
    align-items: stretch;
  }
}

.guide-title {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.88rem;
  font-weight: 700;
  color: var(--text-main, #0f172a);
  margin-bottom: 0.25rem;
}

.guide-desc {
  font-size: 0.8rem;
  color: var(--text-muted, #64748b);
  margin: 0;
  line-height: 1.45;
}

.guide-desc code {
  background: var(--bg-card, #ffffff);
  padding: 0.1rem 0.35rem;
  border-radius: 4px;
  border: 1px solid var(--border-color, #cbd5e1);
  font-size: 0.76rem;
  color: var(--primary-color, #10b981);
}

.guide-actions-btn {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  flex-shrink: 0;
}

.btn-download-template {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  padding: 0.52rem 0.95rem;
  border-radius: 8px;
  border: 1px solid var(--primary-color, #10b981);
  background: linear-gradient(135deg, #059669, #10b981);
  color: #ffffff;
  font-size: 0.82rem;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.2);
  transition: all 0.15s ease;
}

.btn-download-template:hover {
  filter: brightness(1.08);
  transform: translateY(-1px);
}

.btn-download-csv {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  padding: 0.42rem 0.85rem;
  border-radius: 8px;
  border: 1px solid var(--border-color, #cbd5e1);
  background: transparent;
  color: var(--text-muted, #64748b);
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s ease;
}

.btn-download-csv:hover {
  background: var(--bg-card, #ffffff);
  color: var(--text-main, #0f172a);
}

.csv-dropzone {
  border: 2px dashed var(--border-color, #cbd5e1);
  border-radius: 12px;
  padding: 1.75rem 1.25rem;
  text-align: center;
  cursor: pointer;
  background: var(--bg-subtle, rgba(248, 250, 252, 0.4));
  transition: all 0.18s ease;
  margin-bottom: 1.1rem;
}

.csv-dropzone:hover,
.csv-dropzone.is-dragover {
  border-color: var(--primary-color, #10b981);
  background: rgba(16, 185, 129, 0.05);
}

.dropzone-icon {
  color: var(--text-muted, #64748b);
  margin-bottom: 0.6rem;
}

.dropzone-main-text {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-main, #0f172a);
  margin-bottom: 0.25rem;
}

.browse-link {
  color: var(--primary-color, #10b981);
  text-decoration: underline;
}

.dropzone-sub-text {
  font-size: 0.78rem;
  color: var(--text-muted, #64748b);
  margin: 0;
}

.dropzone-selected {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1rem;
}

.selected-icon {
  color: var(--primary-color, #10b981);
}

.selected-file-info {
  display: flex;
  flex-direction: column;
  text-align: left;
}

.file-name {
  font-weight: 700;
  font-size: 0.9rem;
  color: var(--text-main, #0f172a);
}

.file-size {
  font-size: 0.78rem;
  color: var(--text-muted, #64748b);
}

.btn-remove-file {
  background: var(--bg-subtle, rgba(0, 0, 0, 0.1));
  border: none;
  border-radius: 50%;
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--text-muted, #64748b);
}

.btn-remove-file:hover {
  background: rgba(239, 68, 68, 0.2);
  color: #ef4444;
}

.alert-box {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  border-radius: 8px;
  font-size: 0.84rem;
  margin-bottom: 1.1rem;
}

.alert-error {
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.3);
  color: #ef4444;
}

.preview-metrics {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 0.6rem;
  margin-bottom: 0.9rem;
}

.metric-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0.6rem 0.4rem;
  background: var(--bg-subtle, rgba(248, 250, 252, 0.8));
  border: 1px solid var(--border-color, #e2e8f0);
  border-radius: 8px;
}

.metric-card.highlight {
  background: rgba(16, 185, 129, 0.12);
  border-color: rgba(16, 185, 129, 0.35);
}

.metric-num {
  font-size: 1.15rem;
  font-weight: 800;
  color: var(--text-main, #0f172a);
}

.metric-card.highlight .metric-num {
  color: var(--primary-color, #10b981);
}

.metric-label {
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--text-muted, #64748b);
  text-transform: uppercase;
}

.csv-table-wrapper {
  max-height: 230px;
  overflow-y: auto;
  overflow-x: auto;
  border: 1px solid var(--border-color, #e2e8f0);
  border-radius: 8px;
}

.csv-table {
  min-width: 100%;
  width: max-content;
  border-collapse: collapse;
  font-size: 0.8rem;
  table-layout: fixed;
}

.csv-table th {
  background: var(--bg-subtle, #f8fafc);
  color: var(--text-muted, #64748b);
  font-weight: 700;
  text-align: left;
  padding: 0.45rem 0.65rem;
  border-bottom: 1px solid var(--border-color, #e2e8f0);
  white-space: nowrap;
  position: relative;
  user-select: none;
}

.th-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.resize-handle {
  display: inline-block;
  width: 6px;
  height: 16px;
  cursor: col-resize;
  border-right: 2px solid var(--border-color, #cbd5e1);
  margin-left: 6px;
  transition: border-color 0.15s ease;
}

.resize-handle:hover {
  border-right-color: var(--primary-color, #10b981);
}

.csv-table td {
  padding: 0.45rem 0.65rem;
  border-bottom: 1px solid var(--border-color, #e2e8f0);
  color: var(--text-main, #0f172a);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.table-more-hint {
  font-size: 0.78rem;
  color: var(--text-muted, #64748b);
  margin-top: 0.4rem;
  text-align: right;
  font-style: italic;
}

/* PROGRESS BAR */
.progress-status-container {
  margin-top: 1.25rem;
  padding: 0.9rem 1.1rem;
  background: var(--bg-subtle, rgba(248, 250, 252, 0.8));
  border: 1px solid var(--border-color, #e2e8f0);
  border-radius: 10px;
}

.progress-label-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.82rem;
  font-weight: 600;
  margin-bottom: 0.45rem;
}

.status-text {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  color: var(--text-main, #0f172a);
}

.status-percent {
  color: var(--primary-color, #10b981);
}

.progress-bar-track {
  width: 100%;
  height: 7px;
  background: rgba(148, 163, 184, 0.25);
  border-radius: 4px;
  overflow: hidden;
}

.progress-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #059669, #10b981);
  border-radius: 4px;
  transition: width 0.2s ease;
}

.spin-icon {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* MODAL FOOTER */
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  padding: 1rem 1.5rem;
  border-top: 1px solid var(--border-color, #e2e8f0);
  background: var(--bg-subtle, rgba(248, 250, 252, 0.6));
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  padding: 0.55rem 1.1rem;
  border-radius: 9px;
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.15s ease;
}

.btn-secondary {
  background: var(--bg-card, #ffffff);
  border-color: var(--border-color, #cbd5e1);
  color: var(--text-main, #0f172a);
}

.btn-secondary:hover:not(:disabled) {
  background: var(--bg-subtle, rgba(148, 163, 184, 0.15));
}

.btn-submit-wizard {
  background: linear-gradient(135deg, #059669, #10b981);
  color: #ffffff;
  border: none;
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.25);
}

.btn-submit-wizard:hover:not(:disabled) {
  filter: brightness(1.08);
  transform: translateY(-1px);
}

.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  transform: none !important;
}
</style>
