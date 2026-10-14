<template>
  <aside
    ref="chatbotElement"
    class="learning-chatbot"
    :class="{ 'is-locked': locked, 'is-dragging': dragging }"
    :style="{ left: `${position.x}px`, top: `${position.y}px` }"
  >
    <Transition name="chat-panel">
      <section
        v-if="open"
        id="learning-chatbot-panel"
        class="chat-panel"
        role="dialog"
        aria-labelledby="learning-chatbot-title"
        @keydown.esc.stop="closeChat"
      >
        <header class="chat-header" @pointerdown="startDrag">
          <div class="chat-assistant">
            <span class="assistant-avatar" aria-hidden="true">
              <Bot :size="22" />
            </span>
            <div>
              <h2 id="learning-chatbot-title">Trợ lý học tập</h2>
              <span class="assistant-status">
                <i aria-hidden="true"></i>
                {{ locked ? "Tạm khóa trong bài thi" : "Sẵn sàng hỗ trợ" }}
              </span>
            </div>
          </div>
          <button
            ref="closeButton"
            class="chat-close"
            type="button"
            aria-label="Đóng trợ lý học tập"
            @pointerdown.stop
            @click="closeChat"
          >
            <X :size="19" />
          </button>
        </header>

        <div v-if="locked" class="locked-content">
          <span class="locked-icon"><ShieldCheck :size="30" /></span>
          <span class="locked-eyebrow">Chế độ bài kiểm tra</span>
          <h3>Trợ lý đang tạm khóa</h3>
          <p>
            Để đảm bảo tính minh bạch, bạn không thể sử dụng trợ lý trong thời
            gian làm bài. Trợ lý sẽ hoạt động lại khi bạn quay về bài học.
          </p>
        </div>

        <template v-else>
          <div class="chat-course-filter">
            <label for="chat-course">Khóa học</label>
            <select id="chat-course" v-model="selectedCourseId" :disabled="sending" @change="loadHistory">
              <option v-for="course in accessibleCourses" :key="course.id" :value="course.id">
                {{ course.name }}
              </option>
            </select>
          </div>

          <div ref="chatBody" class="chat-body">
            <div class="chat-date">Hôm nay</div>
            <div v-if="loadingHistory" class="chat-loading">Đang tải lịch sử trao đổi...</div>
            <div v-else-if="messages.length === 0" class="assistant-message">
              <span class="message-avatar"><Bot :size="17" /></span>
              <div>
                <p>
                  Xin chào! Tôi chỉ trả lời dựa trên tài liệu đã được index của
                  khóa học đang chọn.
                </p>
                <span>Trợ lý học tập · Bây giờ</span>
              </div>
            </div>

            <article v-for="message in messages" :key="message.id" class="chat-exchange">
              <div class="user-message"><p>{{ message.question }}</p></div>
              <div class="assistant-message">
                <span class="message-avatar"><Bot :size="17" /></span>
                <div class="assistant-answer">
                  <p>{{ message.answer }}</p>
                  <span class="grounding-state" :class="{ grounded: message.grounded }">
                    {{ message.needsClarification
                      ? 'Cần làm rõ câu hỏi'
                      : message.grounded ? 'Có nguồn tài liệu' : 'Không tìm thấy trong tài liệu' }}
                  </span>
                  <ul v-if="message.sources.length" class="chat-sources">
                    <li v-for="source in message.sources" :key="`${message.id}-${source.documentId}-${source.page}-${source.section}`">
                      <FileText :size="13" />
                      <span><strong>{{ source.documentName }}</strong> · Trang {{ source.page }} · {{ source.section }}</span>
                    </li>
                  </ul>
                  <small>{{ formatTime(message.createdAt) }}</small>
                </div>
              </div>
            </article>

            <template v-if="pendingQuestion">
              <div class="user-message"><p>{{ pendingQuestion }}</p></div>
              <div class="assistant-message chat-thinking">
                <span class="message-avatar"><Bot :size="17" /></span>
                <p>
                  <LoaderCircle :size="15" />
                  {{ longWait
                    ? 'Mô hình đang tổng hợp câu trả lời, quá trình này có thể mất thêm một chút thời gian...'
                    : 'Đang tìm trong tài liệu khóa học...' }}
                </p>
              </div>
            </template>

            <p v-if="chatError" class="chat-error">{{ chatError }}</p>

            <div v-if="messages.length === 0 && !loadingHistory" class="quick-prompts" aria-label="Câu hỏi gợi ý">
              <button v-for="prompt in quickPrompts" :key="prompt" type="button" @click="askPrompt(prompt)">
                {{ prompt }}
              </button>
            </div>
          </div>

          <form class="chat-composer" @submit.prevent="sendQuestion">
            <label class="sr-only" for="learning-chat-input">
              Nhập câu hỏi về bài học
            </label>
            <textarea
              id="learning-chat-input"
              v-model="question"
              rows="1"
              placeholder="Hỏi về nội dung bài học..."
              :disabled="sending || !selectedCourseId"
              @keydown.enter.exact="handleQuestionEnter"
            ></textarea>
            <button type="submit" aria-label="Gửi câu hỏi" :disabled="sending || !question.trim() || !selectedCourseId">
              <SendHorizontal :size="19" />
            </button>
            <p>
              <Sparkles :size="13" /> Chỉ trả lời từ tài liệu của khóa học đã chọn
            </p>
          </form>
        </template>
      </section>
    </Transition>

  </aside>
  <Teleport defer to="#learning-chatbot-launcher">
    <button
      ref="launcherButton"
      class="chat-launcher"
      :class="{ 'is-locked': locked, 'is-active': open }"
      type="button"
      :aria-label="open ? 'Đóng trợ lý học tập' : locked ? 'Trợ lý học tập đang bị khóa' : 'Mở trợ lý học tập'"
      :aria-expanded="open"
      aria-controls="learning-chatbot-panel"
      :title="locked ? 'Trợ lý tạm khóa trong bài thi' : 'Trợ lý học tập'"
      @click="toggleChat"
    >
      <LockKeyhole v-if="locked" :size="20" aria-hidden="true" />
      <MessageCircleMore v-else :size="20" aria-hidden="true" />
      <span>Trợ lý</span>
    </button>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { useCourseStore } from "../stores/courseStore";
