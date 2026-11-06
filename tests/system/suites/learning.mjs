// 4.5 Lesson content, bookmarks, progress (F-L01..L12) plus NF-S04 video/document protection.
import { readFileSync } from 'node:fs'
import { Client, tc, assert, expectStatus, expectRejected, sqlNum, sqlOne, multipart, gap, na, BASE } from '../lib.mjs'
import { freshCourse, learner, readAndComplete, answersFor, ok, createCourse, createChapter, createLesson, publish, allocate, setStatus } from '../seed.mjs'

const fixture = (name) => readFileSync(new URL(`../fixtures/${name}`, import.meta.url))
const points = (client) => sqlNum(`select points from users where id=${client.user.id}`)
const completions = (client, lessonId) => sqlNum(`select count(*) from user_completed_lessons where user_id=${client.user.id} and lesson_id='${lessonId}'`)
const save = (c, lessonId, body) => c.post(`/api/users/me/lessons/${lessonId}/progress`, body)
const complete = (c, lessonId) => c.post(`/api/users/me/complete-lesson/${lessonId}`)
const contentComplete = (c, lessonId) => c.post(`/api/users/me/lessons/${lessonId}/content-complete`)

export async function run(ctx) {
  const { admin } = ctx
  const uploadDoc = async (name, data) => ok(await admin.post('/api/admin/documents/upload', undefined, { form: multipart({}, [{ name, data }]) }), `upload ${name}`)
  const video = ok(await admin.post('/api/admin/videos/upload', undefined, { form: multipart({}, [{ name: 'v.mp4', data: fixture('v100.mp4'), type: 'video/mp4' }]) }), 'upload video')
  const videoUrl = video.url ?? video.videoUrl ?? video.fileUrl
  const slideA = async () => ({ ...(await uploadDoc('slide-a.pptx', Buffer.from('PK\x03\x04a'))), purpose: 'SLIDE' })
  const supporting = async () => ({ ...(await uploadDoc('phu-tro.pdf', Buffer.from('%PDF-1.4 x'))), purpose: 'SUPPORTING' })
  const lessonDocs = (lessonId) => sqlOne(`select string_agg(id::text, ',' order by document_order) from lesson_documents where lesson_id='${lessonId}'`).split(',').filter(Boolean).map(Number)

  // ---- F-L01 ordering.
  await tc('F-L01', 'outline đúng, bài sau khoá đến khi hoàn thành bài trước', async () => {
    const f = await freshCourse(ctx, { lessons: [{}, {}, {}] })
    const s = await learner(ctx)
    const outline = expectStatus(await s.get(`/api/courses/${f.course.id}/outline`), 200)
    assert.ok(outline.text.includes(f.lessons[0].id))
    expectStatus(await s.get(`/api/lessons/${f.lessons[0].id}`), 200)
    assert.ok([403, 404].includes((await s.get(`/api/lessons/${f.lessons[1].id}`)).status), 'second lesson open before first complete')
    expectRejected(await save(s, f.lessons[1].id, { contentType: 'TEXT', textCompleted: true }), 'skip progress')
    expectRejected(await complete(s, f.lessons[2].id), 'skip complete')
    expectStatus(await readAndComplete(s, f.lessons[0].id), 200)
    expectStatus(await s.get(`/api/lessons/${f.lessons[1].id}`), 200)
    assert.ok([403, 404].includes((await s.get(`/api/lessons/${f.lessons[2].id}`)).status))
  })
  await tc('F-L01', 'chương sau khoá đến khi xong chương trước', async () => {
    const s = await learner(ctx)
    expectStatus(await s.post(`/api/users/me/register-course/${ctx.C1.id}`), 200)
    const id = sqlOne(`select id from course_enrollment_requests where user_id=${s.user.id} and course_id='${ctx.C1.id}'`)
    expectStatus(await admin.put(`/api/admin/enrollment-requests/${id}`, { approve: true }), 200)
    assert.ok([403, 404].includes((await s.get(`/api/lessons/${ctx.C1L3.id}`)).status), 'chapter 2 open early')
    await readAndComplete(s, ctx.C1L1.id); await readAndComplete(s, ctx.C1L2.id)
    expectStatus(await s.get(`/api/lessons/${ctx.C1L3.id}`), 200)
  })

  // ---- F-L02 content-complete boundaries, video duration 100s from the fixture.
  const videoLesson = () => freshCourse(ctx, { lessons: [{ textBody: null, videoUrl, videoSource: 'UPLOAD' }] })
  for (const [watched, pass] of [[0, false], [89.9, false], [90, true], [100, true]]) {
    await tc('F-L02', `video ${watched}%`, async () => {
      const f = await videoLesson(); const s = await learner(ctx)
      expectStatus(await save(s, f.lesson.id, { contentType: 'VIDEO', lastPositionSeconds: watched, watchedSeconds: watched, durationSeconds: 100 }), 200)
      const res = await complete(s, f.lesson.id)
      if (pass) { expectStatus(res, 200); assert.equal(completions(s, f.lesson.id), 1) }
      else { expectRejected(res); assert.equal(completions(s, f.lesson.id), 0) }
    })
  }
  await tc('F-L02', 'client tự khai durationSeconds nhỏ để lách 90%', async () => {
    const f = await videoLesson(); const s = await learner(ctx)
    const res = await save(s, f.lesson.id, { contentType: 'VIDEO', lastPositionSeconds: 1, watchedSeconds: 1, durationSeconds: 1 })
    const done = await complete(s, f.lesson.id)
    assert.ok(done.status >= 400, `1-second watch completed a 100s video via client-supplied duration (save=${res.status}, complete=${done.status})`)
  })
  await tc('F-L02', 'văn bản chưa đọc chặn, đọc rồi đạt', async () => {
    const f = await freshCourse(ctx); const s = await learner(ctx)
    expectRejected(await complete(s, f.lesson.id))
    expectStatus(await save(s, f.lesson.id, { contentType: 'TEXT', textCompleted: true }), 200)
    expectStatus(await complete(s, f.lesson.id), 200)
  })
  await tc('F-L02', 'slide bắt buộc: chưa đọc đến cuối thì chưa đạt', async () => {
    const f = await freshCourse(ctx, { lessons: [{ textBody: null, documents: [await slideA()] }] })
    const s = await learner(ctx)
    const [d1] = lessonDocs(f.lesson.id)
    expectStatus(await save(s, f.lesson.id, { contentType: 'DOCUMENT', documentId: d1, documentPage: 1 }), 200)
    expectRejected(await complete(s, f.lesson.id), 'slide opened only')
    expectStatus(await save(s, f.lesson.id, { contentType: 'DOCUMENT', documentId: d1, documentPage: 9, documentCompleted: true }), 200)
    expectStatus(await complete(s, f.lesson.id), 200)
    const two = await admin.post('/api/admin/lessons', { id: `TW${Date.now()}`, title: 'Hai slide', chapterId: f.chapter.id, courseId: f.course.id, textBody: '<p>x</p>', documents: [await slideA(), await slideA()] })
    return two.status >= 400 ? gap('Hệ thống chỉ cho 1 slide mỗi bài ("Mỗi bài học chỉ được có một file slide PowerPoint"), trong khi dữ liệu L1 của kịch bản yêu cầu 2 slide bắt buộc. Đã kiểm tra với 1 slide; kịch bản cần cập nhật.') : undefined
  })

  // ---- F-L03 per-document bookmarks.
  await tc('F-L03', 'hai tài liệu giữ trang/scroll riêng sau reload', async () => {
    const f = await freshCourse(ctx, { lessons: [{ documents: [await slideA(), await supporting()] }] })
    const s = await learner(ctx)
    const [d1, d2] = lessonDocs(f.lesson.id)
    await save(s, f.lesson.id, { contentType: 'DOCUMENT', documentId: d1, documentPage: 7, scrollOffset: 11 })
    await save(s, f.lesson.id, { contentType: 'DOCUMENT', documentId: d2, documentPage: 3, scrollOffset: 22 })
    const res = expectStatus(await s.get('/api/users/me/learning'), 200)
    const row = sqlOne(`select document_positions from lesson_progress where user_id=${s.user.id} and lesson_id='${f.lesson.id}'`)
    assert.ok(row, `no stored positions; workspace ${res.text.slice(0, 100)}`)
    const pos = JSON.parse(row)
    assert.equal(pos[d1].page ?? pos[d1].documentPage, 7); assert.equal(pos[d2].page ?? pos[d2].documentPage, 3)
    assert.equal(pos[d1].scrollOffset, 11); assert.equal(pos[d2].scrollOffset, 22)
  })
  await tc('F-L03', 'resume từ dashboard trả về đúng bài', async () => {
    const f = await freshCourse(ctx, { lessons: [{}, {}] })
    const s = await learner(ctx)
    await readAndComplete(s, f.lessons[0].id)
    const resume = expectStatus(await s.get(`/api/users/me/learning/dashboard/courses/${f.course.id}/resume`), 200)
    assert.ok(resume.text.includes(f.lessons[1].id), `resume=${resume.text.slice(0, 200)}`)
  })

  // ---- F-L04 video access.
  await tc('F-L04', 'bookmark video lưu và lùi được khi tua lại', async () => {
    const f = await videoLesson(); const s = await learner(ctx)
    await save(s, f.lesson.id, { contentType: 'VIDEO', lastPositionSeconds: 60, watchedSeconds: 60, durationSeconds: 100 })
    const back = expectStatus(await save(s, f.lesson.id, { contentType: 'VIDEO', lastPositionSeconds: 10, watchedSeconds: 61, durationSeconds: 100 }), 200).json
    assert.equal(back.lastPositionSeconds, 10); assert.ok(back.watchedSeconds >= 61)
  })
  await tc('F-L04', 'nguồn YouTube lưu được, không cấp token stream', async () => {
    const f = await freshCourse(ctx, { lessons: [{ videoUrl: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', videoSource: 'URL' }] })
    const s = await learner(ctx)
    const les = expectStatus(await s.get(`/api/lessons/${f.lesson.id}`), 200).json
    assert.match(les.videoUrl, /youtube/)
    expectRejected(await s.get(`/api/videos/access/${f.lesson.id}`), 'stream token for YouTube')
  })
  await tc('F-L04', 'file video bị gỡ: báo lỗi, không giả hoàn thành', async () => {
    const f = await freshCourse(ctx, { lessons: [{ textBody: null, videoUrl: '/uploads/videos/missing-file.mp4', videoSource: 'UPLOAD' }] })
    const s = await learner(ctx)
    expectRejected(await s.get(`/api/videos/access/${f.lesson.id}`), 'missing video')
    expectRejected(await complete(s, f.lesson.id), 'complete without watching')
  })

  // ---- F-L05 invalid progress input.
  const dirty = await freshCourse(ctx, { lessons: [{ documents: [await slideA()] }] })
  const other = await freshCourse(ctx, { lessons: [{ documents: [await slideA()] }] })
  const dirtyDoc = lessonDocs(dirty.lesson.id)[0]; const otherDoc = lessonDocs(other.lesson.id)[0]
  const dirtyUser = await learner(ctx)
  for (const [name, body] of [
    ['watchedSeconds âm', { contentType: 'VIDEO', watchedSeconds: -1 }], ['lastPosition âm', { contentType: 'VIDEO', lastPositionSeconds: -5 }],
    ['duration âm', { contentType: 'VIDEO', durationSeconds: -1 }], ['documentPage=0', { contentType: 'DOCUMENT', documentId: dirtyDoc, documentPage: 0 }],
    ['documentPage âm', { contentType: 'DOCUMENT', documentId: dirtyDoc, documentPage: -3 }], ['scrollOffset âm', { contentType: 'DOCUMENT', documentId: dirtyDoc, scrollOffset: -1 }],
    ['contentType lạ', { contentType: 'HACK' }], ['documentId bài khác', { contentType: 'DOCUMENT', documentId: otherDoc, documentPage: 1 }],
    ['documentId không tồn tại', { contentType: 'DOCUMENT', documentId: 999999, documentPage: 1 }],
  ]) {
    await tc('F-L05', name, async () => expectRejected(await save(dirtyUser, dirty.lesson.id, body), name) && undefined)
  }
  for (const [name, raw] of [['NaN', '{"contentType":"VIDEO","watchedSeconds":NaN}'], ['Infinity', '{"contentType":"VIDEO","watchedSeconds":Infinity}'], ['chuỗi', '{"contentType":"VIDEO","watchedSeconds":"abc"}']]) {
    await tc('F-L05', `parser ${name}`, async () => {
      const res = await dirtyUser.req('POST', `/api/users/me/lessons/${dirty.lesson.id}/progress`, undefined, { raw })
      expectRejected(res, name)
    })
  }
  await tc('F-L05', 'parser 1e999 không ghi giá trị bẩn', async () => {
    const res = await dirtyUser.req('POST', `/api/users/me/lessons/${dirty.lesson.id}/progress`, undefined, { raw: '{"contentType":"VIDEO","watchedSeconds":1e999,"durationSeconds":100}' })
    assert.ok(res.status < 500, `HTTP ${res.status}`)
    if (res.status === 200) assert.ok(Number.isFinite(res.json.watchedSeconds))
  })
  await tc('F-L05', 'watchedSeconds vượt duration không tự hoàn thành sai', async () => {
    const f = await videoLesson(); const s = await learner(ctx)
    const res = await save(s, f.lesson.id, { contentType: 'VIDEO', watchedSeconds: 1e9, durationSeconds: 100, lastPositionSeconds: 1 })
    if (res.status === 200) assert.ok(res.json.watchedSeconds <= 100, `stored watchedSeconds=${res.json.watchedSeconds} beyond duration 100`)
  })
  await tc('F-L05', 'dữ liệu bẩn không ghi vào DB', async () => {
    const row = sqlOne(`select document_positions::text from lesson_progress where user_id=${dirtyUser.user.id} and lesson_id='${dirty.lesson.id}'`)
    assert.ok(!row.includes(String(otherDoc)), 'foreign document id persisted')
    assert.equal(completions(dirtyUser, dirty.lesson.id), 0)
  })

  // ---- F-L06 quiz gate.
  await tc('F-L06', 'đủ nội dung, chưa đạt quiz bắt buộc: chưa hoàn thành', async () => {
    const f = await freshCourse(ctx, { lessons: [{ questions: 5 }] }); const s = await learner(ctx)
    await save(s, f.lesson.id, { contentType: 'TEXT', textCompleted: true })
    expectRejected(await complete(s, f.lesson.id))
    assert.equal(completions(s, f.lesson.id), 0)
    assert.equal(points(s), 0)
  })
  await tc('F-L06', 'đạt quiz nhưng thiếu nội dung: chưa hoàn thành', async () => {
    const f = await freshCourse(ctx, { lessons: [{ questions: 5 }] }); const s = await learner(ctx)
    expectStatus(await s.post(`/api/quizzes/lessons/${f.lesson.id}/submit`, { selectedAnswers: answersFor(f.lesson.qs, 5) }), 200)
    expectRejected(await complete(s, f.lesson.id))
    assert.equal(completions(s, f.lesson.id), 0, 'completed without reading')
  })
  await tc('F-L06', 'đủ cả hai: hoàn thành; quiz tuỳ chọn không chặn', async () => {
    const f = await freshCourse(ctx, { lessons: [{ questions: 5 }] }); const s = await learner(ctx)
    await save(s, f.lesson.id, { contentType: 'TEXT', textCompleted: true })
    await s.post(`/api/quizzes/lessons/${f.lesson.id}/submit`, { selectedAnswers: answersFor(f.lesson.qs, 5) })
    expectStatus(await complete(s, f.lesson.id), 200)
    const opt = await freshCourse(ctx, { lessons: [{ questions: 5, settings: { requiredForProgress: false } }] })
    expectStatus(await readAndComplete(s, opt.lesson.id), 200)
  })

  // ---- F-L07 combinations.
  await tc('F-L07', 'chỉ text', async () => { const f = await freshCourse(ctx); const s = await learner(ctx); expectStatus(await readAndComplete(s, f.lesson.id), 200) })
  await tc('F-L07', 'tài liệu phụ trợ chưa mở không chặn', async () => {
    const f = await freshCourse(ctx, { lessons: [{ documents: [await supporting()] }] }); const s = await learner(ctx)
    expectStatus(await readAndComplete(s, f.lesson.id), 200)
  })
  await tc('F-L07', 'tất cả required=false: mở bài không tự thưởng', async () => {
    const f = await freshCourse(ctx, { lessons: [{ textRequired: false }] }); const s = await learner(ctx)
    expectStatus(await s.get(`/api/lessons/${f.lesson.id}`), 200)
    await s.get(`/api/courses/${f.course.id}/outline`)
    assert.equal(completions(s, f.lesson.id), 0, 'auto-completed by opening')
    assert.equal(points(s), 0, 'points awarded by opening')
  })
  await tc('F-L07', 'text tuỳ chọn + video bắt buộc: chỉ video mới tính', async () => {
    const f = await freshCourse(ctx, { lessons: [{ textRequired: false, videoUrl, videoSource: 'UPLOAD' }] }); const s = await learner(ctx)
    await save(s, f.lesson.id, { contentType: 'TEXT', textCompleted: true })
    expectRejected(await complete(s, f.lesson.id))
    await save(s, f.lesson.id, { contentType: 'VIDEO', watchedSeconds: 95, durationSeconds: 100, lastPositionSeconds: 95 })
    expectStatus(await complete(s, f.lesson.id), 200)
  })

  // ---- F-L08 idempotent completion.
  await tc('F-L08', 'gọi lặp tuần tự: 1 completion, +100 điểm một lần', async () => {
    const f = await freshCourse(ctx); const s = await learner(ctx)
    await readAndComplete(s, f.lesson.id)
    for (let i = 0; i < 5; i++) expectStatus(await complete(s, f.lesson.id), 200)
    assert.equal(completions(s, f.lesson.id), 1); assert.equal(points(s), 100)
  })
  await tc('F-L08', 'gọi đồng thời 10 request: 1 completion, +100 điểm', async () => {
    const f = await freshCourse(ctx); const s = await learner(ctx)
    await save(s, f.lesson.id, { contentType: 'TEXT', textCompleted: true })
    const out = await Promise.all(Array.from({ length: 10 }, () => complete(s, f.lesson.id)))
    assert.ok(out.every((r) => r.status < 500), `statuses ${out.map((r) => r.status)}`)
    assert.equal(completions(s, f.lesson.id), 1); assert.equal(points(s), 100, `points=${points(s)}`)
  })
  await tc('F-L08', 'phần trăm khoá không vượt 100', async () => {
    const f = await freshCourse(ctx, { lessons: [{}, {}] }); const s = await learner(ctx)
    await readAndComplete(s, f.lessons[0].id); await readAndComplete(s, f.lessons[1].id); await complete(s, f.lessons[1].id)
    const dash = expectStatus(await s.get('/api/users/me/learning/dashboard/courses?tab=mine&page=1&size=50'), 200).json
    const row = dash.items.find((c) => c.course.id === f.course.id)
    assert.ok(row, 'course missing from dashboard')
    assert.equal(row.percent, 100, `percent=${row.percent}`)
  })

  // ---- F-L09 reset.
  await tc('F-L09', 'reset completion: chính sách không xoá thành tích, điểm không đổi', async () => {
    const f = await freshCourse(ctx); const s = await learner(ctx)
    await readAndComplete(s, f.lesson.id)
    const res = await s.del(`/api/users/me/lessons/${f.lesson.id}/completion`)
    expectRejected(res)
    assert.equal(completions(s, f.lesson.id), 1); assert.equal(points(s), 100)
    return 'Backend từ chối reset (400): "Bài đã hoàn thành vẫn có thể học lại; không xóa thành tích"'
  })

  // ---- F-L10 stale writes.
  await tc('F-L10', 'request watchedSeconds cũ đến sau không làm giảm', async () => {
    const f = await videoLesson(); const s = await learner(ctx)
    await save(s, f.lesson.id, { contentType: 'VIDEO', lastPositionSeconds: 80, watchedSeconds: 80, durationSeconds: 100 })
    const res = expectStatus(await save(s, f.lesson.id, { contentType: 'VIDEO', lastPositionSeconds: 20, watchedSeconds: 20, durationSeconds: 100 }), 200).json
    assert.ok(res.watchedSeconds >= 80, `watchedSeconds regressed to ${res.watchedSeconds}`)
    assert.equal(sqlNum(`select watched_seconds from lesson_progress where user_id=${s.user.id} and lesson_id='${f.lesson.id}'`), 80)
  })
  await tc('F-L10', 'hai thiết bị ghi song song không mất tiến độ cao nhất', async () => {
    const f = await videoLesson(); const s = await learner(ctx)
    const values = [10, 50, 30, 70, 20, 60, 40]
    const out = await Promise.all(values.map((w) => save(s, f.lesson.id, { contentType: 'VIDEO', lastPositionSeconds: w, watchedSeconds: w, durationSeconds: 100 })))
    assert.ok(out.every((r) => r.status < 500), `statuses ${out.map((r) => r.status)}`)
    const stored = sqlNum(`select watched_seconds from lesson_progress where user_id=${s.user.id} and lesson_id='${f.lesson.id}'`)
    assert.equal(stored, 70, `stored ${stored}, statuses ${out.map((r) => r.status)}`)
    assert.equal(sqlNum(`select count(*) from lesson_progress where user_id=${s.user.id} and lesson_id='${f.lesson.id}'`), 1)
  })
  await tc('F-L10', 'lưu bookmark song song hai tài liệu không ghi đè nhau', async () => {
    const f = await freshCourse(ctx, { lessons: [{ documents: [await slideA(), await supporting()] }] }); const s = await learner(ctx)
    const [d1, d2] = lessonDocs(f.lesson.id)
    await Promise.all([save(s, f.lesson.id, { contentType: 'DOCUMENT', documentId: d1, documentPage: 4 }), save(s, f.lesson.id, { contentType: 'DOCUMENT', documentId: d2, documentPage: 8 })])
    const pos = JSON.parse(sqlOne(`select document_positions from lesson_progress where user_id=${s.user.id} and lesson_id='${f.lesson.id}'`))
    assert.ok(pos[d1] && pos[d2], `lost a bookmark: ${JSON.stringify(pos)}`)
  })

  // ---- F-L11 requirement changes.
  await tc('F-L11', 'thêm slide bắt buộc sau tiến độ cũ: đánh giá theo yêu cầu hiện tại', async () => {
    const f = await freshCourse(ctx); const s = await learner(ctx)
    await save(s, f.lesson.id, { contentType: 'TEXT', textCompleted: true })
    const slide = await slideA()
    ok(await admin.put(`/api/admin/lessons/${f.lesson.id}`, { title: 'L', textBody: '<p>x</p>', documents: [slide] }), 'add slide')
    expectRejected(await complete(s, f.lesson.id), 'new slide unread')
  })
  await tc('F-L11', 'đổi slide thành phụ trợ thì không còn chặn', async () => {
    const f = await freshCourse(ctx, { lessons: [{ documents: [await slideA()] }] }); const s = await learner(ctx)
    await save(s, f.lesson.id, { contentType: 'TEXT', textCompleted: true })
    expectRejected(await complete(s, f.lesson.id))
    const row = sqlOne(`select id, name, document_type, file_url from lesson_documents where lesson_id='${f.lesson.id}'`).split('|')
    ok(await admin.put(`/api/admin/lessons/${f.lesson.id}`, { title: 'L', textBody: '<p>x</p>', documents: [{ id: Number(row[0]), name: row[1], documentType: row[2], fileUrl: row[3], purpose: 'SUPPORTING' }] }), 'demote')
    expectStatus(await complete(s, f.lesson.id), 200)
  })
  await tc('F-L11', 'lưu bài sau hoàn thành không thưởng lại', async () => {
    const f = await freshCourse(ctx); const s = await learner(ctx)
    await readAndComplete(s, f.lesson.id)
    ok(await admin.put(`/api/admin/lessons/${f.lesson.id}`, { title: 'Sửa', textBody: '<p>y</p>' }), 'edit')
    await complete(s, f.lesson.id)
    assert.equal(points(s), 100)
  })

  // ---- F-L12 course totals.
  await tc('F-L12', 'hoàn thành bài cuối thì khoá hoàn thành', async () => {
    const f = await freshCourse(ctx, { lessons: [{}, {}] }); const s = await learner(ctx)
    await readAndComplete(s, f.lessons[0].id)
    assert.equal(sqlNum(`select count(*) from user_completed_courses where user_id=${s.user.id} and course_id='${f.course.id}'`), 0)
    await readAndComplete(s, f.lessons[1].id)
    assert.equal(sqlNum(`select count(*) from user_completed_courses where user_id=${s.user.id} and course_id='${f.course.id}'`), 1)
  })
  await tc('F-L12', 'dashboard không NaN/chia 0 với khoá rỗng và học viên mới', async () => {
    const s = await learner(ctx)
    const dash = expectStatus(await s.get('/api/users/me/learning/dashboard/summary?tab=mine'), 200)
    assert.ok(!/NaN|Infinity/.test(dash.text))
    assert.ok(!/NaN|Infinity/.test(expectStatus(await s.get('/api/users/me/learning/dashboard/courses?tab=mine&page=1&size=50'), 200).text))
  })
  await tc('F-L12', 'thêm bài sau khi hoàn thành: phần trăm không vượt 100', async () => {
    const f = await freshCourse(ctx); const s = await learner(ctx)
    await readAndComplete(s, f.lesson.id)
    ok(await setStatus(admin, f.course.id, 'DRAFT'), 'to draft')
    await createLesson(admin, f.course, f.chapter, { textBody: '<p>mới</p>' })
    await publish(admin, f.course.id)
    const dash = expectStatus(await s.get('/api/users/me/learning/dashboard/courses?tab=mine&page=1&size=50'), 200).json
    const row = dash.items.find((c) => c.course.id === f.course.id)
    assert.ok(row, 'course missing')
    const pct = row.percent
    assert.ok(pct <= 100 && !Number.isNaN(pct), `progress=${pct}`)
  })

  // ---- NF-S04 protected media.
  const anon = new Client()
  await tc('NF-S04', 'truy cập trực tiếp /uploads/videos bị chặn', async () => {
    const path = videoUrl.startsWith('/') ? videoUrl : new URL(videoUrl).pathname
    for (const c of [anon, admin]) assert.ok([401, 403].includes((await c.get(path)).status), 'raw video readable')
  })
  await tc('NF-S04', 'token stream đúng phát được, Range đúng', async () => {
    const f = await videoLesson(); const s = await learner(ctx)
    const access = expectStatus(await s.get(`/api/videos/access/${f.lesson.id}`), 200).json
    ctx.streamUrl = access.streamUrl ?? access.url
    assert.ok(ctx.streamUrl, JSON.stringify(access))
    ctx.streamLesson = f; ctx.streamUser = s
    const u = ctx.streamUrl.startsWith('http') ? ctx.streamUrl : `${BASE}${ctx.streamUrl}`
    const ranged = await fetch(u, { headers: { Range: 'bytes=0-99' } })
    assert.equal(ranged.status, 206); assert.equal((await ranged.arrayBuffer()).byteLength, 100)
    assert.match(ranged.headers.get('content-range'), /^bytes 0-99\//)
  })
  await tc('NF-S04', 'token stream bị sửa/rác bị từ chối', async () => {
    const u = new URL(ctx.streamUrl, BASE)
    const t = u.searchParams.get('token')
    const [h, p, sig] = t.split('.')
    const forgedPayload = Buffer.from(JSON.stringify({ ...JSON.parse(Buffer.from(p, 'base64url')), lessonId: 'OTHER' })).toString('base64url')
    for (const bad of [`${h}.${forgedPayload}.${sig}`, `${h}.${p}.${sig.slice(0, 5)}${sig[5] === 'A' ? 'B' : 'A'}${sig.slice(6)}`, 'garbage']) {
      const res = await fetch(`${BASE}/api/videos/stream?token=${encodeURIComponent(bad)}`)
      assert.ok(res.status >= 400 && res.status < 500, `tampered token returned ${res.status}`)
    }
  })
  await tc('NF-S04', 'token còn hạn nhưng đã thu hồi quyền/khoá tài khoản', async () => {
    const s = ctx.streamUser
    const { updateUser } = await import('../seed.mjs')
    expectStatus(await updateUser(admin, s.user, { status: 'LOCKED' }), 200)
    const res = await fetch(new URL(ctx.streamUrl, BASE))
    assert.ok(res.status >= 400 && res.status < 500, `locked user's stream token returned ${res.status}`)
  })
  await tc('NF-S04', 'path traversal tài liệu', async () => {
    for (const p of ['/uploads/documents/..%2f..%2fetc%2fpasswd', '/uploads/documents/%2e%2e%2f%2e%2e%2fapp.jar', '/uploads/documents/..\\..\\app.jar', '/uploads/documents/....//....//etc/passwd']) {
      const res = await admin.get(p)
      assert.ok(res.status >= 400, `${p} returned ${res.status}`)
      assert.ok(!/root:|PK\x03\x04/.test(res.text.slice(0, 200)), `${p} leaked a system file`)
    }
  })
  await tc('NF-S04', 'tài liệu của bài ngoài quyền không tải được', async () => {
    const doc = await supporting()
    const f = await freshCourse(ctx, { lessons: [{ documents: [doc] }] })
    const b = await learner(ctx, ctx.B)
    assert.ok([403, 404].includes((await b.get(doc.fileUrl)).status), 'document of another unit readable')
    const a = await learner(ctx)
    expectStatus(await a.get(doc.fileUrl), 200)
    void f
  })
  await tc('NF-S04', 'tài liệu sau khi thu hồi quyền', async () => {
    const doc = await supporting()
    const f = await freshCourse(ctx, { lessons: [{ documents: [doc] }] }); const s = await learner(ctx)
    expectStatus(await s.get(doc.fileUrl), 200)
    ok(await setStatus(admin, f.course.id, 'ARCHIVED'), 'archive')
    assert.ok([403, 404].includes((await s.get(doc.fileUrl)).status), 'document of archived course still readable')
  })
  void gap; void na; void createCourse; void createChapter; void allocate
}
