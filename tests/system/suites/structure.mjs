// 4.3 Training structure, lessons, documents (F-C01..C10), 4.4 is in enrollment.mjs.
import { readFileSync } from 'node:fs'
import { tc, assert, expectStatus, expectRejected, sql, sqlNum, sqlOne, uniq, gap, na, multipart } from '../lib.mjs'
import { createCourse, createChapter, createLesson, freshCourse, learner, publish, setStatus, readAndComplete, allocate, ok } from '../seed.mjs'

const fixture = (name) => readFileSync(new URL(`../fixtures/${name}`, import.meta.url))
const MB = 1024 * 1024

export async function run(ctx) {
  const { admin, A, program } = ctx

  await tc('F-C01', 'tạo lĩnh vực → chương trình → khoá → chương → bài, đọc lại đúng', async () => {
    const code = uniq('DOM')
    const domain = ok(await admin.post('/api/admin/training-domains', { code, name: `Lĩnh vực ${code}`, status: 'ACTIVE' }), 'domain')
    const prog = ok(await admin.post('/api/admin/training-programs', { domainId: domain.id, code: uniq('PRG'), name: 'Chương trình', status: 'ACTIVE' }), 'program')
    const course = await createCourse(admin, prog.id)
    const chapter = await createChapter(admin, course.id, 'Chương A')
    const lesson = await createLesson(admin, course, chapter, { title: 'Bài A', textBody: '<p>Thân bài</p>' })
    const chapters = expectStatus(await admin.get(`/api/admin/courses/${course.id}/chapters`), 200).json
    assert.equal(chapters.length, 1); assert.equal(chapters[0].id, chapter.id)
    const byProgram = expectStatus(await admin.get(`/api/admin/programs/${prog.id}/courses`), 200).json
    assert.ok(byProgram.some((c) => c.id === course.id))
    assert.equal(sqlOne(`select chapter_id from lessons where id='${lesson.id}'`), chapter.id)
  })
  await tc('F-C01', 'sửa khoá/bài rồi đọc lại', async () => {
    const f = await freshCourse(ctx)
    const updated = ok(await admin.put(`/api/admin/lessons/${f.lesson.id}`, { title: 'Tên mới', textBody: '<p>Mới</p>' }), 'update')
    assert.equal(updated.title, 'Tên mới')
    assert.equal(sqlOne(`select title from lessons where id='${f.lesson.id}'`), 'Tên mới')
  })

  for (const [name, call] of [
    ['domain thiếu tên', () => admin.post('/api/admin/training-domains', { code: uniq('X') })],
    ['program sai domainId', () => admin.post('/api/admin/training-programs', { domainId: 'NOPE', code: uniq('X'), name: 'x' })],
    ['course sai programId', () => admin.post('/api/admin/courses', { id: uniq('X'), code: uniq('X'), name: 'x', programId: 'NOPE' })],
    ['course thiếu tên', () => admin.post('/api/admin/courses', { id: uniq('X'), code: uniq('X'), programId: program.id })],
    ['chapter sai course', () => admin.post('/api/admin/courses/NOPE/chapters', { code: uniq('X'), title: 'x' })],
    ['lesson thiếu tên', () => admin.post('/api/admin/lessons', { id: uniq('X'), chapterId: ctx.C1ch1.id })],
    ['lesson sai chapter', () => admin.post('/api/admin/lessons', { id: uniq('X'), title: 'x', chapterId: 'NOPE' })],
  ]) await tc('F-C02', name, async () => expectRejected(await call(), name) && undefined)
  await tc('F-C02', 'mã lĩnh vực trùng', async () => {
    const code = uniq('DUP')
    ok(await admin.post('/api/admin/training-domains', { code, name: code, status: 'ACTIVE' }), 'first')
    expectRejected(await admin.post('/api/admin/training-domains', { code, name: 'khác', status: 'ACTIVE' }))
  })
  await tc('F-C02', 'mã khoá trùng', async () => {
    const c = await createCourse(admin, program.id)
    const res = await admin.post('/api/admin/courses', { id: uniq('X'), code: c.code, name: 'trùng', programId: program.id })
    expectRejected(res)
  })
  await tc('F-C02', 'xoá lĩnh vực có chương trình con không tạo mồ côi', async () => {
    const d = ok(await admin.post('/api/admin/training-domains', { code: uniq('DEL'), name: 'Xoá', status: 'ACTIVE' }), 'domain')
    const p = ok(await admin.post('/api/admin/training-programs', { domainId: d.id, code: uniq('DELP'), name: 'Xoá', status: 'ACTIVE' }), 'program')
    const c = await createCourse(admin, p.id)
    expectStatus(await admin.del(`/api/admin/training-domains/${d.id}`), [200, 204])
    const states = sql(`select (select status from training_domains where id='${d.id}'), (select status from training_programs where id='${p.id}'), (select status from courses where id='${c.id}')`)
    assert.ok(/^(DELETED|ARCHIVED)\|(DELETED|ARCHIVED)\|(DELETED|ARCHIVED)$/.test(states), `orphan after domain delete: ${states}`)
    return `Xoá mềm cả cây (${states}), API không báo ràng buộc`
  })
  await tc('F-C02', 'xoá chương trình có khoá con không tạo mồ côi', async () => {
    const p = ok(await admin.post('/api/admin/training-programs', { domainId: ctx.domain.id, code: uniq('DELP'), name: 'Xoá', status: 'ACTIVE' }), 'program')
    const c = await createCourse(admin, p.id)
    expectStatus(await admin.del(`/api/admin/training-programs/${p.id}`), [200, 204])
    const states = sql(`select (select status from training_programs where id='${p.id}'), (select status from courses where id='${c.id}')`)
    assert.ok(/^(DELETED|ARCHIVED)\|(DELETED|ARCHIVED)$/.test(states), `orphan after program delete: ${states}`)
  })

  const ordered = await freshCourse(ctx, { lessons: [{}, {}, {}] })
  const ids = ordered.lessons.map((l) => l.id)
  await tc('F-C03', 'đổi thứ tự bài hợp lệ, tồn tại sau reload', async () => {
    const order = [ids[2], ids[0], ids[1]]
    expectStatus(await admin.put(`/api/admin/chapters/${ordered.chapter.id}/lessons/order`, order), [200, 204])
    const rows = sqlOne(`select string_agg(id, ',' order by lesson_order) from lessons where chapter_id='${ordered.chapter.id}'`)
    assert.equal(rows, order.join(','))
  })
  for (const [name, list] of [
    ['ID trùng', [ids[0], ids[0], ids[1]]], ['thiếu ID', [ids[0], ids[1]]], ['ID khoá khác', [ids[0], ids[1], ctx.C1L1.id]],
  ]) {
    await tc('F-C03', `order bài: ${name}`, async () => {
      const before = sqlOne(`select string_agg(id, ',' order by lesson_order) from lessons where chapter_id='${ordered.chapter.id}'`)
      expectRejected(await admin.put(`/api/admin/chapters/${ordered.chapter.id}/lessons/order`, list), name)
      assert.equal(sqlOne(`select string_agg(id, ',' order by lesson_order) from lessons where chapter_id='${ordered.chapter.id}'`), before)
      assert.equal(sqlOne(`select chapter_id from lessons where id='${ctx.C1L1.id}'`), ctx.C1ch1.id, 'lesson moved across courses')
    })
    await tc('F-C03', `order bài cấp khoá: ${name}`, async () => {
      expectRejected(await admin.put(`/api/admin/courses/${ordered.course.id}/lessons/order`, list), name)
    })
  }
  await tc('F-C03', 'đổi thứ tự chương hợp lệ và không hợp lệ', async () => {
    const c = await createCourse(admin, program.id)
    const [c1, c2] = [await createChapter(admin, c.id, 'Một'), await createChapter(admin, c.id, 'Hai')]
    expectStatus(await admin.put(`/api/admin/courses/${c.id}/chapters/order`, [c2.id, c1.id]), [200, 204])
    assert.equal(sqlOne(`select string_agg(id, ',' order by chapter_order) from chapters where course_id='${c.id}'`), `${c2.id},${c1.id}`)
    expectRejected(await admin.put(`/api/admin/courses/${c.id}/chapters/order`, [c2.id]), 'missing')
    expectRejected(await admin.put(`/api/admin/courses/${c.id}/chapters/order`, [c2.id, ctx.C1ch1.id]), 'foreign')
  })

  const student = await learner(ctx)
  await tc('F-C04', 'nháp: học viên không mở được qua API', async () => {
    const res = await student.get(`/api/lessons/${ctx.C2.lesson.id}`)
    assert.ok([403, 404].includes(res.status), `draft lesson returned ${res.status}: ${res.text.slice(0, 160)}`)
    assert.ok([403, 404].includes((await student.get(`/api/courses/${ctx.C2.course.id}/outline`)).status))
  })
  await tc('F-C04', 'xuất bản: học viên mở được', async () => {
    const f = await freshCourse(ctx)
    expectStatus(await student.get(`/api/lessons/${f.lesson.id}`), 200)
    ctx.pubCourse = f
  })
  await tc('F-C04', 'lưu trữ: chặn bài + tiến độ qua URL/API', async () => {
    const f = await freshCourse(ctx)
    const s2 = await learner(ctx)
    expectStatus(await s2.get(`/api/lessons/${f.lesson.id}`), 200)
    ok(await setStatus(admin, f.course.id, 'ARCHIVED'), 'archive')
    assert.ok([403, 404].includes((await s2.get(`/api/lessons/${f.lesson.id}`)).status), 'archived lesson readable')
    expectRejected(await s2.post(`/api/users/me/lessons/${f.lesson.id}/progress`, { contentType: 'TEXT', textCompleted: true }), 'progress')
    expectRejected(await s2.post(`/api/users/me/complete-lesson/${f.lesson.id}`), 'complete')
  })

  for (const [name, body] of [
    ['bài rỗng', {}], ['HTML chỉ whitespace', { textBody: '<p>  &nbsp; </p>' }],
    ['chỉ file phụ trợ', { textBody: null, documents: [{ name: 'phụ trợ', documentType: 'PDF', fileUrl: '/uploads/documents/none.pdf', purpose: 'SUPPORTING' }] }],
  ]) {
    await tc('F-C05', `publish ${name} bị chặn`, async () => {
      const c = await createCourse(admin, program.id)
      const ch = await createChapter(admin, c.id)
      const upload = name.includes('phụ trợ') ? ok(await admin.post('/api/admin/documents/upload', undefined, { form: multipart({}, [{ name: 'a.pdf', data: '%PDF-1.4 x' }]) }), 'upload') : null
      const payload = upload ? { ...body, documents: [{ ...upload, purpose: 'SUPPORTING' }] } : { textBody: body.textBody }
      await createLesson(admin, c, ch, payload)
      expectRejected(await setStatus(admin, c.id, 'PUBLISHED'), name)
      assert.notEqual(sqlOne(`select status from courses where id='${c.id}'`), 'PUBLISHED')
    })
  }
  await tc('F-C04', 'phân bổ khoá nháp tự chuyển sang PUBLISHED', async () => {
    const f = await freshCourse(ctx, { publishIt: false, unit: null })
    assert.equal(sqlOne(`select status from courses where id='${f.course.id}'`), 'DRAFT')
    await allocate(admin, A.id, program.id, f.course.id, 'OPTIONAL')
    const after = sqlOne(`select status from courses where id='${f.course.id}'`)
    if (after === 'PUBLISHED') return gap('Phân bổ một khoá DRAFT tự xuất bản khoá và bài (ensureCourseLearnableWhenAllocated). Cần chốt: kịch bản coi nháp không được học.')
  })
  await tc('F-C05', 'thêm text thì publish được', async () => {
    const c = await createCourse(admin, program.id)
    const ch = await createChapter(admin, c.id)
    const l = await createLesson(admin, c, ch, {})
    expectRejected(await setStatus(admin, c.id, 'PUBLISHED'))
    ok(await admin.put(`/api/admin/lessons/${l.id}`, { title: l.title, textBody: '<p>Có nội dung</p>' }), 'add text')
    await publish(admin, c.id)
  })
  await tc('F-C05', 'bài đã xuất bản không sửa thành rỗng', async () => {
    const f = await freshCourse(ctx)
    expectRejected(await admin.put(`/api/admin/lessons/${f.lesson.id}`, { title: 'x', textBody: '<p> </p>' }))
  })

  await tc('F-C06', 'đếm người đã hoàn thành, gửi changelog, thông báo đúng đối tượng', async () => {
    const f = await freshCourse(ctx)
    const done = await learner(ctx); const other = await learner(ctx)
    expectStatus(await readAndComplete(done, f.lesson.id), 200)
    const count = expectStatus(await admin.get(`/api/admin/lessons/${f.lesson.id}/completed-learners-count`), 200).json
    assert.equal(count.count ?? count.completedLearners ?? Object.values(count)[0], 1, `count=${JSON.stringify(count)}`)
    const notify = expectStatus(await admin.post(`/api/admin/lessons/${f.lesson.id}/changelog-notify`, { changeSummary: 'Đã cập nhật nội dung', notifyLearners: true }), 200)
    const log = expectStatus(await done.get(`/api/lessons/${f.lesson.id}/latest-changelog`), [200, 204])
    assert.ok(log.text.includes('Đã cập nhật'), `changelog: ${log.text.slice(0, 120)}, notify: ${notify.text.slice(0, 120)}`)
    const n1 = expectStatus(await done.get('/api/notifications?size=20'), 200).text
    const n2 = expectStatus(await other.get('/api/notifications?size=20'), 200).text
    assert.ok(n1.includes(f.lesson.id) || n1.includes('Đã cập nhật'), `completed learner not notified, notifiedCount=${notify.json?.notifiedCount}: ${n1.slice(0, 200)}`)
    assert.ok(!n2.includes(f.lesson.id) && !n2.includes('Đã cập nhật'), 'learner who has not completed was notified')
  })

  // F-C07 uploads, the test stack caps uploads at 20 MB (M).
  const upload = (path, name, data, type) => admin.post(path, undefined, { form: multipart({}, [{ name, data, type }]) })
  await tc('F-C07', 'PDF/DOCX/PPTX hợp lệ', async () => {
    const pdf = Buffer.from('%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF')
    for (const [name, data] of [['a.pdf', pdf], ['b.docx', Buffer.from('PK\x03\x04docx')], ['c.pptx', Buffer.from('PK\x03\x04pptx')]]) {
      const dto = expectStatus(await upload('/api/admin/documents/upload', name, data), 200).json
      expectStatus(await admin.get(dto.fileUrl), 200, `read ${name}`)
    }
  })
  await tc('F-C07', 'tên tiếng Việt và ký tự đặc biệt', async () => {
    const dto = expectStatus(await upload('/api/admin/documents/upload', "Tài liệu (bản #1) & 'x'.pdf", Buffer.from('%PDF-1.4 x')), 200).json
    assert.ok(/^\/uploads\/documents\/[0-9a-f-]+\.pdf$/.test(dto.fileUrl), `stored path ${dto.fileUrl}`)
    assert.ok(dto.originalFileName.includes('Tài liệu'))
  })
  await tc('F-C07', 'file rỗng bị chặn', async () => expectRejected(await upload('/api/admin/documents/upload', 'empty.pdf', Buffer.alloc(0))) && undefined)
  await tc('F-C07', 'sai định dạng .exe bị chặn', async () => expectRejected(await upload('/api/admin/documents/upload', 'x.exe', Buffer.from('MZ'))) && undefined)
  await tc('F-C07', 'PDF hỏng/giả extension bị chặn', async () => {
    const res = await upload('/api/admin/documents/upload', 'fake.pdf', Buffer.from('MZ\x90\x00 this is an executable'), 'application/pdf')
    assert.ok(res.status >= 400, `corrupt/fake PDF accepted with HTTP ${res.status}`)
  })
  await tc('F-C07', 'PPTX không phải zip bị chặn khi làm slide', async () => {
    const res = await upload('/api/admin/documents/upload', 'fake.pptx', Buffer.from('not a zip at all'))
    assert.ok(res.status >= 400, `fake PPTX accepted with HTTP ${res.status}`)
  })
  for (const [name, size, accept] of [['M-1', 20 * MB - 1024, true], ['M', 20 * MB, true], ['M+1', 20 * MB + 1024, false]]) {
    await tc('F-C07', `tài liệu ${name}`, async () => {
      const res = await upload('/api/admin/documents/upload', 'big.pdf', Buffer.alloc(size, 0x25))
      if (accept) expectStatus(res, 200)
      else assert.ok(res.status === 413 || res.status === 400, `oversize returned ${res.status}: ${res.text.slice(0, 120)}`)
    })
  }
  await tc('F-C07', 'video mp4 hợp lệ', async () => {
    const dto = expectStatus(await upload('/api/admin/videos/upload', 'v.mp4', fixture('v100.mp4'), 'video/mp4'), 200).json
    assert.match(dto.url ?? dto.videoUrl ?? JSON.stringify(dto), /uploads\/videos\//)
    ctx.videoDto = dto
  })
  await tc('F-C07', 'video giả (không phải media) bị chặn', async () => {
    const res = await upload('/api/admin/videos/upload', 'fake.mp4', Buffer.from('not a video'), 'video/mp4')
    assert.ok(res.status >= 400 && res.status < 500, `fake video returned ${res.status}`)
  })
  await tc('F-C07', 'ảnh khoá hợp lệ và file giả ảnh', async () => {
    const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==', 'base64')
    const imagePath = `/api/admin/courses/images?programId=${program.id}`
    const dto = await upload(imagePath, 'a.png', png, 'image/png')
    expectStatus(dto, 200)
    const fake = await upload(imagePath, 'b.png', Buffer.from('not image'), 'image/png')
    assert.ok(fake.status >= 400 && fake.status < 500, `fake image returned ${fake.status}`)
  })
  await tc('F-C07', 'bài không được trỏ tới file không tồn tại', async () => {
    const f = await freshCourse(ctx, { publishIt: false, unit: null })
    const res = await admin.put(`/api/admin/lessons/${f.lesson.id}`, { title: 'x', textBody: '<p>x</p>', documents: [{ name: 'ma', documentType: 'PDF', fileUrl: '/uploads/documents/does-not-exist.pdf', purpose: 'SUPPORTING' }] })
    assert.ok(res.status >= 400, `lesson saved with a dangling file URL (HTTP ${res.status})`)
  })

  await tc('F-C08', 'nguồn tri thức ngoài khoá không gắn được', async () => {
    const f = await freshCourse(ctx)
    const res = await admin.put(`/api/admin/lessons/${f.lesson.id}/knowledge-sources`, [{ documentId: 'NOPE' }])
    assert.ok(res.status >= 400 && res.status < 500, `foreign source returned ${res.status}: ${res.text.slice(0, 140)}`)
  })
  await tc('F-C10', 'màn hình nhập nhanh', async () => na('Không có endpoint nhập nhanh riêng; backend chỉ có API CRUD từng cấp, UI gọi tuần tự.'))
  void A; void gap
}
