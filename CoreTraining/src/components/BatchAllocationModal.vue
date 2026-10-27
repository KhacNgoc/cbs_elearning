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
            </div>
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
          <button
            type="button"
            class="btn btn-ghost btn-sm btn-toggle-expand"
            @click="toggleExpandAll"
            :title="isAnyExpanded ? 'Thu gọn tất cả các cấp' : 'Mở rộng tất cả các cấp'"
          >
            <ChevronUp v-if="isAnyExpanded" :size="14" />
            <ChevronDown v-else :size="14" />
            <span>{{ isAnyExpanded ? "Thu gọn tất cả" : "Mở rộng tất cả" }}</span>
          </button>
        </div>
      </div>

      <!-- MAIN BODY: FULL-WIDTH INTERACTIVE TREE -->
      <div class="batch-modal-body">
        <div v-if="loadingTree" class="loading-state">
          <Loader2 :size="28" class="spin-icon" />
          <p>Đang tải cấu trúc dữ liệu đào tạo...</p>
        </div>

        <div v-else-if="visibleTree.length === 0" class="empty-tree-state">
          <AlertCircle :size="28" class="empty-icon" />
          <p v-if="searchQuery">Không tìm thấy Lĩnh vực, Chương trình hay Khóa học nào khớp với từ khóa "{{ searchQuery }}"</p>
          <p v-else>Chưa có dữ liệu đào tạo.</p>
        </div>

        <div v-else class="tree-container">
          <!-- DOMAIN NODE (LEVEL 1) -->
          <div
            v-for="dn in pagedTree"
            :key="dn.domain.id"
            class="tree-node domain-node"
            :class="stateClass(domainState(dn.domain.id))"
          >
            <div class="node-row domain-row" @click="toggleDomainExpand(dn.domain.id)">
              <div class="node-left">
                <button
                  type="button"
                  class="custom-check-btn"
                  @click.stop="toggleDomain(dn.domain.id)"
                  :title="domainState(dn.domain.id) === 'full' ? 'Bỏ chọn Lĩnh vực' : 'Chọn toàn bộ Lĩnh vực'"
                >
                  <div class="check-box" :class="checkClass(domainState(dn.domain.id))">
                    <Check v-if="domainState(dn.domain.id) === 'full'" :size="12" />
                    <Minus v-else-if="domainState(dn.domain.id) === 'partial'" :size="12" />
                  </div>
                </button>

                <button type="button" class="btn-expand" @click.stop="toggleDomainExpand(dn.domain.id)">
                  <ChevronDown v-if="expandedDomainIds.has(dn.domain.id)" :size="16" />
                  <ChevronRight v-else :size="16" />
                </button>

                <Globe :size="16" class="node-icon domain-icon" />
                <span class="node-name">{{ dn.domain.name }}</span>
                <span class="node-count">
                  <template v-if="domainState(dn.domain.id) === 'partial'">
                    ({{ domainSelectedCourseCount(dn.domain.id) }}/{{ domainCourseTotal(dn.domain.id) }} khóa học)
                  </template>
                  <template v-else>({{ programsOf(dn.domain.id).length }} chương trình)</template>
                </span>
              </div>

              <div class="node-right" @click.stop>
                <div v-if="domainState(dn.domain.id) === 'full'" class="grant-toggle-wrapper">
                  <button
                    type="button"
                    class="btn-grant-toggle"
                    :class="grantClass(domainGrant(dn.domain.id))"
                    @click="cycleDomainGrant(dn.domain.id)"
                    title="Bấm để đổi hình thức cho toàn bộ Lĩnh vực"
                  >
                    <span class="dot"></span>
                    <span>{{ grantLabel(domainGrant(dn.domain.id)) }}</span>
                  </button>
                </div>
              </div>
            </div>

            <!-- PROGRAMS LIST (LEVEL 2) -->
            <div v-if="expandedDomainIds.has(dn.domain.id)" class="children-container program-children">
              <div
                v-for="pn in dn.programs"
                :key="pn.program.id"
                class="tree-node program-node"
                :class="stateClass(programState(pn.program.id))"
              >
                <div class="node-row program-row" @click="toggleProgramExpand(pn.program.id)">
                  <div class="node-left">
                    <button
                      type="button"
                      class="custom-check-btn"
                      @click.stop="toggleProgram(pn.program.id)"
                      :title="programState(pn.program.id) === 'full' ? 'Bỏ chọn Chương trình' : 'Chọn toàn bộ Chương trình'"
                    >
                      <div class="check-box" :class="checkClass(programState(pn.program.id))">
                        <Check v-if="programState(pn.program.id) === 'full'" :size="12" />
                        <Minus v-else-if="programState(pn.program.id) === 'partial'" :size="12" />
                      </div>
                    </button>

                    <button type="button" class="btn-expand" @click.stop="toggleProgramExpand(pn.program.id)">
                      <ChevronDown v-if="expandedProgramIds.has(pn.program.id)" :size="15" />
                      <ChevronRight v-else :size="15" />
                    </button>

                    <Folder :size="15" class="node-icon program-icon" />
                    <span class="node-name">{{ pn.program.name }}</span>
                    <span class="node-count">
                      <template v-if="programState(pn.program.id) === 'partial'">
                        ({{ programSelectedCount(pn.program.id) }}/{{ coursesOf(pn.program.id).length }} khóa học)
                      </template>
                      <template v-else>({{ coursesOf(pn.program.id).length }} khóa học)</template>
                    </span>
                  </div>

                  <div class="node-right" @click.stop>
                    <div v-if="programState(pn.program.id) === 'full'" class="grant-toggle-wrapper">
                      <button
                        type="button"
                        class="btn-grant-toggle"
                        :class="grantClass(programGrant(pn.program.id))"
                        @click="cycleProgramGrant(pn.program.id)"
                        title="Bấm để đổi hình thức cho toàn bộ Chương trình"
                      >
                        <span class="dot"></span>
                        <span>{{ grantLabel(programGrant(pn.program.id)) }}</span>
                      </button>
                    </div>
                  </div>
                </div>

                <!-- COURSES LIST (LEVEL 3) -->
                <div v-if="expandedProgramIds.has(pn.program.id)" class="children-container course-children">
                  <div
                    v-for="c in limitedCourses(pn.program.id, pn.courses)"
                    :key="c.id"
                    class="tree-node course-node"
                    :class="{ 'is-selected': courseSel.has(c.id) }"
                  >
                    <div class="node-row course-row" @click="toggleCourse(c.id)">
                      <div class="node-left">
                        <button
                          type="button"
                          class="custom-check-btn"
                          @click.stop="toggleCourse(c.id)"
                          :title="courseSel.has(c.id) ? 'Bỏ chọn Khóa học' : 'Chọn Khóa học'"
                        >
                          <div class="check-box" :class="{ checked: courseSel.has(c.id) }">
                            <Check v-if="courseSel.has(c.id)" :size="12" />
                          </div>
                        </button>

                        <BookOpen :size="14" class="node-icon course-icon" />
                        <span class="node-name">{{ c.name }}</span>
                      </div>

                      <div class="node-right" @click.stop>
                        <div v-if="courseSel.has(c.id)" class="grant-toggle-wrapper">
                          <button
                            type="button"
                            class="btn-grant-toggle"
                            :class="grantClass(courseSel.get(c.id)!)"
                            @click="toggleCourseGrant(c.id)"
                            title="Bấm để chuyển giữa Bắt buộc và Tự chọn"
                          >
                            <span class="dot"></span>
                            <span>{{ grantLabel(courseSel.get(c.id)!) }}</span>
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>

                  <button
                    v-if="pn.courses.length > courseLimit(pn.program.id)"
                    type="button"
                    class="btn-show-more"
                    @click="showMoreCourses(pn.program.id)"
                  >
                    Xem thêm {{ Math.min(COURSE_PAGE_SIZE, pn.courses.length - courseLimit(pn.program.id)) }} khóa học
                    (còn {{ pn.courses.length - courseLimit(pn.program.id) }})
                  </button>

                  <div v-if="pn.courses.length === 0" class="empty-child-hint">
                    Chưa có khóa học nào trong chương trình này.
                  </div>
                </div>
              </div>

              <div v-if="dn.programs.length === 0" class="empty-child-hint">
                Chưa có chương trình nào trong lĩnh vực này.
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- TREE PAGINATION -->
      <div v-if="!loadingTree && totalPages > 1" class="tree-pagination">
        <span class="pagination-summary">
          Lĩnh vực {{ (page - 1) * DOMAIN_PAGE_SIZE + 1 }}–{{ Math.min(page * DOMAIN_PAGE_SIZE, visibleTree.length) }}
          / {{ visibleTree.length }}
        </span>
        <div class="pagination-buttons">
          <button type="button" class="page-nav-btn" :disabled="page === 1" @click="page--">
            <ChevronLeft :size="14" />
          </button>
          <span class="page-current">{{ page }} / {{ totalPages }}</span>
          <button type="button" class="page-nav-btn" :disabled="page === totalPages" @click="page++">
            <ChevronRight :size="14" />
          </button>
        </div>
      </div>

      <!-- FOOTER -->
      <footer class="batch-modal-footer">
        <div class="footer-summary">
          <div class="select-all-switch-wrap">
            <button
              type="button"
              class="switch-btn"
              :class="{ 'is-active': hasSelection }"
              @click="toggleSelectAll"
              :title="hasSelection ? 'Bỏ chọn tất cả' : 'Chọn tất cả'"
            >
              <span class="switch-track">
                <span class="switch-thumb"></span>
              </span>
              <span class="switch-label">
                {{ hasSelection ? "Bỏ chọn tất cả" : "Chọn tất cả" }}
              </span>
            </button>
          </div>

          <div class="summary-badge">
            <span class="summary-text">
              Đã chọn: <strong>{{ selectedCourseCount }}</strong> khóa học
              <span v-if="hasSelection" class="summary-breakdown">
                (thuộc {{ touchedProgramCount }} Chương trình, {{ touchedDomainCount }} Lĩnh vực)
              </span>
            </span>
          </div>
        </div>

        <div class="footer-buttons">
          <button type="button" class="btn btn-secondary" @click="handleClose" :disabled="submitting">
            Hủy
          </button>
          <button
            type="button"
            class="btn btn-primary btn-submit-batch"
            :disabled="!hasSelection || submitting"
            @click="handleSubmitBatch"
          >
            <Loader2 v-if="submitting" :size="15" class="spin-icon" />
            <span>{{ submitting ? "Đang phân bổ..." : "Phân bổ" }}</span>
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
  ChevronLeft,
  ChevronDown,
  ChevronUp,
  Check,
  Minus,
  Loader2,
  Building2,
  AlertCircle,
} from "lucide-vue-next";
import { axiosClient } from "../api/axiosClient";
import { trainingApi } from "../api/trainingApi";
import { showToast } from "../utils/toast";
import type { TrainingDomain, TrainingProgram, Course } from "../types";
import type { OrganizationUnit } from "../types/organization";

