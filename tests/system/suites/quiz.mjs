// 4.6 Quiz, question bank, anti-cheat (F-Q01..Q15). Deadlines are moved on the isolated DB
// instead of waiting: timestamps there are in business time (UTC+7), hence APP_NOW.
import { tc, assert, expectStatus, expectRejected, sql, sqlNum, sqlOne, gap, uniq } from '../lib.mjs'
import { freshCourse, learner, answersFor, ok, quizSettings, readAndComplete, createCourse, createChapter, createLesson, addQuestions } from '../seed.mjs'

const APP_NOW = "(now() at time zone 'Asia/Ho_Chi_Minh')"
const submit = (c, lessonId, body) => c.post(`/api/quizzes/lessons/${lessonId}/submit`, body)
const start = (c, lessonId) => c.post(`/api/quizzes/lessons/${lessonId}/start`)
const points = (c) => sqlNum(`select points from users where id=${c.user.id}`)
const attempts = (c, lessonId) => sqlNum(`select count(*) from quiz_attempts a join quizzes q on q.id=a.quiz_id where a.user_id=${c.user.id} and q.lesson_id='${lessonId}'`)
const moveDeadline = (sessionId, offsetSeconds) => sql(`update quiz_sessions set deadline_at = ${APP_NOW} + interval '${offsetSeconds} seconds' where id='${sessionId}'`)

