<template>
  <div class="unit-allocation-page">
    <!-- PAGE HEADER -->
    <header class="page-header">
      <div class="header-left">
        <h2>Phân bổ Đào tạo theo Đơn vị</h2>
        <p class="subtitle">
          Quản lý phân bổ nội dung đào tạo (Lĩnh vực, Chương trình, Khóa học) cho từng Đơn vị / Chi nhánh trong hệ thống
        </p>
      </div>
      <div class="header-actions">
        <button type="button" class="btn btn-secondary" @click="loadData" :disabled="loading">
          <RotateCcw :size="14" :class="{ 'spin-icon': loading }" />
          <span>Làm mới</span>
        </button>
      </div>
    </header>

    <!-- MAIN TWO-COLUMN LAYOUT -->
    <div class="allocation-layout">
      <!-- LEFT PANEL: ALLOCATION FORM -->
      <section class="card form-panel">
        <div class="panel-header">
          <Building2 :size="18" class="panel-icon" />
          <h3>Thiết lập Phân bổ mới</h3>
        </div>

        <form @submit.prevent="handleAssignAllocation" class="allocation-form">
          <!-- BƯỚC 1: CHỌN ĐƠN VỊ -->
          <div class="form-group">
            <label for="unit-select">
              1. Đơn vị áp dụng (*):
            </label>
            <select
              id="unit-select"
              v-model="selectedUnitId"
              class="form-control select-control"
              required
              @change="handleUnitChange"
            >
              <option value="">-- Chọn Đơn vị / Chi nhánh / Khối --</option>
              <option
                v-for="u in units"
                :key="u.id"
                :value="u.id"
              >
                {{ u.name }} ({{ u.type === 'DEPARTMENT' ? 'Phòng ban' : 'Chi nhánh/Hội sở' }})
              </option>
            </select>
          </div>

          <!-- BƯỚC 2: CHỌN LOẠI DANH MỤC ĐÀO TẠO -->
          <div class="form-group">
            <label>2. Cấp danh mục đào tạo (*):</label>
            <div class="category-type-selector">
              <label class="radio-card" :class="{ active: categoryType === 'domain' }">
                <input type="radio" value="domain" v-model="categoryType" />
                <Globe :size="15" />
                <span>Lĩnh vực</span>
              </label>
              <label class="radio-card" :class="{ active: categoryType === 'program' }">
                <input type="radio" value="program" v-model="categoryType" />
                <Folder :size="15" />
                <span>Chương trình</span>
              </label>
              <label class="radio-card" :class="{ active: categoryType === 'course' }">
                <input type="radio" value="course" v-model="categoryType" />
                <BookOpen :size="15" />
                <span>Khóa học</span>
              </label>
            </div>
          </div>

          <!-- BƯỚC 3: CHỌN NỘI DUNG TƯƠNG ỨNG -->
          <!-- CASE 3A: CHỌN LĨNH VỰC -->
          <div v-if="categoryType === 'domain'" class="form-group dynamic-group">
            <label>Chọn Lĩnh vực đào tạo (*):</label>
            <select
              v-model="targetDomainId"
              class="form-control select-control"
              required
            >
              <option value="">-- Chọn lĩnh vực --</option>
              <option
                v-for="d in activeDomains"
                :key="d.id"
                :value="d.id"
              >
                [{{ d.code }}] {{ d.name }}
              </option>
            </select>
          </div>

          <!-- CASE 3B: CHỌN CHƯƠNG TRÌNH (CHỈ HIỆN CHƯƠNG TRÌNH KHI ĐÃ CHỌN LĨNH VỰC) -->
          <div v-if="categoryType === 'program'" class="form-group dynamic-group course-cascade-box">
            <!-- BƯỚC A: LĨNH VỰC CHA -->
            <div class="sub-form-group">
              <label>Lĩnh vực cha (*):</label>
              <select
                v-model="programFilterDomainId"
                class="form-control select-control"
                required
                @change="handleProgramDomainFilterChange"
              >
                <option value="">-- Chọn Lĩnh vực cha --</option>
                <option
                  v-for="d in activeDomains"
                  :key="d.id"
                  :value="d.id"
                >
                  [{{ d.code }}] {{ d.name }}
                </option>
              </select>
            </div>

            <!-- BƯỚC B: CHƯƠNG TRÌNH ĐÀO TẠO (CHỈ HIỆN KHI ĐÃ CHỌN LĨNH VỰC) -->
            <div v-if="programFilterDomainId" class="sub-form-group">
              <label>Chọn Chương trình đào tạo (*):</label>
              <select
                v-model="targetProgramId"
                class="form-control select-control"
                required
              >
                <option value="">-- Chọn chương trình đào tạo --</option>
                <option
                  v-for="p in filteredProgramsForProgram"
                  :key="p.id"
                  :value="p.id"
                >
                  [{{ p.code }}] {{ p.name }}
                </option>
              </select>
            </div>
          </div>

          <!-- CASE 3C: CHỌN KHÓA HỌC (CHỈ HIỆN CẤP TIẾP THEO KHI CẤP TRƯỚC ĐƯỢC CHỌN) -->
          <div v-if="categoryType === 'course'" class="form-group dynamic-group course-cascade-box">
            <!-- BƯỚC A: LĨNH VỰC CHA -->
            <div class="sub-form-group">
              <label>Lĩnh vực cha (*):</label>
              <select
                v-model="courseFilterDomainId"
                class="form-control select-control"
                required
                @change="handleDomainFilterChange"
              >
                <option value="">-- Chọn Lĩnh vực cha --</option>
                <option
                  v-for="d in activeDomains"
                  :key="d.id"
                  :value="d.id"
                >
                  [{{ d.code }}] {{ d.name }}
                </option>
              </select>
            </div>

            <!-- BƯỚC B: CHƯƠNG TRÌNH ĐÀO TẠO (CHỈ HIỆN KHI ĐÃ CHỌN LĨNH VỰC) -->
            <div v-if="courseFilterDomainId" class="sub-form-group">
              <label>Chương trình đào tạo (*):</label>
              <select
                v-model="targetProgramId"
                class="form-control select-control"
                required
                @change="handleProgramChange"
              >
                <option value="">-- Chọn Chương trình đào tạo --</option>
                <option
                  v-for="p in filteredProgramsForCourse"
                  :key="p.id"
                  :value="p.id"
                >
                  [{{ p.code }}] {{ p.name }}
                </option>
              </select>
            </div>

            <!-- BƯỚC C: KHÓA HỌC ÁP DỤNG (MẶC ĐỊNH ALL) -->
            <div v-if="targetProgramId" class="sub-courses-picker">
              <div class="picker-header">
                <label>Khóa học áp dụng (*):</label>
                <span class="course-count-badge">
                  Đã chọn {{ selectedSubCourseIds.length }} / {{ availableProgramCourses.length }} khóa
                </span>
              </div>

              <!-- Checkbox TẤT CẢ (ALL) - MẶC ĐỊNH CHECKED -->
              <label class="all-courses-checkbox">
                <input
                  type="checkbox"
                  v-model="selectAllCoursesInProgram"
                  @change="handleToggleAllCourses"
                />
                <span class="all-text">[ALL] Áp dụng toàn bộ khóa học trong chương trình này (Mặc định)</span>
              </label>

              <!-- DANH SÁCH KHÓA HỌC CON VỚI CHECKBOX -->
              <div class="courses-checkbox-list">
                <div v-if="!availableProgramCourses.length" class="empty-hint">
                  Chương trình này hiện chưa có khóa học nào.
                </div>
                <label
                  v-for="c in availableProgramCourses"
                  :key="c.id"
                  class="course-checkbox-item"
                >
                  <input
                    type="checkbox"
                    :value="c.id"
                    v-model="selectedSubCourseIds"
                    @change="handleSubCourseCheckChange"
                  />
                  <span class="course-code-tag">[{{ c.code }}]</span>
                  <span class="course-name">{{ c.name }}</span>
                </label>
              </div>
            </div>
          </div>

          <!-- BƯỚC 4: HÌNH THỨC ÁP DỤNG (DROPDOWN) -->
          <div class="form-group">
            <label for="grant-type-select">3. Hình thức áp dụng (*):</label>
            <select
              id="grant-type-select"
              v-model="grantType"
              class="form-control select-control"
              required
            >
              <option value="MANDATORY">Bắt buộc hoàn thành</option>
              <option value="OPTIONAL">Tự chọn / Tham khảo</option>
            </select>
          </div>

          <!-- SUBMIT ACTION -->
          <div class="form-actions">
            <button
              type="submit"
              class="btn btn-primary btn-submit"
              :disabled="saving || !selectedUnitId"
            >
              <Loader2 v-if="saving" :size="16" class="spinner" />
              <Check :size="16" v-else />
              <span>{{ saving ? "Đang lưu phân bổ..." : "Lưu Phân bổ cho Đơn vị" }}</span>
            </button>
          </div>
        </form>
      </section>

      <!-- RIGHT PANEL: CURRENT ALLOCATION MATRIX -->
      <section class="card matrix-panel">
        <div class="panel-header">
          <ListOrdered :size="18" class="panel-icon" />
          <div class="header-titles">
            <h3>Danh sách Đào tạo đã phân bổ</h3>
            <span v-if="currentUnit" class="current-unit-title">
              Đơn vị: <strong>{{ currentUnit.name }}</strong>
            </span>
          </div>
        </div>

        <div v-if="!selectedUnitId" class="empty-state select-unit-prompt">
          <Building2 :size="48" class="empty-icon" />
          <h4>Vui lòng chọn Đơn vị ở cột bên trái</h4>
          <p>Chọn một Đơn vị để xem và quản lý các nội dung đào tạo đã được phân bổ cho đơn vị đó.</p>
        </div>

        <div v-else-if="loading" class="empty-state">
          <Loader2 :size="36" class="spinner" />
          <p>Đang tải dữ liệu phân bổ...</p>
        </div>

        <div v-else-if="!assignedAllocations.length" class="empty-state">
          <Inbox :size="48" class="empty-icon" />
          <h4>Chưa có phân bổ nào cho đơn vị này</h4>
          <p>Sử dụng biểu mẫu bên trái để gán Lĩnh vực, Chương trình hoặc Khóa học cho đơn vị.</p>
        </div>

        <!-- ALLOCATION TABLE -->
        <div v-else class="allocation-table-wrapper">
          <table class="allocation-table">
            <thead>
              <tr>
                <th>Cấp bậc</th>
                <th>Mã</th>
                <th>Tên Nội dung Đào tạo</th>
                <th>Hình thức</th>
                <th>Thuộc Chương trình</th>
                <th class="actions-col">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in assignedAllocations"
                :key="`${item.type}_${item.id}`"
                :class="{ 'highlight-row': item.type === 'course' }"
              >
                <td>
                  <span class="type-pill" :class="item.type">
                    {{ item.typeLabel }}
                  </span>
                </td>
                <td class="code-col">
                  <code>{{ item.code }}</code>
                </td>
                <td class="name-col">
                  <strong>{{ item.name }}</strong>
                </td>
                <td>
                  <span class="mode-badge" :class="item.grantType.toLowerCase()">
                    {{ item.grantType === 'MANDATORY' ? 'Bắt buộc' : 'Tự chọn' }}
                  </span>
                </td>
                <td class="parent-col">
                  <span v-if="item.parentProgramName" class="parent-text">
                    {{ item.parentProgramName }}
                  </span>
                  <span v-else class="muted-dash">—</span>
                </td>
                <td class="actions-col">
                  <button
                    type="button"
                    class="btn-remove-allocation"
                    title="Gỡ bỏ phân bổ khỏi đơn vị này"
                    :disabled="deletingId === item.id"
                    @click="handleRemoveAllocation(item)"
                  >
                    <Trash2 :size="14" />
                    <span>Gỡ</span>
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from "vue";
import {
  Building2,
  Globe,
  Folder,
  BookOpen,
  RotateCcw,
  Check,
  Trash2,
  Loader2,
  Inbox,
  ListOrdered,
} from "lucide-vue-next";
import { trainingApi } from "../api/trainingApi";
import { axiosClient } from "../api/axiosClient";
import { showToast } from "../utils/toast";
import type { TrainingDomain, TrainingProgram, Course } from "../types";
import type { OrganizationUnit } from "../types/organization";

