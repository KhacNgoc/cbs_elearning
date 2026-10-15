<template>
  <div class="admin-shell">
    <div
      class="admin-sidebar-frame"
      :class="{ 'is-collapsed': !sidebarVisible }"
      :inert="!sidebarVisible"
      :aria-hidden="!sidebarVisible"
    >
      <aside
        id="admin-navigation-sidebar"
        class="admin-shell-sidebar"
        aria-label="Điều hướng quản trị"
      >
        <nav aria-label="Menu chức năng">
          <RouterLink
            v-for="item in standaloneNavigation"
            :key="item.id"
            class="sidebar-item"
            :to="item.route"
            :class="{ active: activeSection === item.id }"
            :aria-current="activeSection === item.id ? 'page' : undefined"
            @click="emit('navigate')"
          >
            <component :is="navigationIcon(item.icon)" :size="18" />
            <span>{{ item.label }}</span>
          </RouterLink>
          <section
            v-for="group in navigationGroups"
            :key="group.id"
            class="sidebar-group"
            :class="{
              'contains-active': activeGroup === group.id,
              'is-open': openGroup === group.id,
            }"
          >
            <button
              type="button"
              class="sidebar-item group-toggle"
              :aria-expanded="openGroup === group.id"
              :aria-controls="`sidebar-group-${group.id}`"
              @click="openGroup = openGroup === group.id ? null : group.id"
            >
              <component :is="group.icon" :size="18" />
              <span>{{ group.label }}</span>
              <ChevronDown
                :size="15"
                class="group-chevron"
                :class="{ rotated: openGroup === group.id }"
              />
            </button>
            <MotionCollapse :open="openGroup === group.id">
              <div :id="`sidebar-group-${group.id}`" class="group-items">
                <RouterLink
                  v-for="item in group.items"
                  :key="item.id"
                  :to="item.route"
                  class="sidebar-item group-item"
                  :class="{ active: itemActive(item) }"
                  :aria-current="itemActive(item) ? 'page' : undefined"
                  @click="emit('navigate')"
                >
                  <component :is="navigationIcon(item.icon)" :size="16" />
                  <span>{{
                    item.id === "dashboard" ? "Tổng quan học tập" : item.label
                  }}</span>
                </RouterLink>
              </div>
            </MotionCollapse>
          </section>
        </nav>
        <div class="admin-shell-footer">
          <Settings :size="18" />
          <span>{{
            user.roles.map((code) => user.roleNames[code] || code).join(", ")
          }}</span>
        </div>
      </aside>
    </div>
    <section
      class="admin-shell-content"
      :class="[
        route.path.startsWith('/admin')
          ? 'admin-management-content'
          : 'learning-shell-content',
        { 'permissions-shell-content': route.name === 'AdminPermissions' },
      ]"
    >
      <slot><RouterView /></slot>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useNavigationStore } from "../stores/navigationStore";
import {
  BookOpen,
  ClipboardCheck,
  FileText,
  HelpCircle,
  Home,
  Trophy,
  Settings,
  Users,
  Building2,
  ShieldCheck,
  UsersRound,
  Library,
  Flag,
  ChevronDown,
  GraduationCap,
  ChartNoAxesCombined,
} from "lucide-vue-next";
import MotionCollapse from "../components/MotionCollapse.vue";
import { reportMenuViews as reportViews } from "../utils/reportingViews";
import type { NavigationItem } from "../api/navigationApi";

import { useUserStore } from "../stores/userStore";

withDefaults(defineProps<{ sidebarVisible?: boolean }>(), {
  sidebarVisible: true,
});
const emit = defineEmits<{ navigate: [] }>();
const user = useUserStore();
const route = useRoute();
const router = useRouter();
const navigationStore = useNavigationStore();
const navigation = computed(() => navigationStore.adminItems);
const learningNavigation = computed(() =>
  user.canLearn
    ? navigationStore.mainItems.filter((item) => item.id !== "admin")
    : [],
);
const iconRegistry = {
  ChartNoAxesCombined,
  BookOpen,
  ClipboardCheck,
  FileText,
  HelpCircle,
  Home,
  Trophy,
  Settings,
  Users,
  Building2,
  ShieldCheck,
  UsersRound,
  Library,
  Flag,
};
const navigationIcon = (name: string) =>
  iconRegistry[name as keyof typeof iconRegistry] || Settings;
