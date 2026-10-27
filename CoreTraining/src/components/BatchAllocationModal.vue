<template>
  <div v-if="show" class="batch-modal-overlay" @click.self="handleClose">
    <div class="batch-modal-container" role="dialog" aria-modal="true">
      <!-- HEADER -->
      <header class="batch-modal-header">
        <div class="header-left">
          <div class="header-icon-wrap">
            <Building2 :size="20" class="header-icon" />
          </div>
          <div class="header-titles">
            <div class="title-row">
              <h3>Phân bổ Đào tạo</h3>
              <span class="target-unit-pill">
                {{ unit?.name || "Đơn vị được chọn" }}
              </span>
              <span class="unit-type-badge" :class="unit?.type === 'DEPARTMENT' ? 'dept' : 'branch'">
                {{ unit?.type === "DEPARTMENT" ? "Phòng ban" : "Chi nhánh / Hội sở" }}
              </span>
            </div>
            <p class="header-desc">
              Tích chọn hỗn hợp Lĩnh vực, Chương trình và Khóa học lẻ. Nhấp nút hình thức ở cuối hàng để chuyển Bắt buộc / Tự chọn.
            </p>
          </div>
        </div>
        <button type="button" class="btn-close-modal" @click="handleClose" title="Đóng modal">
          <X :size="18" />
        </button>
      </header>

      <!-- TOOLBAR: SEARCH & EXPAND -->
      <div class="batch-toolbar">
        <div class="search-wrap">
          <Search :size="15" class="search-icon" />
          <input
            type="text"
            v-model="searchQuery"
            placeholder="Tìm nhanh Lĩnh vực, Chương trình, Khóa học theo tên hoặc mã..."
            class="search-input"
          />
          <button v-if="searchQuery" type="button" class="btn-clear-search" @click="searchQuery = ''">
            <X :size="14" />
          </button>
        </div>
        <div class="toolbar-actions">
          <button type="button" class="btn btn-ghost btn-sm" @click="expandAll">
            <ChevronDown :size="14" />
            <span>Mở rộng tất cả</span>
          </button>
          <button type="button" class="btn btn-ghost btn-sm" @click="collapseAll">
            <ChevronRight :size="14" />
            <span>Thu gọn</span>
          </button>
        </div>
      </div>

      <!-- MAIN BODY: FULL-WIDTH INTERACTIVE TREE -->
      <div class="batch-modal-body">
        <div v-if="loadingTree" class="loading-state">
          <Loader2 :size="28" class="spin-icon" />
          <p>Đang tải cấu trúc dữ liệu đào tạo...</p>
        </div>

        <div v-else-if="filteredDomains.length === 0" class="empty-tree-state">
          <AlertCircle :size="28" class="empty-icon" />
          <p>Không tìm thấy Lĩnh vực, Chương trình hay Khóa học nào khớp với từ khóa "{{ searchQuery }}"</p>
        </div>

        <div v-else class="tree-container">
          <!-- DOMAIN NODE (LEVEL 1) -->
          <div
            v-for="d in filteredDomains"
            :key="d.id"
            class="tree-node domain-node"
            :class="{ 'is-selected': isItemSelected('domain', d.id) }"
          >
            <div class="node-row domain-row" @click="toggleDomainExpand(d.id)">
              <div class="node-left">
                <!-- Checkbox -->
                <button
                  type="button"
                  class="custom-check-btn"
                  @click.stop="toggleItemCheck('domain', d.id, d.name, d.code)"
                  :title="isItemSelected('domain', d.id) ? 'Bỏ chọn Lĩnh vực' : 'Chọn Lĩnh vực'"
                >
                  <div class="check-box" :class="{ checked: isItemSelected('domain', d.id) }">
                    <Check v-if="isItemSelected('domain', d.id)" :size="12" />
                  </div>
                </button>

                <!-- Expand/Collapse toggle -->
                <button type="button" class="btn-expand" @click.stop="toggleDomainExpand(d.id)">
                  <ChevronDown v-if="expandedDomainIds.has(d.id)" :size="16" />
                  <ChevronRight v-else :size="16" />
                </button>

                <!-- Icon & Label -->
                <Globe :size="16" class="node-icon domain-icon" />
                <span class="node-name">{{ d.name }}</span>
                <span class="node-count">({{ getProgramsByDomain(d.id).length }} chương trình)</span>
              </div>

              <!-- RIGHT ACTION: GRANT TYPE TOGGLE BUTTON (When Selected) -->
              <div class="node-right" @click.stop>
                <div v-if="isItemSelected('domain', d.id)" class="grant-toggle-wrapper">
                  <button
                    type="button"
                    class="btn-grant-toggle"
                    :class="getItemGrant('domain', d.id) === 'MANDATORY' ? 'grant-mandatory' : 'grant-optional'"
                    @click="toggleGrantType('domain', d.id)"
                    title="Bấm để đổi hình thức (áp dụng cho tất cả CT & Khóa đã chọn thuộc Lĩnh vực này)"
                  >
                    <span class="dot"></span>
                    <span>{{ getItemGrant("domain", d.id) === "MANDATORY" ? "Bắt buộc" : "Tự chọn" }}</span>
                  </button>
                </div>
              </div>
            </div>

            <!-- PROGRAMS LIST (LEVEL 2) -->
            <div v-show="expandedDomainIds.has(d.id)" class="children-container program-children">
              <div
                v-for="p in getProgramsByDomain(d.id)"
                :key="p.id"
                class="tree-node program-node"
                :class="{ 'is-selected': isItemSelected('program', p.id) }"
              >
                <div class="node-row program-row" @click="toggleProgramExpand(p.id)">
                  <div class="node-left">
                    <!-- Checkbox -->
                    <button
                      type="button"
                      class="custom-check-btn"
                      @click.stop="toggleItemCheck('program', p.id, p.name, p.code, d.name)"
                      :title="isItemSelected('program', p.id) ? 'Bỏ chọn Chương trình' : 'Chọn Chương trình'"
                    >
                      <div class="check-box" :class="{ checked: isItemSelected('program', p.id) }">
                        <Check v-if="isItemSelected('program', p.id)" :size="12" />
                      </div>
                    </button>

                    <!-- Expand/Collapse toggle -->
                    <button type="button" class="btn-expand" @click.stop="toggleProgramExpand(p.id)">
                      <ChevronDown v-if="expandedProgramIds.has(p.id)" :size="15" />
                      <ChevronRight v-else :size="15" />
                    </button>

                    <!-- Icon & Label -->
                    <Folder :size="15" class="node-icon program-icon" />
                    <span class="node-name">{{ p.name }}</span>
                    <span class="node-count">({{ getCoursesByProgram(p.id).length }} khóa học)</span>
                  </div>

                  <!-- RIGHT ACTION: GRANT TYPE TOGGLE BUTTON (When Selected) -->
                  <div class="node-right" @click.stop>
                    <div v-if="isItemSelected('program', p.id)" class="grant-toggle-wrapper">
                      <button
                        type="button"
                        class="btn-grant-toggle"
                        :class="getItemGrant('program', p.id) === 'MANDATORY' ? 'grant-mandatory' : 'grant-optional'"
                        @click="toggleGrantType('program', p.id)"
                        title="Bấm để đổi hình thức (áp dụng cho tất cả Khóa con đã chọn của CT này)"
                      >
                        <span class="dot"></span>
                        <span>{{ getItemGrant("program", p.id) === "MANDATORY" ? "Bắt buộc" : "Tự chọn" }}</span>
                      </button>
                    </div>
                  </div>
                </div>

                <!-- COURSES LIST (LEVEL 3) -->
                <div v-show="expandedProgramIds.has(p.id)" class="children-container course-children">
                  <div
                    v-for="c in getCoursesByProgram(p.id)"
                    :key="c.id"
                    class="tree-node course-node"
                    :class="{ 'is-selected': isItemSelected('course', c.id) }"
                  >
                    <div class="node-row course-row">
                      <div class="node-left">
                        <!-- Checkbox -->
                        <button
                          type="button"
                          class="custom-check-btn"
                          @click.stop="toggleItemCheck('course', c.id, c.name, c.code, p.name)"
                          :title="isItemSelected('course', c.id) ? 'Bỏ chọn Khóa học' : 'Chọn Khóa học'"
                        >
                          <div class="check-box" :class="{ checked: isItemSelected('course', c.id) }">
                            <Check v-if="isItemSelected('course', c.id)" :size="12" />
                          </div>
                        </button>

                        <BookOpen :size="14" class="node-icon course-icon" />
                        <span class="node-name">{{ c.name }}</span>
                      </div>

                      <!-- RIGHT ACTION: GRANT TYPE TOGGLE BUTTON (When Selected) -->
                      <div class="node-right">
                        <div v-if="isItemSelected('course', c.id)" class="grant-toggle-wrapper">
                          <button
                            type="button"
                            class="btn-grant-toggle"
                            :class="getItemGrant('course', c.id) === 'MANDATORY' ? 'grant-mandatory' : 'grant-optional'"
                            @click="toggleGrantType('course', c.id)"
                            title="Bấm để chuyển giữa Bắt buộc và Tự chọn"
                          >
                            <span class="dot"></span>
                            <span>{{ getItemGrant("course", c.id) === "MANDATORY" ? "Bắt buộc" : "Tự chọn" }}</span>
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div v-if="getCoursesByProgram(p.id).length === 0" class="empty-child-hint">
                    Chưa có khóa học nào trong chương trình này.
                  </div>
                </div>
              </div>

              <div v-if="getProgramsByDomain(d.id).length === 0" class="empty-child-hint">
                Chưa có chương trình nào trong lĩnh vực này.
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- FLOATING ACTION BAR AT MODAL BOTTOM -->
      <footer class="batch-modal-footer">
        <div class="footer-summary">
          <div class="summary-badge">
            <span class="badge-icon">🛒</span>
            <span class="summary-text">
              Đã chọn: <strong>{{ totalSelectedCount }}</strong> mục
              <span v-if="totalSelectedCount > 0" class="summary-breakdown">
                ({{ selectedDomainsCount }} Lĩnh vực, {{ selectedProgramsCount }} Chương trình, {{ selectedCoursesCount }} Khóa học)
              </span>
            </span>
          </div>

          <!-- Quick Bulk Actions -->
          <div v-if="totalSelectedCount > 0" class="bulk-actions">
            <span class="bulk-label">Đổi nhanh tất cả:</span>
            <button
              type="button"
              class="btn-bulk-toggle bulk-mandatory"
              @click="setAllGrantType('MANDATORY')"
              title="Đổi tất cả mục đã chọn thành Bắt buộc"
            >
              Bắt buộc
            </button>
            <button
              type="button"
              class="btn-bulk-toggle bulk-optional"
              @click="setAllGrantType('OPTIONAL')"
              title="Đổi tất cả mục đã chọn thành Tự chọn"
            >
              Tự chọn
            </button>
            <button
              type="button"
              class="btn-clear-selection"
              @click="clearAllSelection"
              title="Bỏ chọn tất cả"
            >
              Bỏ chọn hết
            </button>
          </div>
        </div>

        <div class="footer-buttons">
          <button type="button" class="btn btn-secondary" @click="handleClose" :disabled="submitting">
            Hủy
          </button>
          <button
            type="button"
            class="btn btn-primary btn-submit-batch"
            :disabled="totalSelectedCount === 0 || submitting"
            @click="handleSubmitBatch"
          >
            <Loader2 v-if="submitting" :size="15" class="spin-icon" />
            <span>{{ submitting ? "Đang lưu phân bổ..." : `Đăng ký phân bổ (${totalSelectedCount} mục)` }}</span>
          </button>
        </div>
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from "vue";
import {
  Search,
  X,
  Globe,
  Folder,
  BookOpen,
  ChevronRight,
  ChevronDown,
  Check,
  Loader2,
  Building2,
  AlertCircle,
} from "lucide-vue-next";
import { axiosClient } from "../api/axiosClient";
import { trainingApi } from "../api/trainingApi";
import { showToast } from "../utils/toast";
import type { TrainingDomain, TrainingProgram, Course } from "../types";
import type { OrganizationUnit } from "../types/organization";

