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
    <Teleport to="body"
      ><Transition name="toast">
        <div
          v-if="toast"
          class="system-toast"
          :class="`system-toast--${toast.variant}`"
          :role="toast.variant === 'error' ? 'alert' : 'status'"
        >
          <CircleAlert v-if="toast.variant === 'error'" :size="19" />
          <TriangleAlert v-else-if="toast.variant === 'warning'" :size="19" />
          <CircleCheck v-else-if="toast.variant === 'success'" :size="19" />
          <Info v-else :size="19" />
          <span>{{ toast.message }}</span>
          <button aria-label="Đóng" @click="toast = null">
            <X :size="16" />
          </button>
        </div> </Transition
    ></Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import {
  Bell,
  BellOff,
  CircleCheck,
  CircleAlert,
  Info,
  MessageCircleReply,
  TriangleAlert,
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
type ToastVariant = "info" | "success" | "warning" | "error";
const toast = ref<{ message: string; variant: ToastVariant } | null>(null);
let toastTimer: number | undefined;
let socket: NotificationSocket | undefined;
const unreadCount = computed(
  () => notifications.value.filter((item) => !item.read).length,
);

const showToast = (message: string, variant: ToastVariant = "info") => {
  toast.value = { message, variant };
  if (toastTimer) window.clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => (toast.value = null), 5000);
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
  const variant: ToastVariant =
    item.type === "ENROLLMENT_APPROVED"
      ? "success"
      : item.type === "ENROLLMENT_REJECTED"
        ? "warning"
        : "info";
  showToast(item.title, variant);
};
const openNotification = async (item: AppNotification) => {
  if (!item.read) {
    try {
      await notificationApi.markRead(item.id);
      item.read = true;
    } catch {
      showToast("Không thể cập nhật thông báo.", "error");
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
    showToast("Không thể cập nhật thông báo.", "error");
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
const handleAppToast = (event: Event) => {
  const detail = (
    event as CustomEvent<{ message?: string; variant?: ToastVariant }>
  ).detail;
  if (detail?.message) showToast(detail.message, detail.variant || "info");
};

onMounted(async () => {
  document.addEventListener("click", closeOnOutside);
  window.addEventListener("app-toast", handleAppToast);
  try {
    notifications.value = await notificationApi.getNotifications();
  } catch {
    showToast("Không thể tải danh sách thông báo.", "error");
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
  window.removeEventListener("app-toast", handleAppToast);
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
  color: var(--primary-ink);
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
  border: 2px solid var(--line-ffffff);
  border-radius: 99px;
  background: #dc2626;
  color: #fff;
  font-family: var(--font-family);
  font-size: 10px;
  font-weight: 700;
  font-variant-numeric: lining-nums tabular-nums;
  line-height: 1;
  letter-spacing: normal;
  text-align: center;
}
.notification-panel {
  position: absolute;
  top: calc(100% + 12px);
  right: 0;
  width: min(390px, calc(100vw - 24px));
  overflow: hidden;
  border: 1px solid var(--border-color);
  border-radius: 16px;
  background: var(--surface-ffffff);
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
  color: var(--ink-b45309);
  font-size: 0.7rem;
}
.notification-panel header span.connected {
  color: var(--ink-07845e);
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
  color: var(--primary-ink);
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
  border-bottom: 1px solid var(--line-eef2f6);
  background: var(--surface-ffffff);
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.notification-item.unread {
  background: var(--surface-f2faf7);
}
.notification-item:hover {
  background: var(--surface-f8fafc);
}
.notification-icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 11px;
  background: var(--surface-e5f3ee);
  color: var(--ink-006747);
}
.notification-icon.ENROLLMENT_REJECTED {
  background: var(--surface-fef2f2);
  color: var(--ink-b42318);
}
.notification-item strong {
  display: block;
  margin-bottom: 3px;
  font-size: 0.82rem;
}
.notification-item p {
  margin: 0 0 5px;
  color: var(--ink-64748b);
  font-size: 0.76rem;
  line-height: 1.4;
}
.notification-item small {
  color: var(--ink-94a3b8);
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
  color: var(--ink-94a3b8);
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
  border: 1px solid var(--line-bfdbfe);
  border-radius: 12px;
  background: var(--surface-eff6ff);
  color: var(--ink-1d4ed8);
  box-shadow: var(--shadow-lg);
  z-index: 3000;
  font-size: 0.82rem;
}
.system-toast--success {
  border-color: var(--line-a7f3d0);
  background: var(--surface-ecfdf5);
  color: var(--ink-047857);
}
.system-toast > svg {
  flex-shrink: 0;
}
.system-toast > span {
  min-width: 0;
  overflow-wrap: anywhere;
  white-space: pre-line;
}
.system-toast--warning {
  border-color: var(--line-fde68a);
  background: var(--surface-fffbeb);
  color: var(--ink-a16207);
}
.system-toast--error {
  border-color: var(--line-fecaca);
  background: var(--surface-fff7f7);
  color: var(--ink-991b1b);
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