type Grant = "MANDATORY" | "OPTIONAL";
type GrantState = Grant | "MIXED";
type NodeState = "none" | "partial" | "full";

interface ProgramNode {
  program: TrainingProgram;
  courses: Course[];
}
interface DomainNode {
  domain: TrainingDomain;
  programs: ProgramNode[];
}

const DOMAIN_PAGE_SIZE = 10;
const COURSE_PAGE_SIZE = 20;

const props = defineProps<{
  show: boolean;
  unit: OrganizationUnit | null;
}>();

const emit = defineEmits<{
  (e: "close"): void;
  (e: "saved"): void;
}>();

// ---------------- State ----------------
const loadingTree = ref(false);
const submitting = ref(false);
const searchQuery = ref("");
const page = ref(1);

const allDomains = ref<TrainingDomain[]>([]);
const allPrograms = ref<TrainingProgram[]>([]);
const allCourses = ref<Course[]>([]);

const expandedDomainIds = ref<Set<string>>(new Set());
const expandedProgramIds = ref<Set<string>>(new Set());
const courseLimits = ref<Map<string, number>>(new Map());

/**
 * Selection is stored at the leaf level:
 *  - courseSel: selected courses with their grant type.
 *  - emptyProgramSel: programs without any course (selectable as a whole).
 *  - emptyDomainSel: domains without any program (selectable as a whole).
 * Parent states (none / partial / full) are derived from their children.
 */
