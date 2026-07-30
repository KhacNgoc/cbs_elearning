<template>
  <div ref="root" class="notification-center">
    <button
      class="notification-trigger"
      aria-label="Thông báo"
      :aria-expanded="open"
      @click="open = !open"
    >
      <Bell :size="20" />
      <span v-if="unreadCount" class="notification-badge">{{
        unreadCount > 99 ? "99+" : unreadCount
      }}</span>
    </button>
    <Transition name="panel">
      <section v-if="open" class="notification-panel">
        <header>
          <div style="display: flex; gap: 5px">
            <h2>Thông báo</h2>
            <span :class="{ connected }"
              ><i></i>{{ !connected ? "Đang kết nối lại" : "" }}</span
            >
          </div>
          <button v-if="unreadCount" @click="markAllRead">Đã đọc tất cả</button>
        </header>
        <div class="notification-list">
          <button
            v-for="item in notifications"
            :key="item.id"
            class="notification-item"
            :class="{ unread: !item.read }"
            @click="openNotification(item)"
          >
            <span class="notification-icon" :class="item.type">
              <MessageCircleReply
                v-if="item.type === 'COMMENT_REPLY'"
                :size="18"
              />
              <UserRoundCheck v-else :size="18" />
            </span>
            <span
              ><strong>{{ item.title }}</strong>
              <p>{{ item.message }}</p>
              <small>{{ formatTime(item.createdAt) }}</small></span
            >
            <i v-if="!item.read" class="unread-dot"></i>
          </button>
          <div v-if="!notifications.length" class="notification-empty">
            <BellOff :size="28" /><span>Chưa có thông báo.</span>
          </div>
        </div>
      </section>
    </Transition>
    <Transition name="toast">
      <div v-if="toast" class="system-toast" role="alert">
        <CircleAlert :size="19" /><span>{{ toast }}</span>
        <button aria-label="Đóng" @click="toast = ''"><X :size="16" /></button>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import {
  Bell,
  BellOff,
  CircleAlert,
  MessageCircleReply,
  UserRoundCheck,
  X,
} from "lucide-vue-next";
import { notificationApi, type AppNotification } from "../api/notificationApi";
import { NotificationSocket } from "../services/notificationSocket";
import { useUserStore } from "../stores/userStore";

const router = useRouter();
const userStore = useUserStore();
const root = ref<HTMLElement | null>(null);
const open = ref(false);
const connected = ref(false);
const notifications = ref<AppNotification[]>([]);
const toast = ref("");
let toastTimer: number | undefined;
let socket: NotificationSocket | undefined;
const unreadCount = computed(
  () => notifications.value.filter((item) => !item.read).length,
);

const showToast = (message: string) => {
  toast.value = message;
  if (toastTimer) window.clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => (toast.value = ""), 5000);
};
const receiveNotification = (item: AppNotification) => {
  if (!notifications.value.some((existing) => existing.id === item.id))
    notifications.value.unshift(item);
  if (
    item.type === "ENROLLMENT_APPROVED" ||
    item.type === "ENROLLMENT_REJECTED"
  ) {
    void userStore.fetchProgress();
  }
  showToast(item.title);
};
const openNotification = async (item: AppNotification) => {
  if (!item.read) {
    try {
      await notificationApi.markRead(item.id);
      item.read = true;
    } catch {
      showToast("Không thể cập nhật thông báo.");
    }
  }
  open.value = false;
  if (item.targetUrl) await router.push(item.targetUrl);
};
const markAllRead = async () => {
  try {
    await notificationApi.markAllRead();
    notifications.value.forEach((item) => (item.read = true));
  } catch {
    showToast("Không thể cập nhật thông báo.");
  }
};
const formatTime = (value: string) =>
  new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
const closeOnOutside = (event: MouseEvent) => {
  if (root.value && !root.value.contains(event.target as Node))
    open.value = false;
};
const handleSystemError = (event: Event) =>
  showToast(
    (event as CustomEvent<string>).detail || "Hệ thống đang gặp sự cố.",
  );

