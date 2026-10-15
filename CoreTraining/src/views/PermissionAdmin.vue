<template>
  <main class="admin-data-page permission-page">
    <header class="admin-page-heading">
      <div>
        <h1>Phân quyền</h1>
        <p>
          Chọn role, cấp quyền theo trang quản trị và thiết lập phạm vi dữ liệu
          theo đơn vị.
        </p>
      </div>
      <button class="btn btn-secondary" :disabled="busy || dirty" @click="load">
        <RefreshCw :size="16" /> Tải lại
      </button>
    </header>
    <div class="permission-content">
      <div
        class="permission-panel-tabs"
        role="group"
        aria-label="Chọn khối phân quyền"
      >
        <button
          v-for="tab in panelTabs"
          :key="tab.id"
          type="button"
          :aria-pressed="mobilePanel === tab.id"
          :aria-controls="`permission-${tab.id}-panel`"
          :class="{ active: mobilePanel === tab.id }"
          @click="mobilePanel = tab.id"
        >
          {{ tab.label }}
        </button>
      </div>
      <div class="permission-layout">
        <section
          id="permission-roles-panel"
          class="permission-panel role-panel"
          :class="{ 'is-mobile-active': mobilePanel === 'roles' }"
        >
          <header>
            <h2>Vai trò</h2>
            <small>{{ roles.length }} role</small>
          </header>
          <div class="panel-tools">
            <div class="admin-search-box">
              <Search :size="16" /><input
                v-model="search"
                placeholder="Tìm role…"
                aria-label="Tìm role"
              />
            </div>
          </div>
          <div
            class="panel-body role-list"
            role="region"
            aria-label="Danh sách vai trò"
            tabindex="0"
          >
            <button
              v-for="r in filteredRoles"
              :key="r.id"
              type="button"
              class="role-choice"
              :class="{ selected: r.id === selected?.id }"
              :disabled="busy"
              @click="select(r)"
            >
              <span
                ><strong>{{ r.name }}</strong
                ><small>{{ r.code }}</small
                ><small>{{ r.userIds.length }} tài khoản</small></span
              ><ChevronRight :size="16" />
            </button>
            <p v-if="!filteredRoles.length" class="admin-empty">
              {{ loading ? "Đang tải…" : "Không có role phù hợp." }}
            </p>
          </div>
        </section>
        <section
          id="permission-permissions-panel"
          class="permission-panel matrix-panel"
          :class="{ 'is-mobile-active': mobilePanel === 'permissions' }"
        >
          <header>
            <div>
              <h2>Quyền theo trang</h2>
              <small>{{ selected?.name || "Chọn role bên trái" }}</small>
            </div>
            <label v-if="selected"
              ><input
                type="checkbox"
                :checked="allPages"
                :indeterminate="grants.length > 0 && !allPages"
                :disabled="readonly"
                @change="toggleAll(($event.target as HTMLInputElement).checked)"
              />
              Chọn tất cả</label
            >
          </header>
          <div
            class="panel-body matrix-body"
            role="region"
            aria-label="Danh sách quyền theo trang"
            tabindex="0"
          >
            <p class="permission-help">
              Quyền tạo, sửa hoặc xóa luôn kèm quyền xem. Chọn tên trang để cấp
              tất cả quyền của trang đó.
            </p>
            <table class="permission-matrix">
              <colgroup>
                <col class="page-name-col" />
                <col v-for="a in actions" :key="a" class="action-col" />
              </colgroup>
              <thead>
                <tr>
                  <th>Trang quản trị</th>
                  <th v-for="a in actions" :key="a">{{ actionNames[a] }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="p in pages" :key="p.id">
                  <td class="page-name-cell">
                    <label class="page-choice"
                      ><input
                        type="checkbox"
                        :checked="pageChecked(p)"
                        :indeterminate="pageMixed(p)"
                        :disabled="readonly"
                        :aria-label="`Toàn quyền trang ${p.name}`"
                        @change="
                          togglePage(
                            p,
                            ($event.target as HTMLInputElement).checked,
                          )
                        "
                      /><span class="page-label">{{ p.name }}</span></label
                    >
                  </td>
                  <td v-for="a in actions" :key="a">
                    <input
                      v-if="p.actions.includes(a)"
                      type="checkbox"
                      :checked="grants.includes(`${p.id}:${a}`)"
                      :disabled="readonly"
                      :aria-label="`${actionNames[a]} · ${p.name}`"
                      @change="
                        togglePermission(
                          p,
                          a,
                          ($event.target as HTMLInputElement).checked,
                        )
                      "
                    /><span
                      v-else
                      class="unsupported"
                      aria-label="Không áp dụng"
                      >—</span
                    >
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-if="selected?.protectedRole" class="permission-help">
              Role hệ thống được bảo vệ và có toàn quyền.
            </p>
            <p v-if="selected?.assignedCoursesOnly" class="permission-help">
              Role chuyển đổi đang giữ phạm vi khóa được phân công. Lưu cấu hình
              mới sẽ chuyển sang phạm vi đơn vị.
            </p>
          </div>
        </section>
        <section
          id="permission-units-panel"
          class="permission-panel unit-panel"
          :class="{ 'is-mobile-active': mobilePanel === 'units' }"
        >
          <header>
            <div>
              <h2>Phạm vi đơn vị</h2>
              <small>{{
                selected?.protectedRole
                  ? "Toàn hệ thống"
                  : `${scope.length} đơn vị đã chọn`
              }}</small>
            </div>
          </header>
          <div class="panel-body">
            <p class="permission-help">
              Chọn cấp cha bao gồm các cấp con. Role được xem chương trình của
              đơn vị đã chọn và toàn bộ khóa học thuộc chương trình.
            </p>
            <OrganizationCheckTree
              fill-height
              :model-value="
                selected?.protectedRole ? units.map((u) => u.id) : scope
              "
              :units="units"
              :disabled="readonly"
              @update:model-value="scope = $event"
            />
            <p
              v-if="!scope.length && selected && !selected.protectedRole"
              class="scope-empty"
            >
              Chưa chọn đơn vị: chưa có phạm vi dữ liệu đào tạo.
            </p>
          </div>
        </section>
      </div>
    </div>
    <footer v-if="selected" class="permission-footer">
      <span
        >{{ dirty ? "Có thay đổi chưa lưu" : "Cấu hình đã lưu" }} ·
        {{ grants.length }} quyền</span
      >
      <div class="admin-row-actions">
        <button
          class="btn btn-secondary"
          :disabled="!dirty || busy"
          @click="reset"
        >
          Hủy thay đổi</button
        ><button
          v-if="user.can('permissions', 'update')"
          class="btn btn-primary"
          :disabled="readonly || !dirty"
          @click="save"
        >
          {{ busy ? "Đang lưu…" : "Lưu phân quyền" }}
        </button>
      </div>
    </footer>
    <ConfirmModal
      :open="!!pending"
      title="Chuyển role?"
      message="Các thay đổi chưa lưu sẽ bị bỏ. Tiếp tục chọn role khác?"
      confirm-label="Bỏ thay đổi và chuyển"
      @cancel="pending = null"
      @confirm="confirmSelect"
    />
  </main>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { RefreshCw, Search, ChevronRight } from "lucide-vue-next";
import { roleApi, type AccessRole, type AdminPage } from "../api/roleApi";
import type { OrganizationUnit } from "../types/organization";
import { useUserStore } from "../stores/userStore";
import { showToast } from "../utils/toast";
import OrganizationCheckTree from "../components/OrganizationCheckTree.vue";
import ConfirmModal from "../components/ConfirmModal.vue";
const panelTabs = [
  { id: "roles", label: "Vai trò" },
  { id: "permissions", label: "Quyền theo trang" },
  { id: "units", label: "Đơn vị" },
] as const;
const mobilePanel = ref<"roles" | "permissions" | "units">("permissions");
const user = useUserStore(),
  route = useRoute();
const roles = ref<AccessRole[]>([]),
  pages = ref<AdminPage[]>([]),
  units = ref<OrganizationUnit[]>([]),
  selected = ref<AccessRole | null>(null),
  pending = ref<AccessRole | null>(null),
  grants = ref<string[]>([]),
  scope = ref<string[]>([]),
  search = ref(""),
  busy = ref(false),
  loading = ref(false);
const actions = ["create", "read", "update", "delete"];
const actionNames: Record<string, string> = {
  create: "Tạo",
  read: "Xem",
  update: "Sửa",
  delete: "Xóa",
};
const filteredRoles = computed(() =>
  roles.value.filter((r) =>
    `${r.name} ${r.code}`
      .toLocaleLowerCase("vi")
      .includes(search.value.toLocaleLowerCase("vi")),
  ),
);
const normalized = (values: string[]) => [...values].sort().join("|");
const dirty = computed(
  () =>
    !!selected.value &&
    (normalized(grants.value) !== normalized(selected.value.permissions) ||
      normalized(scope.value) !== normalized(selected.value.unitIds)),
);
const readonly = computed(
  () =>
    busy.value ||
    !selected.value ||
    selected.value.protectedRole ||
    !user.can("permissions", "update"),
);
const allPages = computed(
  () => !!pages.value.length && pages.value.every(pageChecked),
);
function pageChecked(p: AdminPage) {
  return p.actions.every((a) => grants.value.includes(`${p.id}:${a}`));
}
function pageMixed(p: AdminPage) {
  return (
    !pageChecked(p) &&
    p.actions.some((a) => grants.value.includes(`${p.id}:${a}`))
  );
}
function togglePage(p: AdminPage, yes: boolean) {
  const set = new Set(grants.value);
  p.actions.forEach((a) =>
    yes ? set.add(`${p.id}:${a}`) : set.delete(`${p.id}:${a}`),
  );
  grants.value = [...set];
}
function toggleAll(yes: boolean) {
  grants.value = yes
    ? pages.value.flatMap((p) => p.actions.map((a) => `${p.id}:${a}`))
    : [];
}
function togglePermission(p: AdminPage, a: string, yes: boolean) {
  const set = new Set(grants.value);
  if (yes) {
    set.add(`${p.id}:${a}`);
    set.add(`${p.id}:read`);
  } else if (a === "read") {
    p.actions.forEach((action) => set.delete(`${p.id}:${action}`));
  } else set.delete(`${p.id}:${a}`);
  grants.value = [...set];
}
function apply(r: AccessRole) {
  selected.value = r;
  grants.value = [...r.permissions];
  scope.value = [...r.unitIds];
}
function select(r: AccessRole) {
  if (r.id === selected.value?.id) return;
  if (dirty.value) pending.value = r;
  else {
    apply(r);
    mobilePanel.value = "permissions";
  }
}
function confirmSelect() {
  if (pending.value) {
    apply(pending.value);
    mobilePanel.value = "permissions";
  }
  pending.value = null;
}
function reset() {
  if (selected.value) apply(selected.value);
}
async function load() {
  loading.value = true;
  try {
    const data = await Promise.all([
      roleApi.options(),
      roleApi.pages(),
      roleApi.units(),
    ]);
    [roles.value, pages.value, units.value] = data;
    const r =
      roles.value.find(
        (r) => r.id === (route.query.role || selected.value?.id),
      ) ||
      roles.value.find((r) => !r.protectedRole) ||
      roles.value[0];
    if (r) apply(r);
    else selected.value = null;
  } catch (e: any) {
    showToast(
      e.response?.data?.message || "Không thể tải cấu hình phân quyền.",
      "error",
    );
  } finally {
    loading.value = false;
  }
}
async function save() {
  if (!selected.value) return;
  busy.value = true;
  try {
    const r = await roleApi.grants(selected.value, grants.value, scope.value);
    roles.value = roles.value.map((x) => (x.id === r.id ? r : x));
    apply(r);
    await user.refreshAccess();
    showToast("Đã lưu phân quyền và cập nhật quyền truy cập", "success");
  } catch (e: any) {
    showToast(
      e.response?.data?.message || "Không thể lưu phân quyền.",
      "error",
    );
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<style scoped>
.permission-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  box-sizing: border-box;
  overflow: hidden;
  padding-bottom: 0;
}
.permission-page > .admin-page-heading,
.permission-page > .admin-notice {
  flex-shrink: 0;
}
.permission-content {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
  padding-bottom: 18px;
  container-type: inline-size;
}
.panel-body:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: -2px;
}
.permission-page > .permission-footer {
  position: static;
  flex-shrink: 0;
  margin-top: 0;
}
.admin-page-heading .btn {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}
.permission-layout {
  display: grid;
  grid-template-columns: minmax(180px, 0.8fr) minmax(420px, 1.8fr) minmax(
      250px,
      1fr
    );
  grid-template-rows: minmax(0, 1fr);
  flex: 1;
  min-height: 0;
  gap: 18px;
  align-items: stretch;
}
.permission-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border: 1px solid var(--line-dde5ec);
  border-radius: 10px;
  background: var(--surface-ffffff);
  min-width: 0;
  overflow: hidden;
}
.permission-panel > header {
  flex-shrink: 0;
  padding: 18px;
  border-bottom: 1px solid var(--line-e3eaf0);
  min-height: 84px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.permission-panel h2 {
  font-size: 1rem;
  margin: 0 0 4px;
}
.permission-panel small {
  color: var(--text-muted, var(--ink-758399));
  font-size: 0.76rem;
}
.panel-body {
  padding: 16px;
  flex: 1;
  min-height: 0;
  overflow: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}
.panel-tools {
  padding: 16px 16px 0;
  flex-shrink: 0;
}
.unit-panel .panel-body {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  scrollbar-gutter: auto;
}
.unit-panel .permission-help,
.unit-panel .scope-empty {
  flex-shrink: 0;
}
.role-choice {
  width: 100%;
  text-align: left;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  background: var(--surface-ffffff);
  border: 1px solid transparent;
  border-radius: 7px;
  padding: 13px 10px;
  margin-top: 6px;
  cursor: pointer;
  color: var(--ink-253449);
}
.role-choice strong,
.role-choice small {
  display: block;
  overflow-wrap: anywhere;
}
.role-choice small {
  margin-top: 4px;
}
.role-choice.selected {
  background: var(--surface-eaf4ef);
  border-color: var(--line-c7dfd4);
  color: var(--ink-00634b);
}
.permission-help {
  font-size: 0.79rem;
  line-height: 1.6;
  color: var(--text-muted);
  margin: 0 0 16px;
}
.permission-matrix {
  width: 100%;
  table-layout: fixed;
  border-collapse: collapse;
}
.page-name-col {
  width: 48%;
}
.action-col {
  width: 13%;
}
.permission-matrix {
  min-width: 420px;
}
.permission-matrix th {
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--surface-f7f9fb);
  font-size: 0.8rem;
  text-align: center;
  padding: 12px 4px;
  color: var(--text-muted);
}
.permission-matrix th:first-child {
  text-align: left;
  padding-left: 10px;
}
.permission-matrix td {
  padding: 15px 4px;
  text-align: center;
  border-bottom: 1px solid var(--line-e3eaf0);
}
.permission-matrix .page-name-cell {
  text-align: left;
  padding-left: 10px;
}
.page-choice {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.page-label {
  display: block;
  min-width: 0;
  flex: 1;
  font-weight: 600;
  font-size: 0.84rem;
  line-height: 1.5;
  color: var(--ink-263449);
  overflow-wrap: break-word;
  white-space: normal;
}
.permission-layout input[type="checkbox"] {
  width: 16px;
  height: 16px;
  flex: 0 0 16px;
  accent-color: #00634b;
  cursor: pointer;
}
.permission-panel header label {
  font-size: 0.75rem;
  white-space: nowrap;
  display: flex;
  gap: 5px;
  align-items: center;
}
.unsupported {
  color: var(--ink-b5bdc7);
}
.scope-empty {
  background: var(--surface-fff8e8);
  padding: 10px;
  border-radius: 6px;
  font-size: 0.8rem;
  color: var(--ink-8b681c);
}
.permission-footer {
  position: sticky;
  bottom: 0;
  background: var(--surface-ffffff);
  border-top: 1px solid var(--line-dde5ec);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 0;
  margin-top: 18px;
  gap: 10px;
  z-index: 2;
}
.permission-footer > span {
  color: var(--text-muted);
  font-size: 0.83rem;
}
.permission-panel-tabs {
  display: none;
  gap: 6px;
  flex-shrink: 0;
  padding-bottom: 12px;
}
.permission-panel-tabs button {
  flex: 1;
  padding: 9px 6px;
  border: 1px solid var(--line-dde5ec);
  border-radius: 6px;
  background: var(--surface-ffffff);
  color: var(--text-muted);
  font-size: 0.8rem;
  cursor: pointer;
}
.permission-panel-tabs button.active {
  background: var(--primary-light);
  color: var(--primary-ink);
  border-color: var(--primary-color);
  font-weight: 600;
}
@container (max-width:899px) {
  .permission-panel-tabs {
    display: flex;
  }
  .permission-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .permission-panel {
    display: none;
  }
  .permission-panel.is-mobile-active {
    display: flex;
  }
}
@media (max-width: 850px) {
  .permission-footer {
    flex-wrap: wrap;
  }
}
</style>
