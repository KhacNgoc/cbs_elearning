<template>
  <div class="app-container" :class="{ 'admin-route': route.path.startsWith('/admin') }">
    <!-- Navbar Header (only when logged in and not on change-password page) -->
    <nav class="navbar" v-if="userStore.isAuthenticated && route.name !== 'ChangePassword'">
      <router-link to="/" class="nav-brand">
        <div class="nav-logo">B</div>
        <span>BIDV Core Training</span>
      </router-link>
      
      <div class="nav-links">
        <router-link v-for="item in navigationStore.mainItems" :key="item.id" :to="item.route" class="nav-link">
          <component :is="navigationIcon(item.icon)" class="nav-icon" />
          <span>{{ item.label }}</span>
        </router-link>
      </div>

      <div class="nav-user-menu">
        <div id="learning-chatbot-launcher"></div>
        <NotificationCenter />
        <router-link to="/profile" class="nav-user" title="Xem hồ sơ cá nhân">
          <div class="nav-user-avatar">{{ userAvatarLetter }}</div>
          <span>{{ userStore.fullName || 'quantrh' }}</span>
        </router-link>
        <button class="btn-logout" @click="handleLogout" title="Đăng xuất">
          <LogOut class="logout-icon" />
        </button>
      </div>
    </nav>

    <!-- Main Router View Content -->
    <main class="main-content">
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>

    <LearningChatbot
      v-if="showLearningChatbot"
      :locked="chatbotLocked"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { useUserStore } from './stores/userStore';
import { useCourseStore } from './stores/courseStore';
import { useNavigationStore } from './stores/navigationStore';
import NotificationCenter from './components/NotificationCenter.vue';
import LearningChatbot from './components/LearningChatbot.vue';
import { BookOpen, ClipboardCheck, HelpCircle, Home, LogOut, Settings, Trophy, UserCircle, Users } from 'lucide-vue-next';

const route = useRoute();
const userStore = useUserStore();
const courseStore = useCourseStore();
const navigationStore = useNavigationStore();
const iconRegistry = { BookOpen, ClipboardCheck, HelpCircle, Home, Settings, Trophy, UserCircle, Users };
const navigationIcon = (name: string) => iconRegistry[name as keyof typeof iconRegistry] || Home;
const showLearningChatbot = computed(() => userStore.isAuthenticated && route.name !== 'ChangePassword');
const chatbotLocked = computed(() => route.name === 'QuizView');

const userAvatarLetter = computed(() => {
  return (userStore.fullName || userStore.username || 'Q').charAt(0).toUpperCase();
});

const handleLogout = async () => {
  navigationStore.clear();
  await userStore.logout();
};

onMounted(async () => {
  if (userStore.isAuthenticated) {
    await Promise.all([
      userStore.fetchProgress(),
      courseStore.fetchCourses(),
      navigationStore.load()
    ]);
  }
});
</script>

<style>
/* Page transition animation */
.page-enter-active,
.page-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

.nav-icon {
  width: 18px;
  height: 18px;
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
  background: #d7e9e3;
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
  background-color: #fee2e2;
  color: #ef4444;
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
</style>
