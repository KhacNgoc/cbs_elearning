import { test } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';

const dom = new JSDOM('');
globalThis.window = dom.window;
globalThis.document = dom.window.document;
const { renderLessonText } = await import('../src/utils/lessonText.ts');
const render = value => {
  const host = document.createElement('div');
  host.innerHTML = renderLessonText(value);
  return host;
};

test('existing Vietnamese plain text keeps line breaks and characters', () => {
  const host = render('Tổng quan bài học\nLĩnh vực: Tín dụng');
  assert.match(host.textContent, /Tổng quan bài học/);
  assert.match(host.textContent, /Lĩnh vực: Tín dụng/);
  assert.equal(host.querySelectorAll('br').length, 1);
});

test('Markdown renders headings, formatting, steps, images and document links', () => {
  const host = render('## Hướng dẫn\n\n**Bắt đầu**\n\n1. Kiểm tra\n2. Xác nhận\n\n![Sơ đồ](https://example.com/chart.png)\n\n[Tài liệu](https://example.com/guide.pdf)');
  assert.equal(host.querySelector('h2').textContent, 'Hướng dẫn');
  assert.equal(host.querySelector('strong').textContent, 'Bắt đầu');
  assert.equal(host.querySelectorAll('ol li').length, 2);
  assert.equal(host.querySelector('img').alt, 'Sơ đồ');
  assert.equal(host.querySelector('a').getAttribute('href'), 'https://example.com/guide.pdf');
});

test('rich HTML preserves formatting and tables', () => {
  const host = render('<h2>Nội dung</h2><p><strong>Đậm</strong> <em>Nghiêng</em> <u>Gạch chân</u></p><table><tr><td>Dữ liệu</td></tr></table>');
  for (const tag of ['h2', 'strong', 'em', 'u', 'table', 'td']) assert.ok(host.querySelector(tag));
});

test('untrusted HTML cannot inject scripts, event handlers, forms or executable URLs', () => {
  const host = render('<script>alert(1)</script><img src="x" onerror="alert(1)"><a href="javascript:alert(1)">X</a><iframe srcdoc="bad"></iframe><form><input></form><p style="position:fixed">An toàn</p>');
  assert.equal(host.querySelector('script, iframe, form, input, [onerror], [style]'), null);
  assert.equal(host.querySelector('a').hasAttribute('href'), false);
  assert.match(host.textContent, /An toàn/);
});

test('Markdown cannot inject executable links and SVG payloads', () => {
  const host = render('[X](javascript:alert%281%29)\n\n<svg onload="alert(1)"></svg>');
  assert.equal(host.querySelector('svg, [onload]'), null);
  assert.equal(host.querySelector('a').hasAttribute('href'), false);
});

test('empty content renders empty output', () => assert.equal(renderLessonText(''), ''));
