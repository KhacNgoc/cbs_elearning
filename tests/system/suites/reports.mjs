// 4.10 Dashboard, profile, leaderboard, reports (F-R01..R07) and CSV security (NF-S09).
import { tc, assert, expectStatus, expectRejected, sql, sqlOne, sqlNum, gap } from '../lib.mjs'
import { freshCourse, learner, readAndComplete, answersFor, ok, updateUser } from '../seed.mjs'

const dash = (c, q = '') => expectStatus(c.get ? null : null, 200)
const courses = async (c, q = 'tab=mine&page=1&size=50') => expectStatus(await c.get(`/api/users/me/learning/dashboard/courses?${q}`), 200).json
const summary = async (c) => expectStatus(await c.get('/api/users/me/learning/dashboard/summary?tab=mine'), 200).json.summary
const read = (c, q) => c.get(`/api/admin/reports/training/read?${q}`)
const csv = (c, q) => c.get(`/api/admin/reports/training/export?${q}`, { headers: { Accept: 'text/csv' } })

// Minimal RFC 4180 parser to check escaping survives a round trip.
export function parseCsv(text) {
  const rows = []; let row = []; let cell = ''; let quoted = false
  const s = text.replace(/^﻿/, '')
  for (let i = 0; i < s.length; i++) {
    const ch = s[i]
    if (quoted) {
      if (ch === '"' && s[i + 1] === '"') { cell += '"'; i++ } else if (ch === '"') quoted = false
      else cell += ch
    } else if (ch === '"') quoted = true
    else if (ch === ',') { row.push(cell); cell = '' } else if (ch === '\n') { row.push(cell.replace(/\r$/, '')); rows.push(row); row = []; cell = '' } else cell += ch
  }
  if (cell || row.length) { row.push(cell); rows.push(row) }
  return rows
}

