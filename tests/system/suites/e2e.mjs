// Section 3 cross-system flows, driven through the real API with new accounts for each flow.
// E2E-08 lives in nonfunctional.mjs and E2E-09 in resilience.mjs because they need fault injection and load.
import { tc, assert, expectStatus, expectRejected, sql, sqlNum, sqlOne, multipart, BASE, Client, loginAs, gap, sleep } from '../lib.mjs'
import { createUser, activate, addRole, createCourse, createChapter, createLesson, publish, allocate, addQuestions, quizSettings, answersFor, learner, freshCourse, setStatus, updateUser, readAndComplete, ok, USER_PASSWORD, ACTIVE_PASSWORD } from '../seed.mjs'
import { readFileSync } from 'node:fs'
import { parseCsv } from './reports.mjs'

const fixture = (name) => readFileSync(new URL(`../fixtures/${name}`, import.meta.url))
const pendingId = (userId, courseId) => sqlOne(`select id from course_enrollment_requests where user_id=${userId} and course_id='${courseId}' order by id desc limit 1`)
const pts = (id) => sqlNum(`select points from users where id=${id}`)
const save = (c, l, b) => c.post(`/api/users/me/lessons/${l}/progress`, b)
const APP_NOW = "(now() at time zone 'Asia/Ho_Chi_Minh')"

