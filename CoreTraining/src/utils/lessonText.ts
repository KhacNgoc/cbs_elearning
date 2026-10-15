import DOMPurify from "dompurify";
import { marked } from "marked";

// Keep old plain text lessons readable, while accepting Markdown and HTML.
export function renderLessonText(value = ""): string {
  return DOMPurify.sanitize(
    marked.parse(value, { async: false, breaks: true }),
    {
      USE_PROFILES: { html: true },
      FORBID_TAGS: ["style", "input", "form", "button"],
      FORBID_ATTR: ["style"],
    },
  );
}