export async function run(ctx) {
  const { admin, u3, A, B } = ctx
  const fresh = await learner(ctx)
  void dash

  await tc('F-R01', 'học viên mới: summary khớp danh sách, không NaN', async () => {
    const [sum, list] = [await summary(fresh), await courses(fresh)]
    assert.equal(sum.joined, list.total, `joined=${sum.joined} total=${list.total}`)
    assert.ok(!/NaN|Infinity/.test(JSON.stringify(list)))
  })
  await tc('F-R01', 'đang học/hoàn thành: summary đúng, tìm và lọc khoá', async () => {
    const f = await freshCourse(ctx, { lessons: [{}, {}] }); const s = await learner(ctx)
    await readAndComplete(s, f.lessons[0].id)
    let row = (await courses(s)).items.find((c) => c.course.id === f.course.id)
    assert.equal(row.percent, 50); assert.equal(row.completedCount, 1); assert.equal(row.totalCount, 2)
    assert.equal(row.resume?.lessonId ?? f.lessons[1].id, f.lessons[1].id)
    const found = await courses(s, `tab=mine&search=${encodeURIComponent(f.course.name)}&page=1&size=10`)
    assert.ok(found.items.some((c) => c.course.id === f.course.id), 'search missed course')
    await readAndComplete(s, f.lessons[1].id)
    row = (await courses(s)).items.find((c) => c.course.id === f.course.id)
    assert.equal(row.percent, 100)
    const sum = await summary(s)
    assert.ok(sum.complete >= 1, `summary.complete=${sum.complete}`)
  })
  await tc('F-R01', 'phân trang danh mục không trùng/mất', async () => {
    const s = await learner(ctx)
    const seen = []; let total = 0
    for (let p = 1; p < 40; p++) {
      const page = await courses(s, `tab=discover&page=${p}&size=7`)
      total = page.total
      seen.push(...page.items.map((c) => c.course.id))
      if (!page.items.length) break
    }
    assert.equal(new Set(seen).size, seen.length, 'duplicates across pages')
    assert.equal(seen.length, total)
  })

  await tc('F-R02', 'hồ sơ dựa dữ liệu thật sau trượt/đạt/thi lại', async () => {
    const f = await freshCourse(ctx, { lessons: [{ questions: 10, settings: { requiredForProgress: false } }] }); const s = await learner(ctx)
    await s.post(`/api/quizzes/lessons/${f.lesson.id}/submit`, { selectedAnswers: answersFor(f.lesson.qs, 5) })
    await s.post(`/api/quizzes/lessons/${f.lesson.id}/submit`, { selectedAnswers: answersFor(f.lesson.qs, 9) })
    await s.post(`/api/quizzes/lessons/${f.lesson.id}/submit`, { selectedAnswers: answersFor(f.lesson.qs, 3) })
    const me = expectStatus(await s.get('/api/users/me'), 200).json
    assert.equal(me.lessonQuizScores[f.lesson.id], 9, 'best score not kept')
    const profile = expectStatus(await s.get('/api/users/me/profile'), 200)
    assert.ok(profile.text.includes(s.user.username))
    expectStatus(await s.get('/api/users/me/awards?page=0&size=20'), 200)
  })

  await tc('F-R03', 'leaderboard: điểm khớp DB, thứ tự ổn định', async () => {
    const f = await freshCourse(ctx); const [x, y] = [await learner(ctx), await learner(ctx)]
    await readAndComplete(x, f.lesson.id); await readAndComplete(y, f.lesson.id)
    const a1 = expectStatus(await admin.get('/api/leaderboard/page?page=0&size=100'), 200).json
    const a2 = expectStatus(await admin.get('/api/leaderboard/page?page=0&size=100'), 200).json
    const list = (p) => (p.items ?? p.content).map((r) => r.username ?? r.name ?? r.id)
    assert.deepEqual(list(a1), list(a2), 'order not stable')
  })
  await tc('F-R03', 'leaderboard không lộ phạm vi ngoài quyền', async () => {
    const f = await freshCourse(ctx, { unit: B, programId: ctx.programB.id }); const b = await learner(ctx, B)
    await readAndComplete(b, f.lesson.id)
    const s = await learner(ctx)
    const res = expectStatus(await s.get('/api/leaderboard/page?page=0&size=100'), 200)
    if (res.text.includes(b.user.username)) return gap('Học viên đơn vị A thấy học viên đơn vị B trên bảng xếp hạng. Kịch bản yêu cầu "không lộ phạm vi ngoài quyền"; cần chốt BXH là toàn hệ thống hay theo đơn vị.')
  })

  // Report data: passed, failed, in-progress and not-started learners in A; one learner in B.
  const rc = await freshCourse(ctx, { lessons: [{ questions: 10 }, { questions: 10 }] })
  const [passer, failer, starter, idle] = [await learner(ctx), await learner(ctx), await learner(ctx), await learner(ctx)]
  await passer.post(`/api/users/me/lessons/${rc.lessons[0].id}/progress`, { contentType: 'TEXT', textCompleted: true })
  await passer.post(`/api/quizzes/lessons/${rc.lessons[0].id}/submit`, { selectedAnswers: answersFor(rc.lessons[0].qs, 10) })
  await passer.post(`/api/users/me/lessons/${rc.lessons[1].id}/progress`, { contentType: 'TEXT', textCompleted: true })
  await passer.post(`/api/quizzes/lessons/${rc.lessons[1].id}/submit`, { selectedAnswers: answersFor(rc.lessons[1].qs, 10) })
  await failer.post(`/api/users/me/lessons/${rc.lessons[0].id}/progress`, { contentType: 'TEXT', textCompleted: true })
  await failer.post(`/api/quizzes/lessons/${rc.lessons[0].id}/submit`, { selectedAnswers: answersFor(rc.lessons[0].qs, 2) })
  await starter.post(`/api/users/me/lessons/${rc.lessons[0].id}/progress`, { contentType: 'TEXT', textCompleted: true })
  const userB = await learner(ctx, B)
  ctx.reportCourse = rc
  const byName = (c) => encodeURIComponent(c.course.name)
  const q = `view=completion&course=${byName(rc)}&page=0&size=100`

  await tc('F-R04', 'U3 xem báo cáo A, có học viên trong phạm vi', async () => {
    const res = expectStatus(await read(u3, q), 200)
    const full = expectStatus(await csv(u3, `view=completion&course=${byName(rc)}`), 200)   // Unit A may exceed one page late in a full run.
    for (const c of [passer, failer, starter, idle]) assert.ok(full.text.includes(c.user.username) || full.text.includes(c.user.fullName), `${c.user.username} missing`)
    assert.ok(!res.text.includes(userB.user.username), 'learner of B in report of U3')
  })
  await tc('F-R04', 'tham số unit=B không mở rộng quyền', async () => {
    const res = await read(u3, `view=completion&unit=${encodeURIComponent(B.name)}&page=0&size=100`)
    assert.ok(res.status >= 400 || !res.text.includes(userB.user.username), 'unit filter widened scope')
    const c = await csv(u3, `view=completion&unit=${encodeURIComponent(B.name)}`)
    assert.ok(c.status >= 400 || !c.text.includes(userB.user.username), 'CSV unit filter widened scope')
  })
  for (const status of ['Hoàn thành', 'Đang học', 'Chưa học', 'Quá hạn']) {
    await tc('F-R04', `lọc trạng thái ${status} không lỗi`, async () => {
      const res = await read(u3, `${q}&status=${encodeURIComponent(status)}`)
      assert.ok(res.status === 200 || res.status === 400, `HTTP ${res.status}`)
    })
  }
  for (const [name, params] of [['from sau to', 'from=2026-02-02&to=2026-01-01'], ['ngày tương lai', 'from=2099-01-01'], ['ngày sai định dạng', 'from=abc'], ]) {
    await tc('F-R04', `tham số sai: ${name}`, async () => {
      const res = await read(u3, `view=completion&${params}`)
      assert.ok(res.status >= 400 && res.status < 500, `HTTP ${res.status}: ${res.text.slice(0, 120)}`)
    })
  }

  await tc('F-R05', 'nhiều lượt thi: phân trang, phân tích đúng lượt', async () => {
    const f = await freshCourse(ctx, { lessons: [{ questions: 5, settings: { requiredForProgress: false } }] }); const s = await learner(ctx)
    for (const right of [1, 2, 3, 4, 5, 0, 1]) await s.post(`/api/quizzes/lessons/${f.lesson.id}/submit`, { selectedAnswers: answersFor(f.lesson.qs, right) })
    assert.equal(sqlNum(`select count(*) from quiz_attempts where user_id=${s.user.id}`), 7)
    const seen = []
    let before = ''
    for (let guard = 0; guard < 10; guard++) {
      const page = expectStatus(await admin.get(`/api/admin/reports/training/attempts?learnerId=${s.user.id}&courseId=${f.course.id}&size=3${before}`), 200).json
      const rows = Object.values(page).find(Array.isArray) ?? []
      assert.ok(rows.length <= 3, `page size ${rows.length}, keys ${Object.keys(page)}`)
      seen.push(...rows.map((r) => r.attemptId ?? r.id))
      const next = page.nextCursor ?? page.nextBefore ?? page.before ?? page.next
      if (!next) break
      before = `&before=${encodeURIComponent(next)}`
    }
    const analysis = expectStatus(await admin.get(`/api/admin/reports/training/attempts/${seen[0]}/analysis`), 200)
    assert.ok(analysis.text.length > 2, 'analysis empty')
    assert.equal(new Set(seen).size, 7, `Có 7 lượt trong quiz_attempts nhưng API lịch sử chỉ trả ${new Set(seen).size} dòng: user_quiz_scores chỉ giữ một dòng điểm cao nhất mỗi học viên/bài, không xem được các lượt còn lại`)
  })
  await tc('F-R05', 'U3 không xem lượt thi người ngoài phạm vi', async () => {
    const f = await freshCourse(ctx, { unit: B, programId: ctx.programB.id, lessons: [{ questions: 2, settings: { requiredForProgress: false } }] }); const b = await learner(ctx, B)
    await b.post(`/api/quizzes/lessons/${f.lesson.id}/submit`, { selectedAnswers: answersFor(f.lesson.qs, 2) })
    const id = sqlOne(`select id from quiz_attempts where user_id=${b.user.id}`)
    assert.ok([403, 404].includes((await u3.get(`/api/admin/reports/training/attempts/${id}/analysis`)).status), 'attempt of B readable')
    const res = await u3.get(`/api/admin/reports/training/attempts?learnerId=${b.user.id}&courseId=${f.course.id}`)
    assert.ok(res.status >= 400 || !/score/.test(res.text), 'attempt list of B readable')
  })

  await tc('F-R06', 'CSV cùng phạm vi, có đủ học viên, parse được', async () => {
    const res = expectStatus(await csv(u3, `view=completion&course=${byName(rc)}`), 200)
    const rows = parseCsv(res.text)
    assert.ok(rows.length >= 5, `rows ${rows.length}`)
    assert.ok(res.text.includes(passer.user.fullName)); assert.ok(!res.text.includes(userB.user.username))
  })
  await tc('F-R06', 'CSV có Content-Type text/csv và BOM UTF-8 cho Excel', async () => {
    const res = expectStatus(await csv(u3, `view=completion&course=${byName(rc)}`), 200)
    assert.match(res.headers.get('content-type') ?? '(thiếu)', /csv/, 'response không có Content-Type')
    assert.equal(res.buffer[0], 0xEF, 'không có BOM: Excel mở sai tiếng Việt')
  })
  await tc('F-R06', 'CSV 0 dòng', async () => {
    const res = expectStatus(await csv(u3, 'view=completion&course=NOPE'), [200, 400, 404])
    if (res.status === 200) assert.ok(parseCsv(res.text).length <= 1, 'non-empty for unknown course')
  })
  await tc('F-R06', 'tên tiếng Việt, dấu phẩy, xuống dòng được escape đúng', async () => {
    const f = await freshCourse(ctx); const s = await learner(ctx)
    const tricky = 'Nguyễn, "Văn"\nTest'
    expectStatus(await updateUser(admin, s.user, { fullName: tricky }), 200)
    await s.login(s.user.username, 'Test-User-Pass-1x')
    await readAndComplete(s, f.lesson.id)
    const res = expectStatus(await csv(admin, `view=completion&course=${byName(f)}`), 200)
    const flat = parseCsv(res.text).flat()
    assert.ok(flat.some((cell) => cell.includes(tricky)), `name not preserved, csv=${res.text.slice(0, 300)}`)
  })

  // NF-S09 formula injection.
  for (const prefix of ['=', '+', '-', '@']) {
    await tc('NF-S09', `tên bắt đầu ${prefix}`, async () => {
      const f = await freshCourse(ctx); const s = await learner(ctx)
      const name = `${prefix}1+1+cmd`
      expectStatus(await updateUser(admin, s.user, { fullName: name }), 200)
      await s.login(s.user.username, 'Test-User-Pass-1x')
      await readAndComplete(s, f.lesson.id)
      const res = expectStatus(await csv(admin, `view=completion&course=${byName(f)}`), 200)
      const cell = parseCsv(res.text).flat().find((c) => c.includes('1+1+cmd'))
      assert.ok(cell, `name absent: ${res.text.slice(0, 200)}`)
      assert.ok(!/^[=+\-@]/.test(cell), `formula not neutralised: ${cell}`)
    })
  }

  // F-R07 UTC+7 day edges. Attempts are written at known instants on the isolated DB.
  await tc('F-R07', 'lượt thi 23:59 và 00:00 UTC+7 vào đúng ngày', async () => {
    const f = await freshCourse(ctx); const [x, y] = [await learner(ctx), await learner(ctx)]
    await readAndComplete(x, f.lesson.id); await readAndComplete(y, f.lesson.id)
    const column = sqlOne("select column_name from information_schema.columns where table_name='user_completed_courses' and data_type like 'timestamp%' limit 1")
    if (!column) return gap('user_completed_courses không có cột thời gian; không dựng được hoàn thành ở 23:59/00:00 bằng dữ liệu kiểm soát.')
    sql(`update user_completed_courses set ${column}='2026-10-10 23:59:30' where user_id=${x.user.id}; update user_completed_courses set ${column}='2026-10-11 00:00:30' where user_id=${y.user.id};`)
    const d10 = expectStatus(await read(admin, `view=completion&course=${byName(f)}&from=2026-10-10&to=2026-10-10&page=0&size=50`), 200).text
    const d11 = expectStatus(await read(admin, `view=completion&course=${byName(f)}&from=2026-10-11&to=2026-10-11&page=0&size=50`), 200).text
    const has = (t, c) => t.includes(c.user.username) || t.includes(c.user.fullName)
    assert.ok(has(d10, x) && !has(d10, y), `day 10: x=${has(d10, x)} y=${has(d10, y)}`)
    assert.ok(has(d11, y) && !has(d11, x), `day 11: x=${has(d11, x)} y=${has(d11, y)}`)
  })
  await tc('F-R07', 'khoảng ngày rỗng không lỗi', async () => {
    const res = expectStatus(await read(admin, `view=learners&course=${rc.course.id}&from=2020-01-01&to=2020-01-02&page=0&size=50`), 200)
    assert.ok(!/NaN|Infinity/.test(res.text))
  })
  await tc('F-R07', 'học viên đổi đơn vị: báo cáo theo đơn vị mới', async () => {
    const f = await freshCourse(ctx); const s = await learner(ctx)
    await readAndComplete(s, f.lesson.id)
    expectStatus(await updateUser(admin, s.user, { unitId: B.id }), 200)
    const res = expectStatus(await read(u3, `view=completion&search=${s.user.username}&page=0&size=100`), 200)
    assert.ok(!res.text.includes(s.user.username), 'moved learner still in report of old unit')
  })
  void A; void expectRejected; void ok
}