export async function run(ctx) {
  const { admin, u1, u3, u8, A, B } = ctx
  const doc = async (name, data, purpose) => ({ ...ok(await admin.post('/api/admin/documents/upload', undefined, { form: multipart({}, [{ name, data }]) }), 'doc'), purpose })
  const video = ok(await admin.post('/api/admin/videos/upload', undefined, { form: multipart({}, [{ name: 'v.mp4', data: fixture('v100.mp4'), type: 'video/mp4' }]) }), 'video')
  const videoUrl = video.url ?? video.videoUrl ?? video.fileUrl

  // ------- E2E-01
  const flow = {}
  await tc('E2E-01', '1-2 U0 tạo đơn vị, người học, cấu trúc đào tạo', async () => {
    flow.unit = ok(await admin.post('/api/admin/units', { name: `E01-${ctx.run}`, type: 'UNIT', active: true }), 'unit')
    flow.child = ok(await admin.post('/api/admin/units', { name: `E01-1-${ctx.run}`, type: 'DEPARTMENT', parentId: flow.unit.id, active: true }), 'child')
    flow.domain = ok(await admin.post('/api/admin/training-domains', { code: `E01D${ctx.run}`, name: `E01 ${ctx.run}`, status: 'ACTIVE' }), 'domain')
    flow.program = ok(await admin.post('/api/admin/training-programs', { domainId: flow.domain.id, code: `E01P${ctx.run}`, name: `E01P ${ctx.run}`, status: 'ACTIVE', unitIds: [flow.unit.id] }), 'program')
    flow.course = await createCourse(admin, flow.program.id, 'E01')
    flow.ch1 = await createChapter(admin, flow.course.id, 'Chương 1'); flow.ch2 = await createChapter(admin, flow.course.id, 'Chương 2')
    flow.learnerUser = await createUser(admin, { unitId: flow.child.id })
    flow.teacher = await createUser(admin, { unitId: flow.unit.id, learner: false })
    flow.manager = await createUser(admin, { unitId: flow.unit.id, learner: false })
    await addRole(admin, { code: `E01T${ctx.run}`, name: `GV ${ctx.run}`, unitIds: [flow.unit.id], userIds: [flow.teacher.id], permissions: ['courses:read', 'courses:create', 'courses:update', 'exams:read', 'exams:create', 'exams:update', 'documents:read', 'documents:create'] })
    await addRole(admin, { code: `E01M${ctx.run}`, name: `QL ${ctx.run}`, unitIds: [flow.unit.id], userIds: [flow.manager.id], permissions: ['enrollments:read', 'enrollments:update', 'overview:read', 'courses:read'] })
    assert.equal(sqlOne(`select must_change_password from users where id=${flow.learnerUser.id}`), 't')
  })
  await tc('E2E-01', '3 U2 tải nội dung, tạo quiz và flashcard', async () => {
    flow.t = await activate(flow.teacher.username)
    // The instructor uploads their own files: an upload belongs to whoever made it.
    const upload = async (name, data, purpose) => ({ ...ok(await flow.t.post('/api/admin/documents/upload', undefined, { form: multipart({}, [{ name, data }]) }), 'teacher doc'), purpose })
    const slide = await upload('slide.pptx', Buffer.from('PK\x03\x04s'), 'SLIDE')
    const aux = await upload('aux.pdf', Buffer.from('%PDF-1.4 x'), 'SUPPORTING')
    const tv = ok(await flow.t.post('/api/admin/videos/upload', undefined, { form: multipart({}, [{ name: 'v.mp4', data: fixture('v100.mp4'), type: 'video/mp4' }]) }), 'teacher video')
    flow.L1 = expectStatus(await flow.t.post('/api/admin/lessons', { id: `E01L1${ctx.run}`, title: 'L1', courseId: flow.course.id, chapterId: flow.ch1.id, textBody: '<p>Văn bản L1</p>', videoUrl: tv.url ?? tv.videoUrl ?? tv.fileUrl, videoSource: 'UPLOAD', documents: [slide, aux] }), 200).json
    flow.L2 = await createLesson(admin, flow.course, flow.ch1, { title: 'L2', textBody: '<p>L2</p>' })
    flow.L3 = await createLesson(admin, flow.course, flow.ch2, { title: 'L3', videoUrl, videoSource: 'UPLOAD' })
    flow.qs = await addQuestions(admin, flow.course, flow.L1, 10)
    await quizSettings(admin, flow.course, flow.L1, { passingScore: 80, requiredForProgress: true })
    const card = ok(await admin.post(`/api/admin/lessons/${flow.L1.id}/flashcards`, { type: 'TERM', front: 'CIF', back: 'Mã khách hàng', status: 'DRAFT' }), 'card')
    ok(await admin.put(`/api/admin/lessons/${flow.L1.id}/flashcards/status`, { ids: [card.id], status: 'PUBLISHED' }), 'publish card')
    flow.slideId = Number(sqlOne(`select id from lesson_documents where lesson_id='${flow.L1.id}' and purpose='SLIDE'`))
  })
  await tc('E2E-01', '4 xuất bản, phân bổ OPTIONAL cho đơn vị', async () => {
    await publish(admin, flow.course.id)
    await allocate(admin, flow.unit.id, flow.program.id, flow.course.id, 'OPTIONAL')
  })
  await tc('E2E-01', '5 người học đổi mật khẩu, gửi đăng ký, chưa học được', async () => {
    const first = await loginAs(flow.learnerUser.username, USER_PASSWORD)
    assert.ok([401, 403].includes((await first.get('/api/courses')).status), 'API open before password change')
    flow.s = await activate(flow.learnerUser.username)
    flow.s.user = flow.learnerUser
    assert.ok(expectStatus(await flow.s.get('/api/courses'), 200).text.includes(flow.course.id))
    expectStatus(await flow.s.post(`/api/users/me/register-course/${flow.course.id}`), 200)
    assert.ok([403, 404].includes((await flow.s.get(`/api/lessons/${flow.L1.id}`)).status))
    assert.ok([400, 403, 404].includes((await flow.s.get(`/api/videos/access/${flow.L1.id}`)).status))
  })
  await tc('E2E-01', '6 quản lý duyệt, có thông báo; bài chưa mở vẫn khoá', async () => {
    flow.m = await activate(flow.manager.username)
    expectStatus(await flow.m.put(`/api/admin/enrollment-requests/${pendingId(flow.learnerUser.id, flow.course.id)}`, { approve: true }), 200)
    assert.ok(sqlNum(`select count(*) from notifications where recipient_id=${flow.learnerUser.id} and type='ENROLLMENT_APPROVED'`) === 1)
    const due = new Date(Date.now() + 7 * 3600e3 + 14 * 86400e3).toISOString().slice(0, 10)
    expectStatus(await admin.put(`/api/admin/users/${flow.learnerUser.id}/course-deadlines`, { courseId: flow.course.id, dueDate: due }), [200, 204], 'hạn học')
    assert.ok(expectStatus(await flow.s.get(`/api/courses/${flow.course.id}/outline`), 200).text.includes(flow.L1.id))
    expectStatus(await flow.s.get(`/api/lessons/${flow.L1.id}`), 200)
    for (const l of [flow.L2, flow.L3]) assert.ok([403, 404].includes((await flow.s.get(`/api/lessons/${l.id}`)).status), `${l.title} open early`)
  })
  await tc('E2E-01', '7 học đủ nội dung, ghi chú, bình luận', async () => {
    const { s } = flow
    expectStatus(await save(s, flow.L1.id, { contentType: 'TEXT', textCompleted: true }), 200)
    expectStatus(await save(s, flow.L1.id, { contentType: 'VIDEO', lastPositionSeconds: 92, watchedSeconds: 92, durationSeconds: 100 }), 200)
    expectStatus(await save(s, flow.L1.id, { contentType: 'DOCUMENT', documentId: flow.slideId, documentPage: 5, documentCompleted: true }), 200)
    expectStatus(await s.post(`/api/lessons/${flow.L1.id}/comments`, { content: 'Câu hỏi E2E' }), 200)
    expectStatus(await s.post(`/api/lessons/${flow.L1.id}/notes`, { content: 'Ghi chú E2E', timestampSeconds: 10, timeSeconds: 10, positionSeconds: 10 }), 200)
    assert.ok(expectStatus(await s.get(`/api/lessons/${flow.L1.id}/flashcards`), 200).text.includes('CIF'))
  })
  await tc('E2E-01', '8 trượt rồi đạt quiz, học tiếp đến hết khoá, không cộng trùng', async () => {
    const { s } = flow; const id = flow.learnerUser.id
    assert.equal(expectStatus(await s.post(`/api/quizzes/lessons/${flow.L1.id}/submit`, { selectedAnswers: answersFor(flow.qs, 4) }), 200).json.passed, false)
    assert.equal(pts(id), 0)
    expectRejected(await s.post(`/api/users/me/complete-lesson/${flow.L1.id}`), 'complete before pass')
    assert.equal(expectStatus(await s.post(`/api/quizzes/lessons/${flow.L1.id}/submit`, { selectedAnswers: answersFor(flow.qs, 9) }), 200).json.passed, true)
    await s.post(`/api/users/me/complete-lesson/${flow.L1.id}`)
    assert.equal(pts(id), 100)
    expectStatus(await readAndComplete(s, flow.L2.id), 200)
    expectStatus(await save(s, flow.L3.id, { contentType: 'VIDEO', lastPositionSeconds: 100, watchedSeconds: 100, durationSeconds: 100 }), 200)
    expectStatus(await s.post(`/api/users/me/complete-lesson/${flow.L3.id}`), 200)
    assert.equal(pts(id), 300)
    assert.equal(sqlNum(`select count(*) from user_completed_courses where user_id=${id} and course_id='${flow.course.id}'`), 1)
  })
  await tc('E2E-01', '9 đánh giá, hồ sơ, dashboard, BXH, báo cáo, CSV nhất quán', async () => {
    const { s, m } = flow; const id = flow.learnerUser.id
    expectStatus(await s.put(`/api/courses/${flow.course.id}/rating`, { stars: 5, content: 'Tốt' }), 200)
    const me = expectStatus(await s.get('/api/users/me'), 200).json
    assert.equal(me.points, 300)
    const row = expectStatus(await s.get('/api/users/me/learning/dashboard/courses?tab=mine&page=1&size=50'), 200).json.items.find((c) => c.course.id === flow.course.id)
    assert.equal(row.percent, 100); assert.equal(row.learningStatus && row.completedCount, 3)
    const board = expectStatus(await s.get('/api/leaderboard/page?page=0&size=100'), 200).text
    assert.ok(board.includes(flow.learnerUser.username) || board.includes(flow.learnerUser.fullName), 'not on leaderboard')
    const q = `view=completion&course=${encodeURIComponent(flow.course.name)}&page=0&size=50`
    const rep = expectStatus(await m.get(`/api/admin/reports/training/read?${q}`), 200)
    assert.ok(rep.text.includes(flow.learnerUser.fullName))
    assert.equal(rep.json.summary.complete, 1, `summary ${JSON.stringify(rep.json.summary).slice(0, 160)}`)
    const file = expectStatus(await m.get(`/api/admin/reports/training/export?view=completion&course=${encodeURIComponent(flow.course.name)}`, { headers: { Accept: 'text/csv' } }), 200)
    assert.ok(parseCsv(file.text).flat().some((c) => c.includes(flow.learnerUser.fullName)))
    const attempt = sqlOne(`select id from user_quiz_scores where user_id=${id} order by id desc limit 1`)
    expectStatus(await m.get(`/api/admin/reports/training/attempts/${attempt}/analysis`), 200)
  })
  await tc('E2E-01', '10 đăng xuất, đăng nhập lại: tiến độ và quyền giữ nguyên', async () => {
    expectStatus(await flow.s.post('/api/auth/logout'), 204)
    const again = await loginAs(flow.learnerUser.username, ACTIVE_PASSWORD)
    const me = expectStatus(await again.get('/api/users/me'), 200).json
    assert.equal(me.points, 300)
    expectStatus(await again.get(`/api/lessons/${flow.L3.id}`), 200)
    assert.ok([403].includes((await again.get('/api/admin/users')).status))
  })
  await tc('E2E-01', 'nguồn AI đã ingest và duyệt nội dung AI', async () => gap('Ingest, gắn nguồn và duyệt AI kiểm tra riêng ở F-I01/F-I05/F-I09, không chạy lại trong luồng này do chi phí suy luận.'))

  // ------- E2E-02
  await tc('E2E-02', 'gửi 2 lần tuần tự + phát lại không tạo trùng', async () => {
    const f = await freshCourse(ctx, { grant: 'OPTIONAL' }); const s = await learner(ctx)
    for (let i = 0; i < 3; i++) expectStatus(await s.post(`/api/users/me/register-course/${f.course.id}`), 200)
    assert.equal(sqlNum(`select count(*) from course_enrollment_requests where user_id=${s.user.id} and course_id='${f.course.id}' and status='PENDING'`), 1)
    ctx.e2 = { f, s }
  })
  await tc('E2E-02', 'từ chối: bài, tài liệu, quiz, chat đều chặn', async () => {
    const { f, s } = ctx.e2
    ok(await admin.put(`/api/admin/enrollment-requests/${pendingId(s.user.id, f.course.id)}`, { approve: false, rejectionReason: 'Thiếu điều kiện' }), 'reject')
    for (const p of [`/api/lessons/${f.lesson.id}`, `/api/quizzes/lessons/${f.lesson.id}/questions`]) assert.ok([403, 404].includes((await s.get(p)).status), p)
    assert.ok(sqlNum(`select count(*) from notifications where recipient_id=${s.user.id} and message like '%Thiếu điều kiện%'`) === 1, 'no reason in notification')
  })
  await tc('E2E-02', 'đăng ký lại rồi duyệt, học bình thường', async () => {
    const { f, s } = ctx.e2
    expectStatus(await s.post(`/api/users/me/register-course/${f.course.id}`), 200)
    ok(await admin.put(`/api/admin/enrollment-requests/${pendingId(s.user.id, f.course.id)}`, { approve: true }), 'approve')
    expectStatus(await readAndComplete(s, f.lesson.id), 200)
  })
  await tc('E2E-02', 'khoá archive trước khi duyệt', async () => {
    const f = await freshCourse(ctx, { grant: 'OPTIONAL' }); const s = await learner(ctx)
    await s.post(`/api/users/me/register-course/${f.course.id}`)
    ok(await setStatus(admin, f.course.id, 'ARCHIVED'), 'archive')
    const res = await admin.put(`/api/admin/enrollment-requests/${pendingId(s.user.id, f.course.id)}`, { approve: true })
    assert.ok([403, 404].includes((await s.get(`/api/lessons/${f.lesson.id}`)).status), `archived course opened after approval (review HTTP ${res.status})`)
  })
  await tc('E2E-02', 'hai quản trị duyệt/từ chối cùng lúc: trạng thái cuối duy nhất', async () => {
    const f = await freshCourse(ctx, { grant: 'OPTIONAL' }); const s = await learner(ctx)
    await s.post(`/api/users/me/register-course/${f.course.id}`)
    const id = pendingId(s.user.id, f.course.id)
    const [a, b] = await Promise.all([admin.put(`/api/admin/enrollment-requests/${id}`, { approve: true }), u1.put(`/api/admin/enrollment-requests/${id}`, { approve: false, rejectionReason: 'x' })])
    assert.equal([a, b].filter((r) => r.status === 200).length, 1, `statuses ${a.status},${b.status}`)
    const status = sqlOne(`select status from course_enrollment_requests where id=${id}`)
    const registered = sqlNum(`select count(*) from user_registered_courses where user_id=${s.user.id} and course_id='${f.course.id}'`)
    assert.equal(status === 'APPROVED', registered === 1, `status=${status} registered=${registered}`)
    assert.equal(sqlNum(`select count(*) from notifications where recipient_id=${s.user.id} and type like 'ENROLLMENT_%'`), 1, 'contradictory notifications')
  })

  // ------- E2E-03
  const e3 = await freshCourse(ctx, { lessons: [{ videoUrl, videoSource: 'UPLOAD', documents: [await doc('s.pptx', Buffer.from('PK\x03\x04'), 'SLIDE'), await doc('a.pdf', Buffer.from('%PDF-1.4'), 'SUPPORTING')] }] })
  const [slideE3, auxE3] = sqlOne(`select string_agg(id::text, ',' order by document_order) from lesson_documents where lesson_id='${e3.lesson.id}'`).split(',').map(Number)
  const dev1 = await learner(ctx)
  const dev2 = await loginAs(dev1.user.username, ACTIVE_PASSWORD)
  await tc('E2E-03', 'lưu mốc từng tài liệu, không gán nhầm', async () => {
    await save(dev1, e3.lesson.id, { contentType: 'DOCUMENT', documentId: slideE3, documentPage: 4 })
    await save(dev1, e3.lesson.id, { contentType: 'DOCUMENT', documentId: auxE3, documentPage: 2 })
    await save(dev1, e3.lesson.id, { contentType: 'VIDEO', lastPositionSeconds: 40, watchedSeconds: 40, durationSeconds: 100 })
    const pos = JSON.parse(sqlOne(`select document_positions from lesson_progress where user_id=${dev1.user.id}`))
    assert.equal(pos[slideE3].page ?? pos[slideE3].documentPage, 4); assert.equal(pos[auxE3].page ?? pos[auxE3].documentPage, 2)
  })
  await tc('E2E-03', 'request bị cắt giữa chừng: dữ liệu đã xác nhận còn nguyên', async () => {
    const ac = new AbortController()
    const p = fetch(`${BASE}/api/users/me/lessons/${e3.lesson.id}/progress`, { method: 'POST', signal: ac.signal, headers: { 'Content-Type': 'application/json', Cookie: dev1.cookieHeader(), 'X-XSRF-TOKEN': dev1.cookies.get('XSRF-TOKEN') }, body: JSON.stringify({ contentType: 'VIDEO', lastPositionSeconds: 5, watchedSeconds: 5, durationSeconds: 100 }) }).catch(() => null)
    ac.abort(); await p
    await sleep(300)
    assert.ok(sqlNum(`select watched_seconds from lesson_progress where user_id=${dev1.user.id}`) >= 40, 'confirmed progress lost')
  })
  await tc('E2E-03', 'request cũ đến sau không giảm watchedSeconds; lastPosition là bookmark', async () => {
    await save(dev2, e3.lesson.id, { contentType: 'VIDEO', lastPositionSeconds: 70, watchedSeconds: 70, durationSeconds: 100 })
    const stale = expectStatus(await save(dev1, e3.lesson.id, { contentType: 'VIDEO', lastPositionSeconds: 30, watchedSeconds: 30, durationSeconds: 100 }), 200).json
    assert.equal(stale.watchedSeconds, 70); assert.equal(stale.lastPositionSeconds, 30)
  })
  await tc('E2E-03', 'thiết bị 2 hoàn thành; hai tab gọi đồng thời: 1 lần thưởng', async () => {
    await save(dev2, e3.lesson.id, { contentType: 'TEXT', textCompleted: true })
    await save(dev2, e3.lesson.id, { contentType: 'VIDEO', lastPositionSeconds: 95, watchedSeconds: 95, durationSeconds: 100 })
    await save(dev2, e3.lesson.id, { contentType: 'DOCUMENT', documentId: slideE3, documentPage: 9, documentCompleted: true })
    const out = await Promise.all([dev1, dev2, dev1, dev2].map((c) => c.post(`/api/users/me/complete-lesson/${e3.lesson.id}`)))
    assert.ok(out.every((r) => r.status === 200 || r.status === 409), `statuses ${out.map((r) => r.status)}`)
    assert.equal(pts(dev1.user.id), 100); assert.equal(sqlNum(`select count(*) from user_completed_lessons where user_id=${dev1.user.id}`), 1)
  })
  await tc('E2E-03', 'trạng thái đồng bộ offline trên UI', async () => gap('Cần kiểm tra UI khi mất mạng; [ĐX], xem phần trình duyệt của báo cáo.'))

  // ------- E2E-04
  const e4 = () => freshCourse(ctx, { lessons: [{ questions: 20, settings: { timeLimitMinutes: 1 } }] })
  await tc('E2E-04', 'reload/tab 2 không cấp thêm giờ; trả lời một phần; nộp đúng lúc', async () => {
    const f = await e4(); const s = await learner(ctx)
    await save(s, f.lesson.id, { contentType: 'TEXT', textCompleted: true })
    const a = (await s.post(`/api/quizzes/lessons/${f.lesson.id}/start`)).json
    sql(`update quiz_sessions set deadline_at = deadline_at - interval '20 seconds' where id='${a.sessionId}'`)
    const tab2 = await loginAs(s.user.username, ACTIVE_PASSWORD)
    const b = (await tab2.post(`/api/quizzes/lessons/${f.lesson.id}/start`)).json
    assert.equal(b.sessionId, a.sessionId); assert.ok(b.remainingSeconds < 41)
    const partial = Object.fromEntries(f.lesson.qs.slice(0, 5).map((q) => [q.id, q.correct]))
    const r = expectStatus(await s.post(`/api/quizzes/lessons/${f.lesson.id}/submit`, { sessionId: a.sessionId, selectedAnswers: partial }), 200).json
    assert.equal(r.score, 5)
  })
  await tc('E2E-04', 'mất response rồi nộp lại + 2 request song song: không cộng hai lần', async () => {
    const f = await e4(); const s = await learner(ctx)
    await save(s, f.lesson.id, { contentType: 'TEXT', textCompleted: true })
    const { sessionId } = (await s.post(`/api/quizzes/lessons/${f.lesson.id}/start`)).json
    const body = { sessionId, selectedAnswers: answersFor(f.lesson.qs, 20) }
    const out = await Promise.all([s.post(`/api/quizzes/lessons/${f.lesson.id}/submit`, body), s.post(`/api/quizzes/lessons/${f.lesson.id}/submit`, body)])
    assert.equal(out.filter((r) => r.status === 200).length, 1, `statuses ${out.map((r) => r.status)}`)
    expectRejected(await s.post(`/api/quizzes/lessons/${f.lesson.id}/submit`, body), 'resend')
    assert.equal(pts(s.user.id), 100, 'points paid twice')
    assert.equal(expectStatus(await s.get(`/api/quizzes/lessons/${f.lesson.id}/review`), 200).json.score, 20)
    assert.equal(sqlNum(`select count(*) from quiz_attempts where user_id=${s.user.id}`), 1)
  })
  for (const [offset, accept] of [[1, true], [-29, true], [-31, false]]) {
    await tc('E2E-04', `nộp tại deadline${offset > 0 ? '−' : '+'}${Math.abs(offset)}s`, async () => {
      const f = await e4(); const s = await learner(ctx)
      const { sessionId } = (await s.post(`/api/quizzes/lessons/${f.lesson.id}/start`)).json
      // The margin is one second, so the row is moved immediately before submitting.
      sql(`update quiz_sessions set deadline_at = ${APP_NOW} + interval '${offset} seconds' where id='${sessionId}'`)
      const res = await s.post(`/api/quizzes/lessons/${f.lesson.id}/submit`, { sessionId, selectedAnswers: answersFor(f.lesson.qs, 1) })
      if (accept) expectStatus(res, 200); else expectRejected(res)
    })
  }

  // ------- E2E-05
  const e5 = await freshCourse(ctx, { lessons: [{ videoUrl, videoSource: 'UPLOAD' }, {}] })
  const [done, partial] = [await learner(ctx), await learner(ctx)]
  await tc('E2E-05', 'chuẩn bị: một người xong, một người dở', async () => {
    await save(done, e5.lesson.id, { contentType: 'TEXT', textCompleted: true })
    await save(done, e5.lesson.id, { contentType: 'VIDEO', lastPositionSeconds: 100, watchedSeconds: 100, durationSeconds: 100 })
    expectStatus(await done.post(`/api/users/me/complete-lesson/${e5.lesson.id}`), 200)
    await save(partial, e5.lesson.id, { contentType: 'TEXT', textCompleted: true })
    assert.equal(pts(done.user.id), 100)
  })
  await tc('E2E-05', 'sửa văn bản, thêm slide; đếm và thông báo đúng người đã xong', async () => {
    const slide = await doc('moi.pptx', Buffer.from('PK\x03\x04m'), 'SLIDE')
    expectStatus(await admin.put(`/api/admin/lessons/${e5.lesson.id}`, { title: 'L1 mới', textBody: '<p>Nội dung mới</p>', videoUrl, videoSource: 'UPLOAD', documents: [slide] }), 200)
    assert.equal(expectStatus(await admin.get(`/api/admin/lessons/${e5.lesson.id}/completed-learners-count`), 200).json.count, 1, 'completed count')
    const res = expectStatus(await admin.post(`/api/admin/lessons/${e5.lesson.id}/changelog-notify`, { changeSummary: 'Thêm slide mới', notifyLearners: true }), 200).json
    assert.equal(res.notifiedCount, 1, 'notifiedCount')
    assert.equal(sqlNum(`select count(*) from notifications where recipient_id=${done.user.id} and type not like 'ENROLLMENT%'`), 1, 'notification to completer')
    assert.equal(sqlNum(`select count(*) from notifications where recipient_id=${partial.user.id}`), 0)
    assert.ok(expectStatus(await done.get(`/api/lessons/${e5.lesson.id}/latest-changelog`), 200).text.includes('Thêm slide mới'))
  })
  await tc('E2E-05', 'người học dở bị đánh giá theo yêu cầu mới; không thưởng lại', async () => {
    await save(partial, e5.lesson.id, { contentType: 'VIDEO', lastPositionSeconds: 100, watchedSeconds: 100, durationSeconds: 100 })
    expectRejected(await partial.post(`/api/users/me/complete-lesson/${e5.lesson.id}`), 'new slide unread')
    await done.post(`/api/users/me/complete-lesson/${e5.lesson.id}`)
    assert.equal(pts(done.user.id), 100, 're-awarded')
  })
  await tc('E2E-05', 'giữ hay reset completion lịch sử', async () => {
    assert.equal(sqlNum(`select count(*) from user_completed_lessons where user_id=${done.user.id} and lesson_id='${e5.lesson.id}'`), 1)
    return gap('Hiện tại giữ completion cũ dù thêm slide bắt buộc. Kịch bản yêu cầu chốt policy [ĐX] trước khi nghiệm thu.')
  })
  await tc('E2E-05', 'phát lại progress trỏ slide đã thay thế bị chặn', async () => {
    const f = await freshCourse(ctx, { lessons: [{ documents: [await doc('c.pptx', Buffer.from('PK\x03\x04c'), 'SLIDE')] }] }); const s = await learner(ctx)
    const old = Number(sqlOne(`select id from lesson_documents where lesson_id='${f.lesson.id}'`))
    await save(s, f.lesson.id, { contentType: 'DOCUMENT', documentId: old, documentPage: 2 })
    ok(await admin.put(`/api/admin/lessons/${f.lesson.id}`, { title: 'x', textBody: '<p>x</p>', documents: [await doc('d.pptx', Buffer.from('PK\x03\x04d'), 'SLIDE')] }), 'replace')
    expectRejected(await save(s, f.lesson.id, { contentType: 'DOCUMENT', documentId: old, documentPage: 3, documentCompleted: true }), 'old version')
  })

  // ------- E2E-06
  const revoke = async (name, doRevoke, restore) => {
    await tc('E2E-06', `thu hồi: ${name}`, async () => {
      const f = await freshCourse(ctx, { lessons: [{ videoUrl, videoSource: 'UPLOAD', documents: [await doc('x.pdf', Buffer.from('%PDF-1.4'), 'SUPPORTING')] }] })
      const s = await learner(ctx)
      await save(s, f.lesson.id, { contentType: 'TEXT', textCompleted: true })
      const card = ok(await s.post(`/api/lessons/${f.lesson.id}/flashcards/mine`, { type: 'TERM', front: 'f', back: 'b' }), 'card')
      const fileUrl = sqlOne(`select file_url from lesson_documents where lesson_id='${f.lesson.id}'`)
      const stream = (await s.get(`/api/videos/access/${f.lesson.id}`)).json.streamUrl
      await doRevoke(f, s)
      const denied = [401, 403, 404]
      for (const [what, res] of [
        ['bài', await s.get(`/api/lessons/${f.lesson.id}`)], ['tài liệu', await s.get(fileUrl)], ['flashcard', await s.get(`/api/lessons/${f.lesson.id}/flashcards`)],
        ['token video mới', await s.get(`/api/videos/access/${f.lesson.id}`)], ['tiến độ', await save(s, f.lesson.id, { contentType: 'TEXT', textCompleted: true })],
      ]) assert.ok(denied.includes(res.status) || res.status === 400, `${what} còn truy cập được: HTTP ${res.status}`)
      const oldToken = await fetch(new URL(stream, BASE))
      assert.ok(oldToken.status >= 400, `token video cũ còn phát: HTTP ${oldToken.status}`)
      if (restore) {
        await restore(f, s)
        expectStatus(await s.get(`/api/lessons/${f.lesson.id}`), 200, 'sau khôi phục')
        assert.equal(sqlOne(`select text_completed from lesson_progress where user_id=${s.user.id} and lesson_id='${f.lesson.id}'`), 't', 'tiến độ mất sau khôi phục')
      }
      void card
    })
  }
  await revoke('khoá tài khoản', (f, s) => updateUser(admin, s.user, { status: 'LOCKED' }).then((r) => expectStatus(r, 200)),
    async (f, s) => { expectStatus(await updateUser(admin, s.user, { status: 'ACTIVE' }), 200); await s.login(s.user.username, ACTIVE_PASSWORD) })
  await revoke('lưu trữ khoá', (f) => setStatus(admin, f.course.id, 'ARCHIVED').then((r) => expectStatus(r, 200)),
    async (f) => { expectStatus(await setStatus(admin, f.course.id, 'PUBLISHED'), 200) })
  await revoke('chuyển đơn vị', async (f, s) => { expectStatus(await updateUser(admin, s.user, { unitId: B.id }), 200); await s.login(s.user.username, ACTIVE_PASSWORD) },
    async (f, s) => { expectStatus(await updateUser(admin, s.user, { unitId: A.id }), 200); await s.login(s.user.username, ACTIVE_PASSWORD) })
  await tc('E2E-06', 'U8 sửa khoá B và đổi ID sang nhánh B bị chặn', async () => {
    const res = await u8.put(`/api/admin/lessons/${ctx.C4.lesson.id}`, { title: 'hack', textBody: '<p>x</p>' })
    assert.ok([401, 403, 404].includes(res.status))
  })
  await tc('E2E-06', 'WebSocket cũ bị đóng sau khoá tài khoản', async () => {
    const s = await learner(ctx)
    // Native WebSocket here cannot send cookies, so the socket check needs the browser pass.
    void s
    return gap('Runtime kiểm thử không gửi được cookie qua WebSocket; cần kiểm tra trên trình duyệt. Code có quét định kỳ 15 giây (NotificationWebSocketHandler).')
  })
  await tc('E2E-06', 'SLA thu hồi token stream', async () => gap('Token video kiểm tra lại quyền ở mỗi request nên thu hồi có hiệu lực ngay; riêng cache permission và socket 15 giây. [ĐX] cần chốt ngưỡng chấp nhận.'))

  // ------- E2E-07
  await tc('E2E-07', 'thẻ thủ công duyệt rồi publish; thẻ riêng; lịch; không tăng điểm', async () => {
    const f = await freshCourse(ctx, { lessons: [{}, {}] }); const s = await learner(ctx)
    const card = ok(await admin.post(`/api/admin/lessons/${f.lesson.id}/flashcards`, { type: 'TERM', front: 'Chung', back: 'x', status: 'DRAFT' }), 'draft')
    assert.ok(!(await s.get(`/api/lessons/${f.lesson.id}/flashcards`)).text.includes('Chung'))
    ok(await admin.put(`/api/admin/lessons/${f.lesson.id}/flashcards/status`, { ids: [card.id], status: 'PUBLISHED' }), 'publish')
    const mine = ok(await s.post(`/api/lessons/${f.lesson.id}/flashcards/mine`, { type: 'CLOZE', front: 'Điền ___', back: 'x' }), 'mine')
    const today = new Date(Date.now() + 7 * 3600e3).toISOString().slice(0, 10)
    const end = new Date(Date.now() + 7 * 3600e3 + 30 * 86400e3).toISOString().slice(0, 10)
    const body = { startDate: today, endDate: end, weekdays: [1, 2, 3, 4, 5, 6, 7], reminderTime: '08:00', active: true }
    const whole = ok(await s.post('/api/users/me/flashcard-plans', { ...body, name: 'Cả bài', items: [{ lessonId: f.lesson.id, cardIds: null }] }), 'whole')
    const fixed = ok(await s.post('/api/users/me/flashcard-plans', { ...body, name: 'Cố định', items: [{ lessonId: f.lesson.id, cardIds: [mine.id] }] }), 'fixed')
    const later = ok(await s.post(`/api/lessons/${f.lesson.id}/flashcards/mine`, { type: 'TERM', front: 'Thẻ sau', back: 'x' }), 'later')
    assert.ok(expectStatus(await s.get(`/api/users/me/flashcard-plans/${whole.id}/cards`), 200).json.cards.some((c) => c.id === later.id))
    assert.ok(!expectStatus(await s.get(`/api/users/me/flashcard-plans/${fixed.id}/cards`), 200).json.cards.some((c) => c.id === later.id))
    assert.equal(pts(s.user.id), 0, 'points from browsing')
    assert.equal(sqlNum(`select count(*) from user_completed_lessons where user_id=${s.user.id}`), 0)
    const other = await learner(ctx)
    assert.ok(!(await other.get(`/api/lessons/${f.lesson.id}/flashcards`)).text.includes('Thẻ sau'), 'personal card leaked')
  })
  void Client; void u3; void u1
}