const groupDefinitions = [
  {
    id: "training",
    label: "Đào tạo & tài liệu",
    icon: BookOpen,
    ids: ["admin.catalog", "admin.courses", "admin.lessons", "admin.documents"],
  },
  {
    id: "assessment",
    label: "Kiểm tra & câu hỏi",
    icon: ClipboardCheck,
    ids: ["admin.exams", "admin.question-bank"],
  },
  {
    id: "learners",
    label: "Học viên & tổ chức",
    icon: Users,
    ids: ["admin.users", "admin.units", "admin.groups", "admin.enrollments"],
  },
  {
    id: "management",
    label: "Quản trị & kiểm duyệt",
    icon: ShieldCheck,
    ids: ["admin.roles", "admin.permissions", "admin.content-reports"],
  },
];
const groupedIds = new Set(groupDefinitions.flatMap((group) => group.ids));
const standaloneNavigation = computed(() =>
  navigation.value.filter((item) => !groupedIds.has(item.id)),
);
const navigationGroups = computed(() =>
  [
    {
      id: "reports",
      label: "Thống kê & báo cáo",
      icon: ChartNoAxesCombined,
      items: navigationStore.allowedPageIds.has("admin.overview")
        ? reportViews.map((view, index): NavigationItem => ({
            id: `admin.reports.${view.id}`,
            label: view.label,
            route: router.resolve({
              path: "/admin",
              query: {
                ...Object.fromEntries(Object.entries(route.query).filter(([key]) => !["report", "tab"].includes(key))),
                report: view.id,
              },
            }).fullPath,
            icon: "ChartNoAxesCombined",
            area: "ADMIN",
            sortOrder: index,
            visible: true,
          }))
        : [],
    },
    ...groupDefinitions.map((group) => ({
      ...group,
      items: group.ids.flatMap((id) =>
        navigation.value.filter((item) => item.id === id),
      ),
    })),
    {
      id: "learning",
      label: "Học tập",
      icon: GraduationCap,
      items: learningNavigation.value,
    },
  ].filter((group) => group.items.length),
);
const openGroup = ref<string | null>(null);

const activeSection = computed(() =>
  route.path==='/admin' && route.query.report
    ? `admin.reports.${String(route.query.report)}`
    : route.path.startsWith("/admin/question-bank")
    ? "admin.question-bank"
    : route.path.startsWith("/admin/content-reports")
      ? "admin.content-reports"
      : route.path.startsWith("/admin/users")
        ? "admin.users"
        : route.path.startsWith("/admin/units")
          ? "admin.units"
          : route.path.startsWith("/admin/groups")
            ? "admin.groups"
            : route.path.startsWith("/admin/roles")
              ? "admin.roles"
              : route.path.startsWith("/admin/permissions")
                ? "admin.permissions"
                : route.path.startsWith("/admin/catalog")
                  ? "admin.catalog"
                  : route.path.startsWith("/admin/courses") ||
                      route.path.endsWith("/edit")
                    ? "admin.courses"
                    : route.path.includes("/admin/quizzes")
                      ? "admin.exams"
                      : route.name === "AdminCourseLessons" ||
                          route.path.startsWith("/admin/lessons")
                        ? "admin.lessons"
                        : typeof route.query.tab === "string"
                          ? `admin.${route.query.tab}`
                          : route.path.startsWith("/admin")
                            ? "admin.overview"
                            : "",
);

function itemActive(item: NavigationItem) {
  return item.area === "ADMIN"
    ? activeSection.value === item.id
    : !route.path.startsWith("/admin") && route.path === item.route;
}
const activeGroup = computed(
  () =>
    navigationGroups.value.find((group) => group.items.some(itemActive))?.id ||
    null,
);
watch(
  [() => route.fullPath, activeGroup],
  () => {
    openGroup.value = activeGroup.value;
  },
  { immediate: true },
);
onMounted(() => navigationStore.load());
</script>