const courseSel = ref<Map<string, Grant>>(new Map());
const emptyProgramSel = ref<Map<string, Grant>>(new Map());
const emptyDomainSel = ref<Map<string, Grant>>(new Map());

watch(
  () => props.show,
  async (open) => {
    if (open) {
      clearAllSelection();
      searchQuery.value = "";
      page.value = 1;
      courseLimits.value = new Map();
      await loadTreeData();
    }
  }
);

async function loadTreeData() {
  loadingTree.value = true;
  try {
    const [catalog, coursesList] = await Promise.all([trainingApi.catalog(), trainingApi.courses()]);
    allDomains.value = (catalog.domains || []).filter((d) => d.status !== "DELETED");
    allPrograms.value = (catalog.programs || []).filter((p) => p.status !== "DELETED");
    allCourses.value = (coursesList || []).filter((c) => c.status !== "DELETED");

    // Domains open by default, programs collapsed (children rendered lazily)
    expandedDomainIds.value = new Set(allDomains.value.map((d) => d.id));
    expandedProgramIds.value = new Set();
  } catch (err) {
    console.error("Lỗi tải cây dữ liệu đào tạo:", err);
    showToast("Không thể tải danh mục đào tạo", "error");
  } finally {
    loadingTree.value = false;
  }
}

// ---------------- Indexes (O(1) lookup) ----------------
const programsByDomain = computed(() => {
  const map = new Map<string, TrainingProgram[]>();
  for (const p of allPrograms.value) {
    if (!p.domainId) continue;
    const list = map.get(p.domainId) ?? [];
    list.push(p);
    map.set(p.domainId, list);
  }
  return map;
});