import { useUserStore } from "../stores/userStore";
import { ragApi } from "../api/ragApi";
import { isTimeoutError } from "../api/axiosClient";
import type { RagChatHistory } from "../api/ragApi";
import {
  Bot,
  FileText,
  LoaderCircle,
  LockKeyhole,
  MessageCircleMore,
  SendHorizontal,
  ShieldCheck,
  Sparkles,
  X,
} from "lucide-vue-next";

const props = defineProps<{ locked: boolean }>();
const route = useRoute();
const courseStore = useCourseStore();
const userStore = useUserStore();
const open = ref(false);
const launcherButton = ref<HTMLButtonElement | null>(null);
const closeButton = ref<HTMLButtonElement | null>(null);
const chatbotElement = ref<HTMLElement | null>(null);
const dragging = ref(false);
const position = ref({ x: 0, y: 0 });
const chatBody = ref<HTMLElement | null>(null);
const selectedCourseId = ref("");
const messages = ref<RagChatHistory[]>([]);
const conversationIds = ref<Record<string, string>>({});
const question = ref("");
const pendingQuestion = ref("");
const sending = ref(false);
const longWait = ref(false);
const loadingHistory = ref(false);
const chatError = ref("");
const quickPrompts = [
  "Tóm tắt nội dung chính của khóa học",
  "Các bước nghiệp vụ quan trọng là gì?",
  "Những điều kiện nào cần lưu ý?",
];
const dragOffset = { x: 0, y: 0 };
let longWaitTimer: number | undefined;

