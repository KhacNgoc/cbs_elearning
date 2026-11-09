// 5.3 Reliability (NF-R01..R05), E2E-08 AI overload, E2E-09 backup and restore into a separate DB/volume.
// Faults only ever target cbstest-* containers.
import { execFileSync, spawnSync } from 'node:child_process'
import { tc, assert, expectStatus, expectRejected, sql, sqlNum, sqlOne, sleep, gap, blocked, BASE, Client, loginAs, multipart } from '../lib.mjs'
import { freshCourse, learner, answersFor, readAndComplete, ok, ACTIVE_PASSWORD } from '../seed.mjs'
import { readFileSync } from 'node:fs'

const docker = (...a) => { if (a.some((x) => /^(coretraining-db|cbs-elearning|cbs_elearning)/.test(x))) throw new Error('refusing to touch the dev stack'); return execFileSync('docker', a, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], maxBuffer: 1 << 27 }) }
const pct = (list, p) => { if (!list.length) return 0; const s = [...list].sort((a, b) => a - b); return Math.round(s[Math.min(s.length - 1, Math.floor(s.length * p / 100))]) }
async function waitBackend(ms = 90000) {
  const end = Date.now() + ms
  while (Date.now() < end) {
    try { if ((await fetch(`${BASE}/api/auth/csrf`)).status === 200) return true } catch { /* still starting */ }
    await sleep(1000)
  }
  return false
}
// Sends a write and drops the connection before the response is read.
async function fireAndAbort(client, path, body, afterMs) {
  const ac = new AbortController()
  await client.ensureCsrf()
  const p = fetch(`${BASE}${path}`, { method: 'POST', signal: ac.signal, headers: { 'Content-Type': 'application/json', Cookie: client.cookieHeader(), 'X-XSRF-TOKEN': client.cookies.get('XSRF-TOKEN') }, body: JSON.stringify(body) }).catch(() => null)
  setTimeout(() => ac.abort(), afterMs)
  await p
  await sleep(800)
}

