<template>
  <div class="app-container" :class="{ 'admin-route': showAdminSidebar }">
    <!-- Navbar Header (only when logged in and not on change-password page) -->
    <nav
      class="navbar"
      :class="{
        'learner-navbar': !userStore.hasAdminAccess,
        'lesson-navbar': isLessonPage && !userStore.hasAdminAccess,
      }"
      aria-label="Điều hướng chính"
      v-if="userStore.isAuthenticated && route.name !== 'ChangePassword'"
    >
      <div class="nav-brand-group">
        <button
          v-if="isLessonPage && !userStore.hasAdminAccess"
          type="button"
          class="lesson-navigation-toggle"
          aria-label="Điều hướng học viên"
          :aria-expanded="lessonNavigationOpen"
          aria-controls="learner-navigation"
          @click="lessonNavigationOpen = !lessonNavigationOpen"
        >
          <component :is="lessonNavigationOpen ? X : Menu" :size="20" />
        </button>
        <button
          v-if="showAdminSidebar"
          type="button"
          class="sidebar-toggle"
          :aria-label="sidebarVisible ? 'Ẩn sidebar' : 'Hiện sidebar'"
          :title="sidebarVisible ? 'Ẩn sidebar' : 'Hiện sidebar'"
          :aria-expanded="sidebarVisible"
          aria-controls="admin-navigation-sidebar"
          @click="sidebarVisible = !sidebarVisible"
        >
          <component
            :is="sidebarVisible ? PanelLeftClose : PanelLeftOpen"
            :size="21"
          />
        </button>
        <router-link
          :to="
            showAdminSidebar ? navigationStore.homeRoute : '/'
          "
          class="nav-brand"
          aria-label="BIDV Core Training — Trang chủ"
        >
          <img class="nav-logo" :src="appLogo" alt="" width="32" height="32" />
          <span class="nav-brand-full" aria-hidden="true">BIDV Core Training</span>
          <span class="nav-brand-short" aria-hidden="true">BIDV</span>
        </router-link>
      </div>

      <div
        v-if="!userStore.hasAdminAccess"
        id="learner-navigation"
        class="nav-links"
        :class="{ 'lesson-navigation-open': lessonNavigationOpen }"
        aria-label="Điều hướng học viên"
      >
        <router-link
          v-for="item in navigationStore.mainItems"
          :key="item.id"
          :to="item.route"
          class="nav-link"
          :aria-label="item.label"
          :title="item.label"
        >
          <component :is="navigationIcon(item.icon)" class="nav-icon" />
          <span>{{ item.label }}</span>
        </router-link>
      </div>

      <div class="nav-user-menu">
        <div id="learning-chatbot-launcher"></div>
        <ThemeToggle />
        <NotificationCenter />
        <router-link to="/profile" class="nav-user" title="Xem hồ sơ cá nhân" :aria-label="`Hồ sơ cá nhân: ${userStore.fullName}`">
          <div class="nav-user-avatar">{{ userAvatarLetter }}</div>
          <span>{{ userStore.fullName || "quantrh" }}</span>
        </router-link>
        <button class="btn-logout" @click="handleLogout" title="Đăng xuất" aria-label="Đăng xuất">
          <LogOut class="logout-icon" />
        </button>
      </div>
    </nav>
    <ThemeToggle
      v-if="!userStore.isAuthenticated || route.name === 'ChangePassword'"
      floating
    />

    <!-- Main Router View Content -->
    <main class="main-content">
      <component
        :is="showAdminSidebar ? AdminLayout : 'div'"
        v-bind="showAdminSidebar ? { sidebarVisible } : {}"
        @navigate="closeMobileNavigation"
      >
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </component>
    </main>

    <LearningChatbot v-if="showLearningChatbot" :locked="chatbotLocked" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { useUserStore } from "./stores/userStore";
import { useNavigationStore } from "./stores/navigationStore";
import NotificationCenter from "./components/NotificationCenter.vue";
import ThemeToggle from "./components/ThemeToggle.vue";
import LearningChatbot from "./components/LearningChatbot.vue";
import AdminLayout from "./views/AdminLayout.vue";
import appLogo from "./assets/app-logo.png";
import {
  BookOpen,
  ClipboardCheck,
  HelpCircle,
  Home,
  Layers3,
  LogOut,
  Menu,
  X,
  PanelLeftClose,
  PanelLeftOpen,
  Settings,
  Trophy,
  UserCircle,
  Users,
} from "lucide-vue-next";

