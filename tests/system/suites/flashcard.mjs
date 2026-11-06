// 4.8 Flashcards and study plans (F-F01..F12). The reminder job runs every 60s; next_reminder_at is
// moved on the isolated DB so a due plan fires on the next tick.
import { tc, assert, expectStatus, expectRejected, sql, sqlNum, sqlOne, sleep, gap, na } from '../lib.mjs'
import { freshCourse, learner, ok, readAndComplete, updateUser } from '../seed.mjs'

const today = (d = 0) => new Date(Date.now() + 7 * 3600e3 + d * 86400e3).toISOString().slice(0, 10)
const adminCards = (lessonId) => `/api/admin/lessons/${lessonId}/flashcards`
const plan = (over) => ({ name: 'Lịch', startDate: today(), endDate: today(30), weekdays: [1, 2, 3, 4, 5, 6, 7], reminderTime: '08:00', active: true, ...over })
const mine = (c, lessonId, front = 'Mặt trước') => c.post(`/api/lessons/${lessonId}/flashcards/mine`, { type: 'TERM', front, back: 'Mặt sau' })
const notifCount = (c, type = 'FLASHCARD%') => sqlNum(`select count(*) from notifications where recipient_id=${c.user.id} and type like '${type}'`)

async function waitFor(check, ms = 75000) {
  const end = Date.now() + ms
  while (Date.now() < end) { if (check()) return true; await sleep(2000) }
  return false
}