<style scoped>
.admin-shell {
  --sidebar-duration: 320ms;
  --sidebar-easing: cubic-bezier(0.22, 1, 0.36, 1);
  --admin-text-caption: 0.75rem;
  --admin-text-label: 0.8125rem;
  --admin-text-body: 0.875rem;
  --admin-heading-section: 1.125rem;
  --admin-heading-page: 1.5rem;
  display: flex;
  position: relative;
  height: calc(100% + 4rem);
  min-height: 0;
  margin: -2rem;
  overflow: clip;
  background: var(--bg-main);
}

.admin-sidebar-frame {
  display: grid;
  grid-template-columns: 280px;
  grid-template-rows: minmax(0, 1fr);
  flex: 0 0 auto;
  min-height: 0;
  overflow: hidden;
  transition: grid-template-columns var(--sidebar-duration)
    var(--sidebar-easing);
}

.admin-sidebar-frame.is-collapsed {
  grid-template-columns: 0px;
}
.admin-sidebar-frame.is-collapsed .admin-shell-sidebar {
  transform: translateX(-100%);
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transition:
    transform var(--sidebar-duration) var(--sidebar-easing),
    opacity var(--sidebar-duration) var(--sidebar-easing),
    visibility 0s linear var(--sidebar-duration);
}

.admin-shell-sidebar {
  position: sticky;
  top: 0;
  align-self: stretch;
  width: 280px;
  height: 100%;
  flex: 0 0 280px;
  display: flex;
  flex-direction: column;
  background: var(--primary-color);
  color: white;
  box-shadow: 2px 0 10px rgba(0, 0, 0, 0.05);
  transform: translateX(0);
  opacity: 1;
  visibility: visible;
  transition:
    transform var(--sidebar-duration) var(--sidebar-easing),
    opacity var(--sidebar-duration) var(--sidebar-easing),
    visibility 0s;
}

.admin-shell-sidebar nav {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.3rem;
  padding: 1.25rem 0.9rem 1rem;
  min-height: 0;
  overflow-y: auto;
}

.sidebar-item {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 0.65rem;
  padding: 0.7rem 0.85rem;
  min-height: 42px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: rgba(255, 255, 255, 0.75);
  cursor: pointer;
  font-weight: 600;
  font-size: 0.85rem;
  line-height: 1.4;
  text-align: left;
  text-decoration: none;
}

.sidebar-item:hover {
  background: rgba(255, 255, 255, 0.08);
  color: white;
}

.sidebar-item.active {
  background: var(--surface-ffffff);
  color: var(--primary-ink);
  box-shadow: var(--shadow-sm);
}

.sidebar-item:focus-visible {
  outline: 2px solid #c4e8da;
  outline-offset: 2px;
}
.sidebar-item > svg {
  flex-shrink: 0;
}
.sidebar-item > span {
  min-width: 0;
  flex: 1;
}
.sidebar-group {
  flex-shrink: 0;
}
.group-toggle {
  color: var(--ink-e5f0eb);
}
.contains-active > .group-toggle,
.is-open > .group-toggle {
  background: rgba(255, 255, 255, 0.08);
  color: white;
}
.group-chevron {
  margin-left: auto;
  transition: transform 180ms ease;
}
.group-chevron.rotated {
  transform: rotate(180deg);
}
.group-items {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  margin: 0.3rem 0.25rem 0.4rem 1.35rem;
  padding-left: 0.65rem;
  border-left: 1px solid rgba(255, 255, 255, 0.22);
}
.group-item {
  padding: 0.55rem 0.65rem;
  min-height: 38px;
  font-size: 0.8125rem;
  font-weight: 500;
}

.admin-shell-footer {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  margin: 0 1rem 0.75rem;
  padding: 0.75rem 0.5rem;
  border-top: 1px solid rgba(255, 255, 255, 0.15);
  font-size: 0.75rem;
  font-weight: 700;
}

