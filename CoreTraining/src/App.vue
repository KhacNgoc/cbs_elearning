<template>
  <div class="app-container">
    <!-- Navbar Header (only when logged in and not on change-password page) -->
    <nav class="navbar" v-if="userStore.isAuthenticated && route.name !== 'ChangePassword'">
      <router-link to="/" class="nav-brand">
        <div class="nav-logo">B</div>
        <span>BIDV Core Training</span>
      </router-link>
      
      <div class="nav-links">
        <router-link to="/" class="nav-link">
          <Home class="nav-icon" />
          <span>Tổng quan</span>
        </router-link>
        
        <router-link to="/leaderboard" class="nav-link">
          <Trophy class="nav-icon" />
          <span>Bảng vinh danh</span>
        </router-link>
        
        <router-link v-slot="{ href, navigate, isActive }" v-if="userStore.role === 'ROLE_ADMIN'" to="/admin" custom>
          <a :href="href" @click="navigate" class="nav-link" :class="{ 'router-link-active': isActive }">
            <Settings class="nav-icon" />
            <span>Quản trị</span>
          </a>
        </router-link>
      </div>

      <div class="nav-user-menu">
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
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { useUserStore } from './stores/userStore';
import { useCourseStore } from './stores/courseStore';
import { Home, Trophy, LogOut, Settings } from 'lucide-vue-next';

const route = useRoute();
const userStore = useUserStore();
const courseStore = useCourseStore();

const userAvatarLetter = computed(() => {
  return (userStore.fullName || userStore.username || 'Q').charAt(0).toUpperCase();
});

const handleLogout = () => {
  userStore.logout();
};

onMounted(async () => {
  if (userStore.isAuthenticated) {
    await Promise.all([
      userStore.fetchProgress(),
      courseStore.fetchTopics()
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
  align-items: center;
  gap: 0.75rem;
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
</style>
