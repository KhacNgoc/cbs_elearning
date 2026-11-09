// Builds the data of section 2 through the real admin API (no direct DB writes).
import { loginAs, uniq } from './lib.mjs'

export const ADMIN_PASSWORD = 'Test-Admin-Pass-1'
export const USER_PASSWORD = 'Test-User-Pass-1'
export const ACTIVE_PASSWORD = `${USER_PASSWORD}x`

export const ok = (res, ctx) => {
  if (res.status >= 300) throw new Error(`seed ${ctx}: ${res.status} ${res.text.slice(0, 400)}`)
  return res.json
}

// New accounts must change password first; this clears it through the real API.
export async function activate(username) {
  const client = await loginAs(username, USER_PASSWORD)
  ok(await client.post('/api/users/me/change-password', { newPassword: ACTIVE_PASSWORD }), `change-password ${username}`)
  await client.login(username, ACTIVE_PASSWORD)
  return client
}

export async function roleByCode(admin, code) {
  return ok(await admin.get('/api/admin/rbac/roles'), 'roles').find((r) => r.code.toLowerCase() === code.toLowerCase())
}

export async function addMembers(admin, code, userIds) {
  const role = await roleByCode(admin, code)
  return ok(await admin.put(`/api/admin/rbac/roles/${role.id}/member-changes`, { addUserIds: userIds, removeUserIds: [], version: role.version }), `members ${code}`)
}

export async function createUser(admin, { username, unitId, learner = true, email, status }) {
  const name = username ?? uniq('u')
  const user = ok(await admin.post('/api/admin/users', {
    username: name, password: USER_PASSWORD, fullName: `Test ${name}`, unitId,
    email: email ?? `${name}@test.local`, employeeId: name, status,
  }), `user ${name}`)
  if (learner) await addMembers(admin, 'ROLE_USER', [user.id])
  return user
}

// A ready-to-learn account: role assigned, first-login password change already done.
export async function learner(ctx, unit = ctx.A) {
  const user = await createUser(ctx.admin, { unitId: unit.id })
  const client = await activate(user.username)
  client.user = user
  return client
}

export async function addRole(admin, { code, name, permissions, unitIds = [], userIds = [], learnerRole = false, learningEnabled = false, active = true, assignedCoursesOnly }) {
  let role = ok(await admin.post('/api/admin/rbac/roles', { code, name, description: name, active, learningEnabled, learnerRole, assignedCoursesOnly }), `role ${code}`)
  if (permissions) role = ok(await admin.put(`/api/admin/rbac/permissions/${role.id}`, { permissions, unitIds, version: role.version }), `grant ${code}`)
  if (userIds.length) role = ok(await admin.put(`/api/admin/rbac/roles/${role.id}/members`, { userIds, version: role.version }), `members ${code}`)
  return role
}

export async function createCourse(admin, programId, tag = 'C') {
  const code = uniq(tag)
  return ok(await admin.post('/api/admin/courses', { id: code, code, name: `Khóa ${code}`, programId, description: 'Khóa test', iconName: 'BookOpen' }), `course ${tag}`)
}
export const createChapter = async (admin, courseId, title = 'Chương') =>
  ok(await admin.post(`/api/admin/courses/${courseId}/chapters`, { code: uniq('CH'), title }), `chapter ${title}`)
export const createLesson = async (admin, course, chapter, body) =>
  ok(await admin.post('/api/admin/lessons', { id: uniq('LS'), courseId: course.id, chapterId: chapter.id, title: uniq('Bài '), ...body }), 'lesson')
export const publish = async (admin, courseId) => ok(await admin.post(`/api/admin/courses/${courseId}/status`, { status: 'PUBLISHED' }), `publish ${courseId}`)
export const setStatus = (admin, courseId, status) => admin.post(`/api/admin/courses/${courseId}/status`, { status })
export const allocate = async (admin, unitId, programId, courseId, grantType = 'MANDATORY') =>
  ok(await admin.post('/api/admin/unit-allocations/assign', { unitId, categoryType: 'COURSE', grantType, programId, allInProgram: false, courseIds: [courseId] }), `allocate ${courseId}`)

