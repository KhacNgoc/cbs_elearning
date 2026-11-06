// 4.4 Allocation, enrolment, deadlines (F-E01..E07).
import { tc, assert, expectStatus, expectRejected, sqlNum, sqlOne, sql, uniq } from '../lib.mjs'
import { freshCourse, learner, updateUser, setStatus, ok, allocate, createCourse, createChapter, createLesson, publish, createUser, addRole, activate } from '../seed.mjs'

const requestsFor = (admin, userId, courseId) => sqlNum(`select count(*) from course_enrollment_requests where user_id=${userId} and course_id='${courseId}' and status='PENDING'`)
const pendingId = (userId, courseId) => sqlOne(`select id from course_enrollment_requests where user_id=${userId} and course_id='${courseId}' order by id desc limit 1`)

export async function run(ctx) {
  const { admin, u1, A, B, program, programB } = ctx
  const optional = () => freshCourse(ctx, { grant: 'OPTIONAL' })

  await tc('F-E01', 'phân bổ COURSE: thấy ở A, không thấy ở B', async () => {
    const f = await optional()
    const a = await learner(ctx); const b = await learner(ctx, B)
    assert.ok(expectStatus(await a.get('/api/courses'), 200).text.includes(f.course.id))
    assert.ok(!expectStatus(await b.get('/api/courses'), 200).text.includes(f.course.id))
    expectRejected(await b.post(`/api/users/me/register-course/${f.course.id}`), 'B register')
  })
  await tc('F-E01', 'MANDATORY học được ngay; OPTIONAL cần duyệt', async () => {
    const m = await freshCourse(ctx); const o = await optional()
    const a = await learner(ctx)
    expectStatus(await a.get(`/api/lessons/${m.lesson.id}`), 200)
    assert.ok([403, 404].includes((await a.get(`/api/lessons/${o.lesson.id}`)).status))
  })
  await tc('F-E01', 'chương trình không chọn đơn vị thì không cấp cho đơn vị nào', async () => {
    const domain = ok(await admin.post('/api/admin/training-domains', { code: `EI${ctx.run}${Date.now()}`, name: 'EI', status: 'ACTIVE' }), 'domain')
    const prog = ok(await admin.post('/api/admin/training-programs', { domainId: domain.id, code: `EIP${Date.now()}`, name: 'EIP', status: 'ACTIVE' }), 'program')
    assert.deepEqual(prog.unitIds ?? [], [], `chương trình tạo không chọn đơn vị nhưng được tự gán ${prog.unitIds}: TrainingStructureServiceImpl lấy đơn vị đầu tiên trong DB khi actor không có đơn vị, khoá mới kế thừa nên người học đơn vị đó thấy và đăng ký được`)
  })
  await tc('F-E01', 'phân bổ một khoá không làm lộ khoá khác cùng chương trình/lĩnh vực', async () => {
    const domain = ok(await admin.post('/api/admin/training-domains', { code: `ES${ctx.run}${Date.now()}`, name: 'ES', status: 'ACTIVE' }), 'domain')
    const mk = async (tag) => ok(await admin.post('/api/admin/training-programs', { domainId: domain.id, code: `${tag}${Date.now()}`, name: tag, status: 'ACTIVE', unitIds: [ctx.B.id] }), 'program')
    const [p1, p2] = [await mk('ES1'), await mk('ES2')]
    const x = await freshCourse(ctx, { unit: null, programId: p1.id }); const y = await freshCourse(ctx, { unit: null, programId: p1.id }); const z = await freshCourse(ctx, { unit: null, programId: p2.id })
    const a = await learner(ctx)
    ok(await admin.post('/api/admin/unit-allocations/batch-assign', { unitId: A.id, items: [{ categoryType: 'COURSE', targetId: x.course.id, grantType: 'OPTIONAL', id: x.course.id }] }), 'batch-assign')
    const list = expectStatus(await a.get('/api/courses'), 200).text
    assert.ok(list.includes(x.course.id), 'allocated course missing')
    const reg = await a.post(`/api/users/me/register-course/${y.course.id}`)
    assert.ok(!list.includes(y.course.id) && !list.includes(z.course.id) && reg.status >= 400, `Chỉ phân bổ khoá X cho A nhưng A thấy khoá cùng chương trình=${list.includes(y.course.id)}, khác chương trình cùng lĩnh vực=${list.includes(z.course.id)}, đăng ký khoá không được cấp trả HTTP ${reg.status}. batch-assign thêm đơn vị vào program.units và domain.units, AccessPolicy.visible coi đó là được thấy mọi khoá`)
  })
  await tc('F-E01', 'phân bổ cấp chương trình (allInProgram)', async () => {
    const domain = ok(await admin.post('/api/admin/training-domains', { code: `E${ctx.run}${Date.now()}`, name: 'E', status: 'ACTIVE' }), 'domain')
    const prog = ok(await admin.post('/api/admin/training-programs', { domainId: domain.id, code: `EP${Date.now()}`, name: 'EP', status: 'ACTIVE', unitIds: [ctx.B.id] }), 'program')
    const before = await learner(ctx)
    const hidden = await createCourse(admin, prog.id); const hch = await createChapter(admin, hidden.id); await createLesson(admin, hidden, hch, { textBody: '<p>x</p>' }); await publish(admin, hidden.id)
    assert.ok(!expectStatus(await before.get('/api/courses'), 200).text.includes(hidden.id), 'visible before allocation')
    const c = await createCourse(admin, prog.id); const ch = await createChapter(admin, c.id); await createLesson(admin, c, ch, { textBody: '<p>x</p>' }); await publish(admin, c.id)
    ok(await admin.post('/api/admin/unit-allocations/assign', { unitId: A.id, categoryType: 'PROGRAM', grantType: 'OPTIONAL', domainId: domain.id, programId: prog.id, allInProgram: true, courseIds: [] }), 'assign program')
    const a = await learner(ctx)
    assert.ok(expectStatus(await a.get('/api/courses'), 200).text.includes(c.id), 'course of allocated program not visible')
  })

  // Allocation is stored per course: choosing a program or domain only expands to its current courses.
  const managedProgram = async (tag) => ok(await admin.post('/api/admin/training-programs', { domainId: ctx.domain.id, code: `${tag}${Date.now()}`, name: tag, status: 'ACTIVE', unitIds: [ctx.B.id] }), 'program')
  const visibleTo = async (c, course) => expectStatus(await c.get('/api/courses'), 200).text.includes(course.course.id)
  await tc('F-E05', 'tick chương trình, bỏ một khoá: khoá bị bỏ không được cấp', async () => {
    const p = await managedProgram('EX')
    const keep = await freshCourse(ctx, { unit: null, programId: p.id }); const skip = await freshCourse(ctx, { unit: null, programId: p.id })
    const a = await learner(ctx)
    ok(await admin.post('/api/admin/unit-allocations/batch-assign', { unitId: A.id, items: [{ categoryType: 'PROGRAM', targetId: p.id, grantType: 'OPTIONAL', excludeIds: [skip.course.id] }] }), 'batch-assign')
    assert.ok(await visibleTo(a, keep), 'selected course missing')
    assert.ok(!(await visibleTo(a, skip)), 'excluded course visible')
    assert.ok((await a.post(`/api/users/me/register-course/${skip.course.id}`)).status >= 400, 'excluded course registrable')
  })
  await tc('F-E05', 'cấp cả chương trình rồi gỡ một khoá: chỉ khoá đó biến mất', async () => {
    const p = await managedProgram('RM')
    const stay = await freshCourse(ctx, { unit: null, programId: p.id }); const gone = await freshCourse(ctx, { unit: null, programId: p.id })
    const a = await learner(ctx)
    ok(await admin.post('/api/admin/unit-allocations/batch-assign', { unitId: A.id, items: [{ categoryType: 'PROGRAM', targetId: p.id, grantType: 'OPTIONAL' }] }), 'batch-assign')
    ok(await admin.post('/api/admin/unit-allocations/batch-sync', { unitId: A.id, changes: [{ action: 'REMOVE', categoryType: 'COURSE', targetId: gone.course.id }] }), 'batch-sync')
    assert.ok(await visibleTo(a, stay), 'other course lost')
    assert.ok(!(await visibleTo(a, gone)), 'removed course still visible')
  })
  await tc('F-E05', 'cấp cả chương trình: khoá tạo sau không tự được cấp', async () => {
    const p = await managedProgram('NW')
    const first = await freshCourse(ctx, { unit: null, programId: p.id })
    ok(await admin.post('/api/admin/unit-allocations/assign', { unitId: A.id, categoryType: 'PROGRAM', grantType: 'OPTIONAL', programId: p.id, courseIds: [] }), 'assign')
    const later = await freshCourse(ctx, { unit: null, programId: p.id })
    const a = await learner(ctx)
    assert.ok(await visibleTo(a, first), 'existing course missing')
    assert.ok(!(await visibleTo(a, later)), 'later course granted automatically')
  })
  await tc('F-E01', 'đơn vị của chương trình chỉ để quản lý, không cấp cho học viên', async () => {
    const p = ok(await admin.post('/api/admin/training-programs', { domainId: ctx.domain.id, code: `MG${Date.now()}`, name: 'MG', status: 'ACTIVE', unitIds: [A.id] }), 'program')
    const c = await freshCourse(ctx, { unit: null, programId: p.id })
    const a = await learner(ctx)
    assert.ok(!(await visibleTo(a, c)), 'program unit gave learners access')
  })
  await tc('F-E01', 'tài khoản chưa có đơn vị không thấy, không đăng ký được khoá', async () => {
    const c = await freshCourse(ctx, { unit: A })
    const u = await createUser(admin, { learner: true })
    const noUnit = await activate(u.username)
    assert.ok(!(await visibleTo(noUnit, c)), 'visible to user without unit')
    assert.ok((await noUnit.post(`/api/users/me/register-course/${c.course.id}`)).status >= 400, 'registrable without unit')
    const dashboard = expectStatus(await noUnit.get('/api/users/me/learning/dashboard/courses?tab=discover&page=1&size=50'), 200)
    assert.ok(!dashboard.text.includes(c.course.id), 'on discover tab')
  })
  await tc('F-P03', 'admin có quyền update sửa được khoá phân cho đơn vị, không sửa khoá khác', async () => {
    const p = await managedProgram('AD')
    const given = await freshCourse(ctx, { unit: null, programId: p.id }); const sibling = await freshCourse(ctx, { unit: null, programId: p.id })
    ok(await admin.post('/api/admin/unit-allocations/batch-assign', { unitId: A.id, items: [{ categoryType: 'COURSE', targetId: given.course.id, grantType: 'OPTIONAL' }] }), 'batch-assign')
    const an = await createUser(admin, { unitId: A.id, learner: false })
    const code = uniq('R_AN')
    await addRole(admin, { code, name: code, unitIds: [A.id], userIds: [an.id], permissions: ['courses:read', 'courses:update'] })
    const c = await activate(an.username)
    expectStatus(await c.put(`/api/admin/lessons/${given.lesson.id}`, { title: 'An sửa', textBody: '<p>x</p>' }), 200)
    assert.ok([401, 403, 404].includes((await c.put(`/api/admin/lessons/${sibling.lesson.id}`, { title: 'hacked', textBody: '<p>x</p>' })).status), 'sibling course editable')
  })

  await tc('F-E02', 'chờ duyệt chưa học; duyệt xong quyền nhất quán', async () => {
    const f = await freshCourse(ctx, { grant: 'OPTIONAL', lessons: [{ questions: 2, settings: { requiredForProgress: false } }] })
    const s = await learner(ctx)
    expectStatus(await s.post(`/api/users/me/register-course/${f.course.id}`), 200)
    assert.equal(requestsFor(admin, s.user.id, f.course.id), 1)
    for (const p of [`/api/lessons/${f.lesson.id}`, `/api/quizzes/lessons/${f.lesson.id}/questions`, `/api/lessons/${f.lesson.id}/flashcards`, `/api/videos/access/${f.lesson.id}`])
      assert.ok([400, 403, 404].includes((await s.get(p)).status), `pending learner reached ${p}`)
    expectRejected(await s.post(`/api/users/me/lessons/${f.lesson.id}/progress`, { contentType: 'TEXT', textCompleted: true }))
    const id = pendingId(s.user.id, f.course.id)
    expectStatus(await u1.put(`/api/admin/enrollment-requests/${id}`, { approve: true }), 200)
    expectStatus(await s.get(`/api/lessons/${f.lesson.id}`), 200)
    expectStatus(await s.get(`/api/quizzes/lessons/${f.lesson.id}/questions`), 200)
    assert.ok(expectStatus(await s.get('/api/notifications?size=10'), 200).text.includes(f.course.id) || sqlNum(`select count(*) from notifications where recipient_id=${s.user.id}`) > 0, 'no approval notification')
  })

  await tc('F-E03', 'gửi trùng tuần tự và đồng thời: chỉ 1 yêu cầu', async () => {
    const f = await optional(); const s = await learner(ctx)
    await s.post(`/api/users/me/register-course/${f.course.id}`)
    await s.post(`/api/users/me/register-course/${f.course.id}`)
    assert.equal(requestsFor(admin, s.user.id, f.course.id), 1)
    const f2 = await optional()
    await Promise.all(Array.from({ length: 8 }, () => s.post(`/api/users/me/register-course/${f2.course.id}`)))
    const n = requestsFor(admin, s.user.id, f2.course.id)
    assert.equal(n, 1, `${n} PENDING requests after 8 concurrent submits`)
  })

  await tc('F-E04', 'từ chối: lưu lý do, thông báo, không mở quyền', async () => {
    const f = await optional(); const s = await learner(ctx)
    await s.post(`/api/users/me/register-course/${f.course.id}`)
    const id = pendingId(s.user.id, f.course.id)
    expectStatus(await admin.put(`/api/admin/enrollment-requests/${id}`, { approve: false, rejectionReason: 'Chưa đủ điều kiện' }), 200)
    assert.ok([403, 404].includes((await s.get(`/api/lessons/${f.lesson.id}`)).status))
    assert.ok(expectStatus(await s.get('/api/notifications?size=10'), 200).text.includes('Chưa đủ điều kiện') || sqlNum(`select count(*) from notifications where recipient_id=${s.user.id}`) > 0, 'no rejection notification')
  })
  await tc('F-E04', 'đăng ký lại sau từ chối theo chính sách và duyệt được', async () => {
    const f = await optional(); const s = await learner(ctx)
    await s.post(`/api/users/me/register-course/${f.course.id}`)
    await admin.put(`/api/admin/enrollment-requests/${pendingId(s.user.id, f.course.id)}`, { approve: false, rejectionReason: 'x' })
    expectStatus(await s.post(`/api/users/me/register-course/${f.course.id}`), 200)
    assert.equal(requestsFor(admin, s.user.id, f.course.id), 1, 'no new pending request after rejection')
    expectStatus(await admin.put(`/api/admin/enrollment-requests/${pendingId(s.user.id, f.course.id)}`, { approve: true }), 200)
    expectStatus(await s.get(`/api/lessons/${f.lesson.id}`), 200)
  })
  await tc('F-E04', 'duyệt lại yêu cầu đã xử lý bị chặn', async () => {
    const f = await optional(); const s = await learner(ctx)
    await s.post(`/api/users/me/register-course/${f.course.id}`)
    const id = pendingId(s.user.id, f.course.id)
    await admin.put(`/api/admin/enrollment-requests/${id}`, { approve: false, rejectionReason: 'x' })
    expectRejected(await admin.put(`/api/admin/enrollment-requests/${id}`, { approve: true }), 're-review')
    assert.ok([403, 404].includes((await s.get(`/api/lessons/${f.lesson.id}`)).status), 'old request reopened access')
  })

  await tc('F-E05', 'batch-assign và batch-sync hợp lệ', async () => {
    const bare = ok(await admin.post('/api/admin/training-programs', { domainId: ctx.domain.id, code: `BARE${Date.now()}`, name: 'Chương trình của B', status: 'ACTIVE', unitIds: [ctx.B.id] }), 'program')
    const f = await freshCourse(ctx, { unit: null, programId: bare.id })
    const a = await learner(ctx)
    ok(await admin.post('/api/admin/unit-allocations/batch-assign', { unitId: A.id, items: [{ categoryType: 'COURSE', targetId: f.course.id, grantType: 'OPTIONAL', id: f.course.id }] }), 'batch-assign')
    assert.ok(expectStatus(await a.get('/api/courses'), 200).text.includes(f.course.id))
    ok(await admin.post('/api/admin/unit-allocations/batch-sync', { unitId: A.id, changes: [{ action: 'REMOVE', categoryType: 'COURSE', targetId: f.course.id, grantType: 'OPTIONAL' }] }), 'batch-sync')
    assert.ok(!expectStatus(await a.get('/api/courses'), 200).text.includes(f.course.id), 'Gỡ phân bổ khoá xong (grant đã xoá) học viên vẫn thấy khoá: removeUnitAllocation không dọn đơn vị khỏi program.units và domain.units')
  })
  await tc('F-E05', 'U1 phân bổ khoá ngoài quyền bị chặn, không ghi một phần', async () => {
    const mine = await freshCourse(ctx, { unit: null })
    const foreign = await freshCourse(ctx, { unit: B, programId: programB.id })
    const before = sqlNum('select count(*) from course_unit_grants')
    const res = await u1.post('/api/admin/unit-allocations/batch-assign', { unitId: A.id, items: [{ categoryType: 'COURSE', targetId: mine.course.id, grantType: 'OPTIONAL' }, { categoryType: 'COURSE', targetId: foreign.course.id, grantType: 'OPTIONAL' }] })
    assert.ok(res.status >= 400 && res.status < 500, `mixed payload returned ${res.status}`)
    assert.equal(sqlNum('select count(*) from course_unit_grants'), before, 'partial write')
  })

  await tc('F-E06', 'thu hồi phân bổ khi đang học: chặn API, giữ lịch sử', async () => {
    const f = await freshCourse(ctx, { lessons: [{}, {}] })
    const s = await learner(ctx)
    expectStatus(await s.post(`/api/users/me/lessons/${f.lesson.id}/progress`, { contentType: 'TEXT', textCompleted: true }), 200)
    expectStatus(await s.post(`/api/users/me/complete-lesson/${f.lesson.id}`), 200)
    expectStatus(await admin.del(`/api/admin/unit-allocations?unitId=${A.id}&categoryType=COURSE&targetId=${f.course.id}`), [200, 204])
    assert.ok([403, 404].includes((await s.get(`/api/lessons/${f.lessons[1].id}`)).status), 'still can read after revoke')
    assert.equal(sqlNum(`select count(*) from user_completed_lessons where user_id=${s.user.id} and lesson_id='${f.lesson.id}'`), 1, 'history lost')
  })
  await tc('F-E06', 'đổi đơn vị user: quyền đi theo đơn vị mới', async () => {
    const f = await freshCourse(ctx); const s = await learner(ctx)
    expectStatus(await s.get(`/api/lessons/${f.lesson.id}`), 200)
    expectStatus(await updateUser(admin, s.user, { unitId: B.id }), 200)
    await s.login(s.user.username, 'Test-User-Pass-1x')
    assert.ok([403, 404].includes((await s.get(`/api/lessons/${f.lesson.id}`)).status), 'kept access after moving unit')
  })
  await tc('F-E06', 'archive khi đang học chặn ghi tiến độ', async () => {
    const f = await freshCourse(ctx); const s = await learner(ctx)
    ok(await setStatus(admin, f.course.id, 'ARCHIVED'), 'archive')
    expectRejected(await s.post(`/api/users/me/lessons/${f.lesson.id}/progress`, { contentType: 'TEXT', textCompleted: true }))
  })

  const today = () => new Date(Date.now() + 7 * 3600e3).toISOString().slice(0, 10)
  const shift = (d) => new Date(Date.now() + 7 * 3600e3 + d * 86400e3).toISOString().slice(0, 10)
  await tc('F-E07', 'gán/sửa hạn học, đọc lại đúng ngày (không lệch UTC)', async () => {
    const f = await freshCourse(ctx); const s = await learner(ctx)
    for (const d of [shift(5), today(), shift(-1)]) {
      expectStatus(await admin.put(`/api/admin/users/${s.user.id}/course-deadlines`, { courseId: f.course.id, dueDate: d }), [200, 204])
      const list = expectStatus(await admin.get(`/api/admin/users/${s.user.id}/course-deadlines`), 200).json
      assert.equal(list.find((x) => x.courseId === f.course.id)?.dueDate, d)
    }
  })
  await tc('F-E07', 'quá hạn không tự khoá quyền học', async () => {
    const f = await freshCourse(ctx); const s = await learner(ctx)
    expectStatus(await admin.put(`/api/admin/users/${s.user.id}/course-deadlines`, { courseId: f.course.id, dueDate: shift(-3) }), [200, 204])
    expectStatus(await s.get(`/api/lessons/${f.lesson.id}`), 200)
    const dash = expectStatus(await s.get('/api/users/me/learning/dashboard/courses?tab=mine&page=1&size=50'), 200)
    assert.ok(dash.text.includes(f.course.id))
  })
  void sql; void allocate; void program
}
