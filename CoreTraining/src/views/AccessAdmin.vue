<template>
  <main class="admin-data-page">
    <header class="admin-page-heading">
      <div>
        <h1>{{ pageInfo.title }}</h1>
        <p>{{ pageInfo.description }}</p>
      </div>
      <button
        class="btn btn-secondary"
        :disabled="busy || loading"
        @click="load"
      >
        <RefreshCw :size="16" /> Tải lại
      </button>
    </header>
    <SkeletonLoader v-if="loading" variant="table" :rows="8" :columns="5" label="Đang tải dữ liệu" />
    <template v-else>
      <section v-if="section === 'users'" class="admin-data-section">
        <div class="admin-section-heading">
          <h2>{{ "Danh sách tài khoản" }}</h2>
          <div v-if="section === 'users'" class="admin-row-actions">
            <template v-if="system"
              ><button
                class="btn btn-secondary"
                :disabled="busy"
                @click="downloadTemplate"
              >
                <Download :size="16" /> File mẫu</button
              ><button
                class="btn btn-secondary"
                :disabled="busy"
                @click="excelInput?.click()"
              >
                <Upload :size="16" /> Nhập Excel</button
              ><input
                ref="excelInput"
                type="file"
                accept=".xlsx,.xls"
                hidden
                @change="importExcel" /></template
            ><button
              v-if="user.can('users', 'create')"
              class="btn btn-primary"
              :disabled="busy"
              @click="editUser()"
            >
              <Plus :size="16" /> Thêm tài khoản
            </button>
          </div>
        </div>
        <div class="admin-table-toolbar">
          <div class="admin-search-box">
            <Search :size="18" /><input
              v-model="query"
              type="search"
              placeholder="Tìm tên, tài khoản, email hoặc mã nhân viên…"
              aria-label="Tìm tài khoản"
            />
          </div>
          <select v-model="unitFilter" aria-label="Lọc đơn vị">
            <option value="">Tất cả đơn vị</option>
            <option v-for="u in units" :key="u.id" :value="u.id">
              {{ u.name }}
            </option></select
          ><select
            v-if="section === 'users'"
            v-model="statusFilter"
            aria-label="Lọc trạng thái tài khoản"
          >
            <option value="">Tất cả trạng thái</option>
            <option v-for="(label, key) in statusNames" :key="key" :value="key">
              {{ label }}
            </option>
          </select>
        </div>
        <div class="admin-table-scroll">
          <table class="admin-data-table account-table">
            <colgroup>
              <col style="width: 24%" />
              <col style="width: 22%" />
              <col style="width: 21%" />
              <col style="width: 15%" />
              <col style="width: 18%" />
            </colgroup>
            <thead>
              <tr>
                <th>Người dùng</th>
                <th>Đơn vị</th>
                <th>Vai trò</th>
                <th>
                  {{ section === "users" ? "Trạng thái" : "Phạm vi quản lý" }}
                </th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="u in paginatedUsers" :key="u.id">
                <td>
                  <strong>{{ u.fullName }}</strong
                  ><small>{{ u.username }}</small
                  ><small v-if="section === 'users'"
                    >{{ u.employeeId }} · {{ u.email }}</small
                  >
                </td>
                <td>
                  {{ unitName(u.unitId) }}
                </td>
                <td>
                  {{
                    (u.roles || [])
                      .map((code) => u.roleNames?.[code] || code)
                      .join(", ") || "Chưa gán role"
                  }}
                </td>
                <td>
                  <span
                    v-if="section === 'users'"
                    class="admin-status"
                    :class="
                      u.status === 'ACTIVE' || !u.status ? 'active' : 'inactive'
                    "
                    >{{ statusNames[u.status || "ACTIVE"] }}</span
                  >
                </td>
                <td>
                  <div class="admin-row-actions">
                    <button
                      v-if="u.allowedActions?.includes('update')"
                      class="admin-row-edit"
                      :disabled="busy"
                      @click="editUser(u)"
                    >
                      {{ section === "users" ? "Sửa" : "Phân quyền" }}</button
                    ><button
                      v-if="section === 'users' && user.can('roles')"
                      class="admin-row-edit"
                      @click="
                        router.push({
                          path: '/admin/roles',
                          query: { user: String(u.id) },
                        })
                      "
                    >
                      <ShieldCheck :size="14" /> Phân quyền
                    </button>
                  </div>
                </td>
              </tr>
              <tr v-if="!userTotal">
                <td colspan="5" class="admin-empty">
                  Không có tài khoản phù hợp.
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <TablePagination
          v-model:page="userPage"
          v-model:page-size="userPageSize"
          :total="userTotal"
          :page-count="userPageCount"
          unit="tài khoản"
          unit-label="Tài khoản"
        />
      </section>
      <section v-if="section === 'units'" class="admin-data-section">
        <div class="admin-section-heading">
          <h2>Danh sách đơn vị / phòng</h2>
          <button
            v-if="user.can('units', 'create')"
            class="btn btn-primary"
            :disabled="busy"
            @click="editUnit()"
          >
            <Plus :size="16" /> Thêm đơn vị / phòng
          </button>
        </div>
        <div class="admin-table-toolbar">
          <div class="admin-search-box">
            <Search :size="18" /><input
              v-model="query"
              type="search"
              placeholder="Tìm mã hoặc tên đơn vị…"
              aria-label="Tìm đơn vị"
            />
          </div>
          <select v-model="statusFilter" aria-label="Lọc trạng thái đơn vị">
            <option value="">Tất cả trạng thái</option>
            <option value="true">Hoạt động</option>
            <option value="false">Ngừng hoạt động</option>
          </select>
        </div>
        <div class="unit-tree-toolbar">
          <p>
            {{ units.length }} đơn vị / phòng<span v-if="unitFiltering">
              · {{ unitMatchCount }} kết quả phù hợp (giữ cấp cha để thấy cơ
              cấu)</span
            >
          </p>
          <div class="admin-row-actions">
            <button
              type="button"
              class="admin-row-edit"
              @click="expandUnits(true)"
            >
              Mở tất cả</button
            ><button
              type="button"
              class="admin-row-edit"
              @click="expandUnits(false)"
            >
              Thu gọn tất cả
            </button>
          </div>
        </div>
        <OrganizationTreeTable
          :rows="visibleUnitRows"
          :expanded-ids="effectiveUnitExpansion"
          :can-edit="user.can('units', 'update')"
          :can-create="user.can('units', 'create')"
          :busy="busy"
          :is-context="(unit) => unitFiltering && !unitMatches(unit)"
          @toggle="toggleUnitBranch"
          @edit="editUnit"
          @add-child="addChildUnit"
        />
        <TablePagination
          v-model:page="unitPage"
          v-model:page-size="unitPageSize"
          :total="unitRootTotal"
          :page-count="unitPageCount"
          unit="nhánh gốc"
          unit-label="Nhánh gốc"
        />
        <p class="unit-tree-help">
          Phân trang theo nhánh gốc; các cấp con được giữ cùng nhánh. Nhấn mũi
          tên để mở hoặc thu gọn từng cấp.
        </p>
      </section>
    </template>
    <AdminModal
      :open="!!editor"
      :title="modalTitle"
      :busy="busy"
      :width="720"
      @close="closeEditor"
      @submit="submitEditor"
    >
      <template v-if="editor === 'user'">
        <div class="admin-form-grid">
          <label class="admin-form-field"
            >Tài khoản *<input
              v-model="userForm.username"
              :disabled="!!editingId"
              required /></label
          ><label class="admin-form-field"
            >Họ và tên *<input v-model="userForm.fullName" required /></label
          ><label class="admin-form-field"
            >Email<input v-model="userForm.email" type="email" /></label
          ><label class="admin-form-field"
            >Mã nhân viên<input
              v-model.trim="userForm.employeeId"
              type="text"
              placeholder="Ví dụ: NV0001"
          /></label>
        </div>
        <label class="admin-form-field"
          >{{ editingId ? "Mật khẩu mới" : "Mật khẩu ban đầu *"
          }}<input
            v-model="userForm.password"
            type="password"
            autocomplete="new-password"
            :required="!editingId"
            minlength="8"
          /><small>{{
            editingId
              ? "Để trống để giữ mật khẩu hiện tại."
              : "Tối thiểu 8 ký tự. Người dùng sẽ đổi mật khẩu ở lần đăng nhập đầu tiên."
          }}</small></label
        >
        <div class="admin-form-grid">
          <label class="admin-form-field"
            >Trạng thái<select v-model="userForm.status">
              <option
                v-for="(label, key) in statusNames"
                :key="key"
                :value="key"
              >
                {{ label }}
              </option>
            </select></label
          ><label class="admin-form-field"
            >Đối tượng đào tạo<select v-model="userForm.learnerType">
              <option
                v-for="(label, key) in learnerNames"
                :key="key"
                :value="key"
              >
                {{ label }}
              </option>
            </select></label
          >
        </div>
        <div class="admin-form-grid">
          <label class="admin-form-field"
            >Đơn vị công tác<select v-model="userForm.unitId">
              <option v-if="system" value="">Chưa gán đơn vị</option>
              <option v-for="u in activeUnits" :key="u.id" :value="u.id">
                {{ u.name }}
              </option>
            </select></label
          ><label class="admin-form-field"
            >Vai trò *<select v-model="selectedRoleId" required>
              <option
                v-for="r in selectableRoles"
                :key="r.id"
                :value="r.id"
              >
                {{ r.name }}
              </option>
            </select></label
          >
        </div>
        <LearnerDeadlineEditor
          v-if="editingId"
          :key="editingId"
          :user-id="editingId"
          :disabled="busy"
        />
      </template>
      <template v-else-if="editor === 'unit'">
        <div :class="{ 'admin-form-grid': !!editingId }">
          <label v-if="editingId" class="admin-form-field"
            >Mã đơn vị<input
              :value="unitForm.id"
              disabled
              readonly /></label
          ><label class="admin-form-field"
            >Tên đơn vị / phòng *<input v-model="unitForm.name" required
          /></label>
        </div>
        <div class="admin-form-field">
          <span>Trực thuộc</span
          ><OrganizationTreeSelect
            v-model="unitForm.parentId"
            :units="units"
            :exclude-ids="excludedParentIds"
            :disabled="busy"
            label="Trực thuộc"
          /><small v-if="editingId"
            >Không thể chọn chính đơn vị này hoặc các đơn vị con làm cấp
            cha.</small
          >
        </div>
        <ToggleSwitch
          v-model="unitForm.active"
          label="Đang hoạt động"
          :disabled="busy"
        />
      </template>
    </AdminModal>
  </main>
