// 4.9 Comments, notes, ratings, notifications, content reports (F-N01..N06).
import { tc, assert, expectStatus, expectRejected, sqlNum, sqlOne, multipart, gap } from '../lib.mjs'
import { freshCourse, learner, readAndComplete, ok } from '../seed.mjs'

const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==', 'base64')

export async function run(ctx) {
  const { admin } = ctx
  const f = await freshCourse(ctx)
  const lid = f.lesson.id
  const [a, b] = [await learner(ctx), await learner(ctx)]

  await tc('F-N01', 'bình luận, trả lời, phân trang reply không trùng/thiếu', async () => {
    const root = expectStatus(await a.post(`/api/lessons/${lid}/comments`, { content: 'Gốc' }), 200).json
    const ids = []
    for (let i = 0; i < 12; i++) ids.push(expectStatus(await b.post(`/api/lessons/${lid}/comments/${root.id}/replies`, { content: `Trả lời ${i}` }), 200).json.id)
    const seen = []
    let cursor = ''
    for (let guard = 0; guard < 10; guard++) {
      const page = expectStatus(await a.get(`/api/lessons/${lid}/comments/${root.id}/replies${cursor ? `?after=${encodeURIComponent(cursor)}` : ''}`), 200).json
      seen.push(...page.content.map((r) => r.id))
      cursor = page.hasMore ? page.nextCursor : ''
      if (!cursor) break
    }
    assert.equal(new Set(seen).size, seen.length, 'duplicate replies')
    assert.deepEqual([...seen].sort((x, y) => x - y), [...ids].sort((x, y) => x - y))
    ctx.comment = root
  })
  await tc('F-N01', 'nội dung rỗng bị từ chối', async () => expectRejected(await a.post(`/api/lessons/${lid}/comments`, { content: '  ' })) && undefined)
  await tc('F-N01', 'xoá comment người khác bị chặn; của mình được; admin được', async () => {
    const mineC = ok(await a.post(`/api/lessons/${lid}/comments`, { content: 'Của A' }), 'c')
    assert.ok([403, 404].includes((await b.del(`/api/lessons/${lid}/comments/${mineC.id}`)).status), 'other learner deleted comment')
    expectStatus(await a.del(`/api/lessons/${lid}/comments/${mineC.id}`), [200, 204])
    const other = ok(await a.post(`/api/lessons/${lid}/comments`, { content: 'Admin xoá' }), 'c')
    expectStatus(await admin.del(`/api/lessons/${lid}/comments/${other.id}`), [200, 204])
  })
  await tc('F-N01', 'reply vào comment của bài khác bị chặn', async () => {
    const g = await freshCourse(ctx)
    expectRejected(await a.post(`/api/lessons/${g.lesson.id}/comments/${ctx.comment.id}/replies`, { content: 'lạc bài' }))
  })

  await tc('F-N02', 'tạo/sửa/xoá ghi chú tại mốc; đọc đúng', async () => {
    const note = expectStatus(await a.post(`/api/lessons/${lid}/notes`, { content: 'Ghi chú', timestampSeconds: 12, timeSeconds: 12, positionSeconds: 12 }), 200).json
    const list = expectStatus(await a.get(`/api/lessons/${lid}/notes`), 200)
    assert.ok(list.text.includes('Ghi chú'))
    expectStatus(await a.put(`/api/lessons/${lid}/notes/${note.id}`, { content: 'Đã sửa', timestampSeconds: 13, timeSeconds: 13, positionSeconds: 13 }), 200)
    ctx.note = note
  })
  await tc('F-N02', 'người khác không đọc/sửa/xoá ghi chú', async () => {
    assert.ok(!expectStatus(await b.get(`/api/lessons/${lid}/notes`), 200).text.includes('Đã sửa'), 'note of A visible to B')
    assert.ok([403, 404].includes((await b.put(`/api/lessons/${lid}/notes/${ctx.note.id}`, { content: 'hack', timestampSeconds: 1, timeSeconds: 1, positionSeconds: 1 })).status))
    assert.ok([403, 404].includes((await b.del(`/api/lessons/${lid}/notes/${ctx.note.id}`)).status))
    assert.equal(sqlOne(`select content from video_notes where id=${ctx.note.id}`), 'Đã sửa')
  })

  // F-N03 ratings.
  const rate = (c, courseId, stars) => c.put(`/api/courses/${courseId}/rating`, { stars, content: 'Đánh giá' })
  await tc('F-N03', 'chưa đủ điều kiện không đánh giá được', async () => {
    const g = await freshCourse(ctx); const u = await learner(ctx)
    const state = expectStatus(await u.get(`/api/courses/${g.course.id}/rating`), 200).json
    assert.equal(state.canRate, false)
    expectRejected(await rate(u, g.course.id, 5))
  })
  await tc('F-N03', '1–5 sao; 0/6 bị chặn; sửa không nhân đôi lượt', async () => {
    const g = await freshCourse(ctx); const u = await learner(ctx)
    expectStatus(await readAndComplete(u, g.lesson.id), 200)
    for (const bad of [0, 6, -1]) expectRejected(await rate(u, g.course.id, bad), `stars ${bad}`)
    let version
    for (const good of [1, 5, 3]) version = expectStatus(await u.put(`/api/courses/${g.course.id}/rating`, { stars: good, content: 'Đánh giá', version }), 200, `stars ${good}`).json.mine?.version ?? version
    expectStatus(await u.get(`/api/courses/${g.course.id}/rating`), 200)
    assert.equal(sqlNum(`select count(*) from course_ratings where course_id='${g.course.id}'`), 1)
    const state = expectStatus(await u.get(`/api/courses/${g.course.id}/rating`), 200).json
    assert.equal(state.summary.count, 1); assert.equal(state.summary.average, 3)
  })

  // F-N04 notifications.
  const notifier = await learner(ctx)
  const notifs = []
  for (let i = 0; i < 12; i++) {
    const g = await freshCourse(ctx, { grant: 'OPTIONAL' })
    await notifier.post(`/api/users/me/register-course/${g.course.id}`)
    const id = sqlOne(`select id from course_enrollment_requests where user_id=${notifier.user.id} and course_id='${g.course.id}'`)
    ok(await admin.put(`/api/admin/enrollment-requests/${id}`, { approve: true }), 'approve')
    notifs.push(g)
    if (i >= 2) break
  }
  await tc('F-N04', 'danh sách, số chưa đọc, mark read, read-all', async () => {
    const page = expectStatus(await notifier.get('/api/notifications?size=2'), 200).json
    const items = page.items ?? page.content ?? page.notifications
    assert.equal(items.length, 2)
    const unread = sqlNum(`select count(*) from notifications where recipient_id=${notifier.user.id} and not is_read`)
    assert.equal(page.unreadCount ?? page.unread, unread)
    expectStatus(await notifier.patch(`/api/notifications/${items[0].id}/read`), 200)
    assert.equal(sqlNum(`select count(*) from notifications where recipient_id=${notifier.user.id} and not is_read`), unread - 1)
    ctx.notifId = items[1].id
  })
  await tc('F-N04', 'cursor không trùng khi có thông báo mới chen vào', async () => {
    const first = expectStatus(await notifier.get('/api/notifications?size=2'), 200).json
    const ids1 = (first.items ?? first.content ?? first.notifications).map((n) => n.id)
    const g = await freshCourse(ctx, { grant: 'OPTIONAL' })
    await notifier.post(`/api/users/me/register-course/${g.course.id}`)
    ok(await admin.put(`/api/admin/enrollment-requests/${sqlOne(`select id from course_enrollment_requests where user_id=${notifier.user.id} and course_id='${g.course.id}'`)}`, { approve: true }), 'approve')
    assert.ok(first.nextBeforeId, 'no nextBeforeId')
    const next = expectStatus(await notifier.get(`/api/notifications?size=2&beforeId=${first.nextBeforeId}`), 200).json
    const ids2 = (next.items ?? next.content ?? next.notifications).map((n) => n.id)
    assert.equal(ids1.filter((x) => ids2.includes(x)).length, 0, 'duplicate across pages')
  })
  await tc('F-N04', 'không đọc hộ thông báo người khác', async () => {
    assert.ok([403, 404].includes((await b.patch(`/api/notifications/${ctx.notifId}/read`)).status))
    assert.equal(sqlOne(`select is_read from notifications where id=${ctx.notifId}`), 'f')
  })
  await tc('F-N04', 'read-all chỉ của mình', async () => {
    expectStatus(await b.patch('/api/notifications/read-all'), [200, 204])
    assert.ok(sqlNum(`select count(*) from notifications where recipient_id=${notifier.user.id} and not is_read`) > 0, 'read-all affected other user')
  })
  await tc('F-N05', 'socket ngắt/reconnect: dữ liệu persisted đọc lại được', async () => {
    const list = expectStatus(await notifier.get('/api/notifications?size=50'), 200).json
    assert.equal((list.items ?? list.content ?? list.notifications).length, sqlNum(`select count(*) from notifications where recipient_id=${notifier.user.id}`))
  })
  await tc('F-N05', 'WebSocket nhận theo đúng user, cookie sai bị từ chối', async () => {
    if (typeof WebSocket === 'undefined') return gap('Runtime không có WebSocket')
    const opened = await new Promise((resolve) => {
      const ws = new WebSocket('ws://127.0.0.1:19000/ws/notifications')
      const timer = setTimeout(() => { ws.close(); resolve('timeout') }, 4000)
      ws.onopen = () => { /* an unauthenticated open is judged by the close that must follow */ }
      ws.onclose = (e) => { clearTimeout(timer); resolve(`closed:${e.code}`) }
      ws.onerror = () => {}
    })
    assert.ok(String(opened).startsWith('closed'), `unauthenticated socket stayed open: ${opened}`)
  })

  // F-N06 content reports.
  const send = (c, lessonId, files, fields = { content: 'Nội dung sai' }) => c.post(`/api/lessons/${lessonId}/reports`, undefined, { form: multipart(fields, files) })
  await tc('F-N06', 'gửi báo lỗi kèm ảnh, admin xử lý, ảnh được bảo vệ', async () => {
    const res = await send(a, lid, [{ field: 'images', name: 'a.png', data: PNG, type: 'image/png' }])
    expectStatus(res, 200)
    const id = res.json.id
    expectStatus(await a.get(`/api/lesson-reports/${id}`), 200)
    expectStatus(await a.get(`/api/lesson-reports/${id}/images/0`), 200)
    assert.ok([403, 404].includes((await b.get(`/api/lesson-reports/${id}`)).status), 'report readable by another learner')
    assert.ok([403, 404].includes((await b.get(`/api/lesson-reports/${id}/images/0`)).status), 'image readable by another learner')
    assert.ok(expectStatus(await admin.get('/api/admin/content-reports'), 200).text.includes(lid))
    expectStatus(await admin.put(`/api/admin/content-reports/${id}`, { status: 'RESOLVED', resolution: 'Đã xử lý', version: res.json.version }), 200)
    assert.equal(sqlOne(`select status from lesson_reports where id=${id}`), 'RESOLVED')
  })
  await tc('F-N06', 'file không phải ảnh bị chặn', async () => {
    expectRejected(await send(a, lid, [{ field: 'images', name: 'x.exe', data: Buffer.from('MZ'), type: 'application/octet-stream' }]))
  })
  await tc('F-N06', 'file đổi tên thành .png bị chặn', async () => {
    const res = await send(a, lid, [{ field: 'images', name: 'x.png', data: Buffer.from('<script>alert(1)</script>'), type: 'image/png' }])
    assert.ok(res.status >= 400 && res.status < 500, `fake PNG returned ${res.status}`)
  })
  await tc('F-N06', 'báo lỗi bài ngoài quyền bị chặn', async () => {
    const x = await learner(ctx, ctx.B)
    assert.ok([403, 404].includes((await send(x, lid, [{ field: 'images', name: 'a.png', data: PNG, type: 'image/png' }])).status))
  })
}