// State
const loading = ref(false);
const saving = ref(false);
const deletingId = ref("");

const units = ref<OrganizationUnit[]>([]);
const domains = ref<TrainingDomain[]>([]);
const programs = ref<TrainingProgram[]>([]);
const courses = ref<Course[]>([]);

// Form State
const selectedUnitId = ref("");
const categoryType = ref<"domain" | "program" | "course">("program");
const targetDomainId = ref("");
const targetProgramId = ref("");

watch(categoryType, () => {
  targetDomainId.value = "";
  targetProgramId.value = "";
  courseFilterDomainId.value = "";
  programFilterDomainId.value = "";
  selectedSubCourseIds.value = [];
  selectAllCoursesInProgram.value = true;
});
const courseFilterDomainId = ref("");
const programFilterDomainId = ref("");
const grantType = ref<"MANDATORY" | "OPTIONAL">("MANDATORY");

// Sub-course picker state for Course cascade
const selectAllCoursesInProgram = ref(true);
const selectedSubCourseIds = ref<string[]>([]);

// Filter active (non-deleted) items
const activeDomains = computed(() => domains.value.filter((d) => d.status !== "DELETED"));
const activePrograms = computed(() => programs.value.filter((p) => p.status !== "DELETED"));
const activeCourses = computed(() => courses.value.filter((c) => c.status !== "DELETED"));