</template>
<script setup lang="ts">
import SkeletonLoader from "../components/SkeletonLoader.vue";
import { computed, onMounted, onBeforeUnmount, ref, watch } from "vue";
import { adminListApi } from "../api/adminListApi";
import {
  Search,
  Plus,
  RefreshCw,
  Download,
  Upload,
  ShieldCheck,
} from "lucide-vue-next";
import AdminModal from "../components/AdminModal.vue";
import LearnerDeadlineEditor from "../components/LearnerDeadlineEditor.vue";
import ToggleSwitch from "../components/ToggleSwitch.vue";
import TablePagination from "../components/TablePagination.vue";
import { useTablePagination } from "../composables/useTablePagination";
import { useRoute, useRouter } from "vue-router";
import { axiosClient } from "../api/axiosClient";
import { showToast } from "../utils/toast";
import { adminApi, type AdminUser } from "../api/adminApi";
import { roleApi, type AccessRole } from "../api/roleApi";
import { useUserStore } from "../stores/userStore";
import type { OrganizationUnit as Unit } from "../types/organization";
import OrganizationTreeTable from "../components/OrganizationTreeTable.vue";
import OrganizationTreeSelect from "../components/OrganizationTreeSelect.vue";
import {
  buildUnitForest,
  filterUnitForest,
  flattenUnitForest,
  unitDescendants,
} from "../utils/organizationTree";
const route = useRoute(),
  router = useRouter(),
  user = useUserStore();