const coursesByProgram = computed(() => {
  const map = new Map<string, Course[]>();
  for (const c of allCourses.value) {
    if (!c.programId) continue;
    const list = map.get(c.programId) ?? [];
    list.push(c);
    map.set(c.programId, list);
  }
  return map;
});

const programById = computed(() => new Map(allPrograms.value.map((p) => [p.id, p])));

function programsOf(domainId: string): TrainingProgram[] {
  return programsByDomain.value.get(domainId) ?? [];
}

function coursesOf(programId: string): Course[] {
  return coursesByProgram.value.get(programId) ?? [];
}

// ---------------- Search-filtered tree ----------------
function matches(q: string, name?: string, code?: string) {
  return (name ?? "").toLowerCase().includes(q) || (code ?? "").toLowerCase().includes(q);
}

const visibleTree = computed<DomainNode[]>(() => {
  const q = searchQuery.value.trim().toLowerCase();
  const result: DomainNode[] = [];
  for (const d of allDomains.value) {
    const programs = programsOf(d.id);
    if (!q || matches(q, d.name, d.code)) {
      result.push({ domain: d, programs: programs.map((p) => ({ program: p, courses: coursesOf(p.id) })) });
      continue;
    }
    const pNodes: ProgramNode[] = [];
    for (const p of programs) {
      const courses = coursesOf(p.id);
      if (matches(q, p.name, p.code)) {
        pNodes.push({ program: p, courses });
      } else {
        const hit = courses.filter((c) => matches(q, c.name, c.code));
        if (hit.length) pNodes.push({ program: p, courses: hit });
      }
    }
    if (pNodes.length) result.push({ domain: d, programs: pNodes });
  }
  return result;
});

const totalPages = computed(() => Math.max(1, Math.ceil(visibleTree.value.length / DOMAIN_PAGE_SIZE)));

const pagedTree = computed(() => {
  const start = (page.value - 1) * DOMAIN_PAGE_SIZE;
  return visibleTree.value.slice(start, start + DOMAIN_PAGE_SIZE);
});

watch(searchQuery, (q) => {
  page.value = 1;
  if (q.trim()) {
    // Auto-expand every branch that contains a match
    expandedDomainIds.value = new Set(visibleTree.value.map((dn) => dn.domain.id));
    expandedProgramIds.value = new Set(visibleTree.value.flatMap((dn) => dn.programs.map((pn) => pn.program.id)));
  }
});

watch(totalPages, (n) => {
  if (page.value > n) page.value = n;
});

// ---------------- Expand / Collapse ----------------
const isAnyExpanded = computed(() => expandedDomainIds.value.size > 0 || expandedProgramIds.value.size > 0);

function toggleExpandAll() {
  if (isAnyExpanded.value) {
    expandedDomainIds.value = new Set();
    expandedProgramIds.value = new Set();
  } else {
    expandedDomainIds.value = new Set(visibleTree.value.map((dn) => dn.domain.id));
    expandedProgramIds.value = new Set(visibleTree.value.flatMap((dn) => dn.programs.map((pn) => pn.program.id)));
  }
}

function toggleSetItem(set: Set<string>, id: string) {
  if (set.has(id)) set.delete(id);
  else set.add(id);
}

function toggleDomainExpand(id: string) {
  toggleSetItem(expandedDomainIds.value, id);
}

function toggleProgramExpand(id: string) {
  toggleSetItem(expandedProgramIds.value, id);
}

function courseLimit(programId: string) {
  return courseLimits.value.get(programId) ?? COURSE_PAGE_SIZE;
}

function limitedCourses(programId: string, courses: Course[]) {
  return courses.slice(0, courseLimit(programId));
}

function showMoreCourses(programId: string) {
  courseLimits.value.set(programId, courseLimit(programId) + COURSE_PAGE_SIZE);
}

// ---------------- Derived node states ----------------
function programSelectedCount(programId: string): number {
  let n = 0;
  for (const c of coursesOf(programId)) if (courseSel.value.has(c.id)) n++;
  return n;
}

function programState(programId: string): NodeState {
  const courses = coursesOf(programId);
  if (courses.length === 0) return emptyProgramSel.value.has(programId) ? "full" : "none";
  const n = programSelectedCount(programId);
  if (n === 0) return "none";
  return n === courses.length ? "full" : "partial";
}

function domainState(domainId: string): NodeState {
  const programs = programsOf(domainId);
  if (programs.length === 0) return emptyDomainSel.value.has(domainId) ? "full" : "none";
  let full = 0;
  let none = 0;
  for (const p of programs) {
    const s = programState(p.id);
    if (s === "full") full++;
    else if (s === "none") none++;
  }
  if (full === programs.length) return "full";
  if (none === programs.length) return "none";
  return "partial";
}

function domainCourseTotal(domainId: string): number {
  return programsOf(domainId).reduce((sum, p) => sum + coursesOf(p.id).length, 0);
}