interface SelectedItem {
  type: "domain" | "program" | "course";
  id: string;
  name: string;
  code?: string;
  parentName?: string;
  grantType: "MANDATORY" | "OPTIONAL";
}

const props = defineProps<{
  show: boolean;
  unit: OrganizationUnit | null;
}>();

const emit = defineEmits<{
  (e: "close"): void;
  (e: "saved"): void;
}>();

// State
const loadingTree = ref(false);
const submitting = ref(false);
const searchQuery = ref("");

const allDomains = ref<TrainingDomain[]>([]);
const allPrograms = ref<TrainingProgram[]>([]);
const allCourses = ref<Course[]>([]);

const expandedDomainIds = ref<Set<string>>(new Set());
const expandedProgramIds = ref<Set<string>>(new Set());

// Map of selected items: key = `${type}_${id}`
const selectedMap = ref<Map<string, SelectedItem>>(new Map());

// Load tree data whenever modal opens
watch(
  () => props.show,
  async (newVal) => {
    if (newVal) {
      selectedMap.value.clear();
      searchQuery.value = "";
      await loadTreeData();
    }
  }
);

async function loadTreeData() {
  loadingTree.value = true;
  try {
    const [catalog, coursesList] = await Promise.all([
      trainingApi.catalog(),
      trainingApi.courses(),
    ]);

    allDomains.value = (catalog.domains || []).filter((d) => d.status !== "DELETED");
    allPrograms.value = (catalog.programs || []).filter((p) => p.status !== "DELETED");
    allCourses.value = (coursesList || []).filter((c) => c.status !== "DELETED");

    // Expand all domains by default
    expandedDomainIds.value = new Set(allDomains.value.map((d) => d.id));
    expandedProgramIds.value = new Set();
  } catch (err) {
    console.error("Lỗi tải cây dữ liệu đào tạo:", err);
    showToast("Không thể tải danh mục đào tạo", "error");
  } finally {
    loadingTree.value = false;
  }
}