export async function run(ctx) {
  const { admin } = ctx
  const q20 = await freshCourse(ctx, { lessons: [{ questions: 20, settings: { requiredForProgress: false } }] })
  const timed = () => freshCourse(ctx, { lessons: [{ questions: 4, settings: { timeLimitMinutes: 1, requiredForProgress: false } }] })

  await tc('F-Q01', 'quiz-context bài có quyền', async () => {
    const s = await learner(ctx)
    const res = expectStatus(await s.get(`/api/users/me/learning/quiz-context?courseId=${q20.course.id}&lessonId=${q20.lesson.id}`), 200)
    assert.ok(res.text.includes(q20.lesson.id))
  })
  await tc('F-Q01', 'bài chưa mở khoá: không lấy được đề/nộp', async () => {
    const f = await freshCourse(ctx, { lessons: [{}, { questions: 2, settings: { requiredForProgress: false } }] })
    const s = await learner(ctx); const locked = f.lessons[1]
    assert.ok([400, 403, 404].includes((await s.get(`/api/quizzes/lessons/${locked.id}/questions`)).status), 'locked questions readable')
    expectRejected(await submit(s, locked.id, { selectedAnswers: answersFor(locked.qs, 2) }), 'locked submit')
    assert.equal(attempts(s, locked.id), 0)
  })
  await tc('F-Q01', 'thiếu quyền: học viên B', async () => {
    const b = await learner(ctx, ctx.B)
    assert.ok([403, 404].includes((await b.get(`/api/quizzes/lessons/${q20.lesson.id}/questions`)).status))
  })
  await tc('F-Q01', 'quiz rỗng không tính đạt/thưởng', async () => {
    const f = await freshCourse(ctx); const s = await learner(ctx)
    expectRejected(await submit(s, f.lesson.id, { selectedAnswers: {} }))
    assert.equal(points(s), 0)
  })

  // F-Q02 multi-choice set grading. Question index 3 is the multi-choice with answers {0,2}.
  const multi = q20.lesson.qs.find((q) => q.multi); const single = q20.lesson.qs.find((q) => !q.multi)
  for (const [name, answer, right] of [['đúng hết', [0, 2], 1], ['đảo thứ tự', [2, 0], 1], ['thiếu một đáp án', [0], 0], ['thừa một đáp án', [0, 2, 3], 0], ['trùng đáp án', [0, 0, 2], 1]]) {
    await tc('F-Q02', `nhiều lựa chọn: ${name}`, async () => {
      const s = await learner(ctx)
      const res = expectStatus(await submit(s, q20.lesson.id, { selectedAnswers: { [multi.id]: answer } }), 200).json
      assert.equal(res.score, right)
    })
  }
  for (const [name, answer, right] of [['đúng', [1], 1], ['sai', [0], 0], ['chọn nhiều gồm đáp án đúng', [1, 2], 0]]) {
    await tc('F-Q02', `một lựa chọn: ${name}`, async () => {
      const s = await learner(ctx)
      assert.equal(expectStatus(await submit(s, q20.lesson.id, { selectedAnswers: { [single.id]: answer } }), 200).json.score, right)
    })
  }

  // F-Q03 scoring table.
  for (const [right, pct, passed] of [[0, 0, false], [15, 75, false], [16, 80, true], [18, 90, true], [19, 95, true], [20, 100, true]]) {
    await tc('F-Q03', `${right}/20`, async () => {
      const s = await learner(ctx)
      const r = expectStatus(await submit(s, q20.lesson.id, { selectedAnswers: answersFor(q20.lesson.qs, right) }), 200).json
      assert.equal(r.percent, pct); assert.equal(r.passed, passed); assert.equal(r.score, right); assert.equal(r.total, 20)
    })
  }
  await tc('F-Q03', 'đổi passingScore=90: 18/20 đạt, 17/20 không', async () => {
    const f = await freshCourse(ctx, { lessons: [{ questions: 20, settings: { passingScore: 90, requiredForProgress: false } }] })
    const s = await learner(ctx)
    assert.equal((await submit(s, f.lesson.id, { selectedAnswers: answersFor(f.lesson.qs, 17) })).json.passed, false)
    assert.equal((await submit(s, f.lesson.id, { selectedAnswers: answersFor(f.lesson.qs, 18) })).json.passed, true)
  })

  // F-Q04 payload tampering.
  await tc('F-Q04', 'gửi score/percent/points giả: server tự chấm', async () => {
    const s = await learner(ctx)
    const r = expectStatus(await submit(s, q20.lesson.id, { selectedAnswers: answersFor(q20.lesson.qs, 0), score: 20, percent: 100, passed: true, points: 9999, total: 20 }), 200).json
    assert.equal(r.score, 0); assert.equal(r.passed, false); assert.equal(points(s), 0)
  })
  for (const [name, answers] of [['questionId lạ', { 99999999: [1] }], ['index ngoài khoảng', { [single.id]: [99] }], ['index âm', { [single.id]: [-1] }]]) {
    await tc('F-Q04', name, async () => {
      const s = await learner(ctx)
      const res = await submit(s, q20.lesson.id, { selectedAnswers: answers })
      assert.ok(res.status < 500, `HTTP ${res.status}`)
      if (res.status === 200) assert.equal(res.json.score, 0)
    })
  }
  await tc('F-Q04', 'questionId của quiz khác không cộng điểm', async () => {
    const f = await freshCourse(ctx, { lessons: [{ questions: 2, settings: { requiredForProgress: false } }] }); const s = await learner(ctx)
    const res = await submit(s, q20.lesson.id, { selectedAnswers: answersFor(f.lesson.qs, 2) })
    assert.equal(res.json?.score ?? 0, 0)
  })

  // F-Q05 settings bounds.
  const cfg = await freshCourse(ctx, { lessons: [{ questions: 1, settings: { requiredForProgress: false } }] })
  for (const [name, settings, accept] of [
    ['time null', { timeLimitMinutes: null }, true], ['time 1', { timeLimitMinutes: 1 }, true], ['time 300', { timeLimitMinutes: 300 }, true],
    ['time 0', { timeLimitMinutes: 0 }, false], ['time -1', { timeLimitMinutes: -1 }, false], ['time 301', { timeLimitMinutes: 301 }, false],
    ['pass 0', { passingScore: 0 }, false], ['pass 1', { passingScore: 1 }, true], ['pass 100', { passingScore: 100 }, true], ['pass 101', { passingScore: 101 }, false],
  ]) {
    await tc('F-Q05', name, async () => {
      const res = await admin.put('/api/admin/quizzes/settings', { courseId: cfg.course.id, lessonId: cfg.lesson.id, passingScore: 80, requiredForProgress: false, ...settings })
      if (accept) expectStatus(res, 200); else expectRejected(res)
    })
  }

  // F-Q06 timed sessions.
  await tc('F-Q06', 'start lại dùng phiên cũ, remainingSeconds giảm', async () => {
    const f = await timed(); const s = await learner(ctx)
    const a = expectStatus(await start(s, f.lesson.id), 200).json
    assert.equal(a.timeLimitMinutes, 1); assert.ok(a.remainingSeconds <= 60 && a.remainingSeconds > 50)
    sql(`update quiz_sessions set deadline_at = deadline_at - interval '20 seconds' where id='${a.sessionId}'`)
    const b = expectStatus(await start(s, f.lesson.id), 200).json
    assert.equal(b.sessionId, a.sessionId); assert.ok(b.remainingSeconds <= 40, `remaining ${b.remainingSeconds}`)
  })
  await tc('F-Q06', 'hai tab start đồng thời chỉ một phiên', async () => {
    const f = await timed(); const s = await learner(ctx)
    const out = await Promise.all(Array.from({ length: 6 }, () => start(s, f.lesson.id)))
    assert.equal(new Set(out.map((r) => r.json?.sessionId)).size, 1)
    assert.equal(sqlNum(`select count(*) from quiz_sessions where user_id=${s.user.id} and lesson_id='${f.lesson.id}'`), 1)
  })
  await tc('F-Q06', 'quiz không giới hạn trả session null', async () => {
    const s = await learner(ctx)
    const a = expectStatus(await start(s, q20.lesson.id), 200).json
    assert.equal(a.sessionId, null); assert.equal(a.remainingSeconds, null)
  })

  // F-Q07 deadline, driven by the deadline stored on the server.
  for (const [name, offset, accept] of [['trước deadline 1s', 1, true], ['sau deadline 25s', -25, true], ['sau deadline 31s', -31, false], ['sau deadline 1 giờ', -3600, false]]) {
    await tc('F-Q07', name, async () => {
      const f = await timed(); const s = await learner(ctx)
      const { sessionId } = expectStatus(await start(s, f.lesson.id), 200).json
      moveDeadline(sessionId, offset)
      const res = await submit(s, f.lesson.id, { sessionId, selectedAnswers: answersFor(f.lesson.qs, 4) })
      if (accept) expectStatus(res, 200)
      else { expectRejected(res); assert.equal(attempts(s, f.lesson.id), 0) }
    })
  }
  await tc('F-Q07', 'client không kéo dài được giờ server', async () => {
    const f = await timed(); const s = await learner(ctx)
    const { sessionId } = (await start(s, f.lesson.id)).json
    moveDeadline(sessionId, -120)
    const res = await submit(s, f.lesson.id, { sessionId, selectedAnswers: {}, timeElapsed: 1, deadlineAt: '2099-01-01T00:00:00' })
    expectRejected(res)
    const again = (await start(s, f.lesson.id)).json
    assert.notEqual(again.sessionId, sessionId, 'expired session reused')
  })

  // F-Q08 session misuse.
  await tc('F-Q08', 'quiz có giờ nộp thiếu sessionId', async () => {
    const f = await timed(); const s = await learner(ctx)
    expectRejected(await submit(s, f.lesson.id, { selectedAnswers: {} }))
  })
  await tc('F-Q08', 'sessionId của người khác', async () => {
    const f = await timed(); const [a, b] = [await learner(ctx), await learner(ctx)]
    const { sessionId } = (await start(a, f.lesson.id)).json
    expectRejected(await submit(b, f.lesson.id, { sessionId, selectedAnswers: {} }))
    assert.equal(sqlOne(`select submitted_at is null from quiz_sessions where id='${sessionId}'`), 't', 'victim session consumed')
  })
  await tc('F-Q08', 'sessionId của bài khác', async () => {
    const [f, g] = [await timed(), await timed()]; const s = await learner(ctx)
    const { sessionId } = (await start(s, f.lesson.id)).json
    expectRejected(await submit(s, g.lesson.id, { sessionId, selectedAnswers: {} }))
  })
  await tc('F-Q08', 'phiên đã nộp không nộp lại', async () => {
    const f = await timed(); const s = await learner(ctx)
    const { sessionId } = (await start(s, f.lesson.id)).json
    expectStatus(await submit(s, f.lesson.id, { sessionId, selectedAnswers: answersFor(f.lesson.qs, 1) }), 200)
    expectRejected(await submit(s, f.lesson.id, { sessionId, selectedAnswers: answersFor(f.lesson.qs, 4) }))
    assert.equal(attempts(s, f.lesson.id), 1)
  })
  await tc('F-Q08', 'hai request nộp đồng thời: 1 kết quả', async () => {
    const f = await timed(); const s = await learner(ctx)
    const { sessionId } = (await start(s, f.lesson.id)).json
    const out = await Promise.all(Array.from({ length: 2 }, () => submit(s, f.lesson.id, { sessionId, selectedAnswers: answersFor(f.lesson.qs, 4) })))
    assert.equal(out.filter((r) => r.status === 200).length, 1, `statuses ${out.map((r) => r.status)}`)
    assert.equal(attempts(s, f.lesson.id), 1)
  })

  // F-Q09 partial answers.
  await tc('F-Q09', 'không trả lời: 0 điểm', async () => {
    const s = await learner(ctx)
    assert.equal(expectStatus(await submit(s, q20.lesson.id, { selectedAnswers: {} }), 200).json.score, 0)
  })
  await tc('F-Q09', 'trả lời một phần: chỉ tính phần đã nộp', async () => {
    const s = await learner(ctx)
    const partial = Object.fromEntries(q20.lesson.qs.slice(0, 3).map((q) => [q.id, q.correct]))
    assert.equal(expectStatus(await submit(s, q20.lesson.id, { selectedAnswers: partial }), 200).json.score, 3)
  })

  // F-Q10 no answer leak.
  await tc('F-Q10', 'đề trước khi nộp không lộ đáp án', async () => {
    const s = await learner(ctx)
    const res = expectStatus(await s.get(`/api/quizzes/lessons/${q20.lesson.id}/questions`), 200)
    assert.ok(!/correct|explanation|answer/i.test(res.text), 'answer fields in question payload')
    const ctxRes = await s.get(`/api/users/me/learning/quiz-context?courseId=${q20.course.id}&lessonId=${q20.lesson.id}`)
    assert.ok(!/correctAnswers/i.test(ctxRes.text), 'answers in quiz-context')
    const les = await s.get(`/api/lessons/${q20.lesson.id}`)
    assert.ok(!/correctAnswers/i.test(les.text), 'answers in lesson payload')
  })
  await tc('F-Q10', 'review trước khi nộp không có dữ liệu', async () => {
    const s = await learner(ctx)
    const res = await s.get(`/api/quizzes/lessons/${q20.lesson.id}/review`)
    assert.ok(res.status === 404 || !/correctAnswers/.test(res.text), `review before submit: ${res.status}`)
  })
  await tc('F-Q10', 'review sau khi nộp đúng lượt của mình', async () => {
    const s = await learner(ctx)
    await submit(s, q20.lesson.id, { selectedAnswers: answersFor(q20.lesson.qs, 16) })
    const r = expectStatus(await s.get(`/api/quizzes/lessons/${q20.lesson.id}/review`), 200).json
    assert.equal(r.score, 16); assert.equal(r.questions.length, 20)
  })
  await tc('F-Q10', 'admin report attempts của người khác bị chặn với học viên', async () => {
    const s = await learner(ctx)
    assert.ok([401, 403].includes((await s.get(`/api/admin/reports/training/attempts?learnerId=1&courseId=${q20.course.id}`)).status))
    assert.ok([401, 403].includes((await s.get('/api/admin/reports/training/attempts/1/analysis')).status))
  })

  // F-Q11 committed result survives a lost response.
  await tc('F-Q11', 'mất response: tra cứu lại được, nộp lại không thưởng trùng', async () => {
    const f = await freshCourse(ctx, { lessons: [{ questions: 4 }] }); const s = await learner(ctx)
    await s.post(`/api/users/me/lessons/${f.lesson.id}/progress`, { contentType: 'TEXT', textCompleted: true })
    await submit(s, f.lesson.id, { selectedAnswers: answersFor(f.lesson.qs, 4) })   // response treated as lost
    expectStatus(await s.get(`/api/quizzes/lessons/${f.lesson.id}/review`), 200)
    const p1 = points(s)
    await submit(s, f.lesson.id, { selectedAnswers: answersFor(f.lesson.qs, 4) })
    assert.equal(points(s), p1, 'points changed on resubmit')
    assert.equal(p1, 400, 'expected 100 for the lesson and 300 for the gold badge')
  })

  // F-Q12 badges. Badge rule per scenarios: 100% gold +300, >90% silver +200, 80-90% bronze +100.
  await tc('F-Q12', 'API quiz cấp khoá có nộp được không', async () => {
    const s = await learner(ctx)
    const res = await s.post(`/api/quizzes/${q20.course.id}/submit`, { selectedAnswers: {} })
    if (res.status >= 400) return gap(`POST /api/quizzes/{courseId}/submit luôn trả ${res.status} ("Bài kiểm tra thuộc bài học"); quiz cấp khoá không còn đường nộp.`)
  })
  for (const [right, badge, bonus] of [[16, 'bronze', 100], [18, 'bronze', 100], [19, 'silver', 200], [20, 'gold', 300]]) {
    await tc('F-Q12', `đạt ${right * 5}% → huy hiệu ${badge} +${bonus}`, async () => {
      const f = await freshCourse(ctx, { lessons: [{ questions: 20, settings: { requiredForProgress: false } }] }); const s = await learner(ctx)
      await submit(s, f.lesson.id, { selectedAnswers: answersFor(f.lesson.qs, right) })
      const types = sql(`select b.type from user_badges ub join badges b on b.id=ub.badge_id where ub.user_id=${s.user.id}`)
      assert.equal(types, badge, `không có huy hiệu sau quiz (${types || 'none'}), points=${points(s)}. checkAndUnlockBadges trong QuizServiceImpl không có nơi gọi và API quiz cấp khoá luôn 400`)
      assert.equal(points(s), bonus)
    })
  }

  // F-Q13 question CRUD.
  const qc = await freshCourse(ctx, { lessons: [{ questions: 1, settings: { requiredForProgress: false } }] })
  const q = (over) => ({ courseId: qc.course.id, lessonId: qc.lesson.id, text: 'Câu hợp lệ', options: ['A', 'B', 'C'], type: 'SINGLE_CHOICE', correctAnswer: 0, correctAnswers: [0], ...over })
  await tc('F-Q13', 'tạo/sửa/xoá một lựa chọn và nhiều lựa chọn', async () => {
    const one = expectStatus(await admin.post('/api/admin/questions', q()), 200).json
    expectStatus(await admin.put(`/api/admin/questions/${one.id}`, q({ text: 'Đã sửa' })), 200)
    assert.equal(sqlOne(`select text from questions where id=${one.id}`), 'Đã sửa')
    const many = expectStatus(await admin.post('/api/admin/questions', q({ type: 'MULTIPLE_CHOICE', options: ['A', 'B', 'C'], correctAnswer: undefined, correctAnswers: [0, 2] })), 200).json
    expectStatus(await admin.del(`/api/admin/questions/${many.id}`), [200, 204])
  })
  for (const [name, over] of [
    ['không đáp án đúng', { correctAnswer: null, correctAnswers: [] }], ['đáp án ngoài options', { correctAnswer: 9, correctAnswers: [9] }],
    ['đáp án âm', { correctAnswer: -1, correctAnswers: [-1] }], ['text rỗng', { text: '  ' }], ['options rỗng', { options: [] }], ['2 lựa chọn (dưới 3)', { options: ['A', 'B'] }], ['7 lựa chọn (trên 6)', { options: ['1', '2', '3', '4', '5', '6', '7'] }], ['một lựa chọn có 2 đáp án', { correctAnswer: null, correctAnswers: [0, 1] }],
    ['bài không thuộc khoá', { lessonId: q20.lesson.id }],
  ]) {
    await tc('F-Q13', name, async () => expectRejected(await admin.post('/api/admin/questions', q(over)), name) && undefined)
  }

  // F-Q14 question bank.
  const bankDraft = await freshCourse(ctx, { publishIt: false, unit: null, lessons: [{ questions: 1 }] })
  const bankQ = async () => ok(await admin.post('/api/admin/question-bank', { code: uniq('BQ').toUpperCase(), courseId: bankDraft.course.id, lessonId: bankDraft.lesson.id, text: `Ngân hàng ${uniq()}`, type: 'SINGLE_CHOICE', options: ['A', 'B', 'C'], correctAnswers: [0], difficulty: 'EASY', status: 'ACTIVE' }), 'bank question')
  await tc('F-Q14', 'tạo câu ngân hàng và tìm được', async () => {
    const b = await bankQ()
    const page = expectStatus(await admin.get(`/api/admin/question-bank/page?query=${encodeURIComponent(b.code)}&size=10`), 200)
    assert.ok(page.text.includes(b.code))
    ctx.bank1 = b
  })
  await tc('F-Q14', 'capture từ quiz', async () => {
    const res = expectStatus(await admin.post(`/api/admin/question-bank/capture/${bankDraft.lesson.qs[0].id}`), 200)
    assert.ok(res.json.id)
  })
  await tc('F-Q14', 'add 1 câu', async () => {
    const res = expectStatus(await admin.post('/api/admin/question-bank/add-to-quiz', { lessonId: bankDraft.lesson.id, questionIds: [ctx.bank1.id] }), 200)
    assert.equal(res.json.length, 1)
  })
  for (const [name, ids] of [['rỗng', []], ['ID trùng', [1, 1]], ['ID null', [null]], ['101 ID', Array.from({ length: 101 }, (_, i) => i + 1)], ['ID không tồn tại', [99999999]]]) {
    await tc('F-Q14', `add ${name} bị chặn`, async () => expectRejected(await admin.post('/api/admin/question-bank/add-to-quiz', { lessonId: bankDraft.lesson.id, questionIds: ids }), name) && undefined)
  }

  // F-Q15 edits during an attempt.
  await tc('F-Q15', 'sửa đáp án sau khi đã nộp: lịch sử giữ snapshot', async () => {
    const f = await freshCourse(ctx, { lessons: [{ questions: 2, settings: { requiredForProgress: false } }] }); const s = await learner(ctx)
    await submit(s, f.lesson.id, { selectedAnswers: answersFor(f.lesson.qs, 2) })
    const stored = sqlOne(`select questions_json from quiz_attempts where user_id=${s.user.id}`)
    assert.ok(stored.includes('correctAnswers'), 'attempt has no snapshot')
    const first = f.lesson.qs[0]
    ok(await admin.put(`/api/admin/questions/${first.id}`, { courseId: f.course.id, lessonId: f.lesson.id, text: 'Đổi', options: ['A', 'B', 'C', 'D'], type: 'SINGLE_CHOICE', correctAnswer: 3, correctAnswers: [3] }), 'edit')
    const review = expectStatus(await s.get(`/api/quizzes/lessons/${f.lesson.id}/review`), 200).json
    assert.equal(review.score, 2, 'stored score changed')
    const q0 = review.questions.find((x) => x.id === first.id)
    if (q0 && JSON.stringify(q0).includes('Đổi')) return gap('Lượt thi lưu snapshot (quiz_attempts.questions_json), nhưng màn review của học viên đọc câu hiện tại: điểm cũ 2/2 hiển thị cạnh đề đã sửa. Cần chốt yêu cầu theo F-Q15.')
  })
  await tc('F-Q15', 'sửa câu trong lúc làm: chấm theo bộ câu hiện tại', async () => {
    const f = await timed(); const s = await learner(ctx)
    const { sessionId } = (await start(s, f.lesson.id)).json
    const first = f.lesson.qs[0]
    ok(await admin.put(`/api/admin/questions/${first.id}`, { courseId: f.course.id, lessonId: f.lesson.id, text: 'Đổi', options: ['A', 'B', 'C', 'D'], type: 'SINGLE_CHOICE', correctAnswer: 3, correctAnswers: [3] }), 'edit')
    const r = expectStatus(await submit(s, f.lesson.id, { sessionId, selectedAnswers: answersFor(f.lesson.qs, 4) }), 200).json
    return gap(`Học viên trả lời đúng theo đề đang thấy nhưng bị chấm theo đề đã sửa: ${r.score}/${r.total}. Phiên không chụp bộ câu, cần chốt theo [ĐX].`)
  })
  void readAndComplete; void quizSettings; void createCourse; void createChapter; void createLesson; void addQuestions
}