export async function addQuestions(admin, course, lesson, count, tag = 'Câu') {
  const out = []
  for (let i = 0; i < count; i++) {
    const multi = i % 4 === 3
    const q = ok(await admin.post('/api/admin/questions', {
      courseId: course.id, lessonId: lesson.id, text: `${tag} ${i + 1}`, options: ['A', 'B', 'C', 'D'],
      type: multi ? 'MULTIPLE_CHOICE' : 'SINGLE_CHOICE',
      correctAnswer: multi ? undefined : 1, correctAnswers: multi ? [0, 2] : [1],
    }), `question ${i}`)
    out.push({ id: q.id, correct: multi ? [0, 2] : [1], multi })
  }
  return out
}

export function answersFor(questions, correctCount) {
  const map = {}
  questions.forEach((q, i) => { map[q.id] = i < correctCount ? q.correct : [3] })
  return map
}

export const quizSettings = async (admin, course, lesson, settings) =>
  ok(await admin.put('/api/admin/quizzes/settings', { courseId: course.id, lessonId: lesson.id, passingScore: 80, requiredForProgress: true, ...settings }), 'quiz settings')

// One published course allocated to a unit, with one chapter holding the given lessons.
// Each test gets its own course so ordering and completion never leak between tests.
export async function freshCourse(ctx, { lessons = [{}], unit = ctx.A, programId = ctx.program.id, grant = 'MANDATORY', publishIt = true } = {}) {
  const { admin } = ctx
  const course = await createCourse(admin, programId)
  const chapter = await createChapter(admin, course.id)
  const built = []
  for (const spec of lessons) {
    const { questions = 0, settings, ...body } = spec
    const lesson = await createLesson(admin, course, chapter, { textBody: '<p>Nội dung bài test</p>', ...body })
    const qs = questions ? await addQuestions(admin, course, lesson, questions) : []
    if (questions) await quizSettings(admin, course, lesson, settings)
    built.push({ ...lesson, qs })
  }
  if (publishIt) await publish(admin, course.id)
  if (unit) await allocate(admin, unit.id, programId, course.id, grant)
  return { course, chapter, lessons: built, lesson: built[0] }
}

// Marks text read and completes the lesson, the way the learner UI does.
export async function readAndComplete(client, lessonId) {
  ok(await client.post(`/api/users/me/lessons/${lessonId}/progress`, { contentType: 'TEXT', textCompleted: true }), 'text progress')
  return client.post(`/api/users/me/complete-lesson/${lessonId}`)
}