// Hierarchy Helpers
function getProgramsByDomain(domainId: string): TrainingProgram[] {
  return allPrograms.value.filter((p) => p.domainId === domainId);
}

function getCoursesByProgram(programId: string): Course[] {
  return allCourses.value.filter((c) => c.programId === programId);
}

// Tree Filtering with Search Query
const filteredDomains = computed(() => {
  if (!searchQuery.value.trim()) return allDomains.value;
  const q = searchQuery.value.trim().toLowerCase();

  return allDomains.value.filter((d) => {
    const matchDomain =
      d.name.toLowerCase().includes(q) ||
      (d.code && d.code.toLowerCase().includes(q));

    const domainPrograms = getProgramsByDomain(d.id);
    const matchProgram = domainPrograms.some(
      (p) =>
        p.name.toLowerCase().includes(q) ||
        (p.code && p.code.toLowerCase().includes(q))
    );

    const matchCourse = domainPrograms.some((p) =>
      getCoursesByProgram(p.id).some(
        (c) =>
          c.name.toLowerCase().includes(q) ||
          (c.code && c.code.toLowerCase().includes(q))
      )
    );

    return matchDomain || matchProgram || matchCourse;
  });
});

// Expand / Collapse
function expandAll() {
  expandedDomainIds.value = new Set(allDomains.value.map((d) => d.id));
  expandedProgramIds.value = new Set(allPrograms.value.map((p) => p.id));
}