export async function run(ctx) {
  const { admin, B } = ctx
  const f = await freshCourse(ctx, { lessons: [{}, {}] })
  const lesson = f.lessons[0]; const second = f.lessons[1]
  const s = await learner(ctx)

  await tc('F-F01', 'admin tạo thẻ nháp: học viên không thấy; publish: thấy', async () => {
    const card = expectStatus(await admin.post(adminCards(lesson.id), { type: 'TERM', front: 'Thẻ chung', back: 'Giải thích', status: 'DRAFT' }), 200).json
    assert.ok(!expectStatus(await s.get(`/api/lessons/${lesson.id}/flashcards`), 200).text.includes('Thẻ chung'), 'draft card visible')
    expectStatus(await admin.put(`${adminCards(lesson.id)}/status`, { ids: [card.id], status: 'PUBLISHED' }), 200)
    assert.ok(expectStatus(await s.get(`/api/lessons/${lesson.id}/flashcards`), 200).text.includes('Thẻ chung'))
    ctx.sharedCard = card
  })
  await tc('F-F01', 'batch tạo + publish/draft, sửa, xoá', async () => {
    const cards = expectStatus(await admin.post(`${adminCards(lesson.id)}/batch`, { cards: [{ type: 'TERM', front: 'B1', back: 'x' }, { type: 'CLOZE', front: 'Điền ___ vào', back: 'chỗ' }] }), 200).json
    expectStatus(await admin.put(`${adminCards(lesson.id)}/status`, { ids: cards.map((c) => c.id), status: 'PUBLISHED' }), 200)
    const up = expectStatus(await admin.put(`${adminCards(lesson.id)}/${cards[0].id}`, { type: 'TERM', front: 'B1 sửa', back: 'x', version: cards[0].version + 1 }), [200, 409])
    expectStatus(await admin.del(`${adminCards(lesson.id)}/${cards[1].id}`), [200, 204])
    void up
  })
  await tc('F-F01', 'học viên chưa mở bài không lấy được thẻ', async () => {
    assert.ok([403, 404].includes((await s.get(`/api/lessons/${second.id}/flashcards`)).status), 'cards of locked lesson readable')
  })

  await tc('F-F02', 'tạo/sửa/xoá thẻ riêng; người khác 404', async () => {
    const other = await learner(ctx)
    const card = expectStatus(await mine(s, lesson.id, 'Riêng tôi'), 200).json
    assert.equal(card.mine, true)
    assert.ok(!expectStatus(await other.get(`/api/lessons/${lesson.id}/flashcards`), 200).text.includes('Riêng tôi'), 'personal card visible to other learner')
    assert.equal((await other.put(`/api/flashcards/mine/${card.id}`, { type: 'TERM', front: 'hack', back: 'x', version: card.version })).status, 404)
    assert.equal((await other.del(`/api/flashcards/mine/${card.id}`)).status, 404)
    const up = expectStatus(await s.put(`/api/flashcards/mine/${card.id}`, { type: 'TERM', front: 'Riêng đã sửa', back: 'x', version: card.version }), 200).json
    expectStatus(await s.del(`/api/flashcards/mine/${up.id}`), [200, 204])
  })
  await tc('F-F02', 'công cụ admin không lộ/sửa thẻ cá nhân', async () => {
    const card = expectStatus(await mine(s, lesson.id, 'Cá nhân kín'), 200).json
    assert.ok(!expectStatus(await admin.get(adminCards(lesson.id)), 200).text.includes('Cá nhân kín'), 'admin list shows personal card')
    assert.ok([403, 404].includes((await admin.put(`${adminCards(lesson.id)}/${card.id}`, { type: 'TERM', front: 'hack', back: 'x', version: card.version })).status))
    assert.ok([403, 404].includes((await admin.del(`${adminCards(lesson.id)}/${card.id}`)).status))
    assert.equal(sqlOne(`select front from flashcards where id=${card.id}`), 'Cá nhân kín')
  })

  await tc('F-F03', 'thẻ cá nhân 199/200/201', async () => {
    const u = await learner(ctx)
    for (let i = 0; i < 199; i++) expectStatus(await mine(u, lesson.id, `T${i}`), 200)
    expectStatus(await mine(u, lesson.id, 'T199'), 200, 'thẻ 200')
    expectRejected(await mine(u, lesson.id, 'T200'), 'thẻ 201')
    assert.equal(sqlNum(`select count(*) from flashcards where owner_user_id=${u.user.id}`), 200)
  })
  await tc('F-F03', 'không giả origin PERSONAL qua admin', async () => {
    expectRejected(await admin.post(adminCards(lesson.id), { type: 'TERM', front: 'x', back: 'y', origin: 'PERSONAL' }))
  })

  await tc('F-F04', 'version cũ không ghi đè', async () => {
    const card = expectStatus(await mine(s, lesson.id, 'Khoá lạc quan'), 200).json
    expectStatus(await s.put(`/api/flashcards/mine/${card.id}`, { type: 'TERM', front: 'Tab 1', back: 'x', version: card.version }), 200)
    assert.equal((await s.put(`/api/flashcards/mine/${card.id}`, { type: 'TERM', front: 'Tab 2', back: 'x', version: card.version })).status, 409)
    assert.equal(sqlOne(`select front from flashcards where id=${card.id}`), 'Tab 1')
  })
  await tc('F-F05', 'lật/phím tắt/shuffle', async () => gap('Thao tác thuần UI; xem phần kiểm tra trình duyệt trong báo cáo.'))

  await tc('F-F06', 'thư viện chỉ có khoá được học; không tăng completion', async () => {
    let text = ''; let after = ''
    for (let guard = 0; guard < 50; guard++) {
      const page = expectStatus(await s.get(`/api/users/me/flashcards/library${after ? `?after=${encodeURIComponent(after)}` : ''}`), 200)
      text += page.text
      after = page.json.nextCursor
      if (!after) break
    }
    assert.ok(text.includes(f.course.id), 'own course missing across pages'); assert.ok(!text.includes(ctx.C4.course.id), 'course of B in library')
    assert.equal(sqlNum(`select count(*) from user_completed_lessons where user_id=${s.user.id}`), 0)
    assert.equal(sqlNum(`select points from users where id=${s.user.id}`), 0, 'browsing cards granted points')
  })

  // F-F07 / F-F08 plans.
  await tc('F-F07', 'tạo, sửa, pause, resume, xoá', async () => {
    const p = expectStatus(await s.post('/api/users/me/flashcard-plans', plan({ items: [{ lessonId: lesson.id, cardIds: null }] })), 200).json
    assert.ok(p.nextReminderAt, 'no next reminder')
    const paused = expectStatus(await s.put(`/api/users/me/flashcard-plans/${p.id}`, plan({ active: false, version: p.version })), 200).json
    assert.equal(paused.nextReminderAt ?? null, null, 'paused plan keeps next reminder')
    const resumed = expectStatus(await s.put(`/api/users/me/flashcard-plans/${p.id}`, plan({ active: true, version: paused.version })), 200).json
    assert.ok(resumed.nextReminderAt)
    const off = expectStatus(await s.put(`/api/users/me/flashcard-plans/${p.id}`, plan({ reminderTime: null, version: resumed.version })), 200).json
    assert.equal(off.nextReminderAt ?? null, null, 'plan without reminder still scheduled')
    expectStatus(await s.del(`/api/users/me/flashcard-plans/${p.id}`), [200, 204])
    assert.equal((await s.get(`/api/users/me/flashcard-plans/${p.id}/cards`)).status, 404)
  })
  await tc('F-F07', 'lịch 1 ngày', async () => {
    expectStatus(await s.post('/api/users/me/flashcard-plans', plan({ startDate: today(1), endDate: today(1), weekdays: null, items: [{ lessonId: lesson.id }] })), 200)
  })
  await tc('F-F08', 'lịch thứ 20 được, thứ 21 bị chặn', async () => {
    const u = await learner(ctx)
    for (let i = 0; i < 20; i++) expectStatus(await u.post('/api/users/me/flashcard-plans', plan({ name: `P${i}`, items: [{ lessonId: lesson.id }] })), 200, `plan ${i + 1}`)
    expectRejected(await u.post('/api/users/me/flashcard-plans', plan({ name: 'P21', items: [{ lessonId: lesson.id }] })))
  })
  for (const [name, over, accept] of [
    ['khoảng 366 ngày', { endDate: today(365) }, true], ['chênh 367 ngày', { endDate: today(367) }, false],
    ['cuối trước đầu', { startDate: today(5), endDate: today(1) }, false], ['thứ ngoài 1-7', { weekdays: [8] }, false],
    ['không có ngày phù hợp', { startDate: '2026-12-07', endDate: '2026-12-08', weekdays: [5] }, false], ['tên rỗng', { name: ' ' }, false],
  ]) {
    await tc('F-F08', name, async () => {
      const u = await learner(ctx)
      const res = await u.post('/api/users/me/flashcard-plans', plan({ items: [{ lessonId: lesson.id }], ...over }))
      if (accept) expectStatus(res, 200); else expectRejected(res)
    })
  }

  await tc('F-F08', 'chênh 366 ngày (367 ngày tính cả hai đầu)', async () => {
    const u = await learner(ctx)
    const res = await u.post('/api/users/me/flashcard-plans', plan({ items: [{ lessonId: lesson.id }], endDate: today(366) }))
    if (res.status === 200) return gap('Từ ngày đầu đến ngày cuối chênh 366 ngày (367 ngày lịch tính cả hai đầu) vẫn được chấp nhận. Cần chốt cách đếm của "tối đa 366 ngày".')
  })

  // F-F09 selection semantics.
  await tc('F-F09', 'cả bài có thẻ mới; cố định giữ tập ID; thẻ xoá bỏ qua an toàn', async () => {
    const u = await learner(ctx)
    const c1 = expectStatus(await mine(u, lesson.id, 'Một'), 200).json
    const c2 = expectStatus(await mine(u, lesson.id, 'Hai'), 200).json
    const whole = expectStatus(await u.post('/api/users/me/flashcard-plans', plan({ name: 'Cả bài', items: [{ lessonId: lesson.id, cardIds: null }] })), 200).json
    const fixed = expectStatus(await u.post('/api/users/me/flashcard-plans', plan({ name: 'Cố định', items: [{ lessonId: lesson.id, cardIds: [c1.id, c2.id] }] })), 200).json
    const c3 = expectStatus(await mine(u, lesson.id, 'Ba'), 200).json
    const wholeCards = expectStatus(await u.get(`/api/users/me/flashcard-plans/${whole.id}/cards`), 200).json.cards.map((c) => c.id)
    const fixedCards = expectStatus(await u.get(`/api/users/me/flashcard-plans/${fixed.id}/cards`), 200).json.cards.map((c) => c.id)
    assert.ok(wholeCards.includes(c3.id), 'whole-lesson plan missed new card')
    assert.ok(!fixedCards.includes(c3.id), 'fixed plan picked up new card')
    expectStatus(await u.del(`/api/flashcards/mine/${c1.id}`), [200, 204])
    const after = expectStatus(await u.get(`/api/users/me/flashcard-plans/${fixed.id}/cards`), 200).json.cards.map((c) => c.id)
    assert.deepEqual(after, [c2.id])
  })

  // F-F10 access loss.
  await tc('F-F10', 'plan người khác 404', async () => {
    const [a, b] = [await learner(ctx), await learner(ctx)]
    const p = expectStatus(await a.post('/api/users/me/flashcard-plans', plan({ items: [{ lessonId: lesson.id }] })), 200).json
    for (const res of [await b.get(`/api/users/me/flashcard-plans/${p.id}/cards`), await b.del(`/api/users/me/flashcard-plans/${p.id}`), await b.put(`/api/users/me/flashcard-plans/${p.id}`, plan({ version: p.version }))]) assert.equal(res.status, 404)
    assert.ok(!(await b.get('/api/users/me/flashcard-plans')).text.includes(String(p.name + p.id)))
  })
  await tc('F-F10', 'tạo plan chứa bài ngoài quyền bị chặn', async () => {
    const b = await learner(ctx, B)
    expectRejected(await b.post('/api/users/me/flashcard-plans', plan({ items: [{ lessonId: lesson.id }] })))
  })
  await tc('F-F10', 'mất quyền bài: plan bỏ bài đó; vẫn xoá được thẻ của mình', async () => {
    const u = await learner(ctx)
    const card = expectStatus(await mine(u, lesson.id, 'Của tôi'), 200).json
    const p = expectStatus(await u.post('/api/users/me/flashcard-plans', plan({ items: [{ lessonId: lesson.id, cardIds: [card.id] }] })), 200).json
    expectStatus(await updateUser(admin, u.user, { unitId: B.id }), 200)
    await u.login(u.user.username, 'Test-User-Pass-1x')
    const cards = await u.get(`/api/users/me/flashcard-plans/${p.id}/cards`)
    assert.ok(cards.status >= 400 || cards.json.cards.length === 0, `cards still served: ${cards.text.slice(0, 120)}`)
    expectStatus(await u.del(`/api/flashcards/mine/${card.id}`), [200, 204])
  })

  // F-F11/F-F12 real scheduler tick.
  const remindable = async () => {
    const u = await learner(ctx)
    const p = expectStatus(await u.post('/api/users/me/flashcard-plans', plan({ items: [{ lessonId: lesson.id }] })), 200).json
    return { u, p }
  }
  const [due, late, paused] = [await remindable(), await remindable(), await remindable()]
  sql(`update flashcard_study_plans set next_reminder_at = now() - interval '1 minute' where id=${due.p.id}`)
  sql(`update flashcard_study_plans set next_reminder_at = now() - interval '3 hours' where id=${late.p.id}`)
  sql(`update flashcard_study_plans set next_reminder_at = now() + interval '30 seconds' where id=${paused.p.id}`)
  ok(await paused.u.put(`/api/users/me/flashcard-plans/${paused.p.id}`, plan({ active: false, version: paused.p.version + 1 })).then((r) => (r.status === 409 ? paused.u.put(`/api/users/me/flashcard-plans/${paused.p.id}`, plan({ active: false, version: paused.p.version })) : r)), 'pause')
  // A backlog above one batch (>200) for F-F12: many plans owned by one account, loaded directly into the test DB.
  const bulkUser = await learner(ctx)
  const selection = sqlOne(`select selection from flashcard_study_plans where id=${due.p.id}`).replaceAll("'", "''")
  sql(`insert into flashcard_study_plans(version, user_id, name, start_date, end_date, weekday_mask, reminder_time, active, next_reminder_at, selection, created_at, updated_at)
       select 0, ${bulkUser.user.id}, 'Bulk '||g, current_date, current_date + 30, 127, '08:00', true, now() - interval '1 minute', '${selection}', now(), now() from generate_series(1, 250) g`)

  await tc('F-F11', 'đến hạn: có thông báo, trỏ đúng lịch, lịch tiếp theo cập nhật', async () => {
    assert.ok(await waitFor(() => sqlNum(`select count(*) from notifications where recipient_id=${due.u.user.id}`) > 0), 'no reminder within 75s')
    assert.ok(sqlOne(`select target_url from notifications where recipient_id=${due.u.user.id}`).includes(String(due.p.id)), 'link does not point at plan')
    assert.equal(sqlOne(`select next_reminder_at > now() from flashcard_study_plans where id=${due.p.id}`), 't')
  })
  await tc('F-F11', 'trễ quá 2 giờ: bỏ lần cũ, không dồn nhắc', async () => {
    assert.ok(await waitFor(() => sqlOne(`select next_reminder_at > now() from flashcard_study_plans where id=${late.p.id}`) === 't'), 'late plan not rescheduled')
    assert.equal(sqlNum(`select count(*) from notifications where recipient_id=${late.u.user.id}`), 0, 'overdue reminder delivered')
  })
  await tc('F-F07', 'lịch paused không nhắc', async () => {
    assert.equal(sqlNum(`select count(*) from notifications where recipient_id=${paused.u.user.id}`), 0)
  })
  await tc('F-F12', 'backlog >200 lịch xử lý qua các batch, không trùng', async () => {
    const left = () => sqlNum(`select count(*) from flashcard_study_plans where user_id=${bulkUser.user.id} and next_reminder_at <= now()`)
    assert.ok(await waitFor(() => left() === 0, 200000), `${left()} plans still due after 200s`)
    assert.equal(sqlNum(`select count(*) from notifications where recipient_id=${bulkUser.user.id}`), 250)
  })
  await tc('F-F12', 'hai scheduler thật chạy đồng thời', async () => na('Chạy ở NF-C03 (hai backend trên cùng DB test).'))
  void readAndComplete; void notifCount; void ok
}