const currentUnit = computed(() => units.value.find((u) => u.id === selectedUnitId.value));

const availableProgramCourses = computed(() => {
  if (!targetProgramId.value) return [];
  return activeCourses.value.filter((c) => c.programId === targetProgramId.value);
});

const filteredProgramsForCourse = computed(() => {
  if (!courseFilterDomainId.value) return activePrograms.value;
  return activePrograms.value.filter((p) => p.domainId === courseFilterDomainId.value);
});

function handleDomainFilterChange() {
  if (targetProgramId.value) {
    const prog = programs.value.find((p) => p.id === targetProgramId.value);
    if (prog && courseFilterDomainId.value && prog.domainId !== courseFilterDomainId.value) {
      targetProgramId.value = "";
      selectedSubCourseIds.value = [];
    }
  }
}

const filteredProgramsForProgram = computed(() => {
  if (!programFilterDomainId.value) return activePrograms.value;
  return activePrograms.value.filter((p) => p.domainId === programFilterDomainId.value);
});

function handleProgramDomainFilterChange() {
  if (targetProgramId.value) {
    const prog = programs.value.find((p) => p.id === targetProgramId.value);
    if (prog && programFilterDomainId.value && prog.domainId !== programFilterDomainId.value) {
      targetProgramId.value = "";
    }
  }
}