function domainSelectedCourseCount(domainId: string): number {
  return programsOf(domainId).reduce((sum, p) => sum + programSelectedCount(p.id), 0);
}

function combineGrants(grants: Grant[]): GrantState {
  if (grants.length === 0) return "MANDATORY";
  return grants.every((g) => g === grants[0]) ? grants[0]! : "MIXED";
}

function programGrants(programId: string): Grant[] {
  const courses = coursesOf(programId);
  if (courses.length === 0) {
    const g = emptyProgramSel.value.get(programId);
    return g ? [g] : [];
  }
  return courses.map((c) => courseSel.value.get(c.id)).filter((g): g is Grant => !!g);
}

function programGrant(programId: string): GrantState {
  return combineGrants(programGrants(programId));
}

function domainGrants(domainId: string): Grant[] {
  const programs = programsOf(domainId);
  if (programs.length === 0) {
    const g = emptyDomainSel.value.get(domainId);
    return g ? [g] : [];
  }
  return programs.flatMap((p) => programGrants(p.id));
}

function domainGrant(domainId: string): GrantState {
  return combineGrants(domainGrants(domainId));
}

// ---------------- Selection actions ----------------
function setProgram(programId: string, grant: Grant | null) {
  const courses = coursesOf(programId);
  if (courses.length === 0) {
    if (grant) emptyProgramSel.value.set(programId, grant);
    else emptyProgramSel.value.delete(programId);
    return;
  }
  for (const c of courses) {
    if (grant) courseSel.value.set(c.id, grant);
    else courseSel.value.delete(c.id);
  }
}

function setDomain(domainId: string, grant: Grant | null) {
  const programs = programsOf(domainId);
  if (programs.length === 0) {
    if (grant) emptyDomainSel.value.set(domainId, grant);
    else emptyDomainSel.value.delete(domainId);
    return;
  }
  for (const p of programs) setProgram(p.id, grant);
}

function toggleDomain(domainId: string) {
  // full -> none ; none/partial -> full (MANDATORY)
  setDomain(domainId, domainState(domainId) === "full" ? null : "MANDATORY");
}

function toggleProgram(programId: string) {
  setProgram(programId, programState(programId) === "full" ? null : "MANDATORY");
}

function toggleCourse(courseId: string) {
  if (courseSel.value.has(courseId)) courseSel.value.delete(courseId);
  else courseSel.value.set(courseId, "MANDATORY");
}

function nextGrant(current: GrantState): Grant {
  return current === "MANDATORY" ? "OPTIONAL" : "MANDATORY";
}

function cycleDomainGrant(domainId: string) {
  setDomain(domainId, nextGrant(domainGrant(domainId)));
}

function cycleProgramGrant(programId: string) {
  setProgram(programId, nextGrant(programGrant(programId)));
}

function toggleCourseGrant(courseId: string) {
  const g = courseSel.value.get(courseId);
  if (g) courseSel.value.set(courseId, nextGrant(g));
}

// ---------------- Select all / clear ----------------
const hasSelection = computed(
  () => courseSel.value.size > 0 || emptyProgramSel.value.size > 0 || emptyDomainSel.value.size > 0
);

function selectAll() {
  // Applies to the whole (search-filtered) result set, not only the current page
  for (const dn of visibleTree.value) {
    if (dn.programs.length === 0 && programsOf(dn.domain.id).length === 0) {
      emptyDomainSel.value.set(dn.domain.id, "MANDATORY");
      continue;
    }
    for (const pn of dn.programs) {
      if (coursesOf(pn.program.id).length === 0) emptyProgramSel.value.set(pn.program.id, "MANDATORY");
      for (const c of pn.courses) if (!courseSel.value.has(c.id)) courseSel.value.set(c.id, "MANDATORY");
    }
  }
}

function clearAllSelection() {
  courseSel.value.clear();
  emptyProgramSel.value.clear();
  emptyDomainSel.value.clear();
}

function toggleSelectAll() {
  if (hasSelection.value) clearAllSelection();
  else selectAll();
}

// ---------------- Counters ----------------
const selectedCourseCount = computed(() => courseSel.value.size);

const touchedProgramCount = computed(() => {
  const ids = new Set<string>(emptyProgramSel.value.keys());
  for (const c of allCourses.value) if (courseSel.value.has(c.id) && c.programId) ids.add(c.programId);
  return ids.size;
});

const touchedDomainCount = computed(() => {
  const ids = new Set<string>(emptyDomainSel.value.keys());
  for (const c of allCourses.value) {
    if (!courseSel.value.has(c.id) || !c.programId) continue;
    const d = programById.value.get(c.programId)?.domainId;
    if (d) ids.add(d);
  }
  for (const pid of emptyProgramSel.value.keys()) {
    const d = programById.value.get(pid)?.domainId;
    if (d) ids.add(d);
  }
  return ids.size;
});

