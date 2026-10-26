import { test } from 'node:test';
import assert from 'node:assert/strict';
import { initialLessonContent } from '../src/utils/lessonContent.ts';

const document = { id: 10, fileUrl: '/lesson.pdf' };
const lesson = { textBody: '# Nội dung', videoUrl: '/video.mp4', documents: [document] };
test('video takes priority on a normal visit even after viewing text or a document', () => {
  assert.deepEqual(initialLessonContent(lesson, { contentType: 'TEXT' }), { type: 'VIDEO', document: null });
  assert.equal(initialLessonContent(lesson, { contentType: 'DOCUMENT', documentId: 10 }).type, 'VIDEO');
});
test('explicit document link takes precedence over the last viewed content', () => {
  assert.deepEqual(initialLessonContent(lesson, { contentType: 'TEXT' }, document), { type: 'DOCUMENT', document });
});
test('restores a saved document and falls back if the document was removed', () => {
  assert.equal(initialLessonContent({ ...lesson, videoUrl: '', textBody: '' }, { contentType: 'DOCUMENT', documentId: 10 }).document, document);
  assert.equal(initialLessonContent(lesson, { contentType: 'DOCUMENT', documentId: 99 }).type, 'VIDEO');
});
test('text and document lessons can restore text without video', () => {
  const withoutVideo = { ...lesson, videoUrl: '' };
  assert.equal(initialLessonContent(withoutVideo).type, 'TEXT');
  assert.equal(initialLessonContent(withoutVideo, { contentType: 'DOCUMENT', documentId: 10 }).type, 'TEXT');
  assert.equal(initialLessonContent(withoutVideo, { contentType: 'TEXT' }).type, 'TEXT');
});
test('handles video only, text only and missing content', () => {
  assert.equal(initialLessonContent({ ...lesson, documents: [], textBody: '' }).type, 'VIDEO');
  assert.equal(initialLessonContent({ ...lesson, documents: [], videoUrl: '' }).type, 'TEXT');
  assert.equal(initialLessonContent({ documents: [], videoUrl: ' ', textBody: ' ' }).type, 'TEXT');
  assert.equal(initialLessonContent(lesson, undefined, { id: 99, fileUrl: '/other.pdf' }).document, null);
});