// Load full data
async function loadData() {
  loading.value = true;
  try {
    const [catalog, allCourses, optionsRes] = await Promise.all([
      trainingApi.catalog(),
      trainingApi.courses(),
      axiosClient.get("/api/admin/options/catalog"),
    ]);

    domains.value = catalog.domains || [];
    programs.value = catalog.programs || [];
    courses.value = allCourses || [];
    if (optionsRes.data?.units) {
      units.value = optionsRes.data.units;
    }
  } catch (err) {
    console.error("Lỗi khi tải dữ liệu phân bổ:", err);
    showToast("Không thể tải danh sách dữ liệu đào tạo", "error");
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadData();
});

function handleUnitChange() {
  // Reset targets if needed
}

function handleProgramChange() {
  selectAllCoursesInProgram.value = true;
  selectedSubCourseIds.value = availableProgramCourses.value.map((c) => c.id);
}

function handleToggleAllCourses() {
  if (selectAllCoursesInProgram.value) {
    selectedSubCourseIds.value = availableProgramCourses.value.map((c) => c.id);
  } else {
    selectedSubCourseIds.value = [];
  }
}

function handleSubCourseCheckChange() {
  selectAllCoursesInProgram.value =
    availableProgramCourses.value.length > 0 &&
    selectedSubCourseIds.value.length === availableProgramCourses.value.length;
}