.admin-shell-content {
  min-width: 0;
  flex: 1;
  align-self: stretch;
  overflow-y: auto;
  overflow-x: hidden;
  background: var(--surface-ffffff);
  color: var(--text-main);
  font-size: var(--admin-text-body);
  line-height: 1.5;
  scrollbar-gutter: stable;
}

.learning-shell-content {
  padding: clamp(1rem, 2vw, 2rem);
  background: var(--bg-main);
  font-size: 1rem;
}

.permissions-shell-content {
  overflow: hidden;
  scrollbar-gutter: auto;
}

.admin-management-content :deep(.card-title) {
  font-size: var(--admin-heading-section);
  line-height: 1.3;
}

.admin-management-content :deep(.card-subtitle),
.admin-management-content :deep(.header-desc),
.admin-management-content :deep(.field-help),
.admin-management-content :deep(.video-source-help),
.admin-management-content :deep(.selected-file-meta) {
  font-size: var(--admin-text-caption);
  line-height: 1.5;
}

.admin-management-content :deep(.form-control),
.admin-management-content :deep(input),
.admin-management-content :deep(textarea),
.admin-management-content :deep(select),
.admin-management-content :deep(td) {
  font-size: var(--admin-text-body);
}

.admin-management-content :deep(label),
.admin-management-content :deep(th),
.admin-management-content :deep(.btn),
.admin-management-content :deep(button) {
  font-size: var(--admin-text-label);
}

.admin-management-content :deep(th),
.admin-management-content :deep(.form-group > label) {
  line-height: 1.4;
}

.admin-management-content
  :deep(.admin-tab-content .tab-pane:not(.overview-pane) .card) {
  padding: 0;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

@media (max-width: 768px) {
  .admin-shell {
    height: calc(100% + 2rem);
    margin: -1rem;
    overflow: clip;
    flex-direction: column;
  }

  .admin-shell-sidebar {
    width: 100%;
    flex: 0 0 auto;
    min-width: 0;
    height: auto;
    border-radius: 0 0 12px 0;
    box-shadow: 6px 10px 24px #0f172a33;
  }

  .admin-sidebar-frame {
    position: absolute;
    z-index: 30;
    top: 0;
    left: 0;
    width: min(320px, 100%);
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: 1fr;
    transition: grid-template-rows var(--sidebar-duration) var(--sidebar-easing);
  }

  .admin-sidebar-frame.is-collapsed {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: 0px;
  }

  .admin-sidebar-frame.is-collapsed .admin-shell-sidebar {
    transform: translateY(-100%);
  }

  .admin-shell-sidebar nav {
    padding: 0.75rem;
  }

  .admin-shell-footer {
    display: none;
  }

  .admin-shell-content {
    min-height: 0;
    overflow-y: auto;
  }
}

@media (max-height: 720px) {
  .admin-shell-sidebar nav {
    padding: 0.75rem;
    gap: 0.15rem;
  }
  .sidebar-item {
    min-height: 36px;
    padding: 0.5rem 0.75rem;
  }
  .group-item {
    min-height: 34px;
    padding: 0.4rem 0.6rem;
  }
  .admin-shell-footer {
    margin-bottom: 0.5rem;
    padding: 0.6rem 0.5rem;
  }
}
@media (max-height: 560px) {
  .admin-shell-sidebar nav {
    padding: 0.5rem 0.75rem;
    gap: 0.1rem;
  }
  .sidebar-item {
    min-height: 30px;
    padding: 0.35rem 0.65rem;
    font-size: 0.8rem;
  }
  .group-item {
    min-height: 28px;
    padding: 0.3rem 0.55rem;
    font-size: 0.75rem;
  }
  .group-items {
    margin-top: 0.2rem;
    margin-bottom: 0.25rem;
    gap: 0;
  }
  .admin-shell-footer {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .admin-sidebar-frame,
  .admin-sidebar-frame.is-collapsed .admin-shell-sidebar,
  .admin-shell-sidebar,
  .group-chevron {
    transition: none;
  }
}
</style>
