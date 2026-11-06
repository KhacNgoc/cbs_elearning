// 4.2 Permissions, units, users (F-P01..P09) and NF-S03 scope matrix.
import { Client, tc, assert, expectStatus, expectRejected, sqlNum, sqlOne, uniq, gap, multipart } from '../lib.mjs'
import { createUser, activate, addRole, createCourse, createChapter, createLesson, freshCourse, learner, updateUser, roleByCode, addMembers, ok } from '../seed.mjs'

const denied = (res, ctx = '') => assert.ok([401, 403, 404].includes(res.status), `${ctx} expected denial, got ${res.status}: ${res.text.slice(0, 160)}`)

export async function run(ctx) {
  const { admin, u1, u3, u8, A, B, B1, A1, C4, program } = ctx

  // F-P01: read-only role.
  await tc('F-P01', 'U3 đọc được báo cáo trong phạm vi', async () => {
    expectStatus(await u3.get('/api/admin/reports/training/read?view=overview'), 200)
  })
  for (const [name, call] of [
    ['POST users', (c) => c.post('/api/admin/users', { username: uniq('hack'), password: 'Some-Pass-123', fullName: 'x' })],
    ['PUT course', (c) => c.put(`/api/admin/courses/${ctx.C1.id}`, { ...ctx.C1, name: 'hack' })],
    ['DELETE course', (c) => c.del(`/api/admin/courses/${ctx.C1.id}`)],
    ['POST domain', (c) => c.post('/api/admin/training-domains', { code: uniq('H'), name: 'hack' })],
    ['POST role', (c) => c.post('/api/admin/rbac/roles', { code: uniq('H'), name: 'hack', active: true })],
    ['POST unit', (c) => c.post('/api/admin/units', { name: 'hack', type: 'UNIT', active: true })],
    ['GET users', (c) => c.get('/api/admin/users')],
  ]) await tc('F-P01', `read-only gọi trực tiếp ${name} bị chặn`, async () => denied(await call(u3), name))

  // F-P02: unit admin scope.
  await tc('F-P02', 'U1 không thấy người của B trong danh sách', async () => {
    const userB = await createUser(admin, { unitId: B.id })
    const page = expectStatus(await u1.get(`/api/admin/users/page?query=${userB.username}&page=1&size=10`), 200).json
    assert.equal(JSON.stringify(page).includes(userB.username), false, 'user of B visible to U1')
    assert.ok(!expectStatus(await u1.get('/api/admin/users'), 200).text.includes(userB.username), 'user of B in full list')
    ctx.userB = userB
  })
  await tc('F-P02', 'U1 sửa/xoá người của B qua ID bị chặn', async () => {
    denied(await u1.put(`/api/admin/users/${ctx.userB.id}`, { ...ctx.userB, fullName: 'hacked' }), 'PUT')
    denied(await u1.del(`/api/admin/users/${ctx.userB.id}`), 'DELETE')
    assert.notEqual(sqlOne(`select full_name from users where id=${ctx.userB.id}`), 'hacked')
  })
  await tc('F-P02', 'U1 không tạo user vào đơn vị B', async () => {
    const name = uniq('x')
    denied(await u1.post('/api/admin/users', { username: name, password: 'Some-Pass-123', fullName: 'x', unitId: B.id }), 'create')
    assert.equal(sqlNum(`select count(*) from users where username='${name}'`), 0)
  })
  await tc('F-P02', 'U1 không xem/sửa khoá chỉ thuộc B', async () => {
    denied(await u1.put(`/api/admin/courses/${C4.course.id}`, { ...C4.course, name: 'hacked' }), 'PUT course')
    denied(await u1.put(`/api/admin/lessons/${C4.lesson.id}`, { title: 'hacked', textBody: 'x' }), 'PUT lesson')
    denied(await u1.get(`/api/admin/courses/${C4.course.id}/chapters`), 'GET chapters')
  })
  await tc('F-P02', 'số liệu tổng/báo cáo không lộ người ngoài phạm vi', async () => {
    const res = expectStatus(await u3.get('/api/admin/reports/training/read?view=overview'), 200)
    assert.ok(!res.text.includes(ctx.userB.username), 'user of B in U3 report')
    denied(await u3.get(`/api/admin/reports/training/read?unitId=${B.id}`).then((r) => r.status === 200 && r.text.includes(ctx.userB.username) ? { status: 200, text: 'leak' } : { status: 403, text: '' }), 'filter B')
  })

  // F-P03 scope/permission must not combine across roles.
  await tc('F-P03', 'U8: update ở A hoạt động', async () => {
    const own = await freshCourse(ctx)
    expectStatus(await u8.put(`/api/admin/lessons/${own.lesson.id}`, { title: 'Sửa bởi U8', textBody: '<p>ok</p>' }), 200)
  })
  await tc('F-P03', 'U8: dùng update của A để sửa khoá B bị chặn', async () => {
    denied(await u8.put(`/api/admin/lessons/${C4.lesson.id}`, { title: 'hacked', textBody: '<p>x</p>' }), 'PUT lesson B')
    denied(await u8.put(`/api/admin/courses/${C4.course.id}`, { ...C4.course, name: 'hacked' }), 'PUT course B')
    assert.notEqual(sqlOne(`select title from lessons where id='${C4.lesson.id}'`), 'hacked')
  })
  await tc('F-P03', 'U8: vẫn đọc được khoá B theo role read', async () => {
    expectStatus(await u8.get(`/api/admin/courses/${C4.course.id}/chapters`), 200)
  })

  // F-P04 assigned instructor.
  await tc('F-P04', 'giảng viên chỉ sửa khoá được giao', async () => {
    const instructor = await createUser(admin, { unitId: A.id, learner: false })
    const code = uniq('R_INS')
    await addRole(admin, { code, name: code, unitIds: [A.id], permissions: ['courses:read', 'courses:update'] })
    // The API cannot set this flag and permission updates clear it, so set it on the test DB, then add the member so the cache reloads.
    sqlOne(`update access_roles set assigned_courses_only=true where lower(code)=lower('${code}') returning id`)
    await addMembers(admin, code, [instructor.id])
    const c = await activate(instructor.username)
    const assigned = await freshCourse(ctx)
    const other = await freshCourse(ctx)
    const access = ok(await admin.get(`/api/admin/courses/${assigned.course.id}/access`), 'access')
    ok(await admin.put(`/api/admin/courses/${assigned.course.id}/access`, { ...access, instructorIds: [instructor.id] }), 'assign instructor')
    await c.login(instructor.username, 'Test-User-Pass-1x')
    expectStatus(await c.put(`/api/admin/lessons/${assigned.lesson.id}`, { title: 'Giảng viên sửa', textBody: '<p>x</p>' }), 200)
    denied(await c.put(`/api/admin/lessons/${other.lesson.id}`, { title: 'hacked', textBody: '<p>x</p>' }), 'unassigned')
  })
  await tc('F-P04', 'role inactive không cấp quyền', async () => {
    const user = await createUser(admin, { unitId: A.id, learner: false })
    const code = uniq('R_OFF')
    await addRole(admin, { code, name: code, unitIds: [A.id], userIds: [user.id], permissions: ['users:read', 'courses:read'], active: false })
    const c = await activate(user.username)
    denied(await c.get('/api/admin/users'), 'inactive role')
  })

  // F-P05 units.
  await tc('F-P05', 'tạo đơn vị cha/con, đọc lại đúng', async () => {
    const parent = ok(await admin.post('/api/admin/units', { name: uniq('P'), type: 'UNIT', active: true }), 'parent')
    const child = ok(await admin.post('/api/admin/units', { name: uniq('Ch'), type: 'DEPARTMENT', parentId: parent.id, active: true }), 'child')
    assert.equal(child.parentId, parent.id)
    ctx.unitParent = parent; ctx.unitChild = child
  })
  await tc('F-P05', 'mã trùng không lưu', async () => {
    const id = uniq('DUP')
    ok(await admin.post('/api/admin/units', { id, name: id, type: 'UNIT', active: true }), 'first')
    expectRejected(await admin.post('/api/admin/units', { id, name: 'trùng', type: 'UNIT', active: true }))
  })
  await tc('F-P05', 'tự làm cha bị chặn', async () => {
    expectRejected(await admin.put(`/api/admin/units/${ctx.unitParent.id}`, { ...ctx.unitParent, parentId: ctx.unitParent.id }))
  })
  await tc('F-P05', 'chuyển cha vào hậu duệ bị chặn', async () => {
    expectRejected(await admin.put(`/api/admin/units/${ctx.unitParent.id}`, { ...ctx.unitParent, parentId: ctx.unitChild.id }))
    assert.equal(sqlOne(`select parent_id from organization_units where id='${ctx.unitParent.id}'`), '')
  })
  await tc('F-P05', 'deactivate cha chặn phiên người thuộc đơn vị con', async () => {
    const c = await learner(ctx, ctx.unitChild)
    expectStatus(await c.get('/api/users/me/learning'), 200)
    ok(await admin.put(`/api/admin/units/${ctx.unitParent.id}`, { ...ctx.unitParent, active: false }), 'deactivate')
    const res = await c.get('/api/users/me/learning')
    assert.ok(res.status === 401 || res.status === 403, `active session under inactive parent got ${res.status}`)
  })

  // F-P06 users.
  await tc('F-P06', 'tạo/sửa/khoá/xoá user', async () => {
    const user = await createUser(admin, { unitId: A.id })
    expectStatus(await updateUser(admin, user, { fullName: 'Tên sửa' }), 200)
    assert.equal(sqlOne(`select full_name from users where id=${user.id}`), 'Tên sửa')
    expectStatus(await updateUser(admin, user, { status: 'LOCKED' }), 200)
    expectStatus(await admin.del(`/api/admin/users/${user.id}`), [200, 204])
    const left = sqlOne(`select status from users where id=${user.id}`)
    assert.ok(left === '' || ['DISABLED', 'DELETED'].includes(left), `user still active: ${left}`)
  })
  await tc('F-P06', 'username trùng không tạo bản ghi thứ 2, không 500', async () => {
    const user = await createUser(admin, { unitId: A.id })
    const res = await admin.post('/api/admin/users', { username: user.username, password: 'Some-Pass-123', fullName: 'x', unitId: A.id, email: `${uniq('e')}@test.local` })
    expectRejected(res)
    assert.equal(sqlNum(`select count(*) from users where username='${user.username}'`), 1)
  })
  await tc('F-P06', 'email trùng theo constraint', async () => {
    const user = await createUser(admin, { unitId: A.id })
    const name = uniq('dupmail')
    const res = await admin.post('/api/admin/users', { username: name, password: 'Some-Pass-123', fullName: 'x', unitId: A.id, email: user.email })
    if (res.status === 200 && sqlNum("select count(*) from pg_indexes where tablename='users' and indexdef ilike '%unique%email%'") === 0)
      return gap('Không có unique constraint trên users.email: hai tài khoản được dùng chung email. Kịch bản ghi "theo constraint" nên đây là quyết định nghiệp vụ, cần chốt vì OTP gửi qua email.')
    expectRejected(res)
  })
  await tc('F-P06', 'đơn vị không tồn tại/inactive bị từ chối', async () => {
    const n1 = uniq('nounit'); const n2 = uniq('offunit')
    expectRejected(await admin.post('/api/admin/users', { username: n1, password: 'Some-Pass-123', fullName: 'x', unitId: 'NOPE', email: `${n1}@test.local` }))
    expectRejected(await admin.post('/api/admin/users', { username: n2, password: 'Some-Pass-123', fullName: 'x', unitId: ctx.UI.id, email: `${n2}@test.local` }))
    assert.equal(sqlNum(`select count(*) from users where username in ('${n1}','${n2}')`), 0)
  })
  await tc('F-P06', 'mật khẩu quá ngắn khi tạo bị từ chối', async () => {
    expectRejected(await admin.post('/api/admin/users', { username: uniq('s'), password: '1234567', fullName: 'x', unitId: A.id }))
  })

  // F-P07 import.
  await tc('F-P07', 'tải template Excel', async () => {
    const res = expectStatus(await admin.get('/api/admin/users/template'), 200)
    assert.equal(res.buffer.subarray(0, 2).toString(), 'PK', 'template is not an xlsx')
  })
  await tc('F-P07', 'file không phải Excel báo lỗi, không tạo user, không 500', async () => {
    const before = sqlNum('select count(*) from users')
    const res = await admin.post('/api/admin/users/upload', undefined, { form: multipart({}, [{ name: 'x.xlsx', data: 'not excel' }]) })
    expectRejected(res)
    assert.equal(sqlNum('select count(*) from users'), before)
  })

  // F-P08 memberships.
  await tc('F-P08', 'thêm/xoá thành viên bằng replace và delta', async () => {
    const [x, y] = [await createUser(admin, { unitId: A.id, learner: false }), await createUser(admin, { unitId: A.id, learner: false })]
    const code = uniq('R_M')
    let role = await addRole(admin, { code, name: code, unitIds: [A.id], permissions: ['courses:read'], userIds: [x.id] })
    expectStatus(await admin.put(`/api/admin/rbac/roles/${role.id}/member-changes`, { addUserIds: [y.id], removeUserIds: [x.id], version: role.version }), [200, 204])
    role = await roleByCode(admin, code)
    assert.deepEqual(role.userIds, [y.id])
    const replaced = ok(await admin.put(`/api/admin/rbac/roles/${role.id}/members`, { userIds: [x.id], version: role.version }), 'replace')
    assert.deepEqual(replaced.userIds, [x.id])
  })
  await tc('F-P08', 'thêm và xoá cùng user trong một delta bị từ chối', async () => {
    const x = await createUser(admin, { unitId: A.id, learner: false })
    const code = uniq('R_M')
    const role = await addRole(admin, { code, name: code, unitIds: [A.id], permissions: ['courses:read'] })
    expectRejected(await admin.put(`/api/admin/rbac/roles/${role.id}/member-changes`, { addUserIds: [x.id], removeUserIds: [x.id], version: role.version }))
  })
  await tc('F-P08', 'U1 không tự gán mình vào role admin hệ thống', async () => {
    const sys = await roleByCode(admin, 'ROLE_ADMIN')
    denied(await u1.put(`/api/admin/rbac/roles/${sys.id}/member-changes`, { addUserIds: [ctx.u1User.id], removeUserIds: [], version: sys.version }), 'self-elevate')
    assert.equal(sqlNum(`select count(*) from user_access_roles uar join access_roles r on r.id=uar.role_id where r.code='ROLE_ADMIN' and uar.user_id=${ctx.u1User.id}`), 0)
  })
  await tc('F-P08', 'role bảo vệ không sửa/xoá được', async () => {
    const sys = await roleByCode(admin, 'ROLE_ADMIN')
    expectRejected(await admin.del(`/api/admin/rbac/roles/${sys.id}`))
    expectRejected(await admin.put(`/api/admin/rbac/permissions/${sys.id}`, { permissions: [], unitIds: [], version: sys.version }))
    assert.ok(sqlNum(`select count(*) from access_role_permissions p join access_roles r on r.id=p.role_id where r.code='ROLE_ADMIN'`) > 0)
  })

  // F-P09 live permission change.
  await tc('F-P09', 'thu quyền: backend chặn ngay ở request kế tiếp', async () => {
    const user = await createUser(admin, { unitId: A.id, learner: false })
    const code = uniq('R_LIVE')
    const role = await addRole(admin, { code, name: code, unitIds: [A.id], userIds: [user.id], permissions: ['users:read'] })
    const c = await activate(user.username)
    expectStatus(await c.get('/api/admin/users'), 200)
    const current = await roleByCode(admin, code)
    ok(await admin.put(`/api/admin/rbac/permissions/${role.id}`, { permissions: ['overview:read'], unitIds: [A.id], version: current.version }), 'revoke')
    const res = await c.get('/api/admin/users')
    assert.ok([401, 403].includes(res.status), `still allowed: ${res.status}`)
  })
  await tc('F-P09', 'menu/route phản ánh quyền sau revalidate (navigation API)', async () => {
    const nav = expectStatus(await u3.get('/api/navigation/me'), 200)
    assert.ok(Array.isArray(nav.json))
    assert.ok(!nav.text.includes('/admin/users'), 'report viewer sees user admin menu')
  })

  // NF-S03: IDOR matrix on resources owned by another learner / another unit.
  const learnerA = await learner(ctx)
  const learnerB = await learner(ctx, B)
  const aCourse = await freshCourse(ctx, { lessons: [{ questions: 2, settings: { requiredForProgress: false } }] })
  const adminPaths = ['/api/admin/users', '/api/admin/rbac/roles', '/api/admin/units', '/api/admin/enrollment-requests', '/api/admin/reports/training', '/api/admin/content-reports', `/api/admin/courses/${aCourse.course.id}/documents`, '/api/admin/question-bank', '/api/admin/training/catalog', '/api/admin/quizzes/courses']
  for (const path of adminPaths) await tc('NF-S03', `học viên gọi ${path}`, async () => denied(await learnerA.get(path), path))
  const crossLearner = [
    ['lesson', `/api/lessons/${aCourse.lesson.id}`], ['outline', `/api/courses/${aCourse.course.id}/outline`], ['course', `/api/courses/${aCourse.course.id}`],
    ['quiz questions', `/api/quizzes/lessons/${aCourse.lesson.id}/questions`], ['quiz review', `/api/quizzes/lessons/${aCourse.lesson.id}/review`],
    ['comments', `/api/lessons/${aCourse.lesson.id}/comments`], ['notes', `/api/lessons/${aCourse.lesson.id}/notes`], ['flashcards', `/api/lessons/${aCourse.lesson.id}/flashcards`],
    ['rating', `/api/courses/${aCourse.course.id}/rating`], ['resume', `/api/users/me/learning/dashboard/courses/${aCourse.course.id}/resume`],
  ]
  for (const [name, path] of crossLearner) await tc('NF-S03', `học viên B đọc ${name} của khoá A`, async () => denied(await learnerB.get(path), path))
  for (const [name, method, path, body] of [
    ['progress', 'POST', `/api/users/me/lessons/${aCourse.lesson.id}/progress`, { contentType: 'TEXT', textCompleted: true }],
    ['complete-lesson', 'POST', `/api/users/me/complete-lesson/${aCourse.lesson.id}`],
    ['start quiz', 'POST', `/api/quizzes/lessons/${aCourse.lesson.id}/start`],
    ['submit quiz', 'POST', `/api/quizzes/lessons/${aCourse.lesson.id}/submit`, { selectedAnswers: {} }],
    ['comment', 'POST', `/api/lessons/${aCourse.lesson.id}/comments`, { text: 'x', content: 'x' }],
    ['note', 'POST', `/api/lessons/${aCourse.lesson.id}/notes`, { content: 'x', text: 'x', timestampSeconds: 1 }],
    ['personal card', 'POST', `/api/lessons/${aCourse.lesson.id}/flashcards/mine`, { type: 'TERM', front: 'f', back: 'b' }],
    ['rating', 'PUT', `/api/courses/${aCourse.course.id}/rating`, { stars: 5, rating: 5 }],
  ]) await tc('NF-S03', `học viên B ghi ${name} vào khoá A`, async () => denied(await learnerB.req(method, path, body), name))
  await tc('NF-S03', 'học viên B đọc changelog đã có của khoá A', async () => {
    expectStatus(await admin.post(`/api/admin/lessons/${aCourse.lesson.id}/changelog-notify`, { changeSummary: 'BÍ MẬT changelog', notifyLearners: false }), 200)
    const res = await learnerB.get(`/api/lessons/${aCourse.lesson.id}/latest-changelog`)
    assert.ok(!res.text.includes('BÍ MẬT'), `changelog of another unit leaked: HTTP ${res.status}`)
    denied(res, 'changelog')
  })
  await tc('NF-S03', 'catalog học viên B không chứa khoá chỉ của A', async () => {
    const list = expectStatus(await learnerB.get('/api/courses'), 200)
    assert.ok(!list.text.includes(aCourse.course.id), 'course of A visible to B')
  })
  await tc('NF-S03', 'catalog học viên A không chứa C4 của B', async () => {
    const list = expectStatus(await learnerA.get('/api/courses'), 200)
    assert.ok(!list.text.includes(C4.course.id), 'course of B visible to A')
  })
  await tc('NF-S03', 'học viên A1 thừa kế khoá của A nhưng không của B', async () => {
    const child = await learner(ctx, A1)
    const list = expectStatus(await child.get('/api/courses'), 200)
    assert.ok(list.text.includes(aCourse.course.id)); assert.ok(!list.text.includes(C4.course.id))
  })
  void program; void B1; void Client; void createCourse; void createChapter; void createLesson; void gap
}
