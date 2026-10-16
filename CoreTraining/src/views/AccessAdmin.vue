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
    <p v-if="loading" class="admin-empty" role="status">Đang tải dữ liệu…</p>
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
              <col style="width: 25%" />
              <col style="width: 23%" />
              <col style="width: 22%" />
              <col style="width: 12%" />
              <col style="width: 18%" />
            </colgroup>
            <thead>
              <tr>
                <th>Người dùng</th>
                <th>Đơn vị / nhóm</th>
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
                  {{ unitName(u.unitId)
                  }}<small v-if="u.groupNames">{{ u.groupNames }}</small>
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
      <section v-if="section === 'groups'" class="admin-data-section">
        <div class="admin-section-heading">
          <h2>Danh sách nhóm học viên</h2>
          <button
            v-if="user.can('groups', 'create')"
            class="btn btn-primary"
            :disabled="busy"
            @click="editGroup()"
          >
            <Plus :size="16" /> Thêm nhóm
          </button>
        </div>
        <div class="admin-table-toolbar">
          <div class="admin-search-box">
            <Search :size="18" /><input
              v-model="query"
              type="search"
              placeholder="Tìm mã hoặc tên nhóm…"
              aria-label="Tìm nhóm"
            />
          </div>
          <select v-model="unitFilter" aria-label="Lọc đơn vị của nhóm">
            <option value="">Tất cả đơn vị</option>
            <option v-for="u in units" :key="u.id" :value="u.id">
              {{ u.name }}
            </option>
          </select>
        </div>
        <div class="admin-table-scroll">
          <table class="admin-data-table">
            <colgroup>
              <col style="width: 25%" />
              <col style="width: 20%" />
              <col style="width: 15%" />
              <col style="width: 15%" />
              <col style="width: 25%" />
            </colgroup>
            <thead>
              <tr>
                <th>Nhóm học viên</th>
                <th>Đơn vị</th>
                <th>Khóa bắt buộc</th>
                <th>Thành viên</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="g in paginatedGroups" :key="g.id">
                <td>
                  <strong>{{ g.name }}</strong
                  ><small>{{ g.id }}</small>
                </td>
                <td>{{ unitName(g.unitId) }}</td>
                <td>{{ g.mandatoryCourseIds.length }} khóa học</td>
                <td>{{ g.memberCount || 0 }} học viên</td>
                <td>
                  <div class="admin-row-actions">
                    <button
                      v-if="g.allowedActions?.includes('read')"
                      type="button"
                      class="admin-row-edit"
                      :disabled="busy"
                      @click="openGroupMembers(g)"
                    >Thành viên</button>
                    <button
                      v-if="g.allowedActions?.includes('update')"
                      class="admin-row-edit"
                      :disabled="busy"
                      @click="editGroup(g)"
                    >
                      Sửa</button
                    ><button
                      v-if="g.allowedActions?.includes('delete')"
                      class="admin-row-delete"
                      :disabled="busy"
                      @click="deleteTarget = g"
                    >
                      Xóa
                    </button>
                  </div>
                </td>
              </tr>
              <tr v-if="!groupTotal">
                <td colspan="5" class="admin-empty">Không có nhóm phù hợp.</td>
              </tr>
            </tbody>
          </table>
        </div>
        <TablePagination
          v-model:page="groupPage"
          v-model:page-size="groupPageSize"
          :total="groupTotal"
          :page-count="groupPageCount"
          unit="nhóm"
          unit-label="Nhóm"
        />
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
          >
        </div>
        <p class="admin-modal-help">
          Thêm hoặc bỏ nhóm cho tài khoản tại menu Nhóm học viên → Thành viên.
          Một học viên có thể thuộc nhiều nhóm.
        </p>
        <p class="admin-modal-help">
          Vai trò hiện tại:
          {{
            userForm.roles
              .map(
                (code) =>
                  users.find((u) => u.id === editingId)?.roleNames?.[code] ||
                  code,
              )
              .join(", ") || "Chưa gán role"
          }}. Cấp hoặc thay đổi vai trò tại menu Quản lý role.
        </p>
        <LearnerDeadlineEditor
          v-if="editingId"
          :key="editingId"
          :user-id="editingId"
          :disabled="busy"
        />
      </template>
      <template v-else-if="editor === 'unit'">
        <div class="admin-form-grid">
          <label class="admin-form-field"
            >Mã đơn vị *<input
              v-model="unitForm.id"
              :disabled="!!editingId"
              required /></label
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
      <template v-else-if="editor === 'group'">
        <div class="admin-form-grid">
          <label class="admin-form-field"
            >Mã nhóm *<input
              v-model="groupForm.id"
              :disabled="!!editingId"
              required /></label
          ><label class="admin-form-field"
            >Tên nhóm *<input v-model="groupForm.name" required
          /></label>
        </div>
        <label class="admin-form-field"
          >Đơn vị *<select v-model="groupForm.unitId" required>
            <option value="">Chọn đơn vị</option>
            <option v-for="u in activeUnits" :key="u.id" :value="u.id">
              {{ u.name }}
            </option>
          </select></label
        >
        <fieldset class="admin-choice-group">
          <legend>Khóa học bắt buộc</legend>
          <div class="admin-search-box group-course-search">
            <Search :size="18" aria-hidden="true" />
            <input
              v-model="groupCourseQuery"
              type="search"
              placeholder="Tìm theo tên hoặc mã khóa học…"
              aria-label="Tìm khóa học bắt buộc"
              :disabled="busy"
              @keydown.enter.prevent
            />
          </div>
          <p class="group-course-summary" role="status">
            Đã chọn {{ groupForm.mandatoryCourseIds.length }} khóa học
            · {{ groupCourseTotal }} kết quả
          </p>
          <div
            v-for="c in paginatedGroupCourses"
            :key="c.id"
            class="admin-choice-row group-course-row"
          >
            <div class="group-course-choice">
              <ToggleSwitch
                :model-value="groupForm.mandatoryCourseIds.includes(c.id)"
                :label="c.name"
                :disabled="busy"
                @update:model-value="
                  (checked) =>
                    toggleSelection(groupForm.mandatoryCourseIds, c.id, checked)
                "
              />
              <small v-if="c.code">Mã khóa học: {{ c.code }}</small>
            </div>
          </div>
          <p v-if="!courses.length">Chưa có khóa học để giao cho nhóm.</p>
          <p v-else-if="!groupCourseTotal" class="admin-empty">
            Không tìm thấy khóa học phù hợp. Hãy thử tên hoặc mã khác.
          </p>
          <TablePagination
            v-if="groupCourseTotal"
            v-model:page="groupCoursePage"
            v-model:page-size="groupCoursePageSize"
            :total="groupCourseTotal"
            :page-count="groupCoursePageCount"
            :sizes="[5, 10, 20]"
            unit="khóa học"
            unit-label="Khóa học"
          />
        </fieldset>
      </template>
    </AdminModal>
    <AdminModal
      :open="!!memberGroup"
      :title="`Thành viên nhóm ${memberGroup?.name || ''}`"
      :busy="memberSaving"
      :width="850"
      submit-label="Lưu thành viên"
      :submit-disabled="!memberChangeCount || memberLoading"
      @close="closeGroupMembers"
      @submit="saveGroupMembers"
    >
      <p class="admin-modal-help">
        Chọn học viên thuộc đơn vị của nhóm hoặc đơn vị con. Mỗi học viên có thể
        tham gia nhiều nhóm và nhận các khóa học bắt buộc từ tất cả nhóm đó.
        Tài khoản cần có đơn vị công tác trước khi thêm vào nhóm.
      </p>
      <div class="admin-table-toolbar group-members-toolbar">
        <button
          v-if="memberGroup?.allowedActions?.includes('update')"
          type="button"
          class="btn btn-secondary"
          :disabled="memberSaving"
          @click="membersOnly = false"
        ><Plus :size="16" /> Thêm thành viên</button>
        <div class="admin-search-box">
          <Search :size="18" aria-hidden="true" />
          <input
            v-model="memberQuery"
            type="search"
            aria-label="Tìm học viên"
            placeholder="Tìm tên, tài khoản, mã nhân viên hoặc email…"
            :disabled="memberSaving"
            @keydown.enter.prevent
          />
        </div>
        <label class="group-members-filter">
          <input v-model="membersOnly" type="checkbox" :disabled="memberSaving" />
          Chỉ thành viên hiện tại
        </label>
      </div>
      <p role="status" class="admin-modal-help">
        Hiện có {{ memberCount }} thành viên
        <template v-if="memberChangeCount"> · {{ memberChangeCount }} thay đổi chưa lưu</template>.
        Lựa chọn được giữ khi tìm kiếm hoặc chuyển trang.
      </p>
      <p v-if="memberError" class="admin-notice error" role="alert">
        {{ memberError }}
        <button type="button" class="btn btn-secondary" @click="loadGroupMembers">Thử lại</button>
      </p>
      <div class="admin-table-scroll" :aria-busy="memberLoading">
        <table class="admin-data-table group-members-table">
          <thead>
            <tr>
              <th>Trong nhóm</th>
              <th>Học viên</th>
              <th>Mã nhân viên</th>
              <th>Đơn vị</th>
              <th>Trạng thái</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="memberLoading"><td colspan="5" class="admin-empty">Đang tải học viên…</td></tr>
            <template v-else>
              <tr v-for="m in memberRows" :key="m.id">
                <td>
                  <input
                    type="checkbox"
                    :checked="memberChanges[m.id] ?? m.member"
                    :disabled="memberSaving || !m.selectable"
                    :aria-label="`Thành viên nhóm: ${m.fullName}`"
                    @change="toggleGroupMember(m, ($event.target as HTMLInputElement).checked)"
                  />
                </td>
                <td><strong>{{ m.fullName }}</strong><small>{{ m.username }}</small></td>
                <td>{{ m.employeeId || 'Chưa cập nhật' }}</td>
                <td>{{ m.unitName }}</td>
                <td>{{ statusNames[m.status] || m.status }}</td>
              </tr>
              <tr v-if="!memberRows.length && !memberError"><td colspan="5" class="admin-empty">Không có học viên phù hợp.</td></tr>
            </template>
          </tbody>
        </table>
      </div>
      <TablePagination
        v-model:page="memberPage"
        v-model:page-size="memberPageSize"
        :total="memberTotal"
        :page-count="memberPageCount"
        :sizes="[10, 20, 50]"
        unit="học viên"
        unit-label="Học viên"
      />
    </AdminModal>
    <ConfirmModal
      :open="!!deleteTarget"
      title="Xóa nhóm học viên?"
      :message="`Xóa nhóm ${deleteTarget?.name || ''}? Nhóm có thành viên sẽ không thể xóa.`"
      :loading="busy"
      confirm-label="Xóa nhóm"
      @cancel="!busy && (deleteTarget = null)"
      @confirm="deleteGroup"
    />
  </main>