function collapseAll() {
  expandedDomainIds.value.clear();
  expandedProgramIds.value.clear();
}

function toggleDomainExpand(id: string) {
  if (expandedDomainIds.value.has(id)) {
    expandedDomainIds.value.delete(id);
  } else {
    expandedDomainIds.value.add(id);
  }
}

function toggleProgramExpand(id: string) {
  if (expandedProgramIds.value.has(id)) {
    expandedProgramIds.value.delete(id);
  } else {
    expandedProgramIds.value.add(id);
  }
}

// Selection Logic
function itemKey(type: "domain" | "program" | "course", id: string): string {
  return `${type}_${id}`;
}

function isItemSelected(type: "domain" | "program" | "course", id: string): boolean {
  return selectedMap.value.has(itemKey(type, id));
}

function getItemGrant(type: "domain" | "program" | "course", id: string): "MANDATORY" | "OPTIONAL" {
  const item = selectedMap.value.get(itemKey(type, id));
  return item ? item.grantType : "MANDATORY";
}

function toggleItemCheck(
  type: "domain" | "program" | "course",
  id: string,
  name: string,
  code?: string,
  parentName?: string
) {
  const key = itemKey(type, id);
  const isSelected = selectedMap.value.has(key);

  if (isSelected) {
    // Uncheck
    selectedMap.value.delete(key);

    // If domain unchecked, uncheck child programs & courses
    if (type === "domain") {
      const childPrograms = getProgramsByDomain(id);
      for (const p of childPrograms) {
        selectedMap.value.delete(itemKey("program", p.id));
        const childCourses = getCoursesByProgram(p.id);
        for (const c of childCourses) {
          selectedMap.value.delete(itemKey("course", c.id));
        }
      }
    } else if (type === "program") {
      const childCourses = getCoursesByProgram(id);
      for (const c of childCourses) {
        selectedMap.value.delete(itemKey("course", c.id));
      }
    }
  } else {
    // Check with default MANDATORY
    selectedMap.value.set(key, {
      type,
      id,
      name,
      code,
      parentName,
      grantType: "MANDATORY",
    });

    // If domain checked, also auto-check its child programs and courses with MANDATORY
    if (type === "domain") {
      const childPrograms = getProgramsByDomain(id);
      for (const p of childPrograms) {
        selectedMap.value.set(itemKey("program", p.id), {
          type: "program",
          id: p.id,
          name: p.name,
          code: p.code,
          parentName: name,
          grantType: "MANDATORY",
        });
        const childCourses = getCoursesByProgram(p.id);
        for (const c of childCourses) {
          selectedMap.value.set(itemKey("course", c.id), {
            type: "course",
            id: c.id,
            name: c.name,
            code: c.code,
            parentName: p.name,
            grantType: "MANDATORY",
          });
        }
      }
    } else if (type === "program") {
      const childCourses = getCoursesByProgram(id);
      for (const c of childCourses) {
        selectedMap.value.set(itemKey("course", c.id), {
          type: "course",
          id: c.id,
          name: c.name,
          code: c.code,
          parentName: name,
          grantType: "MANDATORY",
        });
      }
    }
  }

  // Trigger Vue reactivity
  selectedMap.value = new Map(selectedMap.value);
}