const units = ref<Unit[]>([]),
  users = ref<AdminUser[]>([]),
  roleOptions = ref<AccessRole[]>([]);
const selectedRoleId = ref("");
const loading = ref(false),
  busy = ref(false),
  query = ref("");
const section = computed(() => String(route.meta.accessSection || "users"));
const pages: Record<string, { title: string; description: string }> = {
  users: {
    title: "Tài khoản",
    description: "Quản lý hồ sơ, trạng thái và đơn vị công tác của người dùng.",
  },
  units: {
    title: "Đơn vị / phòng",
    description: "Tổ chức cơ cấu đơn vị, phòng và các cấp trực thuộc.",
  },
  permissions: {
    title: "Phân quyền",
    description:
      "Gán vai trò, phạm vi quản lý, giảng viên và quyền học khóa học.",
  },
};
const pageInfo = computed(() => pages[section.value] || pages.permissions!);
const unitFilter = ref(""),
  statusFilter = ref("");
const system = computed(() => user.systemAdmin);
const statusNames: Record<string, string> = {
  ACTIVE: "Hoạt động",
  LOCKED: "Khóa",
  DISABLED: "Ngừng sử dụng",
};
const learnerNames: Record<string, string> = {
  NEW_STAFF: "Cán bộ mới",
  TRANSFERRED_STAFF: "Cán bộ luân chuyển",
  CURRENT_STAFF: "Cán bộ đang công tác",
  EXTERNAL: "Học viên đơn vị khác",
};
const activeUnits = computed(() => units.value.filter((u) => u.active));
const selectableRoles = computed(() =>
  roleOptions.value.filter(
    (r) => r.active && (system.value || !r.protectedRole),
  ),
);
const defaultLearnerRoleId = () =>
  selectableRoles.value.find((r) => r.code === "ROLE_USER")?.id ||
  selectableRoles.value.find((r) => r.learnerRole)?.id ||
  selectableRoles.value[0]?.id ||
  "";