// Assigned allocations for the selected unit
interface AllocationRow {
  id: string;
  type: "domain" | "program" | "course";
  typeLabel: string;
  code: string;
  name: string;
  grantType: "MANDATORY" | "OPTIONAL";
  parentProgramName?: string;
  rawObj: any;
}

const assignedAllocations = computed<AllocationRow[]>(() => {
  if (!selectedUnitId.value) return [];
  const uId = selectedUnitId.value;
  const rows: AllocationRow[] = [];

  // 1. Domains
  for (const d of activeDomains.value) {
    const isMan = d.mandatoryUnitIds?.includes(uId);
    const isOpt = d.optionalUnitIds?.includes(uId) || d.unitIds?.includes(uId);
    if (isMan || isOpt) {
      rows.push({
        id: d.id,
        type: "domain",
        typeLabel: "Lĩnh vực",
        code: d.code,
        name: d.name,
        grantType: isMan ? "MANDATORY" : "OPTIONAL",
        rawObj: d,
      });
    }
  }

  // 2. Programs
  for (const p of activePrograms.value) {
    const isMan = p.mandatoryUnitIds?.includes(uId);
    const isOpt = p.optionalUnitIds?.includes(uId) || p.unitIds?.includes(uId);
    if (isMan || isOpt) {
      rows.push({
        id: p.id,
        type: "program",
        typeLabel: "Chương trình",
        code: p.code,
        name: p.name,
        grantType: isMan ? "MANDATORY" : "OPTIONAL",
        rawObj: p,
      });
    }
  }

  // 3. Courses
  for (const c of activeCourses.value) {
    const isMan = c.mandatoryUnitIds?.includes(uId);
    const isOpt = c.optionalUnitIds?.includes(uId) || c.unitIds?.includes(uId);
    if (isMan || isOpt) {
      const prog = programs.value.find((p) => p.id === c.programId);
      rows.push({
        id: c.id,
        type: "course",
        typeLabel: "Khóa học",
        code: c.code || "",
        name: c.name,
        grantType: isMan ? "MANDATORY" : "OPTIONAL",
        parentProgramName: prog?.name,
        rawObj: c,
      });
    }
  }

  return rows;
});

// Handle assign action
async function handleAssignAllocation() {
  if (!selectedUnitId.value) {
    showToast("Vui lòng chọn Đơn vị áp dụng!", "warning");
    return;
  }

  saving.value = true;
  const uId = selectedUnitId.value;

  try {
    if (categoryType.value === "domain") {
      if (!targetDomainId.value) {
        showToast("Vui lòng chọn Lĩnh vực!", "warning");
        saving.value = false;
        return;
      }
      const domain = domains.value.find((d) => d.id === targetDomainId.value);
      if (domain) {
        const manIds = new Set(domain.mandatoryUnitIds || []);
        const optIds = new Set(domain.optionalUnitIds || domain.unitIds || []);
        if (grantType.value === "MANDATORY") {
          manIds.add(uId);
          optIds.delete(uId);
        } else {
          optIds.add(uId);
          manIds.delete(uId);
        }
        await trainingApi.saveDomain({
          id: domain.id,
          name: domain.name,
          code: domain.code,
          mandatoryUnitIds: Array.from(manIds),
          optionalUnitIds: Array.from(optIds),
        });
        showToast(`Đã phân bổ lĩnh vực [${domain.name}] cho đơn vị!`, "success");
      }
    } else if (categoryType.value === "program") {
      if (!targetProgramId.value) {
        showToast("Vui lòng chọn Chương trình đào tạo!", "warning");
        saving.value = false;
        return;
      }
      const program = programs.value.find((p) => p.id === targetProgramId.value);
      if (program) {
        const manIds = new Set(program.mandatoryUnitIds || []);
        const optIds = new Set(program.optionalUnitIds || program.unitIds || []);
        if (grantType.value === "MANDATORY") {
          manIds.add(uId);
          optIds.delete(uId);
        } else {
          optIds.add(uId);
          manIds.delete(uId);
        }
        await trainingApi.saveProgram({
          id: program.id,
          name: program.name,
          code: program.code,
          domainId: program.domainId,
          mandatoryUnitIds: Array.from(manIds),
          optionalUnitIds: Array.from(optIds),
        });
        showToast(
          `Đã phân bổ chương trình [${program.name}] cho đơn vị!`,
          "success"
        );
      }
    } else if (categoryType.value === "course") {
      if (!targetProgramId.value) {
        showToast("Vui lòng chọn Chương trình đào tạo!", "warning");
        saving.value = false;
        return;
      }
      const coursesToApply = selectAllCoursesInProgram.value
        ? availableProgramCourses.value
        : availableProgramCourses.value.filter((c) => selectedSubCourseIds.value.includes(c.id));

      if (!coursesToApply.length) {
        showToast("Vui lòng chọn ít nhất 1 khóa học để phân bổ!", "warning");
        saving.value = false;
        return;
      }

      for (const c of coursesToApply) {
        const cManIds = new Set(c.mandatoryUnitIds || []);
        const cOptIds = new Set(c.optionalUnitIds || c.unitIds || []);
        if (grantType.value === "MANDATORY") {
          cManIds.add(uId);
          cOptIds.delete(uId);
        } else {
          cOptIds.add(uId);
          cManIds.delete(uId);
        }
        await axiosClient.put(`/api/admin/courses/${c.id}`, {
          name: c.name,
          code: c.code || "",
          programId: targetProgramId.value,
          mandatoryUnitIds: Array.from(cManIds),
          optionalUnitIds: Array.from(cOptIds),
        });
      }
      showToast(
        `Đã phân bổ ${coursesToApply.length} khóa học cho đơn vị!`,
        "success"
      );
    }

    await loadData();
  } catch (err: any) {
    console.error("Lỗi khi lưu phân bổ:", err);
    showToast(err.response?.data?.message || "Lỗi khi lưu phân bổ cho đơn vị", "error");
  } finally {
    saving.value = false;
  }
}