// Toggle Grant Type with Child Cascade
function toggleGrantType(type: "domain" | "program" | "course", id: string) {
  const key = itemKey(type, id);
  const item = selectedMap.value.get(key);
  if (!item) return;

  const nextGrant = item.grantType === "MANDATORY" ? "OPTIONAL" : "MANDATORY";
  item.grantType = nextGrant;

  // Cascade to currently selected children
  if (type === "domain") {
    const childPrograms = getProgramsByDomain(id);
    for (const p of childPrograms) {
      const pItem = selectedMap.value.get(itemKey("program", p.id));
      if (pItem) pItem.grantType = nextGrant;

      const childCourses = getCoursesByProgram(p.id);
      for (const c of childCourses) {
        const cItem = selectedMap.value.get(itemKey("course", c.id));
        if (cItem) cItem.grantType = nextGrant;
      }
    }
  } else if (type === "program") {
    const childCourses = getCoursesByProgram(id);
    for (const c of childCourses) {
      const cItem = selectedMap.value.get(itemKey("course", c.id));
      if (cItem) cItem.grantType = nextGrant;
    }
  }

  selectedMap.value = new Map(selectedMap.value);
}

// Bulk Grant Setting
function setAllGrantType(grant: "MANDATORY" | "OPTIONAL") {
  for (const item of selectedMap.value.values()) {
    item.grantType = grant;
  }
  selectedMap.value = new Map(selectedMap.value);
}