const clearLongWaitTimer = () => {
  if (longWaitTimer) window.clearTimeout(longWaitTimer);
  longWaitTimer = undefined;
  longWait.value = false;
};

const accessibleCourses = computed(() =>
  courseStore.courses.filter(course => userStore.isCourseAccessible(course.id)),
);

const scrollToLatest = async () => {
  await nextTick();
  if (chatBody.value) chatBody.value.scrollTop = chatBody.value.scrollHeight;
};

const selectCourseFromContext = () => {
  const contextualCourseId = route.name === "LessonDetail"
    ? courseStore.currentCourse?.id
    : undefined;
  const nextCourseId = contextualCourseId && accessibleCourses.value.some(course => course.id === contextualCourseId)
    ? contextualCourseId
    : accessibleCourses.value.some(course => course.id === selectedCourseId.value)
      ? selectedCourseId.value
      : accessibleCourses.value[0]?.id || "";
  const changed = nextCourseId !== selectedCourseId.value;
  selectedCourseId.value = nextCourseId;
  if (changed) messages.value = [];
  return changed;
};

const loadHistory = async () => {
  if (!selectedCourseId.value) {
    messages.value = [];
    return;
  }
  loadingHistory.value = true;
  chatError.value = "";
  try {
    messages.value = await ragApi.getHistory(
      selectedCourseId.value,
      conversationIds.value[selectedCourseId.value],
    );
    const loadedConversationId = messages.value[0]?.conversationId;
    if (loadedConversationId) conversationIds.value[selectedCourseId.value] = loadedConversationId;
    await scrollToLatest();
  } catch (error) {
    console.error("Failed to load chat history:", error);
    chatError.value = "Không thể tải lịch sử trợ lý.";
  } finally {
    loadingHistory.value = false;
  }
};

const sendQuestion = async () => {
  const content = question.value.trim();
  if (!content || !selectedCourseId.value || sending.value) return;
  sending.value = true;
  chatError.value = "";
  pendingQuestion.value = content;
  question.value = "";
  clearLongWaitTimer();
  longWaitTimer = window.setTimeout(() => {
    longWait.value = true;
  }, 15_000);
  await scrollToLatest();
  try {
    const response = await ragApi.chat(
      content,
      selectedCourseId.value,
      undefined,
      conversationIds.value[selectedCourseId.value],
    );
    conversationIds.value[selectedCourseId.value] = response.conversationId;
    messages.value.push({
      id: Date.now(),
      conversationId: response.conversationId,
      courseId: selectedCourseId.value,
      question: content,
      answer: response.answer,
      grounded: response.grounded,
      needsClarification: response.needsClarification,
      sources: response.sources,
      createdAt: new Date().toISOString(),
    });
  } catch (error: any) {
    console.error("Failed to ask learning assistant:", error);
    question.value = content;
    const status = error?.response?.status;
    chatError.value = isTimeoutError(error) || status === 504
      ? "Trợ lý chưa hoàn tất câu trả lời trong thời gian cho phép. Câu hỏi đã được giữ lại để bạn có thể thử lại."
      : status === 502
        ? "Trợ lý chưa tạo được câu trả lời hoàn chỉnh. Vui lòng thử lại."
        : "Không thể kết nối tới trợ lý học tập. Vui lòng thử lại.";
  } finally {
    clearLongWaitTimer();
    pendingQuestion.value = "";
    sending.value = false;
    await scrollToLatest();
  }
};

const handleQuestionEnter = (event: KeyboardEvent) => {
  // With Vietnamese and other IMEs, the text visible in the textarea can still
  // be composing and therefore not yet be reflected in Vue's v-model. Let the
  // first Enter commit that text instead of sending a stale question value.
  if (event.isComposing || event.key === "Process" || event.keyCode === 229) return;
  event.preventDefault();
  void sendQuestion();
};

const askPrompt = (prompt: string) => {
  question.value = prompt;
  void sendQuestion();
};