// ---------------- UI helpers ----------------
function stateClass(state: NodeState) {
  return { "is-selected": state === "full", "is-partial": state === "partial" };
}

function checkClass(state: NodeState) {
  return { checked: state === "full", partial: state === "partial" };
}

function grantClass(g: GrantState) {
  if (g === "MIXED") return "grant-mixed";
  return g === "MANDATORY" ? "grant-mandatory" : "grant-optional";
}

function grantLabel(g: GrantState) {
  if (g === "MIXED") return "Hỗn hợp";
  return g === "MANDATORY" ? "Bắt buộc" : "Tự chọn";
}

// ---------------- Minimal payload ----------------
/**
 * Sends the user's intent with as few items as possible:
 *  - Whole domain with a uniform grant -> 1 domain item
 *  - Whole program with a uniform grant -> 1 program item
 *  - Otherwise -> individual course items
 */
function buildPayloadItems() {
  const items: { categoryType: "domain" | "program" | "course"; targetId: string; grantType: Grant }[] = [];
  for (const d of allDomains.value) {
    const dState = domainState(d.id);
    if (dState === "none") continue;
    const dGrant = domainGrant(d.id);
    if (dState === "full" && dGrant !== "MIXED") {
      items.push({ categoryType: "domain", targetId: d.id, grantType: dGrant });
      continue;
    }
    for (const p of programsOf(d.id)) {
      const pState = programState(p.id);
      if (pState === "none") continue;
      const pGrant = programGrant(p.id);
      if (pState === "full" && pGrant !== "MIXED") {
        items.push({ categoryType: "program", targetId: p.id, grantType: pGrant });
        continue;
      }
      for (const c of coursesOf(p.id)) {
        const g = courseSel.value.get(c.id);
        if (g) items.push({ categoryType: "course", targetId: c.id, grantType: g });
      }
    }
  }
  return items;
}