function clearAllSelection() {
  selectedMap.value.clear();
  selectedMap.value = new Map(selectedMap.value);
}

// Counters
const totalSelectedCount = computed(() => selectedMap.value.size);

const selectedDomainsCount = computed(
  () => Array.from(selectedMap.value.values()).filter((i) => i.type === "domain").length
);

const selectedProgramsCount = computed(
  () => Array.from(selectedMap.value.values()).filter((i) => i.type === "program").length
);

const selectedCoursesCount = computed(
  () => Array.from(selectedMap.value.values()).filter((i) => i.type === "course").length
);

// Submit Batch Allocation
async function handleSubmitBatch() {
  if (!props.unit?.id) {
    showToast("Vui lòng chọn đơn vị áp dụng", "warning");
    return;
  }

  if (totalSelectedCount.value === 0) {
    showToast("Vui lòng chọn ít nhất 1 nội dung để phân bổ", "warning");
    return;
  }

  submitting.value = true;
  try {
    const items = Array.from(selectedMap.value.values()).map((item) => ({
      categoryType: item.type,
      targetId: item.id,
      grantType: item.grantType,
    }));

    const payload = {
      unitId: props.unit.id,
      items,
    };

    await axiosClient.post("/api/admin/unit-allocations/batch-assign", payload);

    showToast(
      `Phân bổ thành công ${totalSelectedCount.value} nội dung cho ${props.unit.name}!`,
      "success"
    );
    emit("saved");
    emit("close");
  } catch (err: any) {
    console.error("Lỗi khi gửi phân bổ batch:", err);
    const msg = err.response?.data?.message || err.message || "Lỗi khi phân bổ đào tạo";
    showToast(msg, "error");
  } finally {
    submitting.value = false;
  }
}

function handleClose() {
  if (!submitting.value) {
    emit("close");
  }
}
</script>

<style scoped>
.batch-modal-overlay {
  position: fixed;
  inset: 0;
  background-color: rgba(15, 23, 42, 0.65);
  backdrop-filter: blur(4px);
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.5rem;
}