const formatTime = (value: string) => new Intl.DateTimeFormat("vi-VN", {
  hour: "2-digit",
  minute: "2-digit",
}).format(new Date(value));

const clampPosition = () => {
  const element = chatbotElement.value;
  if (!element) return;
  const margin = 12;
  const rect = element.getBoundingClientRect();
  position.value = {
    x: Math.min(
      Math.max(margin, position.value.x),
      Math.max(margin, window.innerWidth - rect.width - margin),
    ),
    y: Math.min(
      Math.max(margin, position.value.y),
      Math.max(margin, window.innerHeight - rect.height - margin),
    ),
  };
};

const moveChatbot = (event: PointerEvent) => {
  if (!dragging.value) return;
  const nextX = event.clientX - dragOffset.x;
  const nextY = event.clientY - dragOffset.y;
  position.value = { x: nextX, y: nextY };
  clampPosition();
};

const stopDrag = () => {
  if (!dragging.value) return;
  dragging.value = false;
  window.removeEventListener("pointermove", moveChatbot);
  window.removeEventListener("pointerup", stopDrag);
};

const startDrag = (event: PointerEvent) => {
  if (event.button !== 0 || !chatbotElement.value) return;
  const rect = chatbotElement.value.getBoundingClientRect();
  dragOffset.x = event.clientX - rect.left;
  dragOffset.y = event.clientY - rect.top;
  dragging.value = true;
  window.addEventListener("pointermove", moveChatbot);
  window.addEventListener("pointerup", stopDrag);
  event.preventDefault();
};

const toggleChat = async () => {
  if (open.value) {
    void closeChat();
    return;
  }
  open.value = true;
  await nextTick();
  await placeAtBottomRight();
  closeButton.value?.focus({ preventScroll: true });
  selectCourseFromContext();
  await loadHistory();
};

const closeChat = async () => {
  stopDrag();
  open.value = false;
  await nextTick();
  launcherButton.value?.focus({ preventScroll: true });
};

const placeAtBottomRight = async () => {
  await nextTick();
  const element = chatbotElement.value;
  if (!element) return;
  const rect = element.getBoundingClientRect();
  position.value = {
    x: window.innerWidth - rect.width - 24,
    y: window.innerHeight - rect.height - 20,
  };
  clampPosition();
};

watch(
  () => props.locked,
  () => {
    stopDrag();
    open.value = false;
  },
);

watch(
  () => [route.name, courseStore.currentCourse?.id, accessibleCourses.value.map(course => course.id).join(",")],
  () => {
    const changed = selectCourseFromContext();
    if (open.value && changed) void loadHistory();
  },
  { immediate: true },
);

onMounted(() => {
  window.addEventListener("resize", clampPosition);
});

onBeforeUnmount(() => {
  clearLongWaitTimer();
  window.removeEventListener("resize", clampPosition);
  window.removeEventListener("pointermove", moveChatbot);
  window.removeEventListener("pointerup", stopDrag);
});
</script>

<style scoped>
.learning-chatbot {
  position: fixed;
  z-index: 240;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 14px;
  touch-action: none;
}

.learning-chatbot.is-dragging {
  user-select: none;
}

.chat-launcher {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 44px;
  min-height: 44px;
  padding: 0 12px;
  border: 1px solid var(--line-c7dfd5);
  border-radius: 12px;
  background: var(--surface-edf6f1);
  color: var(--ink-005b41);
  font-size: 0.82rem;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease;
}

.chat-launcher:hover,
.chat-launcher.is-active {
  background: var(--surface-d7e9e3);
  border-color: #005b41;
}

.chat-launcher:focus-visible {
  outline: 2px solid #005b41;
  outline-offset: 3px;
}

.chat-launcher.is-locked {
  background: var(--surface-f1f5f9);
  border-color: var(--line-cbd5e1);
  color: var(--ink-475569);
}