export async function seed() {
  const admin = await loginAs('admin', ADMIN_PASSWORD)
  const run = uniq('r')
  const ctx = { run, admin }

  const unit = async (name, parentId) => ok(await admin.post('/api/admin/units',
    { name: `${name}-${run}`, type: parentId ? 'DEPARTMENT' : 'UNIT', parentId, active: true }), `unit ${name}`)
  ctx.A = await unit('A'); ctx.A1 = await unit('A1', ctx.A.id); ctx.A11 = await unit('A11', ctx.A1.id)
  ctx.B = await unit('B'); ctx.B1 = await unit('B1', ctx.B.id)
  ctx.UI = await unit('INACTIVE')
  ok(await admin.put(`/api/admin/units/${ctx.UI.id}`, { ...ctx.UI, active: false }), 'deactivate unit')

  ctx.domain = ok(await admin.post('/api/admin/training-domains', { code: `D${run}`, name: `Lĩnh vực ${run}`, status: 'ACTIVE' }), 'domain')
  ctx.program = ok(await admin.post('/api/admin/training-programs', { domainId: ctx.domain.id, code: `P${run}`, name: `Chương trình ${run}`, status: 'ACTIVE', unitIds: [ctx.A.id] }), 'program')
  ctx.programB = ok(await admin.post('/api/admin/training-programs', { domainId: ctx.domain.id, code: `PB${run}`, name: `Chương trình B ${run}`, status: 'ACTIVE', unitIds: [ctx.B.id] }), 'programB')

  // C1: 2 chapters, 3 lessons, OPTIONAL for A so enrolling needs approval.
  ctx.C1 = await createCourse(admin, ctx.program.id, 'C1')
  ctx.C1ch1 = await createChapter(admin, ctx.C1.id, 'Chương 1')
  ctx.C1ch2 = await createChapter(admin, ctx.C1.id, 'Chương 2')
  ctx.C1L1 = await createLesson(admin, ctx.C1, ctx.C1ch1, { title: 'Bài 1', textBody: '<p>Nội dung bài 1</p>' })
  ctx.C1L2 = await createLesson(admin, ctx.C1, ctx.C1ch1, { title: 'Bài 2', textBody: '<p>Nội dung bài 2</p>' })
  ctx.C1L3 = await createLesson(admin, ctx.C1, ctx.C1ch2, { title: 'Bài 3', textBody: '<p>Nội dung bài 3</p>' })
  await publish(admin, ctx.C1.id)
  await allocate(admin, ctx.A.id, ctx.program.id, ctx.C1.id, 'OPTIONAL')

  // C2 draft, C3 archived (both allocated to A), C4 published for B only.
  ctx.C2 = await freshCourse(ctx, { publishIt: false, unit: null })
  ctx.C3 = await freshCourse(ctx)
  ok(await setStatus(admin, ctx.C3.course.id, 'ARCHIVED'), 'archive C3')
  ctx.C4 = await freshCourse(ctx, { unit: ctx.B, programId: ctx.programB.id, lessons: [{ textBody: '<p>Bí mật của đơn vị B</p>' }] })

  // Staff accounts.
  ctx.u0 = admin
  ctx.u1User = await createUser(admin, { unitId: ctx.A.id, learner: false })
  ctx.u2User = await createUser(admin, { unitId: ctx.A.id, learner: false })
  ctx.u3User = await createUser(admin, { unitId: ctx.A.id, learner: false })
  ctx.u8User = await createUser(admin, { unitId: ctx.A.id, learner: false })
  ctx.rUnitAdmin = await addRole(admin, { code: `R_UA_${run}`, name: `Quản trị A ${run}`, unitIds: [ctx.A.id], userIds: [ctx.u1User.id],
    permissions: ['users:read', 'users:create', 'users:update', 'courses:read', 'courses:update', 'enrollments:read', 'enrollments:update', 'units:read', 'overview:read', 'catalog:read', 'exams:read'] })
  ctx.rReport = await addRole(admin, { code: `R_RP_${run}`, name: `Báo cáo A ${run}`, unitIds: [ctx.A.id], userIds: [ctx.u3User.id], permissions: ['overview:read'] })
  ctx.rEditA = await addRole(admin, { code: `R_EA_${run}`, name: `Sửa khóa A ${run}`, unitIds: [ctx.A.id], userIds: [ctx.u8User.id], permissions: ['courses:read', 'courses:update'] })
  ctx.rReadB = await addRole(admin, { code: `R_RB_${run}`, name: `Đọc khóa B ${run}`, unitIds: [ctx.B.id], userIds: [ctx.u8User.id], permissions: ['courses:read'] })
  ctx.u1 = await activate(ctx.u1User.username)
  ctx.u3 = await activate(ctx.u3User.username)
  ctx.u8 = await activate(ctx.u8User.username)
  return ctx
}

if (process.argv[1]?.endsWith('seed.mjs')) {
  const ctx = await seed()
  console.log('seed ok', ctx.run)
}

// Admin updates need the account's current roles echoed back, so read it first.
export async function currentUser(admin, user) {
  const page = ok(await admin.get(`/api/admin/users/page?query=${encodeURIComponent(user.username)}&page=1&size=5`), 'user page')
  return (page.items ?? page.content ?? page).find((u) => u.id === user.id)
}
export async function updateUser(admin, user, changes) {
  return admin.put(`/api/admin/users/${user.id}`, { ...(await currentUser(admin, user)), ...changes })
}
