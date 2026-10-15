<template>
  <section
    ref="workspace"
    class="text-workspace"
    aria-label="Soạn nội dung bài học"
  >
    <header class="workspace-heading">
      <div>
        <h2>Nội dung bài học</h2>
        <p>
          Soạn nội dung, chèn hình ảnh và liên kết đến sơ đồ hoặc tài liệu liên
          quan.
        </p>
      </div>
      <button type="button" class="expand-button" @click="toggleExpanded">
        <component :is="expanded ? Minimize2 : Maximize2" :size="16" />{{
          expanded ? "Thu gọn" : "Mở rộng"
        }}
      </button>
    </header>
    <div class="workspace-modes" role="group" aria-label="Chế độ soạn thảo">
      <button
        v-for="item in modes"
        :key="item.id"
        type="button"
        :aria-pressed="mode === item.id"
        :class="{ active: mode === item.id }"
        @click="switchMode(item.id)"
      >
        {{ item.label }}
      </button>
    </div>
    <div
      v-if="mode === 'rich' && editor && !readOnly"
      class="format-toolbar"
      role="toolbar"
      aria-label="Định dạng nội dung"
    >
      <button
        v-for="tool in tools"
        :key="tool.label"
        type="button"
        :title="tool.label"
        :aria-label="tool.label"
        :aria-pressed="tool.active?.() || false"
        @click="tool.run"
      >
        <component :is="tool.icon" :size="18" />
      </button>
      <button type="button" @click="openInsert('link')">
        <Link2 :size="17" /> Liên kết
      </button>
      <button type="button" @click="openInsert('image')">
        <ImagePlus :size="17" /> Hình ảnh
      </button>
      <button type="button" @click="attachDocument">
        <Paperclip :size="17" /> Tệp đính kèm
      </button>
    </div>
    <div v-if="insertType" class="insert-panel">
      <strong>{{
        insertType === "image"
          ? "Chèn hình ảnh từ URL"
          : "Liên kết đến sơ đồ / tài liệu"
      }}</strong>
      <label
        >Đường dẫn<input
          ref="urlInput"
          v-model="insertUrl"
          type="url"
          placeholder="https://..."
          @keydown.enter.prevent="insertContent"
          @keydown.esc.prevent="insertType = null"
      /></label>
      <label
        >{{ insertType === "image" ? "Mô tả hình ảnh" : "Nội dung liên kết"
        }}<input v-model="insertLabel" @keydown.enter.prevent="insertContent"
      /></label>
      <p v-if="insertError" role="alert">{{ insertError }}</p>
      <div>
        <button type="button" class="btn btn-primary" @click="insertContent">
          Chèn</button
        ><button
          type="button"
          class="btn btn-secondary"
          @click="insertType = null"
        >
          Hủy
        </button>
      </div>
    </div>
    <div class="writing-area">
      <EditorContent
        v-show="mode === 'rich'"
        :editor="editor"
        class="lesson-writing-surface"
      />
      <textarea
        v-if="mode === 'markdown' || mode === 'html'"
        :value="source"
        :readonly="readOnly"
        :aria-label="mode === 'markdown' ? 'Nội dung Markdown' : 'Mã HTML'"
        spellcheck="false"
        @input="updateSource(($event.target as HTMLTextAreaElement).value)"
      ></textarea>
      <LessonRichText
        v-if="mode === 'preview'"
        :content="modelValue"
        class="content-preview"
      />
    </div>
    <footer class="workspace-status">
      <span>{{
        mode === "preview"
          ? "Nội dung hiển thị cho học viên"
          : mode === "markdown"
            ? "Hỗ trợ tiêu đề, danh sách, hình ảnh, liên kết và khối mã Markdown."
            : mode === "html"
              ? "HTML được lọc an toàn khi hiển thị. Chuyển sang soạn thảo có thể giản lược định dạng nâng cao."
              : "Ctrl+B: in đậm · Ctrl+I: in nghiêng · Ctrl+Z: hoàn tác"
      }}</span
      ><span>{{ modelValue.length.toLocaleString("vi-VN") }} ký tự</span>
    </footer>
  </section>
</template>
<script setup lang="ts">
import { ref, watch, nextTick, onMounted, onBeforeUnmount } from "vue";
import { EditorContent, useEditor } from "@tiptap/vue-3";
import StarterKit from "@tiptap/starter-kit";
import Image from "@tiptap/extension-image";
import TurndownService from "turndown";
import {
  Bold,
  Italic,
  Underline,
  Heading2,
  List,
  ListOrdered,
  Quote,
  Undo2,
  Redo2,
  Link2,
  ImagePlus,
  Paperclip,
  Maximize2,
  Minimize2,
} from "lucide-vue-next";
import { renderLessonText } from "../../utils/lessonText";
import LessonRichText from "./LessonRichText.vue";
import "../../assets/styles/lesson-content.css";
const props = defineProps<{ modelValue: string; readOnly?: boolean }>();
const emit = defineEmits<{
  "update:modelValue": [value: string];
  attach: [];
}>();
type Mode = "rich" | "markdown" | "html" | "preview";
const modes: { id: Mode; label: string }[] = [
  { id: "rich", label: "Soạn thảo" },
  { id: "markdown", label: "Markdown" },
  { id: "html", label: "HTML" },
  { id: "preview", label: "Xem trước" },
];
const mode = ref<Mode>("rich"),
  source = ref(""),
  expanded = ref(false);