.batch-modal-container {
  background: var(--bg-surface, #ffffff);
  border-radius: 12px;
  width: 100%;
  max-width: 1050px;
  height: 88vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
  border: 1px solid var(--border-color, #e2e8f0);
  overflow: hidden;
}

/* HEADER */
.batch-modal-header {
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid var(--border-color, #e2e8f0);
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--bg-surface-elevated, #f8fafc);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.header-icon-wrap {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  background: rgba(13, 148, 136, 0.12);
  color: #0d9488;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.title-row h3 {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--text-primary, #0f172a);
}

.target-unit-pill {
  font-size: 0.82rem;
  font-weight: 600;
  background: #005a54;
  color: #ffffff;
  padding: 0.2rem 0.6rem;
  border-radius: 6px;
}

.unit-type-badge {
  font-size: 0.72rem;
  font-weight: 500;
  padding: 0.15rem 0.5rem;
  border-radius: 4px;
}

.unit-type-badge.dept {
  background: #e0f2fe;
  color: #0369a1;
}

.unit-type-badge.branch {
  background: #fef3c7;
  color: #b45309;
}

.header-desc {
  margin: 0.25rem 0 0 0;
  font-size: 0.8rem;
  color: var(--text-secondary, #64748b);
}

.btn-close-modal {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: 1px solid var(--border-color, #e2e8f0);
  background: transparent;
  color: var(--text-secondary, #64748b);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-close-modal:hover {
  background: #f1f5f9;
  color: #0f172a;
}

/* TOOLBAR */
.batch-toolbar {
  padding: 0.75rem 1.5rem;
  border-bottom: 1px solid var(--border-color, #e2e8f0);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  background: var(--bg-surface, #ffffff);
}

.search-wrap {
  position: relative;
  flex: 1;
  max-width: 500px;
}

.search-icon {
  position: absolute;
  left: 0.75rem;
  top: 50%;
  transform: translateY(-50%);
  color: #94a3b8;
}

.search-input {
  width: 100%;
  padding: 0.45rem 2rem 0.45rem 2.2rem;
  border-radius: 8px;
  border: 1px solid var(--border-color, #cbd5e1);
  font-size: 0.85rem;
  outline: none;
  background: var(--bg-surface-elevated, #f8fafc);
  transition: border-color 0.15s ease;
}

.search-input:focus {
  border-color: #005a54;
  background: #ffffff;
}

.btn-clear-search {
  position: absolute;
  right: 0.6rem;
  top: 50%;
  transform: translateY(-50%);
  border: none;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
}

.toolbar-actions {
  display: flex;
  gap: 0.5rem;
}

.btn-ghost {
  border: 1px solid var(--border-color, #e2e8f0);
  background: #f8fafc;
  color: #475569;
  padding: 0.35rem 0.65rem;
  font-size: 0.8rem;
  font-weight: 500;
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 0.35rem;
  cursor: pointer;
  transition: all 0.15s;
}

.btn-ghost:hover {
  background: #e2e8f0;
  color: #0f172a;
}

/* TREE BODY */
.batch-modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 1rem 1.5rem;
  background: var(--bg-surface, #ffffff);
}

.loading-state,
.empty-tree-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3rem 1rem;
  color: var(--text-secondary, #64748b);
  gap: 0.75rem;
}

.tree-container {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

/* NODE ROWS */
.tree-node {
  border-radius: 8px;
  transition: background 0.15s ease;
}

.node-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.5rem 0.75rem;
  border-radius: 6px;
  cursor: pointer;
  user-select: none;
  transition: all 0.15s ease;
}

.domain-row {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.domain-row:hover {
  background: #f1f5f9;
}

.program-row {
  background: #fafafa;
  border-left: 2px solid #cbd5e1;
}

.program-row:hover {
  background: #f4f4f5;
}

.course-row {
  background: #ffffff;
  border-bottom: 1px solid #f1f5f9;
}

.course-row:hover {
  background: #f8fafc;
}

.tree-node.is-selected > .domain-row {
  background: rgba(0, 90, 84, 0.08);
  border-color: #005a54;
}

.tree-node.is-selected > .program-row {
  background: rgba(3, 105, 161, 0.08);
  border-left-color: #0369a1;
}

.tree-node.is-selected > .course-row {
  background: rgba(16, 185, 129, 0.08);
}

.node-left {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex: 1;
}

.custom-check-btn {
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
  display: flex;
  align-items: center;
}

.check-box {
  width: 18px;
  height: 18px;
  border-radius: 4px;
  border: 2px solid #cbd5e1;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  transition: all 0.15s;
}

.check-box.checked {
  background: #005a54;
  border-color: #005a54;
}

.btn-expand {
  border: none;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  display: flex;
  align-items: center;
  padding: 0.15rem;
  border-radius: 4px;
}

.btn-expand:hover {
  background: rgba(0, 0, 0, 0.05);
}

.node-icon {
  flex-shrink: 0;
}

.domain-icon {
  color: #0d9488;
}

.program-icon {
  color: #0284c7;
}

.course-icon {
  color: #10b981;
}

.node-name {
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--text-primary, #0f172a);
}

.program-node .node-name {
  font-weight: 500;
  font-size: 0.85rem;
}

.course-node .node-name {
  font-weight: 400;
  font-size: 0.82rem;
  color: #334155;
}

.node-code {
  font-size: 0.72rem;
  font-family: monospace;
  background: #e2e8f0;
  color: #475569;
  padding: 0.1rem 0.35rem;
  border-radius: 4px;
}

.node-count {
  font-size: 0.75rem;
  color: #94a3b8;
}

/* CHILDREN CONTAINERS */
.children-container {
  padding-left: 1.8rem;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin-top: 0.35rem;
}

.empty-child-hint {
  font-size: 0.78rem;
  font-style: italic;
  color: #94a3b8;
  padding: 0.4rem 0.75rem;
}

/* GRANT TYPE TOGGLE BUTTON AT END OF ROW */
.node-right {
  display: flex;
  align-items: center;
  margin-left: 1rem;
  flex-shrink: 0;
}

.btn-grant-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.22rem 0.65rem;
  border-radius: 6px;
  font-size: 0.72rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
  user-select: none;
  border: 1px solid transparent;
}

.btn-grant-toggle .dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
}

.btn-grant-toggle.grant-mandatory {
  background: #f8fafc;
  color: #334155;
  border-color: #cbd5e1;
}

.btn-grant-toggle.grant-mandatory .dot {
  background: #475569;
}

.btn-grant-toggle.grant-mandatory:hover {
  background: #f1f5f9;
  border-color: #94a3b8;
  color: #0f172a;
}

.btn-grant-toggle.grant-optional {
  background: #ffffff;
  color: #64748b;
  border-color: #e2e8f0;
}

.btn-grant-toggle.grant-optional .dot {
  background: #cbd5e1;
}

.btn-grant-toggle.grant-optional:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
  color: #334155;
}

/* FOOTER */
.batch-modal-footer {
  padding: 0.9rem 1.5rem;
  background: #ffffff;
  border-top: 1px solid var(--border-color, #e2e8f0);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  box-shadow: 0 -4px 12px rgba(0, 0, 0, 0.05);
}

.footer-summary {
  display: flex;
  align-items: center;
  gap: 1.25rem;
}

.summary-badge {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: #1e293b;
}

.badge-icon {
  font-size: 1.1rem;
}

.summary-breakdown {
  font-size: 0.78rem;
  color: #64748b;
  margin-left: 0.25rem;
}

.bulk-actions {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  border-left: 1px solid #e2e8f0;
  padding-left: 1rem;
}

.bulk-label {
  font-size: 0.75rem;
  color: #64748b;
}

.btn-bulk-toggle {
  padding: 0.2rem 0.55rem;
  border-radius: 4px;
  font-size: 0.72rem;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.15s;
}

.btn-bulk-toggle.bulk-mandatory {
  background: #f1f5f9;
  color: #334155;
  border-color: #cbd5e1;
}

.btn-bulk-toggle.bulk-mandatory:hover {
  background: #e2e8f0;
  color: #0f172a;
}

.btn-bulk-toggle.bulk-optional {
  background: #f0fdf4;
  color: #15803d;
  border-color: #bbf7d0;
}

.btn-bulk-toggle.bulk-optional:hover {
  background: #dcfce7;
}

.btn-clear-selection {
  background: transparent;
  border: none;
  font-size: 0.75rem;
  color: #94a3b8;
  cursor: pointer;
  text-decoration: underline;
  margin-left: 0.25rem;
}

.btn-clear-selection:hover {
  color: #dc2626;
}

.footer-buttons {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.btn {
  padding: 0.5rem 1rem;
  border-radius: 8px;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.btn-secondary {
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #475569;
}

.btn-secondary:hover:not(:disabled) {
  background: #f1f5f9;
  color: #0f172a;
}

.btn-primary.btn-submit-batch {
  border: none;
  background: #005a54;
  color: #ffffff;
}

.btn-primary.btn-submit-batch:hover:not(:disabled) {
  background: #004540;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.spin-icon {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