onMounted(async () => {
  document.addEventListener("click", closeOnOutside);
  window.addEventListener("system-error", handleSystemError);
  try {
    notifications.value = await notificationApi.getNotifications();
  } catch {
    showToast("Không thể tải danh sách thông báo.");
  }
  socket = new NotificationSocket(
    receiveNotification,
    (value) => (connected.value = value),
  );
  socket.start();
});
onBeforeUnmount(() => {
  socket?.stop();
  document.removeEventListener("click", closeOnOutside);
  window.removeEventListener("system-error", handleSystemError);
  if (toastTimer) window.clearTimeout(toastTimer);
});
</script>

<style scoped>
.notification-center {
  position: relative;
}
.notification-trigger {
  position: relative;
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}
.notification-trigger:hover {
  background: var(--primary-light);
  color: var(--primary-color);
}
.notification-badge {
  position: absolute;
  top: 0;
  right: -2px;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  display: grid;
  place-items: center;
  border: 2px solid #fff;
  border-radius: 99px;
  background: #dc2626;
  color: #fff;
  font-size: 10px;
  font-weight: 800;
}
.notification-panel {
  position: absolute;
  top: calc(100% + 12px);
  right: 0;
  width: min(390px, calc(100vw - 24px));
  overflow: hidden;
  border: 1px solid var(--border-color);
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 22px 60px rgba(15, 23, 42, 0.2);
  z-index: 300;
}
.notification-panel header {
  padding: 16px 18px;
  display: flex;
  justify-content: space-between;
  border-bottom: 1px solid var(--border-color);
}
.notification-panel h2 {
  margin: 0 0 3px;
  font-size: 1rem;
}
.notification-panel header span {
  display: flex;
  align-items: center;
  gap: 5px;
  color: #b45309;
  font-size: 0.7rem;
}
.notification-panel header span.connected {
  color: #07845e;
}
.notification-panel header span i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
}
.notification-panel header button {
  border: 0;
  background: transparent;
  color: var(--primary-color);
  cursor: pointer;
  font-size: 0.72rem;
  font-weight: 700;
}
.notification-list {
  max-height: min(520px, calc(100vh - 150px));
  overflow-y: auto;
}
.notification-item {
  position: relative;
  width: 100%;
  padding: 14px 18px;
  display: grid;
  grid-template-columns: 38px 1fr 8px;
  gap: 10px;
  border: 0;
  border-bottom: 1px solid #eef2f6;
  background: #fff;
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.notification-item.unread {
  background: #f2faf7;
}
.notification-item:hover {
  background: #f8fafc;
}
.notification-icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 11px;
  background: #e5f3ee;
  color: #006747;
}
.notification-icon.ENROLLMENT_REJECTED {
  background: #fef2f2;
  color: #b42318;
}
.notification-item strong {
  display: block;
  margin-bottom: 3px;
  font-size: 0.82rem;
}
.notification-item p {
  margin: 0 0 5px;
  color: #64748b;
  font-size: 0.76rem;
  line-height: 1.4;
}
.notification-item small {
  color: #94a3b8;
  font-size: 0.67rem;
}
.unread-dot {
  align-self: center;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #00845e;
}
.notification-empty {
  min-height: 180px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #94a3b8;
  font-size: 0.82rem;
}
.system-toast {
  position: fixed;
  top: 86px;
  right: 22px;
  max-width: 390px;
  padding: 12px 14px;
  display: flex;
  align-items: center;
  gap: 9px;
  border: 1px solid #fecaca;
  border-radius: 12px;
  background: #fff7f7;
  color: #991b1b;
  box-shadow: var(--shadow-lg);
  z-index: 500;
  font-size: 0.82rem;
}
.system-toast button {
  margin-left: auto;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}
.panel-enter-active,
.panel-leave-active,
.toast-enter-active,
.toast-leave-active {
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}
.panel-enter-from,
.panel-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(16px);
}
@media (max-width: 640px) {
  .notification-panel {
    position: fixed;
    top: 68px;
    right: 12px;
  }
  .system-toast {
    top: 76px;
    right: 12px;
    left: 12px;
    max-width: none;
  }
}
</style>