.chat-panel {
  width: min(380px, calc(100vw - 32px));
  height: min(560px, calc(100vh - 120px));
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(0, 91, 65, 0.14);
  border-radius: 22px;
  background: var(--surface-ffffff);
  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.2);
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 17px 18px;
  background: linear-gradient(135deg, #005b41, #007552);
  color: #fff;
  cursor: grab;
  touch-action: none;
}

.is-dragging .chat-header {
  cursor: grabbing;
}

.chat-assistant {
  display: flex;
  align-items: center;
  gap: 11px;
}

.assistant-avatar,
.message-avatar {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 13px;
}

.assistant-avatar {
  width: 42px;
  height: 42px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.chat-header h2 {
  margin: 0 0 3px;
  font-size: 1rem;
  line-height: 1.2;
}

.assistant-status {
  display: flex;
  align-items: center;
  gap: 6px;
  color: rgba(255, 255, 255, 0.78);
  font-size: 0.73rem;
}

.assistant-status i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--surface-63e6a7);
}

.is-locked .assistant-status i {
  background: #fbbf24;
}

.chat-close {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #fff;
  cursor: pointer;
}

.chat-close:hover {
  background: rgba(255, 255, 255, 0.12);
}

.chat-course-filter {
  display: grid;
  grid-template-columns: auto 1fr;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--line-e2e8f0);
  background: var(--surface-ffffff);
}

.chat-course-filter label {
  color: var(--ink-64748b);
  font-size: 0.72rem;
  font-weight: 700;
}

.chat-course-filter select {
  min-width: 0;
  padding: 7px 9px;
  border: 1px solid var(--line-cbd5e1);
  border-radius: 9px;
  background: var(--surface-f8fafc);
  color: var(--ink-334155);
  font-size: 0.78rem;
}

.chat-body {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 22px 18px;
  background:
    radial-gradient(
      circle at top right,
      rgba(0, 91, 65, 0.06),
      transparent 35%
    ),
    var(--surface-f8fafc);
}

