<template>
  <div class="admin-shell">
    <div class="admin-sidebar-frame" :class="{ 'is-collapsed': !sidebarVisible }" :inert="!sidebarVisible" :aria-hidden="!sidebarVisible">
    <aside id="admin-navigation-sidebar" class="admin-shell-sidebar" aria-label="Điều hướng quản trị">
      <nav>
        <button
          v-for="item in navigation"
          :key="item.id"
          :class="{ active: activeSection === item.id }"
          @click="openSection(item.id)"
        >
          <component :is="navigationIcon(item.icon)" :size="18" />
          <span>{{ item.label }}</span>
        </button>
        <template v-if="learningNavigation.length">
          <span class="sidebar-section-label">Học tập</span>
          <button v-for="item in learningNavigation" :key="item.id" :class="{ active: !route.path.startsWith('/admin') && route.path === item.route }" @click="router.push(item.route)">
            <component :is="navigationIcon(item.icon)" :size="18" />
            <span>{{ item.id === 'dashboard' ? 'Tổng quan học tập' : item.label }}</span>
          </button>
        </template>
      </nav>
      <div class="admin-shell-footer">
        <Settings :size="18" />
        <span>{{ user.roles.map(code=>user.roleNames[code] || code).join(', ') }}</span>
      </div>
    </aside>
    </div>
    <section class="admin-shell-content" :class="[route.path.startsWith('/admin') ? 'admin-management-content' : 'learning-shell-content', { 'permissions-shell-content': route.name === 'AdminPermissions' }]">
      <slot><RouterView /></slot>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { onMounted } from "vue";
import { useNavigationStore } from "../stores/navigationStore";
import { BookOpen, ClipboardCheck, FileText, HelpCircle, Home, Trophy, Settings, Users, Building2, ShieldCheck, UsersRound } from "lucide-vue-next";

import { useUserStore } from '../stores/userStore';

withDefaults(defineProps<{ sidebarVisible?: boolean }>(), { sidebarVisible: true });
const user=useUserStore();
const route = useRoute();
const router = useRouter();
const navigationStore = useNavigationStore();
const navigation = computed(() => navigationStore.adminItems);
const learningNavigation = computed(() => user.canLearn ? navigationStore.mainItems.filter(item => item.id !== 'admin') : []);
const iconRegistry = { BookOpen, ClipboardCheck, FileText, HelpCircle, Home, Trophy, Settings, Users, Building2, ShieldCheck, UsersRound };
const navigationIcon = (name: string) => iconRegistry[name as keyof typeof iconRegistry] || Settings;

const activeSection = computed(() =>
  route.path.startsWith("/admin/users") ? "admin.users"
    : route.path.startsWith("/admin/units") ? "admin.units"
    : route.path.startsWith("/admin/groups") ? "admin.groups"
    : route.path.startsWith("/admin/roles") ? "admin.roles"
    : route.path.startsWith("/admin/permissions") ? "admin.permissions"
    : route.path.startsWith("/admin/catalog") ? "admin.catalog"
    : route.path.startsWith("/admin/courses") || route.path.endsWith("/edit") ? "admin.courses"
    : route.path.includes("/admin/quizzes")
    ? "admin.exams"
    : route.name === "AdminCourseLessons" || route.path.startsWith("/admin/lessons")
      ? "admin.lessons"
    : typeof route.query.tab === "string"
      ? `admin.${route.query.tab}`
          : route.path.startsWith('/admin') ? "admin.overview" : "",
);

const openSection = (id: string) => {
  const item = navigationStore.adminItems.find(entry => entry.id === id);
  if (item) router.push(item.route);
};
onMounted(() => navigationStore.load());
</script>

<style scoped>
.admin-shell {
  --sidebar-duration: 320ms;
  --sidebar-easing: cubic-bezier(.22, 1, .36, 1);
  --admin-text-caption: 0.75rem;
  --admin-text-label: 0.8125rem;
  --admin-text-body: 0.875rem;
  --admin-heading-section: 1.125rem;
  --admin-heading-page: 1.5rem;
  display: flex;
  height: calc(100% + 4rem);
  min-height: 0;
  margin: -2rem;
  overflow: hidden;
  background: var(--bg-main);
}

.admin-sidebar-frame {
  display: grid;
  grid-template-columns: 280px;
  grid-template-rows: minmax(0, 1fr);
  flex: 0 0 auto;
  min-height: 0;
  overflow: hidden;
  transition: grid-template-columns var(--sidebar-duration) var(--sidebar-easing);
}

.admin-sidebar-frame.is-collapsed { grid-template-columns: 0px; }
.admin-sidebar-frame.is-collapsed .admin-shell-sidebar {
  transform: translateX(-100%);
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transition: transform var(--sidebar-duration) var(--sidebar-easing), opacity var(--sidebar-duration) var(--sidebar-easing), visibility 0s linear var(--sidebar-duration);
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
  transition: transform var(--sidebar-duration) var(--sidebar-easing), opacity var(--sidebar-duration) var(--sidebar-easing), visibility 0s;
}

.admin-shell-sidebar nav {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.5rem;
  padding: 2rem 1rem 1.5rem;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.admin-shell-sidebar button {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.85rem 1.25rem;
  border: 0;
  border-radius: var(--radius-md);
  background: transparent;
  color: rgba(255, 255, 255, 0.75);
  cursor: pointer;
  font-weight: 600;
  text-align: left;
}

.admin-shell-sidebar button:hover {
  background: rgba(255, 255, 255, 0.08);
  color: white;
}

.admin-shell-sidebar button.active {
  background: white;
  color: var(--primary-color);
  box-shadow: var(--shadow-sm);
}

.sidebar-section-label {
  margin-top: .75rem;
  padding: .75rem 1.25rem 0;
  border-top: 1px solid rgba(255, 255, 255, .15);
  color: rgba(255, 255, 255, .6);
  font-size: .75rem;
  font-weight: 600;
}

.admin-shell-footer {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  margin: 0 1rem 1rem;
  padding: 1rem 0.5rem;
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
  background: white;
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

.permissions-shell-content { overflow: hidden; scrollbar-gutter: auto; }

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

.admin-management-content :deep(.admin-tab-content .tab-pane:not(.overview-pane) .card) {
  padding: 0;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

@media (max-width: 768px) {
  .admin-shell {
    height: calc(100% + 2rem);
    margin: -1rem;
    overflow: hidden;
    flex-direction: column;
  }

  .admin-shell-sidebar {
    width: 100%;
    flex: 0 0 auto;
    min-width: 0;
  }

  .admin-sidebar-frame {
    width: 100%;
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: 4.25rem;
    transition: grid-template-rows var(--sidebar-duration) var(--sidebar-easing);
  }

  .admin-sidebar-frame.is-collapsed {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: 0px;
  }

  .admin-sidebar-frame.is-collapsed .admin-shell-sidebar { transform: translateY(-100%); }

  .admin-shell-sidebar nav {
    flex-direction: row;
    overflow-x: auto;
    padding: .65rem;
    gap: .5rem;
  }

  .admin-shell-sidebar button {
    flex-shrink: 0;
    padding: .7rem .85rem;
    white-space: nowrap;
  }

  .admin-shell-footer {
    display: none;
  }

  .admin-shell-content {
    min-height: 0;
    overflow-y: auto;
  }
}

@media (prefers-reduced-motion: reduce) {
  .admin-sidebar-frame,
  .admin-sidebar-frame.is-collapsed .admin-shell-sidebar,
  .admin-shell-sidebar { transition: none; }
}
</style>
