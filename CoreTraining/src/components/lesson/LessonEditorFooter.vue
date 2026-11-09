<template>
  <!-- One sticky action bar for every lesson editor tab: destructive actions on the left, saving on the right. -->
  <footer class="lesson-editor-footer">
    <div class="lesson-editor-footer-start"><slot name="start" /></div>
    <div class="lesson-editor-footer-end"><slot /></div>
  </footer>
</template>

<style scoped>
.lesson-editor-footer {
  position: sticky;
  bottom: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-top: 1.5rem;
  padding: 1rem 1.75rem;
  border-top: 1px solid var(--border-color);
  border-radius: 0 0 10px 10px;
  background: var(--surface-ffffff);
  box-shadow: 0 -8px 20px rgba(15, 35, 29, 0.05);
}
.lesson-editor-footer-start,
.lesson-editor-footer-end {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.75rem;
}
.lesson-editor-footer-end {
  flex: 1 1 auto;
  min-width: 0;
  margin-left: auto;
  justify-content: flex-end;
}
.lesson-editor-footer-start {
  flex-shrink: 0;
}
/* Buttons keep their label on one line; only the explanatory note wraps. */
.lesson-editor-footer :slotted(button) {
  white-space: nowrap;
  flex-shrink: 0;
}
.lesson-editor-footer :slotted(.footer-danger) {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.6rem 0.9rem;
  border: 1px solid var(--line-fecaca);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--ink-b91c1c);
  font-weight: 700;
  cursor: pointer;
}
.lesson-editor-footer :slotted(.footer-danger:hover:not(:disabled)) {
  background: var(--surface-fff7f7);
}
.lesson-editor-footer :slotted(.footer-danger:disabled) {
  opacity: 0.55;
  cursor: not-allowed;
}
.lesson-editor-footer :slotted(.footer-note) {
  flex: 1 1 6rem;
  min-width: 0;
  color: var(--text-muted);
  font-size: 0.85rem;
  text-align: right;
}
@media (max-width: 700px) {
  .lesson-editor-footer {
    padding: 0.8rem 1rem;
  }
}
</style>