const unitName = (id?: string | null) =>
  units.value.find((u) => u.id === id)?.name || id || "Chưa gán";
const matches = (text: string, query: string) =>
  text.toLocaleLowerCase("vi").includes(query.trim().toLocaleLowerCase("vi"));
const unitForest = computed(() => buildUnitForest(units.value));
const unitFiltering = computed(() =>
  Boolean(query.value.trim() || statusFilter.value),
);
const unitMatches = (unit: Unit) =>
  matches(`${unit.id} ${unit.name}`, query.value) &&
  (!statusFilter.value || String(unit.active) === statusFilter.value);
const filteredUnitForest = computed(() =>
  unitFiltering.value
    ? filterUnitForest(unitForest.value, unitMatches)
    : unitForest.value,
);
const expandedUnitIds = ref(new Set<string>()),
  filteredExpandedUnitIds = ref(new Set<string>());
const effectiveUnitExpansion = computed(() =>
  unitFiltering.value ? filteredExpandedUnitIds.value : expandedUnitIds.value,
);
let initializedUnitTree = false;
watch(units, () => {
  if (!initializedUnitTree && units.value.length) {
    expandedUnitIds.value = new Set(
      unitForest.value.map((branch) => branch.unit.id),
    );
    initializedUnitTree = true;
  }
  if (unitFiltering.value)
    filteredExpandedUnitIds.value = new Set(
      flattenUnitForest(filteredUnitForest.value)
        .filter((row) => row.hasChildren)
        .map((row) => row.unit.id),
    );
});
watch([query, statusFilter], () => {
  if (unitFiltering.value)
    filteredExpandedUnitIds.value = new Set(
      flattenUnitForest(filteredUnitForest.value)
        .filter((row) => row.hasChildren)
        .map((row) => row.unit.id),
    );
});
function toggleUnitBranch(id: string) {
  const target = unitFiltering.value
    ? filteredExpandedUnitIds
    : expandedUnitIds;
  const next = new Set(target.value);
  next.has(id) ? next.delete(id) : next.add(id);
  target.value = next;
}
function expandUnits(all: boolean) {
  const target = unitFiltering.value
    ? filteredExpandedUnitIds
    : expandedUnitIds;
  target.value = all
    ? new Set(
        flattenUnitForest(filteredUnitForest.value)
          .filter((row) => row.hasChildren)
          .map((row) => row.unit.id),
      )
    : new Set();
}

