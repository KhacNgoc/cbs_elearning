<template>
  <main class="admin-data-page">
    <header class="admin-page-heading">
      <div>
        <h1>Quản lý role</h1>
        <p>
          Tạo role học viên hoặc role quản trị và gán tài khoản. Một tài khoản
          có thể thuộc nhiều role.
        </p>
      </div>
      <button class="btn btn-secondary" :disabled="busy" @click="load">
        <RefreshCw :size="16" /> Tải lại
      </button>
    </header>
    <section class="admin-data-section">
      <div class="admin-section-heading">
        <h2>Danh sách role</h2>
        <button
          v-if="user.can('roles', 'create')"
          class="btn btn-primary"
          @click="edit()"
        >
          <Plus :size="16" /> Thêm role
        </button>
      </div>
      <div class="admin-table-toolbar">
        <div class="admin-search-box">
          <Search :size="18" /><input
            v-model="query"
            type="search"
            placeholder="Tìm mã hoặc tên role…"
            aria-label="Tìm role"
          />
        </div>
        <select v-model="status">
          <option value="">Tất cả trạng thái</option>
          <option value="active">Hoạt động</option>
          <option value="inactive">Ngừng hoạt động</option>
        </select>
      </div>
      <div class="admin-table-scroll">
        <table class="admin-data-table">
          <colgroup>
            <col style="width: 30%" />
            <col style="width: 8%" />
            <col style="width: 20%" />
            <col style="width: 14%" />
            <col style="width: 28%" />
          </colgroup>
          <thead>
            <tr>
              <th>Role</th>
              <th>Tài khoản</th>
              <th>Phạm vi áp dụng</th>
              <th>Trạng thái</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in paginated" :key="r.id">
              <td>
                <strong>{{ r.name }}</strong
                ><small
                  >{{ r.code
                  }}<span v-if="r.protectedRole"> · Role hệ thống</span>
                  <span v-else-if="r.learnerRole"> · Học viên</span></small
                ><small v-if="r.description">{{ r.description }}</small>
              </td>
              <td>{{ r.userIds.length }}</td>
              <td>
                {{
                  r.protectedRole
                    ? "Toàn hệ thống"
                    : r.learnerRole
                      ? "Theo đơn vị của tài khoản"
                      : r.assignedCoursesOnly
                        ? "Khóa học được phân công"
                        : `${r.unitIds.length} đơn vị`
                }}
              </td>
              <td>
                <span
                  class="admin-status"
                  :class="r.active ? 'active' : 'inactive'"
                  >{{ r.active ? "Hoạt động" : "Ngừng hoạt động" }}</span
                >
              </td>
              <td>
                <div class="admin-row-actions">
                  <template v-if="!r.protectedRole"
                    ><button
                      v-if="user.can('roles', 'update')"
                      class="admin-row-edit"
                      @click="edit(r)"
                    >
                      Sửa</button
                    ><button
                      v-if="user.can('roles', 'update')"
                      class="admin-row-edit"
                      @click="assign(r)"
                    >
                      <Users :size="14" /> Gán tài khoản</button
                    ><button
                      v-if="!r.learnerRole && user.can('permissions', 'read')"
                      class="admin-row-edit"
                      @click="
                        router.push({
                          path: '/admin/permissions',
                          query: { role: r.id },
                        })
                      "
                    >
                      Phân quyền</button
                    ><button
                      v-if="user.can('roles', 'delete')"
                      class="admin-row-delete"
                      @click="deleteTarget = r"
                    >
                      Xóa
                    </button></template
                  ><template v-else
                    ><button
                      v-if="user.systemAdmin && user.can('roles', 'update')"
                      class="admin-row-edit"
                      @click="assign(r)"
                    >
                      Gán tài khoản</button
                    ><span class="protected-label">Được bảo vệ</span></template
                  >
                </div>
              </td>
            </tr>
            <tr v-if="!filtered.length">
              <td colspan="5" class="admin-empty">
                {{ loading ? "Đang tải role…" : "Không có role phù hợp." }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <TablePagination
        v-model:page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-count="pageCount"
        unit="role"
        unit-label="Role"
      />
    </section>
    <AdminModal
      :open="editor === 'role'"
      :title="form.id ? 'Cập nhật role' : 'Thêm role'"
      :busy="busy"
      @submit="save"
      @close="editor = ''"
    >
      <div class="admin-form-grid">
        <label class="admin-form-field"
          >Mã role *<input
            v-model="form.code"
            required
            pattern="[A-Za-z0-9_-]{2,80}" /></label
        ><label class="admin-form-field"
          >Tên role *<input v-model="form.name" required
        /></label>
      </div>
      <label class="admin-form-field"
        >Mô tả<textarea v-model="form.description" rows="3" />
      </label>
      <div class="role-form-toggles">
        <ToggleSwitch
          v-model="form.learnerRole"
          label="Là học viên"
          :disabled="busy"
        />
        <ToggleSwitch v-model="form.active" label="Đang hoạt động" />
        <ToggleSwitch
          v-if="!form.learnerRole"
          v-model="form.learningEnabled"
          label="Cho phép tham gia học tập"
        />
      </div>
      <p v-if="form.learnerRole" class="admin-modal-help">
        Vai trò học viên được tham gia học tập theo đơn vị công tác của từng tài
        khoản, không cần phân quyền chi tiết hoặc chọn đơn vị cho role.
      </p>
      <p v-else class="admin-modal-help">
        Cấu hình quyền quản trị và phạm vi quản trị dữ liệu tại menu Phân quyền,
        sau đó gán tài khoản vào role.
      </p>
      <p
        v-if="form.learnerRole && convertingToLearner"
        class="admin-notice"
        role="status"
      >
        Khi lưu, toàn bộ quyền quản trị và phạm vi đơn vị của role này sẽ được
        gỡ. Các tài khoản vẫn giữ quyền được cấp từ những role khác.
      </p>
    </AdminModal>
    <AdminModal
      :open="editor === 'members'"
      :title="`Gán tài khoản · ${selected?.name || ''}`"
      :busy="busy"
      :width="850"
      @submit="saveMembers"
      @close="editor = ''"
    >
      <p class="admin-modal-help">
        {{ memberIds.length }} tài khoản đã chọn. Các role khác của tài khoản
        được giữ nguyên.
      </p>
      <div class="admin-search-box">
        <Search :size="16" /><input
          v-model="memberQuery"
          type="search"
          placeholder="Tìm họ tên hoặc tài khoản…"
        />
      </div>
      <table class="admin-data-table">
        <colgroup>
          <col style="width: 48px" />
          <col />
          <col style="width: 25%" />
          <col style="width: 30%" />
        </colgroup>
        <thead>
          <tr>
            <th>
              <input
                type="checkbox"
                :checked="allMembers"
                :indeterminate="someMembers && !allMembers"
                aria-label="Chọn tất cả tài khoản phù hợp"
                @change="
                  toggleMembers(($event.target as HTMLInputElement).checked)
                "
              />
            </th>
            <th>Tài khoản</th>
            <th>Đơn vị</th>
            <th>Role hiện tại</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in memberRows" :key="m.id">
            <td>
              <input
                v-model="memberIds"
                :value="m.id"
                type="checkbox"
                :aria-label="`Gán ${m.fullName}`"
              />
            </td>
            <td>
              <strong>{{ m.fullName }}</strong
              ><small>{{ m.username }}</small>
            </td>
            <td>
              {{
                units.find((u) => u.id === m.unitId)?.name ||
                m.unitId ||
                "Chưa gán"
              }}
            </td>
            <td>
              {{
                m.roles
                  .map(
                    (code) => roles.find((r) => r.code === code)?.name || code,
                  )
                  .join(", ") || "Chưa gán role"
              }}
            </td>
          </tr>
        </tbody>
      </table>
      <TablePagination
        v-model:page="memberPage"
        v-model:page-size="memberSize"
        :total="filteredMembers.length"
        :page-count="memberCount"
        unit="tài khoản"
        unit-label="Tài khoản"
      />
    </AdminModal>
    <ConfirmModal
      :open="!!deleteTarget"
      title="Xóa role?"
      :message="`Xóa role ${deleteTarget?.name || ''}? Chỉ có thể xóa khi role không còn tài khoản.`"
      :loading="busy"
      confirm-label="Xóa"
      @cancel="deleteTarget = null"
      @confirm="remove"
    />
  </main>
</template>
<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { Plus, RefreshCw, Search, Users } from "lucide-vue-next";
import { roleApi, type AccessRole, type RoleMember } from "../api/roleApi";
import { axiosClient } from "../api/axiosClient";
import type { OrganizationUnit } from "../types/organization";
import { useUserStore } from "../stores/userStore";
import { useTablePagination } from "../composables/useTablePagination";
import { showToast } from "../utils/toast";
import AdminModal from "../components/AdminModal.vue";
import ConfirmModal from "../components/ConfirmModal.vue";
import TablePagination from "../components/TablePagination.vue";
import ToggleSwitch from "../components/ToggleSwitch.vue";
const user = useUserStore(),
  router = useRouter();
const roles = ref<AccessRole[]>([]),
  units = ref<OrganizationUnit[]>([]),
  members = ref<RoleMember[]>([]),
  loading = ref(false),
  busy = ref(false),
  query = ref(""),
  status = ref(""),
  editor = ref(""),
  selected = ref<AccessRole | null>(null),
  deleteTarget = ref<AccessRole | null>(null);
const form = ref({
  id: "",
  code: "",
  name: "",
  description: "",
  active: true,
  learnerRole: true,
  learningEnabled: true,
  version: 0,
});
const convertingToLearner = computed(() => {
  const original = roles.value.find((r) => r.id === form.value.id);
  return (
    !!original &&
    !original.learnerRole &&
    (original.permissions.length > 0 ||
      original.unitIds.length > 0 ||
      original.assignedCoursesOnly)
  );
});
const filtered = computed(() =>
  roles.value.filter(
    (r) =>
      `${r.name} ${r.code}`
        .toLocaleLowerCase("vi")
        .includes(query.value.toLocaleLowerCase("vi")) &&
      (!status.value || r.active === (status.value === "active")),
  ),
);
const { page, pageSize, total, pageCount, paginated, reset } =
  useTablePagination(filtered);
watch([query, status], reset);
const memberIds = ref<number[]>([]),
  memberQuery = ref(""),
  memberPage = ref(1),
  memberSize = ref(10);
const filteredMembers = computed(() =>
  members.value.filter((m) =>
    `${m.fullName} ${m.username}`
      .toLocaleLowerCase("vi")
      .includes(memberQuery.value.toLocaleLowerCase("vi")),
  ),
);
const memberCount = computed(() =>
  Math.max(1, Math.ceil(filteredMembers.value.length / memberSize.value)),
);
const memberRows = computed(() =>
  filteredMembers.value.slice(
    (memberPage.value - 1) * memberSize.value,
    memberPage.value * memberSize.value,
  ),
);
watch([memberQuery, memberSize], () => (memberPage.value = 1));
const allMembers = computed(
  () =>
    !!filteredMembers.value.length &&
    filteredMembers.value.every((m) => memberIds.value.includes(m.id)),
);
const someMembers = computed(() =>
  filteredMembers.value.some((m) => memberIds.value.includes(m.id)),
);
function toggleMembers(yes: boolean) {
  const ids = new Set(memberIds.value);
  filteredMembers.value.forEach((m) =>
    yes ? ids.add(m.id) : ids.delete(m.id),
  );
  memberIds.value = [...ids];
}
const message = (e: any) =>
  e.response?.data?.message || "Không thể lưu dữ liệu. Vui lòng thử lại.";
async function load() {
  loading.value = true;
  try {
    roles.value = await roleApi.list();
    units.value = (
      await axiosClient.get("/api/admin/options/roles")
    ).data.units;
  } catch (e) {
    showToast(message(e), "error");
  } finally {
    loading.value = false;
  }
}
function edit(r?: AccessRole) {
  form.value = {
    id: r?.id || "",
    code: r?.code || "",
    name: r?.name || "",
    description: r?.description || "",
    active: r?.active ?? true,
    learnerRole: r?.learnerRole ?? user.canLearn,
    learningEnabled: r?.learningEnabled ?? user.canLearn,
    version: r?.version || 0,
  };
  editor.value = "role";
}
async function save() {
  busy.value = true;
  try {
    await roleApi.save({
      ...form.value,
      learningEnabled: form.value.learnerRole || form.value.learningEnabled,
    });
    editor.value = "";
    await load();
    await user.refreshAccess();
    showToast("Đã lưu role", "success");
  } catch (e) {
    showToast(message(e), "error");
  } finally {
    busy.value = false;
  }
}
async function assign(r: AccessRole) {
  selected.value = r;
  memberIds.value = [...r.userIds];
  memberQuery.value = "";
  memberPage.value = 1;
  try {
    members.value = await roleApi.members();
    editor.value = "members";
  } catch (e) {
    showToast(message(e), "error");
  }
}
async function saveMembers() {
  if (!selected.value) return;
  busy.value = true;
  try {
    await roleApi.assign(selected.value, memberIds.value);
    editor.value = "";
    await load();
    await user.refreshAccess();
    showToast("Đã cập nhật tài khoản theo role", "success");
  } catch (e) {
    showToast(message(e), "error");
  } finally {
    busy.value = false;
  }
}
async function remove() {
  if (!deleteTarget.value) return;
  busy.value = true;
  try {
    await roleApi.remove(deleteTarget.value.id);
    deleteTarget.value = null;
    await load();
    await user.refreshAccess();
    showToast("Đã xóa role", "success");
  } catch (e) {
    showToast(message(e), "error");
    deleteTarget.value = null;
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<style scoped>
.role-form-toggles {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1rem;
  margin: 0.25rem 0 0.75rem;
}
.admin-page-heading .btn,
.admin-section-heading .btn,
.admin-row-edit {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}
.protected-label {
  color: var(--text-muted);
  font-size: 0.8rem;
}
input[type="checkbox"] {
  width: 16px;
  height: 16px;
  accent-color: #00634b;
}
.admin-form-grid + .admin-form-field {
  margin-top: 1rem;
}
</style>