// Handle remove allocation
async function handleRemoveAllocation(item: AllocationRow) {
  if (!confirm(`Bạn có chắc chắn muốn gỡ bỏ phân bổ [${item.name}] khỏi đơn vị này?`)) {
    return;
  }

  deletingId.value = item.id;
  const uId = selectedUnitId.value;

  try {
    if (item.type === "domain") {
      const manIds = (item.rawObj.mandatoryUnitIds || []).filter((id: string) => id !== uId);
      const optIds = (item.rawObj.optionalUnitIds || item.rawObj.unitIds || []).filter(
        (id: string) => id !== uId
      );
      await trainingApi.saveDomain({
        id: item.id,
        name: item.rawObj.name,
        code: item.rawObj.code,
        mandatoryUnitIds: manIds,
        optionalUnitIds: optIds,
      });
    } else if (item.type === "program") {
      const manIds = (item.rawObj.mandatoryUnitIds || []).filter((id: string) => id !== uId);
      const optIds = (item.rawObj.optionalUnitIds || item.rawObj.unitIds || []).filter(
        (id: string) => id !== uId
      );
      await trainingApi.saveProgram({
        id: item.id,
        name: item.rawObj.name,
        code: item.rawObj.code,
        domainId: item.rawObj.domainId,
        mandatoryUnitIds: manIds,
        optionalUnitIds: optIds,
      });
    } else if (item.type === "course") {
      const manIds = (item.rawObj.mandatoryUnitIds || []).filter((id: string) => id !== uId);
      const optIds = (item.rawObj.optionalUnitIds || item.rawObj.unitIds || []).filter(
        (id: string) => id !== uId
      );
      await axiosClient.put(`/api/admin/courses/${item.id}`, {
        name: item.rawObj.name,
        code: item.rawObj.code,
        programId: item.rawObj.programId,
        mandatoryUnitIds: manIds,
        optionalUnitIds: optIds,
      });
    }

    showToast(`Đã gỡ bỏ phân bổ [${item.name}]!`, "success");
    await loadData();
  } catch (err: any) {
    console.error("Lỗi khi gỡ phân bổ:", err);
    showToast(err.response?.data?.message || "Lỗi khi gỡ phân bổ", "error");
  } finally {
    deletingId.value = "";
  }
}
</script>