const userPage = ref(1), userPageSize = ref(10), userTotal = ref(0), userPageCount = ref(1);
const paginatedUsers = users;
function resetUsers() { userPage.value = 1; }
const {
  page: unitPage,
  pageSize: unitPageSize,
  total: unitRootTotal,
  pageCount: unitPageCount,
  paginated: paginatedUnitRoots,
  reset: resetUnits,
} = useTablePagination(filteredUnitForest);
const visibleUnitRows = computed(() =>
  flattenUnitForest(paginatedUnitRoots.value, effectiveUnitExpansion.value),
);
const unitMatchCount = computed(() => units.value.filter(unitMatches).length);
let listRequest = 0;
let listTimer: ReturnType<typeof setTimeout> | undefined;
watch([query, unitFilter, statusFilter, userPageSize], () => {
  ++listRequest;
  clearTimeout(listTimer);
  resetUnits(); resetUsers();
  if (section.value !== "units") listTimer = setTimeout(() => { listTimer = undefined; void loadRows(); }, 250);
});
watch(userPage, () => { if (!listTimer && section.value !== "units") void loadRows(); });
watch(section, () => {
  ++listRequest;
  clearTimeout(listTimer);
  query.value = ""; unitFilter.value = ""; statusFilter.value = ""; editor.value = "";
  resetUsers(); resetUnits();
  void load();
});
onBeforeUnmount(() => { ++listRequest; clearTimeout(listTimer); });
const editor = ref<"" | "user" | "unit">(""),
  editingId = ref<string | number | null>(null);
const blankUser = () => ({
  username: "",
  fullName: "",
  password: "",
  email: "",
  employeeId: "",
  avatarColor: "#005B41",
  points: 0,
  role: "UNASSIGNED",
  roles: [] as string[],
  status: "ACTIVE",
  learnerType: "CURRENT_STAFF",
  unitId: "",
  scopeUnitIds: [] as string[],
});
const userForm = ref(blankUser()),
  unitForm = ref<Unit>({
    id: "",
    name: "",
    type: "UNIT",
    parentId: "",
    active: true,
  });