const route = useRoute();
const isLessonPage = computed(() => route.name === "LessonDetail");
const lessonNavigationOpen = ref(false);
watch(
  () => route.fullPath,
  () => { lessonNavigationOpen.value = false; },
);
const userStore = useUserStore();
const navigationStore = useNavigationStore();
const showAdminSidebar = computed(
  () =>
    userStore.isAuthenticated &&
    userStore.hasAdminAccess &&
    route.name !== "ChangePassword",
);
const sidebarVisible = ref(
  localStorage.getItem("adminSidebarVisible") !== "false",
);
function closeMobileNavigation() {
  if (window.matchMedia("(max-width: 768px)").matches)
    sidebarVisible.value = false;
}
watch(sidebarVisible, (visible) => {
  try {
    localStorage.setItem("adminSidebarVisible", String(visible));
  } catch {
    /* Keep the current session usable if storage is unavailable. */
  }
});
const iconRegistry = {
  BookOpen,
  ClipboardCheck,
  HelpCircle,
  Home,
  Layers3,
  Settings,
  Trophy,
  UserCircle,
  Users,
};
const navigationIcon = (name: string) =>
  iconRegistry[name as keyof typeof iconRegistry] || Home;
const showLearningChatbot = computed(
  () => userStore.isAuthenticated && route.name !== "ChangePassword",
);
const chatbotLocked = computed(() => route.name === "QuizView");

const userAvatarLetter = computed(() => {
  return (userStore.fullName || userStore.username || "Q")
    .charAt(0)
    .toUpperCase();
});

const handleLogout = async () => {
  navigationStore.clear();
  await userStore.logout();
};

onMounted(async () => {
  if (userStore.isAuthenticated) {
    await navigationStore.load();
  }
});
</script>

<style>
/* Page transition animation */
.page-enter-active {
  transition:
    opacity var(--motion-duration) var(--motion-easing),
    transform var(--motion-duration) var(--motion-easing);
}
.page-leave-active {
  transition:
    opacity var(--motion-fast) ease,
    transform var(--motion-fast) ease;
  pointer-events: none;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.nav-icon {
  width: 18px;
  height: 18px;
}

.nav-brand-group {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-width: 0;
}
.nav-brand-short {
  display: none;
}
.nav-logo,
.nav-user-avatar {
  flex-shrink: 0;
}
.nav-user > span {
  max-width: 180px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.sidebar-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 40px;
  width: 40px;
  height: 40px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-ffffff);
  color: var(--primary-ink);
  cursor: pointer;
}
.sidebar-toggle:hover {
  background: var(--primary-light);
}
.sidebar-toggle:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: 3px;
}

.nav-user-menu {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 0.75rem;
}

#learning-chatbot-launcher {
  display: flex;
  align-items: center;
}

.nav-user {
  transition: var(--transition);
}

.nav-user:hover,
.nav-user.router-link-active {
  background: var(--surface-d7e9e3);
  box-shadow: inset 0 0 0 1px rgba(0, 91, 65, 0.16);
}

.btn-logout {
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0.5rem;
  border-radius: var(--radius-sm);
  transition: var(--transition);
}

.btn-logout:hover {
  background-color: var(--surface-fee2e2);
  color: var(--ink-ef4444);
}

.logout-icon {
  width: 18px;
  height: 18px;
}

@media (max-width: 560px) {
  .navbar {
    flex-wrap: wrap;
  }

  .nav-links {
    order: 3;
    flex: 1 0 100%;
  }

  .nav-user-menu {
    gap: 0.35rem;
  }
}

/* Equal side columns keep the menu centred on the screen, not in the gap between brand and user menu.
   The user menu never shrinks below its content, so on narrow desktops the menu drifts left instead of overlapping. */