export async function run(ctx) {
  const { admin, A } = ctx
  void A

  // NF-R01.
  await tc('NF-R01', 'submit: cắt kết nối sau commit, retry không thưởng/tạo trùng', async () => {
    const f = await freshCourse(ctx, { lessons: [{ questions: 4, settings: { timeLimitMinutes: 5 } }] }); const s = await learner(ctx)
    await s.post(`/api/users/me/lessons/${f.lesson.id}/progress`, { contentType: 'TEXT', textCompleted: true })
    const { sessionId } = (await s.post(`/api/quizzes/lessons/${f.lesson.id}/start`)).json
    await fireAndAbort(s, `/api/quizzes/lessons/${f.lesson.id}/submit`, { sessionId, selectedAnswers: answersFor(f.lesson.qs, 4) }, 150)
    const stored = sqlNum(`select count(*) from quiz_attempts where user_id=${s.user.id}`)
    const retry = await s.post(`/api/quizzes/lessons/${f.lesson.id}/submit`, { sessionId, selectedAnswers: answersFor(f.lesson.qs, 4) })
    if (stored === 1) {
      expectRejected(retry, 'retry after commit')
      expectStatus(await s.get(`/api/quizzes/lessons/${f.lesson.id}/review`), 200)
    }
    assert.equal(sqlNum(`select count(*) from quiz_attempts where user_id=${s.user.id}`), 1)
    assert.equal(sqlNum(`select points from users where id=${s.user.id}`), 100)
  })
  await tc('NF-R01', 'enroll: cắt kết nối, gửi lại không trùng', async () => {
    const f = await freshCourse(ctx, { grant: 'OPTIONAL' }); const s = await learner(ctx)
    await fireAndAbort(s, `/api/users/me/register-course/${f.course.id}`, undefined, 100)
    await s.post(`/api/users/me/register-course/${f.course.id}`)
    assert.equal(sqlNum(`select count(*) from course_enrollment_requests where user_id=${s.user.id} and course_id='${f.course.id}' and status='PENDING'`), 1)
  })
  await tc('NF-R01', 'progress/complete: cắt kết nối, retry không cộng điểm thêm', async () => {
    const f = await freshCourse(ctx); const s = await learner(ctx)
    await s.post(`/api/users/me/lessons/${f.lesson.id}/progress`, { contentType: 'TEXT', textCompleted: true })
    await fireAndAbort(s, `/api/users/me/complete-lesson/${f.lesson.id}`, undefined, 100)
    expectStatus(await s.post(`/api/users/me/complete-lesson/${f.lesson.id}`), 200)
    assert.equal(sqlNum(`select points from users where id=${s.user.id}`), 100)
  })

  // NF-R03 dependency failures: mail and RAG are covered in F-A07 and F-I08; these keep unrelated APIs in check.
  await tc('NF-R03', 'Qdrant tắt: chat lỗi rõ, API học chạy, không giữ cạn DB pool', async () => {
    const f = await freshCourse(ctx); const s = await learner(ctx)
    docker('stop', 'cbstest-qdrant')
    try {
      const calls = Array.from({ length: 6 }, () => s.post('/api/rag/chat', { question: 'Xin chào', courseId: f.course.id }))
      await sleep(500)
      const conns = sqlNum("select count(*) from pg_stat_activity where datname='coretraining_test' and state='idle in transaction'")
      const t0 = performance.now()
      expectStatus(await s.get(`/api/lessons/${f.lesson.id}`), 200)
      const elapsed = performance.now() - t0
      const out = await Promise.all(calls)
      assert.ok(out.every((r) => r.status !== 200 || r.json.answer), 'empty success')
      assert.ok(elapsed < 2000, `learning API took ${Math.round(elapsed)}ms`)
      assert.ok(conns <= 2, `${conns} connections idle in transaction while waiting for AI`)
    } finally { docker('start', 'cbstest-qdrant'); await sleep(3000) }
  })
  await tc('NF-R03', 'hết dung lượng volume', async () => blocked('Volume test dùng ổ Docker chung; không lấp đầy để tránh ảnh hưởng stack dev. Cần môi trường riêng có quota.'))
  await tc('NF-R03', 'model timeout', async () => gap('Không tắt được Ollama vì đang dùng chung với máy dev. Timeout có unit test ở rag-service/tests/test_chat_resilience.py.'))

  // NF-R02 DB outage, then backend restart under writes.
  await tc('NF-R02', 'DB mất kết nối 30s rồi phục hồi: lỗi rõ, kết nối tự hồi', async () => {
    const f = await freshCourse(ctx); const s = await learner(ctx)
    docker('network', 'disconnect', 'cbstest_default', 'cbstest-db')
    let during
    try {
      await sleep(8000)
      during = await Promise.race([s.get(`/api/lessons/${f.lesson.id}`), sleep(25000).then(() => ({ status: 'no-response' }))])
      await sleep(22000)
    } finally { docker('network', 'connect', '--alias', 'test-postgres', 'cbstest_default', 'cbstest-db') }
    assert.ok(during.status === 'no-response' || during.status >= 500 || during.status === 401, `during outage HTTP ${during.status}`)
    const end = Date.now() + 40000
    let back = 0
    while (Date.now() < end) { back = (await s.get(`/api/lessons/${f.lesson.id}`).catch(() => ({ status: 0 }))).status; if (back === 200) break; await sleep(1500) }
    assert.equal(back, 200, 'backend did not recover')
    return `Trong sự cố: ${during.status}. Phục hồi không cần restart.`
  })
  await tc('NF-R02', 'restart backend khi đang ghi: transaction nguyên vẹn, phiên còn hiệu lực', async () => {
    const f = await freshCourse(ctx, { lessons: Array.from({ length: 12 }, () => ({})) })
    const users = []
    for (let i = 0; i < 8; i++) users.push(await learner(ctx))
    let stop = false
    const writers = users.map(async (s) => {
      for (const l of f.lessons) {
        if (stop) break
        await s.post(`/api/users/me/lessons/${l.id}/progress`, { contentType: 'TEXT', textCompleted: true }).catch(() => null)
        await s.post(`/api/users/me/complete-lesson/${l.id}`).catch(() => null)
      }
    })
    await sleep(600)
    docker('restart', '-t', '0', 'cbstest-backend')
    stop = true
    await Promise.all(writers)
    assert.ok(await waitBackend(), 'backend not back')
    for (const s of users) {
      const n = sqlNum(`select count(*) from user_completed_lessons where user_id=${s.user.id}`)
      assert.equal(sqlNum(`select points from users where id=${s.user.id}`), n * 100, `half-written completion: points vs ${n} lessons`)
      expectStatus(await s.get('/api/auth/session'), 200, 'session after restart')
    }
    await sleep(500)
  })
  await tc('NF-R02', 'job AI đang chạy khi restart backend', async () => {
    const res = await admin.get('/api/admin/quizzes/generate/jobs/khong-ton-tai')
    assert.equal(res.status, 404)
    return gap('Job nằm trong bộ nhớ RAG service; restart RAG thì polling nhận 404 "không tồn tại", UI phải thoát loading. Chưa kiểm tra UI ở tình huống này.')
  })

  // NF-R05.
  await tc('NF-R05', 'rolling restart hai backend, reload frontend, job AI còn chạy', async () => blocked('Stack test không có load balancer trước hai backend nên không đo được "không mất request". Phần scheduler nhiều instance đã kiểm ở NF-C03.'))

  // E2E-08 AI load while the learning API keeps working.
  await tc('E2E-08', 'chat + sinh đề đồng thời: API học/thi nền vẫn đáp ứng, DB pool không cạn', async () => {
    const f = await freshCourse(ctx, { lessons: [{ questions: 4, settings: { requiredForProgress: false } }] })
    const doc = await admin.post(`/api/admin/courses/${f.course.id}/documents`, undefined, { form: multipart({}, [{ name: 'facts.docx', data: readFileSync(new URL('../fixtures/facts.docx', import.meta.url)) }]) })
    expectStatus(doc, 202)
    for (let i = 0; i < 120; i++) {
      if (sqlOne(`select status from course_documents where id='${doc.json.id}'`) === 'INDEXED') break
      await sleep(2000)
    }
    const s = await learner(ctx); const bg = await learner(ctx)
    const ai = [
      ...Array.from({ length: 4 }, () => s.post('/api/rag/chat', { question: 'Hạn mức thấu chi của Sao Biển là bao nhiêu?', courseId: f.course.id })),
      admin.post('/api/admin/quizzes/generate/jobs', { courseId: f.course.id, singleChoiceCount: 2 }),
    ]
    const times = []; let peakConn = 0; let done = false
    const all = Promise.all(ai).then((r) => { done = true; return r })
    const end = Date.now() + 90000
    while (!done && Date.now() < end) {
      const t0 = performance.now()
      const [a, b] = await Promise.all([bg.get(`/api/lessons/${f.lesson.id}`), bg.post(`/api/quizzes/lessons/${f.lesson.id}/submit`, { selectedAnswers: answersFor(f.lesson.qs, 4) })])
      times.push(performance.now() - t0)
      assert.ok(a.status === 200 && b.status === 200, `background ${a.status}/${b.status}`)
      peakConn = Math.max(peakConn, sqlNum("select count(*) from pg_stat_activity where datname='coretraining_test' and state<>'idle'"))
      await sleep(500)
    }
    const out = await Promise.race([all, sleep(120000).then(() => [])])
    const statuses = out.map((r) => r.status)
    assert.ok(pct(times, 95) < 2000, `background p95=${pct(times, 95)}ms`)
    assert.ok(peakConn < 20, `DB connections ${peakConn}`)
    return `AI: ${statuses}; nền p95=${pct(times, 95)}ms (${times.length} vòng), DB active tối đa=${peakConn}.`
  })
  await tc('NF-P04', 'chat 1→2→4→8 đồng thời (rút gọn, mỗi mức một đợt)', async () => {
    const f = ctx.ragCourse
    if (!f) return blocked('Cần suite ai chạy trước trong cùng lượt để có khoá đã ingest.')
    const rows = []
    for (const n of [1, 2, 4, 8]) {
      const clients = []
      for (let i = 0; i < n; i++) clients.push(await learner(ctx))
      const out = await Promise.all(clients.map(async (c) => { const t0 = performance.now(); const r = await c.post('/api/rag/chat', { question: 'Hạn mức thấu chi của sản phẩm Sao Biển là bao nhiêu?', courseId: f.course.id }); return { status: r.status, ms: performance.now() - t0 } }))
      const ok200 = out.filter((r) => r.status === 200)
      rows.push(`${n}: success ${ok200.length}/${n}, p95 ${Math.round(pct(out.map((r) => r.ms), 95) / 1000)}s`)
    }
    return gap(`${rows.join(' | ')}. Đợt ngắn, chưa giữ mỗi mức ≥2 phút và chưa tới 12/16; cold/warm chưa tách. Chạy đầy đủ bằng scripts/loadtest/ai-concurrency.js trên staging.`)
  })
  await tc('NF-P05', 'vượt trần job trả 429 có kiểm soát', async () => {
    const f = ctx.ragCourse
    if (!f) return blocked('Cần suite ai chạy trước.')
    const out = await Promise.all(Array.from({ length: 24 }, () => admin.post('/api/admin/quizzes/generate/jobs', { courseId: f.course.id, singleChoiceCount: 1 })))
    const codes = out.map((r) => r.status)
    docker('restart', 'cbstest-rag')   // Drops the queued jobs so they stop using the shared Ollama.
    await sleep(6000)
    assert.ok(codes.includes(429), `24 job đồng thời trả ${[...new Set(codes)]}: RAG trả 429 nhưng QuizGenerationServiceImpl.startJob đổi mọi RestClientException thành 503 (job thẻ thì giữ 429)`)
    assert.ok(codes.every((c) => c < 500), `${[...new Set(codes)]}`)
    return `${codes.filter((c) => c === 429).length}/24 bị 429. Bậc 1→5 job với tỷ lệ hoàn thành chưa đo vì cần model và thời lượng dài.`
  })

  // E2E-09 / NF-R04 backup + restore into a brand-new DB and upload volume.
  await tc('E2E-09', 'backup DB + file, phục hồi sang môi trường riêng, đối soát', async () => {
    const f = await freshCourse(ctx, { lessons: [{ questions: 2, settings: { requiredForProgress: false } }] }); const s = await learner(ctx)
    await readAndComplete(s, f.lesson.id)
    await s.post(`/api/quizzes/lessons/${f.lesson.id}/submit`, { selectedAnswers: answersFor(f.lesson.qs, 2) })
    await s.post(`/api/lessons/${f.lesson.id}/flashcards/mine`, { type: 'TERM', front: 'Phục hồi', back: 'x' })
    const upload = ok(await admin.post('/api/admin/documents/upload', undefined, { form: multipart({}, [{ name: 'r.pdf', data: '%PDF-1.4 restore-marker' }]) }), 'doc')
    const counts = (run) => Object.fromEntries(['users', 'courses', 'lessons', 'questions', 'quiz_attempts', 'user_completed_lessons', 'lesson_progress', 'flashcards', 'flashcard_study_plans', 'notifications', 'course_enrollment_requests'].map((t) => [t, run(`select count(*) from ${t}`)]))
    const before = counts((q) => sqlOne(q))
    const pointsBefore = sqlOne('select coalesce(sum(points),0) from users')

    const t0 = Date.now()
    docker('exec', 'cbstest-db', 'sh', '-c', 'pg_dump -U test -d coretraining_test -Fc -f /tmp/backup.dump')
    docker('exec', 'cbstest-backend', 'tar', '-czf', '/tmp/uploads.tgz', '-C', '/app', 'uploads')
    const pgRestore = ['exec', '-i', 'cbstest-db', 'psql', '-U', 'test', '-d', 'postgres', '-AtX']
    execFileSync('docker', pgRestore, { input: 'drop database if exists restore_test; create database restore_test;', encoding: 'utf8' })
    docker('exec', 'cbstest-db', 'pg_restore', '-U', 'test', '-d', 'restore_test', '--no-owner', '/tmp/backup.dump')
    const base = docker('inspect', 'cbstest-backend', '--format', '{{range .Config.Env}}{{println .}}{{end}}').split('\n').filter((l) => /=/.test(l) && !/^(SPRING_DATASOURCE_URL|PATH|JAVA_|LANG|LC_)/.test(l)).flatMap((l) => ['-e', l])
    try {
      docker('run', '-d', '--name', 'cbstest-restore', '--network', 'cbstest_default', '-p', '127.0.0.1:19001:8080', ...base, '-e', 'SPRING_DATASOURCE_URL=jdbc:postgresql://test-postgres:5432/restore_test', 'cbstest-backend:latest')
      const tmp = 'cbstest-uploads-copy'
      spawnSync('docker', ['cp', 'cbstest-backend:/tmp/uploads.tgz', `${process.env.TEMP ?? '.'}/${tmp}.tgz`], { stdio: 'ignore' })
      spawnSync('docker', ['cp', `${process.env.TEMP ?? '.'}/${tmp}.tgz`, 'cbstest-restore:/tmp/uploads.tgz'], { stdio: 'ignore' })
      docker('exec', 'cbstest-restore', 'tar', '-xzf', '/tmp/uploads.tgz', '-C', '/app')
      let up = false
      for (let i = 0; i < 80 && !up; i++) { try { up = (await fetch('http://127.0.0.1:19001/api/auth/csrf')).status === 200 } catch { /* starting */ } if (!up) await sleep(1000) }
      assert.ok(up, 'restored backend did not start')
      const rto = Math.round((Date.now() - t0) / 1000)
      const q = (query) => execFileSync('docker', ['exec', '-i', 'cbstest-db', 'psql', '-U', 'test', '-d', 'restore_test', '-AtX'], { input: query, encoding: 'utf8' }).trim()
      assert.deepEqual(counts(q), before, 'row counts differ')
      assert.equal(q('select coalesce(sum(points),0) from users'), pointsBefore, 'total points differ')
      assert.equal(q('select count(*) from user_completed_lessons c left join users u on u.id=c.user_id where u.id is null'), '0', 'orphans')
      // Same JWT secret: a fresh login against the restored system.
      const restored = await fetch('http://127.0.0.1:19001/api/auth/csrf')
      const xsrf = restored.headers.getSetCookie().map((c) => c.split(';')[0]).find((c) => c.startsWith('XSRF-TOKEN'))
      const login = await fetch('http://127.0.0.1:19001/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json', Cookie: xsrf, 'X-XSRF-TOKEN': xsrf.split('=')[1] }, body: JSON.stringify({ username: s.user.username, password: ACTIVE_PASSWORD }) })
      assert.equal(login.status, 200, 'login on restored system')
      const cookie = login.headers.getSetCookie().map((c) => c.split(';')[0]).join('; ')
      const review = await fetch(`http://127.0.0.1:19001/api/quizzes/lessons/${f.lesson.id}/review`, { headers: { Cookie: cookie } })
      assert.equal(review.status, 200, 'quiz review on restored system')
      const file = await fetch(`http://127.0.0.1:19001${upload.fileUrl}`, { headers: { Cookie: (await (async () => { const a = await fetch('http://127.0.0.1:19001/api/auth/csrf'); const x = a.headers.getSetCookie().map((c) => c.split(';')[0]).find((c) => c.startsWith('XSRF-TOKEN')); const l = await fetch('http://127.0.0.1:19001/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json', Cookie: x, 'X-XSRF-TOKEN': x.split('=')[1] }, body: JSON.stringify({ username: 'admin', password: 'Test-Admin-Pass-1' }) }); return l.headers.getSetCookie().map((c) => c.split(';')[0]).join('; ') })()) } })
      assert.equal(file.status, 200, 'restored file not served')
      ctx.rto = rto
      return `RTO đo được ${rto}s trên dữ liệu test (${before.users} user). RPO theo tần suất backup, chưa có job backup định kỳ trong repo.`
    } finally {
      spawnSync('docker', ['rm', '-f', 'cbstest-restore'], { stdio: 'ignore' })
      spawnSync('docker', ['exec', 'cbstest-db', 'rm', '-f', '/tmp/backup.dump'], { stdio: 'ignore' })
      execFileSync('docker', pgRestore, { input: 'drop database if exists restore_test;', encoding: 'utf8' })
    }
  })
  await tc('NF-R04', 'RPO/RTO', async () => gap(`Quy trình pg_dump + tar khôi phục được trong ${ctx.rto ?? '?'}s ở cỡ dữ liệu test, dưới mức RTO 4h [ĐX]. Không thấy job backup tự động trong repo nên chưa chứng minh RPO ≤24h; chủ hệ thống cần chốt.`))
  void Client; void loginAs; void sql; void expectRejected; void ok
}