</template>
<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
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
import ConfirmModal from "../components/ConfirmModal.vue";
import ToggleSwitch from "../components/ToggleSwitch.vue";
import TablePagination from "../components/TablePagination.vue";
import { useTablePagination } from "../composables/useTablePagination";
import { useRoute, useRouter } from "vue-router";
import { axiosClient } from "../api/axiosClient";
import { showToast } from "../utils/toast";
import { adminApi, type AdminUser } from "../api/adminApi";
import type { Course } from "../types";
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
interface Group {
  id: string;
  name: string;
  unitId: string;
  mandatoryCourseIds: string[];
  allowedActions?: string[];
  memberCount?: number;
}
interface GroupMember {
  id: number;
  username: string;
  fullName: string;
  employeeId?: string;
  unitName: string;
  status: string;
  member: boolean;
  selectable: boolean;
}
const route = useRoute(),
  router = useRouter(),
  user = useUserStore();
const units = ref<Unit[]>([]),
  groups = ref<Group[]>([]),
  users = ref<AdminUser[]>([]),
  courses = ref<Course[]>([]);
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
  groups: {
    title: "Nhóm học viên",
    description: "Quản lý nhóm học viên và các khóa học bắt buộc.",
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
const unitName = (id?: string | null) =>
  units.value.find((u) => u.id === id)?.name || id || "Chưa gán";
const matches = (text: string, query: string) =>
  text.toLocaleLowerCase("vi").includes(query.trim().toLocaleLowerCase("vi"));
const groupCourseQuery = ref("");
const normalizeCourseSearch = (text: string) =>
  text
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[đĐ]/g, "d")
    .toLocaleLowerCase("vi")
    .trim();
