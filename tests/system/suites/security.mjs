// NF-S05 injection, NF-S07 abuse limits, NF-O01 error handling and traceability.
import { execFileSync } from 'node:child_process'
import { Client, BASE, tc, assert, expectStatus, sqlNum, sqlOne, uniq, gap, multipart, sql } from '../lib.mjs'
import { freshCourse, learner, updateUser } from '../seed.mjs'

const XSS = '<img src=x onerror=alert(1)><script>alert(2)</script>'
const SQLI = ["' OR '1'='1", "'; DROP TABLE users; --", "\" OR 1=1 --", "%' UNION SELECT password FROM users --", "\\'; SELECT pg_sleep(5); --"]

export async function run(ctx) {
  const { admin, u3 } = ctx
  const f = await freshCourse(ctx)
  const s = await learner(ctx)

  await tc('NF-S05', 'comment: API trả JSON, không phải HTML', async () => {
    const c = expectStatus(await s.post(`/api/lessons/${f.lesson.id}/comments`, { content: XSS }), 200)
    assert.match(c.headers.get('content-type'), /json/)
  })
  await tc('NF-S05', 'HTML trong textBody được làm sạch ở backend', async () => {
    const g = await freshCourse(ctx, { lessons: [{ textBody: `<p>ok</p>${XSS}` }] })
    const stored = sqlOne(`select text_body from lesson_contents c join lessons l on l.id=c.lesson_id where l.id='${g.lesson.id}'`)
    if (/onerror|<script/i.test(stored)) return gap('Backend lưu nguyên script trong textBody; lớp bảo vệ duy nhất là sanitize ở frontend (utils/lessonText.ts). Đã kiểm tra render trên trình duyệt, xem báo cáo.')
  })
  await tc('NF-S05', 'tên file có script khi upload', async () => {
    const res = await admin.post('/api/admin/documents/upload', undefined, { form: multipart({}, [{ name: '<img src=x onerror=alert(1)>.pdf', data: '%PDF-1.4' }]) })
    expectStatus(res, 200)
    assert.match(res.json.fileUrl, /^\/uploads\/documents\/[0-9a-f-]+\.pdf$/)
  })
  await tc('NF-S05', 'tài liệu phục vụ không chạy trong origin (Content-Disposition/nosniff)', async () => {
    const up = expectStatus(await admin.post('/api/admin/documents/upload', undefined, { form: multipart({}, [{ name: 'a.pdf', data: `%PDF-<html>${XSS}</html>` }]) }), 200).json
    const res = expectStatus(await admin.get(up.fileUrl), 200)
    assert.equal(res.headers.get('x-content-type-options'), 'nosniff')
    assert.ok(!/text\/html/.test(res.headers.get('content-type') ?? ''), 'served as HTML')
  })
  const usersBefore = sqlNum('select count(*) from users')
  for (const payload of SQLI) {
    for (const [name, call] of [
      ['users/page', () => admin.get(`/api/admin/users/page?query=${encodeURIComponent(payload)}&page=1&size=5`)],
      ['question-bank/page', () => admin.get(`/api/admin/question-bank/page?query=${encodeURIComponent(payload)}&size=5`)],
      ['dashboard search', () => s.get(`/api/users/me/learning/dashboard/courses?tab=mine&search=${encodeURIComponent(payload)}&page=1&size=5`)],
      ['report course', (v = payload) => u3.get(`/api/admin/reports/training/read?view=learners&course=${encodeURIComponent(v)}&page=0&size=5`)],
      ['report sort/status', (v = payload) => u3.get(`/api/admin/reports/training/read?view=learners&status=${encodeURIComponent(v)}&page=0&size=5`)],
      ['login', () => new Client().post('/api/auth/login', { username: payload, password: payload })],
    ]) {
      await tc('NF-S05', `SQL ${name}: ${payload.slice(0, 16)}`, async () => {
        const b0 = performance.now()
        await call('khong-co-gia-tri-nay')
        const baseline = performance.now() - b0
        const t0 = performance.now()
        const res = await call()
        assert.ok(res.status < 500, `HTTP ${res.status}`)
        let took = performance.now() - t0
        if (took - baseline >= 4000) { const t1 = performance.now(); await call(); took = Math.min(took, performance.now() - t1) }
        assert.ok(took - baseline < 4000, `time-based injection suspected: ${Math.round(took)}ms, giá trị thường ${Math.round(baseline)}ms`)
        assert.ok(!/\$2[aby]\$/.test(res.text), 'password hash leaked')
        if (name === 'users/page') assert.equal((res.json.items ?? []).length, 0, 'payload matched users')
        if (name === 'login') assert.ok(res.status >= 400, 'login bypass')
      })
    }
  }
  await tc('NF-S05', 'bảng users còn nguyên sau payload', async () => assert.ok(sqlNum('select count(*) from users') >= usersBefore))

  // NF-S07.
  await tc('NF-S07', 'flood đăng nhập sai có hạn chế tần suất', async () => {
    const victim = await learner(ctx)
    const out = await Promise.all(Array.from({ length: 60 }, () => new Client().post('/api/auth/login', { username: victim.user.username, password: 'wrong-password' })))
    const limited = out.filter((r) => r.status === 429).length
    if (!limited) return gap(`60 lần đăng nhập sai đồng thời đều trả ${[...new Set(out.map((r) => r.status))]}: không có rate limit/khoá tạm. Ghi nhận gap theo kịch bản.`)
  })
  await tc('NF-S07', 'xin lại OTP liên tục bị giới hạn', async () => {
    const v = await learner(ctx)
    for (let i = 0; i < 5; i++) await new Client().post('/api/auth/forgot-password-otp', { username: v.user.username, email: v.user.email })
    const mailbox = await (await fetch('http://127.0.0.1:18025/messages')).json()
    const n = mailbox.filter((m) => m.to.includes(v.user.email)).length
    assert.equal(n, 1, `${n} OTP mails from 5 requests`)
  })
  await tc('NF-S07', 'giới hạn thử OTP được cưỡng chế dưới flood song song', async () => {
    const v = await learner(ctx)
    await new Client().post('/api/auth/forgot-password-otp', { username: v.user.username, email: v.user.email })
    await Promise.all(Array.from({ length: 30 }, (_, i) => new Client().post('/api/auth/reset-password-otp', { username: v.user.username, otp: String(100000 + i), newPassword: 'Flood-Pass-123' })))
    const attempts = sqlNum(`select attempts from password_reset_otps where user_id=${v.user.id}`)
    assert.ok(attempts <= 5, `recorded ${attempts}`)
    const tried = await new Client().post('/api/auth/login', { username: v.user.username, password: 'Test-User-Pass-1x' })
    assert.equal(tried.status, 200, 'password changed by flood')
  })
  await tc('NF-S07', 'tên file traversal không thoát thư mục', async () => {
    for (const name of ['../../../../tmp/evil.pdf', '..\\..\\evil.pdf', '/etc/evil.pdf']) {
      const res = await admin.post('/api/admin/documents/upload', undefined, { form: multipart({}, [{ name, data: '%PDF-1.4' }]) })
      if (res.status === 200) assert.match(res.json.fileUrl, /^\/uploads\/documents\/[0-9a-f-]+\.pdf$/)
      else assert.ok(res.status < 500, `HTTP ${res.status}`)
    }
    const found = execFileSync('docker', ['exec', 'cbstest-backend', 'sh', '-c', 'ls /tmp/evil.pdf /etc/evil.pdf /app/evil.pdf 2>/dev/null | wc -l'], { encoding: 'utf8' }).trim()
    assert.equal(found, '0', 'file escaped upload directory')
  })
  await tc('NF-S07', 'upload học viên không có quyền', async () => {
    const res = await s.post('/api/admin/documents/upload', undefined, { form: multipart({}, [{ name: 'a.pdf', data: '%PDF-1.4' }]) })
    assert.ok([401, 403].includes(res.status))
  })
  await tc('NF-S07', 'JSON cực lớn/lồng sâu không làm sập', async () => {
    const res = await s.req('POST', `/api/lessons/${f.lesson.id}/comments`, undefined, { raw: JSON.stringify({ content: 'x'.repeat(3 * 1024 * 1024) }) })
    assert.ok(res.status < 500, `3MB comment HTTP ${res.status}`)
    const stored = sqlNum(`select coalesce(max(length(content)),0) from comments where lesson_id='${f.lesson.id}'`)
    assert.ok(stored <= 100000, `stored a ${stored}-character comment; no length limit`)
  })

  // NF-O01 controlled errors and tracing.
  await tc('NF-O01', 'X-Correlation-Id được trả và dò được trong log', async () => {
    const cid = uniq('trace-')
    const res = await s.get('/api/courses/NOPE', { headers: { 'X-Correlation-Id': cid } })
    const header = res.headers.get('x-correlation-id')
    assert.ok(header, 'no correlation header')
    const logs = execFileSync('docker', ['logs', 'cbstest-backend'], { encoding: 'utf8', maxBuffer: 1 << 28, stdio: ['ignore', 'pipe', 'pipe'] })
    assert.ok(logs.includes(header), 'correlation id not in logs')
  })
  for (const [name, call, want] of [
    ['JSON sai cú pháp', () => s.req('POST', `/api/lessons/${f.lesson.id}/comments`, undefined, { raw: '{not json' }), 400],
    ['route không tồn tại', () => s.get('/api/does-not-exist'), 404],
    ['sai method', () => admin.get(`/api/admin/lessons/${f.lesson.id}`), 405],
    ['thiếu query param', () => s.get('/api/users/me/learning/quiz-context'), 400],
    ['path param sai kiểu', () => admin.get('/api/admin/reports/training/attempts/abc/analysis'), 400],
    ['content-type sai', () => s.req('POST', `/api/lessons/${f.lesson.id}/comments`, undefined, { raw: 'content=x', headers: { 'Content-Type': 'text/plain' } }), 415],
    ['request bị thiếu body', () => s.post(`/api/lessons/${f.lesson.id}/comments`), 400],
  ]) {
    await tc('NF-O01', `${name} → ${want}, không 500`, async () => {
      const res = await call()
      assert.ok(res.status < 500, `HTTP ${res.status} (mong đợi ${want}), lỗi client bị GlobalExceptionHandler xếp thành 500 và làm nhiễu cảnh báo`)
    })
  }
  await tc('NF-O01', 'phản hồi lỗi không lộ stack trace/SQL', async () => {
    const res = await s.req('POST', `/api/lessons/${f.lesson.id}/comments`, undefined, { raw: '{bad' })
    assert.ok(!/org\.springframework|hibernate|at com\.bidv|SELECT /i.test(res.text), 'internals leaked')
  })
  await tc('NF-O01', 'có traceId trong body lỗi', async () => assert.ok((await s.get('/api/courses/NOPE')).json?.traceId, 'no traceId'))
  await tc('NF-O01', 'cảnh báo error/latency/DB pool/scheduler lag', async () => gap('Stack test không chạy Grafana/Loki; repo chỉ có dashboard log (monitoring/), không thấy luật cảnh báo latency, DB pool hay scheduler lag. Cần bổ sung nếu áp dụng [ĐX].'))
  void BASE; void updateUser; void sql
}