@media (min-width: 1101px) {
  .navbar.learner-navbar {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto minmax(max-content, 1fr);
  }
  .learner-navbar .nav-user-menu {
    justify-self: end;
  }
}

/* Learner navigation has a dedicated second row on tablet and mobile. */
@media (max-width: 1100px) {
  .navbar.learner-navbar {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 10px 12px;
    padding: 12px 16px 8px;
  }
  .learner-navbar .nav-brand-group {
    grid-column: 1;
    grid-row: 1;
  }
  .learner-navbar .nav-brand {
    font-size: 1.1rem;
    gap: 8px;
  }
  .learner-navbar .nav-user-menu {
    grid-column: 2;
    grid-row: 1;
    justify-content: flex-end;
    gap: 8px;
  }
  .learner-navbar .nav-links {
    grid-column: 1 / -1;
    grid-row: 2;
    width: 100%;
    justify-content: flex-start;
    gap: 8px;
    padding-top: 8px;
    border-top: 1px solid var(--border-color);
    overflow-x: auto;
    scrollbar-width: thin;
  }
  .learner-navbar .nav-link {
    flex: 1 0 120px;
    justify-content: center;
    min-height: 44px;
    padding: 8px 12px;
    white-space: nowrap;
    font-size: 0.8125rem;
  }
  .learner-navbar .nav-link > span {
    display: inline;
  }
  .learner-navbar .nav-user > span {
    display: none;
  }
  .learner-navbar .nav-user {
    justify-content: center;
    width: 44px;
    height: 44px;
    padding: 0;
  }
  .learner-navbar .btn-logout {
    width: 44px;
    height: 44px;
    padding: 0;
  }
}
@media (max-width: 560px) {
  .navbar.learner-navbar {
    padding-inline: 12px;
    column-gap: 8px;
  }
  .learner-navbar .nav-brand-full {
    display: none;
  }
  .learner-navbar .nav-brand-short {
    display: inline;
  }
  .learner-navbar .nav-link {
    padding-inline: 8px;
    gap: 6px;
  }
  .learner-navbar .nav-user-menu {
    gap: 4px;
  }
  .learner-navbar .theme-toggle,
  .learner-navbar .notification-trigger {
    width: 44px;
    height: 44px;
  }
}
@media (max-width: 360px) {
  .learner-navbar .nav-brand-short {
    display: none;
  }
}
.lesson-navigation-toggle { display: none; }
@media (max-width: 900px) {
  .navbar.lesson-navbar {
    grid-template-columns: auto minmax(0, 1fr);
    gap: 0 8px;
    min-height: 60px;
    padding: 8px 12px;
  }
  .lesson-navbar .nav-brand-group { gap: 8px; }
  .lesson-navbar .nav-brand-full,
  .lesson-navbar .nav-brand-short { display: none; }
  .lesson-navigation-toggle {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    padding: 0;
    border: 1px solid var(--border-color);
    border-radius: 8px;
    color: var(--text-main);
    background: transparent;
    cursor: pointer;
  }
  .lesson-navigation-toggle:focus-visible {
    outline: 2px solid var(--primary-color);
    outline-offset: 2px;
  }
  .lesson-navbar .nav-links { display: none; }
  .lesson-navbar .nav-links.lesson-navigation-open {
    display: flex;
    margin-top: 8px;
  }
  .lesson-navbar .nav-user-menu { gap: 4px; min-width: 0; }
  .lesson-navbar .theme-toggle,
  .lesson-navbar .notification-trigger,
  .lesson-navbar .nav-user,
  .lesson-navbar .btn-logout,
  .lesson-navbar .chat-launcher {
    width: 40px;
    min-width: 40px;
    height: 40px;
    min-height: 40px;
    padding: 0;
    justify-content: center;
  }
  .lesson-navbar .chat-launcher span { display: none; }
}
@media (max-width: 360px) {
  .lesson-navbar .lesson-navigation-toggle,
  .lesson-navbar .theme-toggle,
  .lesson-navbar .notification-trigger,
  .lesson-navbar .nav-user,
  .lesson-navbar .btn-logout,
  .lesson-navbar .chat-launcher {
    width: 36px;
    min-width: 36px;
    height: 36px;
    min-height: 36px;
  }
}
</style>