async function handleSubmitBatch() {
  if (!props.unit?.id) {
    showToast("Vui lòng chọn đơn vị áp dụng", "warning");
    return;
  }
  const items = buildPayloadItems();
  if (items.length === 0) {
    showToast("Vui lòng chọn ít nhất 1 nội dung để phân bổ", "warning");
    return;
  }

  submitting.value = true;
  try {
    await axiosClient.post("/api/admin/unit-allocations/batch-assign", { unitId: props.unit.id, items });
    showToast(`Phân bổ thành công cho ${props.unit.name}!`, "success");
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
  if (!submitting.value) emit("close");
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

/* Selected state (Full) */
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

/* Partial state (Tri-state parent) */
.tree-node.is-partial > .domain-row {
  border-color: #99f6e4;
  background: rgba(0, 90, 84, 0.04);
}

.tree-node.is-partial > .program-row {
  border-left-color: #7dd3fc;
  background: rgba(3, 105, 161, 0.04);
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

.check-box.partial {
  background: #005a54;
  border-color: #005a54;
  color: #ffffff;
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

.btn-show-more {
  align-self: flex-start;
  margin: 0.25rem 0 0.5rem 0.5rem;
  background: transparent;
  border: 1px dashed #cbd5e1;
  color: #0284c7;
  font-size: 0.78rem;
  font-weight: 500;
  padding: 0.35rem 0.75rem;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-show-more:hover {
  background: #f0f9ff;
  border-color: #0284c7;
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

.btn-grant-toggle.grant-mixed {
  background: #f8fafc;
  color: #475569;
  border-color: #cbd5e1;
}

.btn-grant-toggle.grant-mixed .dot {
  background: #94a3b8;
}

/* TREE PAGINATION */
.tree-pagination {
  padding: 0.6rem 1.5rem;
  border-top: 1px solid var(--border-color, #e2e8f0);
  background: var(--bg-surface-elevated, #f8fafc);
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 0.8rem;
  color: #64748b;
}

.tree-pagination .pagination-buttons {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.tree-pagination .page-nav-btn {
  padding: 0.25rem 0.55rem;
  border-radius: 6px;
  border: 1px solid var(--border-color, #cbd5e1);
  background: #ffffff;
  color: #475569;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease;
}

.tree-pagination .page-nav-btn:hover:not(:disabled) {
  background: #f1f5f9;
  color: #0f172a;
}

.tree-pagination .page-nav-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.tree-pagination .page-current {
  font-weight: 600;
  color: #1e293b;
  padding: 0 0.4rem;
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

.select-all-switch-wrap {
  display: flex;
  align-items: center;
  border-right: 1px solid #e2e8f0;
  padding-right: 1.25rem;
}

.switch-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.55rem;
  background: #f8fafc;
  border: 1px solid #cbd5e1;
  cursor: pointer;
  padding: 0.3rem 0.65rem;
  border-radius: 20px;
  transition: all 0.2s ease;
  user-select: none;
}

.switch-btn:hover {
  background: #f1f5f9;
  border-color: #94a3b8;
}

.switch-btn.is-active {
  background: #f0fdfa;
  border-color: #99f6e4;
}

.switch-track {
  width: 32px;
  height: 18px;
  background: #cbd5e1;
  border-radius: 10px;
  position: relative;
  transition: background 0.2s ease;
  flex-shrink: 0;
}

.switch-thumb {
  width: 14px;
  height: 14px;
  background: #ffffff;
  border-radius: 50%;
  position: absolute;
  top: 2px;
  left: 2px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
  transition: transform 0.2s ease;
}

.switch-btn.is-active .switch-track {
  background: #0d9488;
}

.switch-btn.is-active .switch-thumb {
  transform: translateX(14px);
}

.switch-label {
  font-size: 0.78rem;
  font-weight: 600;
  color: #475569;
}

.switch-btn.is-active .switch-label {
  color: #0f766e;
}

.summary-breakdown {
  font-size: 0.78rem;
  color: #64748b;
  margin-left: 0.25rem;
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

/* ========================================================
   NIGHT MODE (DARK THEME) SUPPORT
   ======================================================== */
:root[data-theme="dark"] .batch-modal-overlay {
  background-color: rgba(5, 10, 18, 0.75);
}

:root[data-theme="dark"] .batch-modal-container {
  background: var(--bg-card);
  border-color: var(--border-color);
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.6);
}

:root[data-theme="dark"] .batch-modal-header {
  background: var(--bg-subtle);
  border-bottom-color: var(--border-color);
}

:root[data-theme="dark"] .header-icon-wrap {
  background: var(--primary-light);
  color: var(--primary-ink);
}

:root[data-theme="dark"] .title-row h3 {
  color: var(--text-main);
}

:root[data-theme="dark"] .target-unit-pill {
  background: var(--primary-color);
  color: #ffffff;
}

:root[data-theme="dark"] .btn-close-modal {
  border-color: var(--border-color);
  color: var(--text-muted);
}

:root[data-theme="dark"] .btn-close-modal:hover {
  background: #2a3c50;
  color: #ffffff;
}

:root[data-theme="dark"] .batch-toolbar {
  background: var(--bg-card);
  border-bottom-color: var(--border-color);
}

:root[data-theme="dark"] .search-input {
  background: var(--bg-subtle);
  border-color: var(--border-color);
  color: var(--text-main);
}

:root[data-theme="dark"] .search-input:focus {
  background: #151e2a;
  border-color: var(--primary-ink);
}

:root[data-theme="dark"] .search-icon,
:root[data-theme="dark"] .btn-clear-search {
  color: var(--text-muted);
}

:root[data-theme="dark"] .btn-ghost {
  background: var(--bg-subtle);
  border-color: var(--border-color);
  color: var(--text-main);
}

:root[data-theme="dark"] .btn-ghost:hover {
  background: #2a3c50;
  color: #ffffff;
}

:root[data-theme="dark"] .batch-modal-body {
  background: var(--bg-main);
}

:root[data-theme="dark"] .loading-state,
:root[data-theme="dark"] .empty-tree-state {
  color: var(--text-muted);
}

:root[data-theme="dark"] .domain-row {
  background: var(--bg-card);
  border-color: var(--border-color);
}

:root[data-theme="dark"] .domain-row:hover {
  background: var(--bg-subtle);
}

:root[data-theme="dark"] .program-row {
  background: #16202c;
  border-left-color: #0284c7;
}

:root[data-theme="dark"] .program-row:hover {
  background: #1f2d3d;
}

:root[data-theme="dark"] .course-row {
  background: #131b26;
  border-bottom-color: var(--bg-subtle);
}

:root[data-theme="dark"] .course-row:hover {
  background: #1a2534;
}

:root[data-theme="dark"] .tree-node.is-selected > .domain-row {
  background: rgba(8, 120, 92, 0.25);
  border-color: var(--primary-color);
}

:root[data-theme="dark"] .tree-node.is-selected > .program-row {
  background: rgba(2, 132, 199, 0.22);
  border-left-color: #38bdf8;
}

:root[data-theme="dark"] .tree-node.is-selected > .course-row {
  background: rgba(16, 185, 129, 0.18);
}

:root[data-theme="dark"] .tree-node.is-partial > .domain-row {
  border-color: var(--primary-light);
  background: rgba(8, 120, 92, 0.12);
}

:root[data-theme="dark"] .tree-node.is-partial > .program-row {
  border-left-color: #0284c7;
  background: rgba(2, 132, 199, 0.12);
}

:root[data-theme="dark"] .check-box {
  background: var(--bg-subtle);
  border-color: #475569;
}

:root[data-theme="dark"] .check-box.checked,
:root[data-theme="dark"] .check-box.partial {
  background: var(--primary-color);
  border-color: var(--primary-color);
}

:root[data-theme="dark"] .node-name {
  color: var(--text-main);
}

:root[data-theme="dark"] .program-node .node-name {
  color: var(--text-main);
}

:root[data-theme="dark"] .course-node .node-name {
  color: #cbd5e1;
}

:root[data-theme="dark"] .node-count {
  color: var(--text-muted);
}

:root[data-theme="dark"] .btn-expand {
  color: var(--text-muted);
}

:root[data-theme="dark"] .btn-expand:hover {
  background: var(--bg-subtle);
}

:root[data-theme="dark"] .btn-show-more {
  border-color: var(--border-color);
  color: #38bdf8;
}

:root[data-theme="dark"] .btn-show-more:hover {
  background: var(--bg-subtle);
}

:root[data-theme="dark"] .btn-grant-toggle.grant-mandatory {
  background: var(--bg-subtle);
  color: #cbd5e1;
  border-color: var(--border-color);
}

:root[data-theme="dark"] .btn-grant-toggle.grant-mandatory .dot {
  background: #94a3b8;
}

:root[data-theme="dark"] .btn-grant-toggle.grant-mandatory:hover {
  background: #2a3c50;
  border-color: #64748b;
  color: #ffffff;
}

:root[data-theme="dark"] .btn-grant-toggle.grant-optional {
  background: var(--bg-card);
  color: var(--text-muted);
  border-color: var(--border-color);
}

:root[data-theme="dark"] .btn-grant-toggle.grant-optional .dot {
  background: #64748b;
}

:root[data-theme="dark"] .btn-grant-toggle.grant-optional:hover {
  background: var(--bg-subtle);
  border-color: #475569;
  color: var(--text-main);
}

:root[data-theme="dark"] .btn-grant-toggle.grant-mixed {
  background: var(--bg-subtle);
  color: var(--text-muted);
  border-color: var(--border-color);
}

:root[data-theme="dark"] .btn-grant-toggle.grant-mixed .dot {
  background: var(--text-muted);
}

:root[data-theme="dark"] .tree-pagination {
  background: var(--bg-card);
  border-top-color: var(--border-color);
  color: var(--text-muted);
}

:root[data-theme="dark"] .tree-pagination .page-nav-btn {
  background: var(--bg-subtle);
  border-color: var(--border-color);
  color: var(--text-main);
}

:root[data-theme="dark"] .tree-pagination .page-nav-btn:hover:not(:disabled) {
  background: #2a3c50;
  color: #ffffff;
}

:root[data-theme="dark"] .tree-pagination .page-current {
  color: var(--text-main);
}

:root[data-theme="dark"] .batch-modal-footer {
  background: var(--bg-subtle);
  border-top-color: var(--border-color);
  box-shadow: 0 -4px 12px rgba(0, 0, 0, 0.3);
}

:root[data-theme="dark"] .summary-badge {
  color: var(--text-main);
}

:root[data-theme="dark"] .select-all-switch-wrap {
  border-right-color: var(--border-color);
}

:root[data-theme="dark"] .switch-btn {
  background: var(--bg-card);
  border-color: var(--border-color);
}

:root[data-theme="dark"] .switch-btn:hover {
  background: var(--bg-subtle);
  border-color: #475569;
}

:root[data-theme="dark"] .switch-btn.is-active {
  background: var(--primary-light);
  border-color: var(--primary-color);
}

:root[data-theme="dark"] .switch-track {
  background: var(--border-color);
}

:root[data-theme="dark"] .switch-thumb {
  background: var(--text-main);
}

:root[data-theme="dark"] .switch-btn.is-active .switch-track {
  background: var(--primary-color);
}

:root[data-theme="dark"] .switch-label {
  color: var(--text-muted);
}

:root[data-theme="dark"] .switch-btn.is-active .switch-label {
  color: var(--primary-ink);
}

:root[data-theme="dark"] .summary-breakdown {
  color: var(--text-muted);
}

:root[data-theme="dark"] .btn-secondary {
  background: var(--bg-card);
  border-color: var(--border-color);
  color: var(--text-main);
}

:root[data-theme="dark"] .btn-secondary:hover:not(:disabled) {
  background: var(--bg-subtle);
  color: #ffffff;
}

:root[data-theme="dark"] .btn-primary.btn-submit-batch {
  background: var(--primary-color);
  color: #ffffff;
}

:root[data-theme="dark"] .btn-primary.btn-submit-batch:hover:not(:disabled) {
  background: var(--primary-hover);
}
</style>