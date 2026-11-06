// Prepares data for the manual browser pass. Prints account names; passwords are the test values in seed.mjs.
import { sql, multipart } from './lib.mjs'
import { seed, freshCourse, createUser, ok, addMembers, USER_PASSWORD } from './seed.mjs'
import { activate } from './seed.mjs'

const ctx = await seed()
const { admin } = ctx
const f = await freshCourse(ctx, { lessons: [
  { title: 'Bài XSS', textBody: '<p>Nội dung an toàn</p><img src=x onerror="window.__xss=1"><script>window.__xss=2</script>', questions: 4, settings: { timeLimitMinutes: 1, requiredForProgress: false } },
  { title: 'Bài thứ hai', textBody: '<p>Bài 2</p>' },
] })
const card = ok(await admin.post(`/api/admin/lessons/${f.lesson.id}/flashcards/batch`, { cards: [{ type: 'TERM', front: 'CIF', back: 'Mã khách hàng' }, { type: 'TERM', front: 'LTV', back: 'Tỷ lệ cho vay' }, { type: 'CLOZE', front: 'Giao dịch viên đối chiếu ___ với chứng từ', back: 'CIF' }] }), 'cards')
ok(await admin.put(`/api/admin/lessons/${f.lesson.id}/flashcards/status`, { ids: card.map((c) => c.id), status: 'PUBLISHED' }), 'publish')
const s = await createUser(admin, { username: 'ui_learner', unitId: ctx.A.id })
const c = await activate(s.username)
ok(await c.post(`/api/lessons/${f.lesson.id}/comments`, { content: '<img src=x onerror="window.__xss=3">Bình luận XSS' }), 'comment')
const fresh = await createUser(admin, { username: 'ui_new', unitId: ctx.A.id })
sql(`select 1`)
void multipart; void addMembers; void fresh; void USER_PASSWORD
console.log(JSON.stringify({ course: f.course.id, lesson: f.lesson.id, name: f.course.name }))