.chat-date {
  margin-bottom: 18px;
  color: var(--ink-94a3b8);
  font-size: 0.7rem;
  font-weight: 600;
  text-align: center;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.assistant-message {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  min-width: 0;
  max-width: 100%;
}

.assistant-message > div {
  min-width: 0;
}

.chat-exchange {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  max-width: 100%;
  margin-bottom: 18px;
}

.user-message {
  display: flex;
  justify-content: flex-end;
  min-width: 0;
  margin: 0 0 10px 42px;
}

.user-message p {
  max-width: 100%;
  margin: 0;
  padding: 10px 13px;
  border-radius: 15px 5px 15px 15px;
  background: #006747;
  color: #fff;
  font-size: 0.86rem;
  line-height: 1.5;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.assistant-answer {
  min-width: 0;
  flex: 1;
}

.message-avatar {
  width: 32px;
  height: 32px;
  background: var(--surface-e5f3ee);
  color: var(--ink-005b41);
}

.assistant-message p {
  min-width: 0;
  max-width: 270px;
  margin: 0 0 5px;
  padding: 12px 14px;
  border: 1px solid var(--line-e2e8f0);
  border-radius: 5px 16px 16px;
  background: var(--surface-ffffff);
  color: var(--ink-334155);
  font-size: 0.88rem;
  line-height: 1.55;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.assistant-message span {
  color: var(--ink-94a3b8);
  font-size: 0.68rem;
}

.assistant-answer > small {
  display: block;
  margin-top: 5px;
  color: var(--ink-94a3b8);
  font-size: 0.68rem;
}

.assistant-answer .grounding-state {
  display: inline-flex;
  margin: 1px 0 7px;
  padding: 3px 7px;
  border-radius: 999px;
  background: var(--surface-f1f5f9);
  color: var(--ink-64748b);
  font-size: 0.66rem;
  font-weight: 700;
}

.assistant-answer .grounding-state.grounded {
  background: var(--surface-dcfce7);
  color: var(--ink-166534);
}

.chat-sources {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
  max-width: 100%;
  margin: 0;
  padding: 0;
  list-style: none;
}

.chat-sources li {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  min-width: 0;
  max-width: 100%;
  padding: 7px 8px;
  border: 1px solid var(--line-dbe7e2);
  border-radius: 8px;
  background: var(--surface-f7faf9);
  color: var(--ink-475569);
}

.chat-sources li > svg {
  flex: 0 0 auto;
  margin-top: 1px;
  color: var(--ink-006747);
}

.chat-sources li span {
  flex: 1 1 auto;
  min-width: 0;
  color: var(--ink-475569);
  font-size: 0.68rem;
  line-height: 1.4;
  white-space: normal;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.chat-thinking p {
  display: flex;
  align-items: center;
  gap: 7px;
}

.chat-thinking svg {
  animation: chat-spin 0.9s linear infinite;
}

.chat-loading,
.chat-error {
  margin: 12px 0;
  color: var(--ink-64748b);
  font-size: 0.78rem;
  text-align: center;
}

.chat-error {
  padding: 8px;
  border-radius: 8px;
  background: var(--surface-fee2e2);
  color: var(--ink-b91c1c);
}

.quick-prompts {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  margin: 22px 0 0 41px;
}

.quick-prompts button {
  padding: 8px 12px;
  border: 1px solid var(--line-b8d8cd);
  border-radius: 999px;
  background: var(--surface-ffffff);
  color: var(--ink-006747);
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
}

.chat-composer {
  position: relative;
  display: grid;
  grid-template-columns: 1fr 42px;
  gap: 8px;
  padding: 14px 14px 10px;
  border-top: 1px solid var(--line-e2e8f0);
  background: var(--surface-ffffff);
}

.chat-composer textarea {
  min-height: 42px;
  max-height: 90px;
  resize: none;
  padding: 10px 12px;
  border: 1px solid var(--line-cbd5e1);
  border-radius: 12px;
  outline: none;
  color: var(--ink-1e293b);
  font-size: 0.86rem;
}

.chat-composer textarea:focus {
  border-color: #007552;
  box-shadow: 0 0 0 3px rgba(0, 117, 82, 0.1);
}

.chat-composer > button {
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 12px;
  background: #005b41;
  color: #fff;
  cursor: pointer;
}

.chat-composer > button:disabled,
.chat-composer textarea:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.chat-composer p {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  margin: 0;
  color: var(--ink-94a3b8);
  font-size: 0.68rem;
}

.locked-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 36px;
  background: var(--surface-f8fafc);
  text-align: center;
}

.locked-icon {
  display: grid;
  place-items: center;
  width: 66px;
  height: 66px;
  margin-bottom: 18px;
  border-radius: 20px;
  background: var(--surface-fff7df);
  color: var(--ink-b7791f);
  box-shadow: inset 0 0 0 1px #fde7a8;
}

.locked-eyebrow {
  color: var(--ink-b7791f);
  font-size: 0.72rem;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.09em;
}

.locked-content h3 {
  margin: 7px 0 10px;
  color: var(--ink-1e293b);
  font-size: 1.25rem;
}

.locked-content p {
  margin: 0;
  color: var(--ink-64748b);
  font-size: 0.88rem;
  line-height: 1.65;
}

.chat-panel-enter-active,
.chat-panel-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.24s ease;
  transform-origin: bottom right;
}

.chat-panel-enter-from,
.chat-panel-leave-to {
  opacity: 0;
  transform: translateY(40px);
}

@keyframes chat-spin {
  to { transform: rotate(360deg); }
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 1024px) {
  .chat-launcher {
    width: 44px;
    padding: 0;
  }

  .chat-launcher span {
    display: none;
  }
}

@media (max-width: 640px) {
  .chat-panel {
    width: calc(100vw - 28px);
    height: min(560px, calc(100vh - 88px));
  }
}

@media (prefers-reduced-motion: reduce) {
  .chat-launcher,
  .chat-panel-enter-active,
  .chat-panel-leave-active {
    transition: none;
  }

}
</style>