<style scoped>
.unit-allocation-page {
  padding: 1.5rem 2rem;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  max-width: 1500px;
  margin: 0 auto;
  width: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #1e293b;
  padding-bottom: 1rem;
}

.header-left h2 {
  font-size: 1.45rem;
  font-weight: 700;
  color: #f8fafc;
  margin: 0 0 0.35rem;
}

.subtitle {
  font-size: 0.86rem;
  color: #94a3b8;
  margin: 0;
}

.allocation-layout {
  display: grid;
  grid-template-columns: 460px 1fr;
  gap: 1.5rem;
  align-items: start;
}

@media (max-width: 1024px) {
  .allocation-layout {
    grid-template-columns: 1fr;
  }
}

.card {
  background: #0f172a;
  border: 1px solid #1e293b;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 1rem 1.25rem;
  border-bottom: 1px solid #1e293b;
  background: rgba(30, 41, 59, 0.4);
}

.panel-header h3 {
  font-size: 1rem;
  font-weight: 600;
  color: #f1f5f9;
  margin: 0;
}

.panel-icon {
  color: #38bdf8;
}

.header-titles {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.current-unit-title {
  font-size: 0.78rem;
  color: #94a3b8;
}

.current-unit-title strong {
  color: #38bdf8;
}

/* FORM STYLING */
.allocation-form {
  padding: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.form-group label {
  font-size: 0.82rem;
  font-weight: 600;
  color: #e2e8f0;
}

.form-control {
  width: 100%;
  background: #1e293b;
  border: 1px solid #334155;
  color: #f8fafc;
  border-radius: 6px;
  padding: 0.6rem 0.8rem;
  font-size: 0.84rem;
  outline: none;
  transition: border-color 0.15s ease;
}

.form-control:focus {
  border-color: #38bdf8;
}

.category-type-selector {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.5rem;
}

.radio-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  background: #1e293b;
  border: 1px solid #334155;
  border-radius: 6px;
  padding: 0.65rem 0.4rem;
  cursor: pointer;
  font-size: 0.8rem;
  color: #94a3b8;
  transition: all 0.15s ease;
}

.radio-card input {
  display: none;
}

.radio-card:hover {
  background: #334155;
  color: #f1f5f9;
}

.radio-card.active {
  background: rgba(56, 189, 248, 0.15);
  border-color: #38bdf8;
  color: #38bdf8;
  font-weight: 600;
}

/* Sub-courses picker inside Program */
.sub-courses-picker {
  margin-top: 0.75rem;
  background: #09101d;
  border: 1px solid #22334d;
  border-radius: 6px;
  padding: 0.85rem;
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}

.picker-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.course-count-badge {
  font-size: 0.7rem;
  background: rgba(56, 189, 248, 0.15);
  color: #38bdf8;
  padding: 0.1rem 0.4rem;
  border-radius: 4px;
}

.all-courses-checkbox {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: rgba(30, 41, 59, 0.7);
  border: 1px solid #334155;
  border-radius: 4px;
  padding: 0.5rem 0.65rem;
  cursor: pointer;
}

.all-courses-checkbox .all-text {
  font-size: 0.8rem;
  font-weight: 600;
  color: #93c5fd;
}

.courses-checkbox-list {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  max-height: 220px;
  overflow-y: auto;
  border-top: 1px dashed #334155;
  padding-top: 0.5rem;
}

.select-hint {
  font-size: 0.74rem;
  color: #94a3b8;
  margin: 0;
}

.course-checkbox-item {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.78rem;
  color: #cbd5e1;
  padding: 0.35rem 0.5rem;
  border-radius: 4px;
  background: #111c2e;
  cursor: pointer;
}

.course-checkbox-item:hover {
  background: #1e293b;
  color: #f8fafc;
}

.course-code-tag {
  font-family: monospace;
  color: #64748b;
  font-size: 0.72rem;
}

/* Grant mode picker */
.grant-mode-picker {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.6rem;
}

.mode-label {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  background: #1e293b;
  border: 1px solid #334155;
  border-radius: 6px;
  padding: 0.6rem;
  cursor: pointer;
  transition: all 0.15s ease;
}

.mode-label input {
  display: none;
}

.mode-label div {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.mode-label strong {
  font-size: 0.8rem;
  color: #e2e8f0;
}

.mode-label small {
  font-size: 0.68rem;
  color: #94a3b8;
}

.mode-label.selected {
  background: rgba(59, 130, 246, 0.15);
  border-color: #3b82f6;
}

.mode-label.selected strong {
  color: #93c5fd;
}

.form-actions {
  margin-top: 0.5rem;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  padding: 0.55rem 1rem;
  font-size: 0.82rem;
  font-weight: 500;
  border-radius: 6px;
  border: none;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-primary {
  background: #00856a;
  color: #ffffff;
}

.btn-primary:hover:not(:disabled) {
  background: #006b54;
}

.btn-submit {
  width: 100%;
  padding: 0.75rem;
  font-size: 0.88rem;
  font-weight: 600;
}

.btn-secondary {
  background: #1e293b;
  border: 1px solid #334155;
  color: #cbd5e1;
}

.btn-secondary:hover:not(:disabled) {
  background: #334155;
  color: #ffffff;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* MATRIX TABLE */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem 2rem;
  text-align: center;
  color: #64748b;
  gap: 0.6rem;
}

.empty-icon {
  color: #334155;
  margin-bottom: 0.5rem;
}

.empty-state h4 {
  font-size: 1rem;
  font-weight: 600;
  color: #cbd5e1;
  margin: 0;
}

.empty-state p {
  font-size: 0.82rem;
  color: #64748b;
  max-width: 420px;
  margin: 0;
}

.allocation-table-wrapper {
  overflow-x: auto;
}

.allocation-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.82rem;
  text-align: left;
}

.allocation-table th {
  background: #111c2e;
  color: #94a3b8;
  font-weight: 600;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #1e293b;
  white-space: nowrap;
}

.allocation-table td {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #1e293b;
  color: #e2e8f0;
}

.allocation-table tbody tr:hover {
  background: rgba(30, 41, 59, 0.4);
}

.type-pill {
  font-size: 0.7rem;
  padding: 0.15rem 0.45rem;
  border-radius: 4px;
  font-weight: 600;
}

.type-pill.domain {
  background: rgba(56, 189, 248, 0.15);
  color: #38bdf8;
  border: 1px solid rgba(56, 189, 248, 0.3);
}

.type-pill.program {
  background: rgba(168, 85, 247, 0.15);
  color: #c084fc;
  border: 1px solid rgba(168, 85, 247, 0.3);
}

.type-pill.course {
  background: rgba(34, 197, 94, 0.15);
  color: #4ade80;
  border: 1px solid rgba(34, 197, 94, 0.3);
}

.code-col code {
  font-size: 0.76rem;
  background: #1e293b;
  color: #cbd5e1;
  padding: 0.15rem 0.35rem;
  border-radius: 4px;
}

.name-col strong {
  font-weight: 500;
  color: #f8fafc;
}

.mode-badge {
  font-size: 0.72rem;
  padding: 0.15rem 0.45rem;
  border-radius: 4px;
  font-weight: 500;
}

.mode-badge.mandatory {
  background: rgba(59, 130, 246, 0.15);
  color: #93c5fd;
  border: 1px solid rgba(59, 130, 246, 0.3);
}

.mode-badge.optional {
  background: rgba(148, 163, 184, 0.15);
  color: #94a3b8;
  border: 1px solid rgba(148, 163, 184, 0.3);
}

.parent-text {
  font-size: 0.78rem;
  color: #94a3b8;
}

.muted-dash {
  color: #475569;
}

.btn-remove-allocation {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  background: rgba(239, 68, 68, 0.15);
  color: #f87171;
  border: 1px solid rgba(239, 68, 68, 0.3);
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-size: 0.72rem;
  cursor: pointer;
  transition: all 0.12s ease;
}

.btn-remove-allocation:hover:not(:disabled) {
  background: #ef4444;
  color: #ffffff;
}

.spinner {
  animation: spin 1s linear infinite;
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

.course-cascade-box {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.sub-form-group {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.sub-form-group label {
  font-size: 0.8rem;
  color: #94a3b8;
  font-weight: 500;
}

</style>