const excludedParentIds = computed(() =>
  editor.value === "unit" && typeof editingId.value === "string"
    ? unitDescendants(units.value, editingId.value)
    : new Set<string>(),
);
function addChildUnit(unit: Unit) {
  expandedUnitIds.value = new Set([...expandedUnitIds.value, unit.id]);
  editUnit(undefined, unit.id);
}
const excelInput = ref<HTMLInputElement | null>(null);
function error(e: any) {
  showToast(
    e.response?.data?.message || "Không thực hiện được thao tác",
    "error",
  );
}
async function loadRows() {
  const request = ++listRequest;
  const currentSection = section.value;
  loading.value = true;
  try {
    if (currentSection === "users") {
      const result = await adminListApi.users({ query: query.value, unitId: unitFilter.value, status: statusFilter.value, page: userPage.value, size: userPageSize.value });
      if (request !== listRequest) return;
      users.value = result.items; userTotal.value = result.total; userPageCount.value = result.pageCount;
    }
  } catch (e) { if (request === listRequest) error(e); }
  finally { if (request === listRequest) loading.value = false; }
}
async function load() {
  const request = ++listRequest;
  loading.value = true;
  try {
    await user.refreshAccess();
    if (request !== listRequest) return;
    if (section.value === "units") {
      const result = await axiosClient.get<Unit[]>("/api/admin/units");
      if (request === listRequest) units.value = result.data;
    } else {
      const [optionsResult, rolesResult] = await Promise.all([
        axiosClient.get(`/api/admin/options/${section.value}`),
        section.value === "users"
          ? roleApi.list().catch(() => [] as AccessRole[])
          : Promise.resolve([] as AccessRole[]),
      ]);
      if (request !== listRequest) return;
      units.value = optionsResult.data.units;
      if (section.value === "users") roleOptions.value = rolesResult;
      await loadRows();
    }
  } catch (e) { if (request === listRequest) error(e); }
  finally { if (request === listRequest) loading.value = false; }
}
function closeEditor() {
  if (!busy.value) {
    editor.value = "";
    if (route.query.user) router.replace({ query: {} });
  }
}
function roleIdForCodes(codes: string[]) {
  return (
    selectableRoles.value.find((r) => codes.includes(r.code))?.id ||
    defaultLearnerRoleId()
  );
}
function editUser(u?: AdminUser) {
  editingId.value = u?.id || null;
  userForm.value = {
    ...blankUser(),
    ...u,
    password: "",
    unitId: u?.unitId || "",
    roles: [...(u?.roles || [])],
    scopeUnitIds: [...(u?.scopeUnitIds || [])],
    status: u?.status || "ACTIVE",
    learnerType: u?.learnerType || "CURRENT_STAFF",
  };
  selectedRoleId.value = u
    ? roleIdForCodes(u.roles || [])
    : defaultLearnerRoleId();
  editor.value = "user";
}
async function syncUserRole(userId: number, previousCodes: string[]) {
  if (!selectedRoleId.value) return;
  const selected = selectableRoles.value.find(
    (r) => r.id === selectedRoleId.value,
  );
  if (!selected) return;
  const previousIds = selectableRoles.value
    .filter((r) => previousCodes.includes(r.code) && r.id !== selected.id)
    .map((r) => r.id);
  if (!previousCodes.includes(selected.code)) {
    const role = await adminListApi.roleOption(selected.id);
    await adminListApi.assignRoleChanges(role.id, {
      addUserIds: [userId],
      removeUserIds: [],
      version: role.version,
    });
  }
  for (const roleId of previousIds) {
    const role = await adminListApi.roleOption(roleId);
    await adminListApi.assignRoleChanges(role.id, {
      addUserIds: [],
      removeUserIds: [userId],
      version: role.version,
    });
  }
}
function editUnit(u?: Unit, parentId = "") {
  editingId.value = u?.id || null;
  unitForm.value = u
    ? { ...u, parentId: u.parentId || "" }
    : { id: "", name: "", type: "UNIT", parentId, active: true };
  editor.value = "unit";
}
async function saveEditor() {
  busy.value = true;
  try {
    const endpoint = editor.value === "user" ? "users" : "units";
    const savingUser = editor.value === "user";
    const previousCodes = savingUser ? [...userForm.value.roles] : [];
    const editingUserId = savingUser ? editingId.value : null;
    const data =
      savingUser
        ? userForm.value
        : {
            ...unitForm.value,
            id: editingId.value
              ? unitForm.value.id
              : unitForm.value.id || undefined,
          };
    const response = await axiosClient.request({
      method: editingId.value ? "put" : "post",
      url: `/api/admin/${endpoint}${editingId.value ? "/" + encodeURIComponent(String(editingId.value)) : ""}`,
      data,
    });
    if (savingUser && selectedRoleId.value) {
      try {
        const userId = Number(editingUserId || response.data?.id);
        if (userId) await syncUserRole(userId, previousCodes);
      } catch {
        editor.value = "";
        if (route.query.user) await router.replace({ query: {} });
        showToast(
          editingUserId
            ? "Đã lưu tài khoản nhưng chưa cập nhật được vai trò. Hãy gán tại menu Quản lý role."
            : "Đã tạo tài khoản nhưng chưa gán được vai trò. Hãy gán tại menu Quản lý role.",
          "error",
        );
        await load();
        return;
      }
    }
    editor.value = "";
    if (route.query.user) await router.replace({ query: {} });
    showToast("Đã lưu", "success");
    await load();
  } catch (e) {
    error(e);
  } finally {
    busy.value = false;
  }
}
const modalTitle = computed(() =>
  editor.value === "user"
    ? editingId.value
      ? "Cập nhật tài khoản"
      : "Thêm tài khoản"
    : editingId.value
      ? "Cập nhật đơn vị / phòng"
      : "Thêm đơn vị / phòng",
);
async function submitEditor() {
  await saveEditor();
}
async function downloadTemplate() {
  busy.value = true;
  try {
    const blob = await adminApi.downloadUserTemplate();
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "user_import_template.xlsx";
    link.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    error(e);
  } finally {
    busy.value = false;
  }
}
async function importExcel(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  busy.value = true;
  try {
    await adminApi.importUsersExcel(file);
    showToast(
      "Đã nhập tài khoản. Gán đơn vị và role tại menu Quản lý role.",
      "success",
    );
    await load();
  } catch (e) {
    error(e);
  } finally {
    input.value = "";
    busy.value = false;
  }
}
onMounted(load);
</script>
<style scoped>
.unit-tree-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.85rem;
  flex-wrap: wrap;
}
.unit-tree-toolbar p,
.unit-tree-help {
  color: var(--text-muted);
  font-size: 0.8rem;
  line-height: 1.5;
}
.unit-tree-help {
  margin-top: 0.75rem;
}

.grant-field {
  margin: 0;
  min-width: 220px;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
.admin-row-edit {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}
.admin-page-heading .btn,
.admin-section-heading .btn {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}
@media (max-width: 900px) {
  .admin-section-heading {
    flex-wrap: wrap;
  }
  .admin-section-heading .admin-row-actions {
    flex-wrap: wrap;
  }
}
</style>