const workspace = ref<HTMLElement>(),
  urlInput = ref<HTMLInputElement>();
const insertType = ref<"link" | "image" | null>(null),
  insertUrl = ref(""),
  insertLabel = ref(""),
  insertError = ref("");
const editor = useEditor({
  extensions: [StarterKit.configure({ link: { openOnClick: false } }), Image],
  content: renderLessonText(props.modelValue),
  editable: !props.readOnly,
  editorProps: {
    attributes: {
      "aria-label": "Nội dung văn bản có định dạng",
      role: "textbox",
      "aria-multiline": "true",
    },
  },
  onUpdate: ({ editor }) =>
    emit("update:modelValue", editor.isEmpty ? "" : editor.getHTML()),
});
const tools = [
  {
    label: "In đậm",
    icon: Bold,
    active: () => editor.value?.isActive("bold"),
    run: () => editor.value?.chain().focus().toggleBold().run(),
  },
  {
    label: "In nghiêng",
    icon: Italic,
    active: () => editor.value?.isActive("italic"),
    run: () => editor.value?.chain().focus().toggleItalic().run(),
  },
  {
    label: "Gạch chân",
    icon: Underline,
    active: () => editor.value?.isActive("underline"),
    run: () => editor.value?.chain().focus().toggleUnderline().run(),
  },
  {
    label: "Tiêu đề",
    icon: Heading2,
    active: () => editor.value?.isActive("heading", { level: 2 }),
    run: () => editor.value?.chain().focus().toggleHeading({ level: 2 }).run(),
  },
  {
    label: "Danh sách",
    icon: List,
    active: () => editor.value?.isActive("bulletList"),
    run: () => editor.value?.chain().focus().toggleBulletList().run(),
  },
  {
    label: "Danh sách các bước",
    icon: ListOrdered,
    active: () => editor.value?.isActive("orderedList"),
    run: () => editor.value?.chain().focus().toggleOrderedList().run(),
  },
  {
    label: "Trích dẫn",
    icon: Quote,
    active: () => editor.value?.isActive("blockquote"),
    run: () => editor.value?.chain().focus().toggleBlockquote().run(),
  },
  {
    label: "Hoàn tác",
    icon: Undo2,
    run: () => editor.value?.chain().focus().undo().run(),
  },
  {
    label: "Làm lại",
    icon: Redo2,
    run: () => editor.value?.chain().focus().redo().run(),
  },
];
function switchMode(value: Mode) {
  if (mode.value === value) return;
  insertType.value = null;
  if (value === "markdown")
    source.value = new TurndownService({ headingStyle: "atx" })
      .keep(["table", "u"])
      .turndown(renderLessonText(props.modelValue));
  if (value === "html") source.value = renderLessonText(props.modelValue);
  if (value === "rich")
    editor.value?.commands.setContent(renderLessonText(props.modelValue), {
      emitUpdate: false,
    });
  mode.value = value;
}
function updateSource(value: string) {
  source.value = value;
  emit("update:modelValue", value);
}
async function openInsert(type: "link" | "image") {
  insertType.value = type;
  insertUrl.value = "";
  insertError.value = "";
  const selection = editor.value?.state.selection;
  insertLabel.value = selection
    ? editor.value?.state.doc.textBetween(selection.from, selection.to, " ") ||
      ""
    : "";
  await nextTick();
  urlInput.value?.focus();
}
function insertContent() {
  const url = insertUrl.value.trim();
  try {
    if (!["http:", "https:"].includes(new URL(url).protocol)) throw new Error();
  } catch {
    insertError.value =
      "Nhập đường dẫn hợp lệ bắt đầu bằng https:// hoặc http://.";
    return;
  }
  if (insertType.value === "image")
    editor.value
      ?.chain()
      .focus()
      .setImage({ src: url, alt: insertLabel.value })
      .run();
  else
    editor.value
      ?.chain()
      .focus()
      .insertContent({
        type: "text",
        text: insertLabel.value || url,
        marks: [
          {
            type: "link",
            attrs: { href: url, target: "_blank", rel: "noopener noreferrer" },
          },
        ],
      })
      .run();
  insertType.value = null;
}
async function toggleExpanded() {
  if (document.fullscreenElement === workspace.value)
    await document.exitFullscreen();
  else await workspace.value?.requestFullscreen();
}
async function attachDocument() {
  if (document.fullscreenElement === workspace.value)
    await document.exitFullscreen();
  emit("attach");
}
function syncExpanded() {
  expanded.value = document.fullscreenElement === workspace.value;
}
watch(
  () => props.readOnly,
  (value) => editor.value?.setEditable(!value),
);
watch(
  () => props.modelValue,
  (value) => {
    if (
      mode.value === "rich" &&
      editor.value &&
      value !== editor.value.getHTML()
    )
      editor.value.commands.setContent(renderLessonText(value), {
        emitUpdate: false,
      });
  },
);
onMounted(() => document.addEventListener("fullscreenchange", syncExpanded));
onBeforeUnmount(() =>
  document.removeEventListener("fullscreenchange", syncExpanded),
);
</script>
<style scoped>
.text-workspace {
  border: 1px solid var(--line-cbded5);
  border-radius: 12px;
  background: var(--surface-ffffff);
  overflow: hidden;
  min-width: 0;
  margin: 1.5rem 0;
}
.workspace-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  padding: 1.25rem 1.5rem;
}
.workspace-heading h2 {
  font-size: 1.15rem;
  color: var(--primary-ink);
  margin: 0;
}
.workspace-heading p {
  margin: 0.35rem 0 0;
  color: var(--text-muted);
  font-size: 0.85rem;
}
.expand-button {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  white-space: nowrap;
  background: var(--surface-ffffff);
  border: 1px solid var(--border-color);
  padding: 0.6rem 0.8rem;
  border-radius: 7px;
  color: var(--primary-ink);
  cursor: pointer;
}
.workspace-modes {
  display: flex;
  gap: 0.25rem;
  padding: 0 1.5rem;
  border-bottom: 1px solid var(--border-color);
  overflow: auto;
}
.workspace-modes button {
  padding: 0.85rem 1rem;
  border: 0;
  border-bottom: 3px solid transparent;
  background: transparent;
  color: var(--text-muted);
  white-space: nowrap;
  cursor: pointer;
}
.workspace-modes button.active {
  border-bottom-color: var(--primary-color);
  color: var(--primary-ink);
  font-weight: 700;
}
.format-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
  padding: 0.65rem 1rem;
  background: var(--surface-f7faf8);
  border-bottom: 1px solid var(--border-color);
}
.format-toolbar button {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  border: 0;
  background: transparent;
  border-radius: 5px;
  padding: 0.5rem;
  cursor: pointer;
  color: var(--ink-36554a);
}
.format-toolbar button:hover,
.format-toolbar button[aria-pressed="true"] {
  background: var(--surface-dfede6);
}
.writing-area {
  min-height: 460px;
  max-height: 720px;
  overflow: auto;
}
.lesson-writing-surface :deep(.tiptap) {
  min-height: 460px;
  padding: 1.75rem 2rem;
  outline: none;
}
.lesson-writing-surface :deep(.tiptap:focus) {
  box-shadow: inset 0 0 0 2px #8bbca9;
}
.writing-area textarea {
  display: block;
  width: 100%;
  min-height: 460px;
  resize: vertical;
  padding: 1.75rem 2rem;
  border: 0;
  outline-offset: -2px;
  font:
    0.95rem/1.8 Consolas,
    monospace;
  box-sizing: border-box;
}
.content-preview {
  padding: 1.75rem 2rem;
  min-height: 460px;
}
.workspace-status {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.7rem 1.5rem;
  background: var(--surface-f7faf8);
  border-top: 1px solid var(--border-color);
  font-size: 0.75rem;
  color: var(--text-muted);
}
.workspace-status span:last-child {
  white-space: nowrap;
}
.insert-panel {
  display: grid;
  gap: 0.65rem;
  padding: 1rem 1.5rem;
  background: var(--surface-f2f7f5);
  border-bottom: 1px solid var(--border-color);
}
.insert-panel label {
  display: grid;
  gap: 0.25rem;
  font-size: 0.85rem;
}
.insert-panel input {
  width: 100%;
  padding: 0.6rem;
  border: 1px solid var(--border-color);
  border-radius: 6px;
}
.insert-panel > div {
  display: flex;
  gap: 0.5rem;
}
.insert-panel p {
  color: var(--ink-b91c1c);
}
.text-workspace:fullscreen {
  display: flex;
  flex-direction: column;
  border: 0;
  border-radius: 0;
  margin: 0;
  padding: 0;
  width: 100%;
  height: 100%;
  background: var(--surface-ffffff);
}
.text-workspace:fullscreen .writing-area {
  flex: 1;
  min-height: 0;
  max-height: none;
}
.text-workspace:fullscreen .workspace-status {
  margin-top: auto;
}
.text-workspace:fullscreen .lesson-writing-surface :deep(.tiptap),
.text-workspace:fullscreen textarea {
  min-height: 65vh;
}
@media (max-width: 700px) {
  .workspace-heading {
    padding: 1rem;
    align-items: flex-start;
  }
  .workspace-modes {
    padding: 0 0.5rem;
  }
  .workspace-status {
    padding: 0.7rem 1rem;
    flex-wrap: wrap;
  }
  .lesson-writing-surface :deep(.tiptap),
  .writing-area textarea,
  .content-preview {
    padding: 1rem;
    min-height: 360px;
  }
  .writing-area {
    min-height: 360px;
  }
  .expand-button {
    font-size: 0.8rem;
  }
}
</style>