const filteredGroupCourses = computed(() => {
  const search = normalizeCourseSearch(groupCourseQuery.value);
  return courses.value.filter((course) =>
    normalizeCourseSearch(
      `${course.name} ${course.code || ""} ${course.id}`,
    ).includes(search),
  );
});
const {
  page: groupCoursePage,
  pageSize: groupCoursePageSize,
  total: groupCourseTotal,
  pageCount: groupCoursePageCount,
  paginated: paginatedGroupCourses,
  reset: resetGroupCourses,
} = useTablePagination(filteredGroupCourses);
groupCoursePageSize.value = 5;
watch(groupCourseQuery, resetGroupCourses);
const filteredUsers = computed(() =>
  users.value.filter(
    (u) =>
      matches(
        `${u.username} ${u.fullName} ${u.employeeId || ""} ${u.email || ""}`,
        query.value,
      ) &&
      (!unitFilter.value || u.unitId === unitFilter.value) &&
      (!statusFilter.value || (u.status || "ACTIVE") === statusFilter.value),
  ),
);
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

const filteredGroups = computed(() =>
  groups.value.filter(
    (g) =>
      matches(`${g.id} ${g.name}`, query.value) &&
      (!unitFilter.value || g.unitId === unitFilter.value),
  ),
);
const {
  page: userPage,
  pageSize: userPageSize,
  total: userTotal,
  pageCount: userPageCount,
  paginated: paginatedUsers,
  reset: resetUsers,
} = useTablePagination(filteredUsers);
const {
  page: unitPage,
  pageSize: unitPageSize,
  total: unitRootTotal,
  pageCount: unitPageCount,
  paginated: paginatedUnitRoots,
  reset: resetUnits,
} = useTablePagination(filteredUnitForest);
const {
  page: groupPage,
  pageSize: groupPageSize,
  total: groupTotal,
  pageCount: groupPageCount,
  paginated: paginatedGroups,
  reset: resetGroups,
} = useTablePagination(filteredGroups);
const visibleUnitRows = computed(() =>
  flattenUnitForest(paginatedUnitRoots.value, effectiveUnitExpansion.value),
);
const unitMatchCount = computed(() => units.value.filter(unitMatches).length);
watch([query, unitFilter, statusFilter], () => {
  resetUsers();
  resetUnits();
  resetGroups();
});
watch(section, () => {
  query.value = "";
  unitFilter.value = "";
  statusFilter.value = "";
  editor.value = "";
  resetUsers();
  resetUnits();
  resetGroups();
  void load();
});
function toggleSelection<T>(items: T[], item: T, enabled: boolean) {
  const index = items.indexOf(item);
  if (enabled && index < 0) items.push(item);
  else if (!enabled && index >= 0) items.splice(index, 1);
}
const editor = ref<"" | "user" | "unit" | "group">(""),
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
  }),
  groupForm = ref<Group>({
    id: "",
    name: "",
    unitId: "",
    mandatoryCourseIds: [],
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
async function load() {
  loading.value = true;
  try {
    await user.refreshAccess();
    if (section.value === "units") {
      units.value = (await axiosClient.get<Unit[]>("/api/admin/units")).data;
      return;
    }
    const options = (
      await axiosClient.get(`/api/admin/options/${section.value}`)
    ).data;
    units.value = options.units;
    if (section.value === "users") {
      users.value = (
        await axiosClient.get<AdminUser[]>("/api/admin/users")
      ).data;
      groups.value = options.groups || [];
    }
    if (section.value === "groups") {
      groups.value = (
        await axiosClient.get<Group[]>("/api/admin/access/groups")
      ).data;
      courses.value = options.courses || [];
    }
  } catch (e) {
    error(e);
  } finally {
    loading.value = false;
  }
}
function closeEditor() {
  if (!busy.value) {
    editor.value = "";
    if (route.query.user) router.replace({ query: {} });
  }
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
  editor.value = "user";
}
function editUnit(u?: Unit, parentId = "") {
  editingId.value = u?.id || null;
  unitForm.value = u
    ? { ...u, parentId: u.parentId || "" }
    : { id: "", name: "", type: "UNIT", parentId, active: true };
  editor.value = "unit";
}
function editGroup(g?: Group) {
  groupCourseQuery.value = "";
  groupCoursePageSize.value = 5;
  resetGroupCourses();
  editingId.value = g?.id || null;
  groupForm.value = g
    ? { ...g, mandatoryCourseIds: [...g.mandatoryCourseIds] }
    : { id: "", name: "", unitId: "", mandatoryCourseIds: [] };
  editor.value = "group";
}
const memberGroup = ref<Group | null>(null);
const memberRows = ref<GroupMember[]>([]);
const memberQuery = ref("");
const membersOnly = ref(true);
const memberPage = ref(1);
const memberPageSize = ref(10);
const memberTotal = ref(0);
const memberPageCount = ref(1);
const memberCount = ref(0);
const memberLoading = ref(false);
const memberSaving = ref(false);
const memberError = ref("");
const memberChanges = ref<Record<number, boolean>>({});
const memberChangeCount = computed(() => Object.keys(memberChanges.value).length);
let memberRequest = 0;
function openGroupMembers(group: Group) {
  memberChanges.value = {};
  memberRows.value = [];
  memberQuery.value = "";
  membersOnly.value = true;
  memberPage.value = 1;
  memberPageSize.value = 10;
  memberTotal.value = 0;
  memberPageCount.value = 1;
  memberCount.value = group.memberCount || 0;
  memberError.value = "";
  memberGroup.value = group;
}
function closeGroupMembers() {
  if (memberSaving.value) return;
  memberRequest++;
  memberGroup.value = null;
}
async function loadGroupMembers() {
  const group = memberGroup.value;
  if (!group) return;
  const request = ++memberRequest;
  memberLoading.value = true;
  memberError.value = "";
  try {
    const { data } = await axiosClient.get<{
      items: GroupMember[]; total: number; page: number; pageCount: number; memberCount: number;
    }>(`/api/admin/groups/${encodeURIComponent(group.id)}/members`, {
      params: { query: memberQuery.value, page: memberPage.value, size: memberPageSize.value, membersOnly: membersOnly.value },
    });
    if (request !== memberRequest) return;
    memberRows.value = data.items;
    memberTotal.value = data.total;
    memberPageCount.value = data.pageCount;
    memberCount.value = data.memberCount;
    memberPage.value = data.page;
  } catch (e: any) {
    if (request !== memberRequest) return;
    memberRows.value = [];
    memberError.value = e.response?.data?.message || "Không tải được danh sách học viên.";
  } finally {
    if (request === memberRequest) memberLoading.value = false;
  }
}
watch(
  [() => memberGroup.value?.id, memberQuery, membersOnly, memberPageSize, memberPage],
  (values, previous, onCleanup) => {
    memberRequest++;
    if (!values[0]) return;
    if (values.slice(0, 4).some((value, index) => value !== previous[index]) && memberPage.value !== 1) {
      memberPage.value = 1;
      return;
    }
    memberLoading.value = true;
    const timer = setTimeout(() => void loadGroupMembers(), values[1] !== previous[1] ? 250 : 0);
    onCleanup(() => clearTimeout(timer));
  },
);
function toggleGroupMember(member: GroupMember, selected: boolean) {
  if (!member.selectable || memberSaving.value) return;
  if (selected === member.member) delete memberChanges.value[member.id];
  else memberChanges.value[member.id] = selected;
}
async function saveGroupMembers() {
  if (!memberGroup.value || !memberChangeCount.value || memberLoading.value || memberSaving.value) return;
  memberSaving.value = true;
  try {
    const entries = Object.entries(memberChanges.value);
    await axiosClient.put(`/api/admin/groups/${encodeURIComponent(memberGroup.value.id)}/members`, {
      addUserIds: entries.filter(([, selected]) => selected).map(([id]) => Number(id)),
      removeUserIds: entries.filter(([, selected]) => !selected).map(([id]) => Number(id)),
    });
    memberRequest++;
    memberGroup.value = null;
    memberChanges.value = {};
    showToast("Đã cập nhật thành viên nhóm", "success");
    await load();
  } catch (e) {
    error(e);
  } finally {
    memberSaving.value = false;
  }
}
async function saveEditor() {
  busy.value = true;
  try {
    const endpoint =
      editor.value === "user"
        ? "users"
        : editor.value === "unit"
          ? "units"
          : "groups";
    const data =
      editor.value === "user"
        ? userForm.value
        : editor.value === "unit"
          ? unitForm.value
          : groupForm.value;
    await axiosClient.request({
      method: editingId.value ? "put" : "post",
      url: `/api/admin/${endpoint}${editingId.value ? "/" + encodeURIComponent(String(editingId.value)) : ""}`,
      data,
    });
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
const deleteTarget = ref<Group | null>(null);
async function deleteGroup() {
  if (!deleteTarget.value) return;
  busy.value = true;
  try {
    await axiosClient.delete(
      `/api/admin/groups/${encodeURIComponent(deleteTarget.value.id)}`,
    );
    deleteTarget.value = null;
    showToast("Đã xóa nhóm", "success");
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
    : editor.value === "unit"
      ? editingId.value
        ? "Cập nhật đơn vị / phòng"
        : "Thêm đơn vị / phòng"
      : editingId.value
        ? "Cập nhật nhóm học viên"
        : "Thêm nhóm học viên",
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
      "Đã nhập tài khoản. Gán đơn vị, nhóm và role tại menu Quản lý role.",
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
.group-members-toolbar {
  margin-top: 1rem;
}
.group-members-filter {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.8125rem;
}
.group-members-table {
  margin-bottom: 1rem;
}
.group-members-table input[type="checkbox"] {
  width: 18px;
  height: 18px;
  accent-color: var(--primary-color);
}
.group-course-search {
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
}
.group-course-search:focus-within {
  border-color: var(--primary-color);
}
.group-course-search input {
  min-width: 0;
  color: var(--text-main);
}
.group-course-search > svg {
  flex-shrink: 0;
}
.group-course-summary {
  margin: 0.65rem 0;
}
.group-course-choice {
  min-width: 0;
  width: 100%;
}
.group-course-choice :deep(.toggle-switch) {
  width: 100%;
  min-width: 0;
}
.group-course-choice :deep(.toggle-label) {
  overflow-wrap: anywhere;
}
.group-course-choice small {
  margin-left: calc(38px + 0.65rem);
  overflow-wrap: anywhere;
}
.group-course-row:last-of-type {
  border-bottom: 0;
}
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
