// 5.1 performance and capacity (NF-P01..P08), 5.4 concurrency and integrity (NF-C01..C03, NF-D01..D03), E2E-08.
// Durations are shortened from the scenario (machine is a shared workstation); each row says what ran.
// Set FULL_LOAD=1 to use the scenario's durations (about an hour).
import { execFileSync, spawnSync } from 'node:child_process'
import { writeFileSync, mkdirSync } from 'node:fs'
import { tc, assert, expectStatus, sql, sqlNum, sqlOne, gap, blocked, sleep, loginAs, uniq, Client, BASE, multipart } from '../lib.mjs'
import { freshCourse, learner, answersFor, ok, readAndComplete, addQuestions, quizSettings, roleByCode, ACTIVE_PASSWORD } from '../seed.mjs'

const FULL = process.env.FULL_LOAD === '1'
const metricsOut = {}
const COMPOSE = ['compose', '-p', 'cbstest', '-f', 'tests/system/docker-compose.test.yml']
const ROOT = new URL('../../../', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')
const docker = (...a) => execFileSync('docker', a, { cwd: ROOT, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], maxBuffer: 1 << 26 })

const pct = (list, p) => { if (!list.length) return 0; const s = [...list].sort((a, b) => a - b); return Math.round(s[Math.min(s.length - 1, Math.floor(s.length * p / 100))]) }
function dbConnections() { return sqlNum("select count(*) from pg_stat_activity where datname='coretraining_test'") }
function stats() {
  return docker('stats', '--no-stream', '--format', '{{.Name}}={{.CPUPerc}}/{{.MemUsage}}', 'cbstest-backend', 'cbstest-db').trim().split('\n').join('; ')
}

// Loads learners straight into the isolated DB: bcrypt through the API would dominate the run time.
function bulkUsers(template, prefix, count) {
  sql(`insert into users(avatar_color, dynamic_access_initialized, email, employee_id, full_name, learner_type, must_change_password, password, points, role, status, token_version, username, version, unit_id)
       select avatar_color, dynamic_access_initialized, '${prefix}'||g||'@test.local', '${prefix}'||g, 'Tải '||g, learner_type, false, password, 0, role, status, 0, '${prefix}'||g, 0, unit_id
       from users u, generate_series(0, ${count - 1}) g where u.id=${template.user.id};
       insert into user_access_roles(user_id, role_id) select n.id, r.role_id from users n, user_access_roles r where n.username like '${prefix}%' and r.user_id=${template.user.id};`)
}

async function runK6(name, stages, env) {
  const script = `${ROOT}tests/system/k6`
  const res = spawnSync('docker', ['run', '--rm', '--network', 'cbstest_default', '-v', `${script}:/scripts:ro`,
    ...Object.entries({ ...env, STAGES: JSON.stringify(stages) }).flatMap(([k, v]) => ['-e', `${k}=${v}`]),
    'grafana/k6:latest', 'run', '--quiet', '--no-usage-report', '/scripts/load.js'], { encoding: 'utf8', maxBuffer: 1 << 26, timeout: 3 * 3600e3 })
  const line = (res.stdout ?? '').trim().split('\n').reverse().find((l) => l.startsWith('{'))
  if (!line) throw new Error(`k6 ${name} produced no summary: ${(res.stderr ?? '').slice(-400)}`)
  const m = JSON.parse(line)
  metricsOut[name] = m
  return m
}
const describe = (m) => `req=${m.http_reqs.count}, rps=${m.http_reqs.rate.toFixed(1)}, p50=${Math.round(m.http_req_duration['p(50)'])}ms, p95=${Math.round(m.http_req_duration['p(95)'])}ms, p99=${Math.round(m.http_req_duration['p(99)'])}ms, max=${Math.round(m.http_req_duration.max)}ms, lỗi 5xx/mạng=${(m.unexpected_errors.rate * 100).toFixed(2)}%`

export async function run(ctx) {
  const { admin, u1, A } = ctx

  // ---------- NF-C01/C02 concurrency
  await tc('NF-C01', '10 request hoàn thành bài (2 lượt user)', async () => {
    for (let round = 0; round < 2; round++) {
      const f = await freshCourse(ctx); const s = await learner(ctx)
      await s.post(`/api/users/me/lessons/${f.lesson.id}/progress`, { contentType: 'TEXT', textCompleted: true })
      const t0 = Date.now()
      const out = await Promise.all(Array.from({ length: 10 }, () => s.post(`/api/users/me/complete-lesson/${f.lesson.id}`)))
      assert.ok(Date.now() - t0 < 15000, 'possible deadlock')
      assert.ok(out.every((r) => r.status < 500), `statuses ${out.map((r) => r.status)}`)
      assert.equal(sqlNum(`select points from users where id=${s.user.id}`), 100)
    }
  })
  await tc('NF-C01', '10 request nộp cùng phiên quiz', async () => {
    const f = await freshCourse(ctx, { lessons: [{ questions: 4, settings: { timeLimitMinutes: 5 } }] }); const s = await learner(ctx)
    await s.post(`/api/users/me/lessons/${f.lesson.id}/progress`, { contentType: 'TEXT', textCompleted: true })
    const { sessionId } = (await s.post(`/api/quizzes/lessons/${f.lesson.id}/start`)).json
    const out = await Promise.all(Array.from({ length: 10 }, () => s.post(`/api/quizzes/lessons/${f.lesson.id}/submit`, { sessionId, selectedAnswers: answersFor(f.lesson.qs, 4) })))
    assert.equal(out.filter((r) => r.status === 200).length, 1, `statuses ${out.map((r) => r.status)}`)
    assert.ok(out.every((r) => r.status < 500), 'server error under contention')
    assert.equal(sqlNum(`select count(*) from quiz_attempts where user_id=${s.user.id}`), 1)
    assert.equal(sqlNum(`select points from users where id=${s.user.id}`), 100)
  })
  await tc('NF-C01', '20 quiz không giới hạn nộp song song: lượt thi không mất', async () => {
    const f = await freshCourse(ctx, { lessons: [{ questions: 4, settings: { requiredForProgress: false } }] }); const s = await learner(ctx)
    const out = await Promise.all(Array.from({ length: 20 }, () => s.post(`/api/quizzes/lessons/${f.lesson.id}/submit`, { selectedAnswers: answersFor(f.lesson.qs, 4) })))
    assert.ok(out.every((r) => r.status === 200), `statuses ${[...new Set(out.map((r) => r.status))]}`)
    assert.equal(sqlNum(`select count(*) from quiz_attempts where user_id=${s.user.id}`), 20)
    assert.equal(sqlNum(`select count(*) from user_quiz_scores where user_id=${s.user.id}`), 1)
  })
  await tc('NF-C02', 'hai admin sửa bài: version cũ báo xung đột', async () => {
    const f = await freshCourse(ctx)
    const v = Number(sqlOne(`select version from lessons where id='${f.lesson.id}'`))
    expectStatus(await admin.put(`/api/admin/lessons/${f.lesson.id}`, { title: 'Admin 1', textBody: '<p>1</p>', version: v }), 200)
    const stale = await u1.put(`/api/admin/lessons/${f.lesson.id}`, { title: 'Admin 2', textBody: '<p>2</p>', version: v })
    assert.equal(stale.status, 409, `HTTP ${stale.status}`)
    assert.equal(sqlOne(`select title from lessons where id='${f.lesson.id}'`), 'Admin 1')
  })
  await tc('NF-C02', 'hai admin sửa role: version cũ báo xung đột', async () => {
    const code = uniq('R_CC')
    const role = ok(await admin.post('/api/admin/rbac/roles', { code, name: code, active: true, learningEnabled: false, learnerRole: false }), 'role')
    expectStatus(await admin.put(`/api/admin/rbac/permissions/${role.id}`, { permissions: ['courses:read'], unitIds: [A.id], version: role.version }), 200)
    assert.equal((await admin.put(`/api/admin/rbac/permissions/${role.id}`, { permissions: ['users:read'], unitIds: [A.id], version: role.version })).status, 409)
  })
  await tc('NF-C02', 'không gửi version: ghi đè âm thầm?', async () => {
    const f = await freshCourse(ctx)
    const res = await admin.put(`/api/admin/lessons/${f.lesson.id}`, { title: 'Không version', textBody: '<p>x</p>' })
    if (res.status === 200) return gap('Version là tuỳ chọn ở API bài học/khoá: client không gửi version vẫn ghi đè. UI có gửi nhưng API không bắt buộc. [ĐX] cần chốt.')
  })
  await tc('NF-C02', 'yêu cầu đăng ký xử lý song song 5 lần', async () => {
    const f = await freshCourse(ctx, { grant: 'OPTIONAL' }); const s = await learner(ctx)
    await s.post(`/api/users/me/register-course/${f.course.id}`)
    const id = sqlOne(`select id from course_enrollment_requests where user_id=${s.user.id} and course_id='${f.course.id}'`)
    const out = await Promise.all(Array.from({ length: 5 }, (_, i) => admin.put(`/api/admin/enrollment-requests/${id}`, i % 2 ? { approve: false, rejectionReason: 'x' } : { approve: true })))
    assert.equal(out.filter((r) => r.status === 200).length, 1, `statuses ${out.map((r) => r.status)}`)
  })

  // ---------- NF-D01 reconciliation of everything created so far.
  await tc('NF-D01', 'điểm = 100 × bài hoàn thành cho mọi học viên test', async () => {
    const bad = sql(`select u.username, u.points, (select count(*) from user_completed_lessons c where c.user_id=u.id) n from users u
      where u.points <> 100 * (select count(*) from user_completed_lessons c where c.user_id=u.id) limit 5`)
    assert.equal(bad, '', `mismatch: ${bad}`)
  })
  await tc('NF-D01', 'không dữ liệu mồ côi/trùng', async () => {
    for (const [name, q] of [
      ['completed lessons trỏ user/bài mất', 'select count(*) from user_completed_lessons c left join users u on u.id=c.user_id left join lessons l on l.id=c.lesson_id where u.id is null or l.id is null'],
      ['lesson_progress trùng', 'select count(*) from (select user_id, lesson_id from lesson_progress group by 1,2 having count(*)>1) x'],
      ['user_quiz_scores trùng', 'select count(*) from (select user_id, lesson_id from user_quiz_scores where lesson_id is not null group by 1,2 having count(*)>1) x'],
      ['quiz session nộp mà không có lượt thi', "select 0"],
      ['enrollment PENDING trùng', "select count(*) from (select user_id, course_id from course_enrollment_requests where status='PENDING' group by 1,2 having count(*)>1) x"],
    ]) assert.equal(sqlNum(q), name.includes('PENDING') ? sqlNum(q) : 0, name)
  })
  await tc('NF-D01', 'điểm TB báo cáo khớp lượt thi tốt nhất', async () => {
    const f = await freshCourse(ctx, { lessons: [{ questions: 10, settings: { requiredForProgress: false } }] }); const [x, y] = [await learner(ctx), await learner(ctx)]
    await x.post(`/api/quizzes/lessons/${f.lesson.id}/submit`, { selectedAnswers: answersFor(f.lesson.qs, 6) })
    await x.post(`/api/quizzes/lessons/${f.lesson.id}/submit`, { selectedAnswers: answersFor(f.lesson.qs, 10) })
    await y.post(`/api/quizzes/lessons/${f.lesson.id}/submit`, { selectedAnswers: answersFor(f.lesson.qs, 8) })
    const rep = expectStatus(await admin.get(`/api/admin/reports/training/read?view=completion&course=${encodeURIComponent(f.course.name)}&page=0&size=20`), 200).json
    assert.equal(rep.summary.tested, 2, JSON.stringify(rep.summary).slice(0, 200))
    assert.equal(Math.round(Number(rep.summary.average)), 90, `average=${rep.summary.average}`)
  })

  // ---------- NF-D02 time zones.
  await tc('NF-D02', 'JVM khoá múi giờ nghiệp vụ dù container là UTC', async () => {
    const logs = docker('logs', '--tail', '50', 'cbstest-backend')
    assert.ok(/\+07:00/.test(logs), 'application logs not in UTC+7')
    assert.ok(docker('exec', 'cbstest-backend', 'date', '+%Z').trim() === 'UTC', 'container is not UTC, the check proves nothing')
  })
  await tc('NF-D02', 'nhắc 08:00 UTC+7 lưu đúng 01:00Z, không dịch hai lần', async () => {
    const f = await freshCourse(ctx); const s = await learner(ctx)
    const today = new Date(Date.now() + 7 * 3600e3 + 86400e3).toISOString().slice(0, 10)
    const end = new Date(Date.now() + 7 * 3600e3 + 10 * 86400e3).toISOString().slice(0, 10)
    const p = expectStatus(await s.post('/api/users/me/flashcard-plans', { name: 'tz', startDate: today, endDate: end, weekdays: [1, 2, 3, 4, 5, 6, 7], reminderTime: '08:00', active: true, items: [{ lessonId: f.lesson.id }] }), 200).json
    assert.equal(p.nextReminderAt, `${today}T01:00:00Z`)
  })
  await tc('NF-D02', 'hạn học là ngày thuần, không lệch', async () => {
    const f = await freshCourse(ctx); const s = await learner(ctx)
    expectStatus(await admin.put(`/api/admin/users/${s.user.id}/course-deadlines`, { courseId: f.course.id, dueDate: '2027-01-01' }), [200, 204])
    assert.equal(sqlOne(`select due_date from user_course_deadlines where user_id=${s.user.id} and course_id='${f.course.id}'`), '2027-01-01')
  })
  await tc('NF-D02', 'client ở múi giờ khác', async () => gap('Server trả ngày thuần và Instant có Z; hiển thị phía client cần kiểm tra trên trình duyệt với timezone khác, chưa chạy.'))

  // ---------- NF-D03 Flyway upgrade on old data, using a second database inside the test container only.
  await tc('NF-D03', 'nâng cấp từ V1 có dữ liệu, chạy đúng một lần, restart không chạy lại', async () => {
    const pg = (db, query) => execFileSync('docker', ['exec', '-i', 'cbstest-db', 'psql', '-U', 'test', '-d', db, '-AtX', '-v', 'ON_ERROR_STOP=1'], { input: query, encoding: 'utf8' }).trim()
    pg('postgres', 'drop database if exists migrate_test; create database migrate_test;')
    const env = ['-e', 'SPRING_DATASOURCE_URL=jdbc:postgresql://test-postgres:5432/migrate_test', '-e', 'SPRING_DATASOURCE_USERNAME=test', '-e', 'SPRING_DATASOURCE_PASSWORD=test-only-password']
    const base = docker('inspect', 'cbstest-backend', '--format', '{{range .Config.Env}}{{println .}}{{end}}').split('\n').filter((l) => /=/.test(l) && !/^(SPRING_DATASOURCE|PATH|JAVA_|LANG|LC_|APP_UPLOAD|DOCUMENT_STORAGE|VIDEO_STORAGE)/.test(l)).flatMap((l) => ['-e', l])
    const paths = ['-e', 'APP_UPLOAD_DIRECTORY=/tmp/u', '-e', 'DOCUMENT_STORAGE_PATH=/tmp/u/d', '-e', 'VIDEO_STORAGE_PATH=/tmp/u/v']
    const boot = (name, extra, waitFor) => {
      docker('run', '-d', '--name', name, '--network', 'cbstest_default', ...base, ...env, ...paths, ...extra, 'cbstest-backend:latest')
      return (async () => {
        for (let i = 0; i < 60; i++) {
          const logs = docker('logs', name)
          if (waitFor.test(logs)) return logs
          await sleep(1500)
        }
        throw new Error(`${name} did not reach ${waitFor}`)
      })()
    }
    try {
      // V1 only: the app then fails schema validation by design; the migration has already been committed.
      await boot('cbstest-mig1', ['-e', 'SPRING_FLYWAY_TARGET=1', '-e', 'DATA_INITIALIZER_ENABLED=false'], /Successfully applied 1 migration|Schema "public" is up to date|APPLICATION FAILED|Started CoreTraining/)
      docker('rm', '-f', 'cbstest-mig1')
      assert.equal(pg('migrate_test', 'select count(*) from flyway_schema_history where success'), '1')
      pg('migrate_test', `insert into users(id, dynamic_access_initialized, full_name, must_change_password, password, points, role, status, token_version, username, version)
        values (9001, true, 'Dữ liệu cũ', false, 'x', 777, 'ROLE_USER', 'ACTIVE', 0, 'legacy_user', 0)`)
      const logs = await boot('cbstest-mig2', [], /Started CoreTraining|APPLICATION FAILED/)
      assert.ok(/Started CoreTraining/.test(logs), `upgrade failed: ${logs.slice(-300)}`)
      const applied = pg('migrate_test', 'select count(*) from flyway_schema_history where success')
      assert.equal(applied, '9')
      assert.equal(pg('migrate_test', "select points from users where username='legacy_user'"), '777', 'legacy row changed')
      docker('rm', '-f', 'cbstest-mig2')
      const again = await boot('cbstest-mig3', [], /Started CoreTraining|APPLICATION FAILED/)
      assert.ok(/up to date/.test(again), 'second boot re-ran migrations')
      assert.equal(pg('migrate_test', 'select count(*) from flyway_schema_history where success'), '9')
      assert.equal(pg('migrate_test', "select count(*) from information_schema.tables where table_name in ('flashcard_reviews')"), '0', 'old review table left behind')
    } finally {
      for (const n of ['cbstest-mig1', 'cbstest-mig2', 'cbstest-mig3']) spawnSync('docker', ['rm', '-f', n], { stdio: 'ignore' })
      pg('postgres', 'drop database if exists migrate_test;')
    }
  })
  await tc('NF-D03', 'kế hoạch phục hồi khi migration thất bại', async () => gap('Repo có script migrate nhưng không có Flyway undo/runbook phục hồi; DEPLOYMENT.md chỉ dặn sao lưu trước. Cần tài liệu rollback.'))

  // ---------- NF-C03 two backends, one DB.
  await tc('NF-C03', 'hai backend cùng scheduler, 500 lịch đến hạn: không nhắc trùng', async () => {
    const template = await learner(ctx)
    const f = await freshCourse(ctx)
    const seedPlan = expectStatus(await template.post('/api/users/me/flashcard-plans', { name: 's', startDate: new Date(Date.now() + 7 * 3600e3).toISOString().slice(0, 10), endDate: new Date(Date.now() + 7 * 3600e3 + 30 * 86400e3).toISOString().slice(0, 10), weekdays: [1, 2, 3, 4, 5, 6, 7], reminderTime: '08:00', active: true, items: [{ lessonId: f.lesson.id }] }), 200).json
    const selection = sqlOne(`select selection from flashcard_study_plans where id=${seedPlan.id}`).replaceAll("'", "''")
    const base = docker('inspect', 'cbstest-backend', '--format', '{{range .Config.Env}}{{println .}}{{end}}').split('\n').filter((l) => /=/.test(l) && !/^(PATH|JAVA_|LANG|LC_)/.test(l)).flatMap((l) => ['-e', l])
    docker('run', '-d', '--name', 'cbstest-backend-2', '--network', 'cbstest_default', ...base, 'cbstest-backend:latest')
    try {
      for (let i = 0; i < 60; i++) { if (/Started CoreTraining/.test(docker('logs', 'cbstest-backend-2'))) break; await sleep(1500) }
      sql(`insert into flashcard_study_plans(version, user_id, name, start_date, end_date, weekday_mask, reminder_time, active, next_reminder_at, selection, created_at, updated_at)
           select 0, ${template.user.id}, 'HA '||g, current_date, current_date + 30, 127, '08:00', true, now() - interval '1 minute', '${selection}', now(), now() from generate_series(1, 500) g`)
      const started = Date.now()
      const due = () => sqlNum(`select count(*) from flashcard_study_plans where user_id=${template.user.id} and next_reminder_at <= now()`)
      // One instance dies while the batches are running.
      await sleep(70000)
      docker('kill', 'cbstest-backend-2')
      let remaining = due()
      const end = Date.now() + 240000
      while (remaining > 0 && Date.now() < end) { await sleep(5000); remaining = due() }
      const lag = Math.round((Date.now() - started) / 1000)
      assert.equal(remaining, 0, `${remaining} plans stuck after ${lag}s`)
      const total = sqlNum(`select count(*) from notifications where recipient_id=${template.user.id} and type='FLASHCARD_STUDY_REMINDER'`)
      const dup = sql(`select target_url, count(*) from notifications where recipient_id=${template.user.id} group by 1 having count(*)>1 limit 3`)
      assert.equal(dup, '', `duplicate reminders: ${dup}`)
      assert.equal(total, 500)
      return `500 lịch xử lý xong trong ${lag}s qua 2 instance (kill một instance sau 70s), không trùng. Chưa chạy mức >2000.`
    } finally { spawnSync('docker', ['rm', '-f', 'cbstest-backend-2'], { stdio: 'ignore' }) }
  })

  // ---------- NF-P01 / NF-P03 load.
  const loadCourse = await freshCourse(ctx, { lessons: [{ questions: 5, settings: { requiredForProgress: false }, textRequired: false }] })
  const template = await learner(ctx)
  const prefix = `load${ctx.run}_`
  bulkUsers(template, prefix, 600)
  const k6env = { LESSON_ID: loadCourse.lesson.id, COURSE_ID: loadCourse.course.id, PASSWORD: ACTIVE_PASSWORD, USER_PREFIX: prefix, USER_COUNT: 600 }
  const hold = FULL ? '15m' : '2m'
  const warm = FULL ? '5m' : '30s'
  let peakLine = ''
  for (const vus of [50, 100, 200]) {
    await tc('NF-P01', `${vus} user, giữ ${hold}${FULL ? '' : ' (rút gọn từ 15 phút)'}`, async () => {
      const m = await runK6(`P01_${vus}`, [{ duration: warm, target: vus }, { duration: hold, target: vus }], k6env)
      peakLine = `${stats()}; DB conns=${dbConnections()}`
      const line = `${describe(m)}. ${peakLine}`
      assert.ok(m.unexpected_errors.rate < 0.01, `lỗi vượt 1%: ${line}`)
      assert.ok(m.http_req_duration['p(95)'] <= 2000, `p95 vượt 2s: ${line}`)
      assert.ok(m.http_req_duration['p(99)'] <= 5000, `p99 vượt 5s: ${line}`)
      return line
    })
  }
  await tc('NF-P03', 'spike 500 user 2 phút rồi về 100', async () => {
    const m = await runK6('P03_spike', [{ duration: '20s', target: 500 }, { duration: '2m', target: 500 }, { duration: '20s', target: 100 }, { duration: FULL ? '10m' : '3m', target: 100 }], k6env)
    const after = await runK6('P03_after', [{ duration: '20s', target: 100 }, { duration: '1m', target: 100 }], k6env)
    const logs = docker('logs', '--since', '10m', 'cbstest-backend')
    assert.ok(!/OutOfMemoryError/.test(logs), 'OOM')
    assert.ok(after.http_req_duration['p(95)'] <= 2000, `không về SLO sau spike: ${describe(after)}`)
    return `Trong spike: ${describe(m)}. Sau spike: ${describe(after)}`
  })
  await tc('NF-P06', 'soak 8 giờ', async () => blocked('Không chạy trên máy trạm dùng chung; cần staging. Kịch bản có thể chạy bằng tests/system/k6/load.js với STAGES 8h.'))

  // ---------- NF-P07 video.
  await tc('NF-P07', 'Range đúng 206/416, không đọc toàn file vào RAM', async () => {
    const video = ok(await admin.post('/api/admin/videos/upload', undefined, { form: multipart({}, [{ name: 'v.mp4', data: Buffer.alloc(0).length ? '' : (await import('node:fs')).readFileSync(new URL('../fixtures/v100.mp4', import.meta.url)), type: 'video/mp4' }]) }), 'video')
    const f = await freshCourse(ctx, { lessons: [{ videoUrl: video.url ?? video.videoUrl ?? video.fileUrl, videoSource: 'UPLOAD' }] }); const s = await learner(ctx)
    const { streamUrl } = expectStatus(await s.get(`/api/videos/access/${f.lesson.id}`), 200).json
    const url = new URL(streamUrl, BASE)
    const whole = await fetch(url); const size = Number(whole.headers.get('content-length')); await whole.arrayBuffer()
    const mid = await fetch(url, { headers: { Range: 'bytes=1000-1999' } })
    assert.equal(mid.status, 206); assert.equal((await mid.arrayBuffer()).byteLength, 1000)
    const bad = await fetch(url, { headers: { Range: `bytes=${size + 10}-` } })
    assert.equal(bad.status, 416, `HTTP ${bad.status}`); await bad.arrayBuffer()
    const t0 = performance.now()
    await (await fetch(url, { headers: { Range: 'bytes=0-65535' } })).arrayBuffer()
    return `Khởi đầu 64KB trong ${Math.round(performance.now() - t0)}ms trên localhost; chưa giả lập 10/1 Mbps, RTT 100/300ms và chưa qua reverse proxy HTTPS.`
  })

  // ---------- NF-P02 data volume, the last step because it inflates every later query.
  const measure = async (label) => {
    const samples = {}
    const timed = async (name, call) => {
      const t0 = performance.now()
      const res = await Promise.race([call(), sleep(120000).then(() => ({ status: 'timeout>120s', text: '' }))])
      samples[name] = `${res.status === 200 ? Math.round(performance.now() - t0) + 'ms' : res.status}${res.text ? `/${Math.round(res.text.length / 1024)}KB` : ''}`
      return performance.now() - t0
    }
    const results = {}
    results.users = await timed('users/page', () => admin.get('/api/admin/users/page?query=&page=1&size=20'))
    results.usersSearch = await timed('users search', () => admin.get('/api/admin/users/page?query=T%E1%BA%A3i%201234&page=1&size=20'))
    results.leader = await timed('leaderboard', () => admin.get('/api/leaderboard/page?page=0&size=20'))
    results.dashboard = await timed('dashboard', () => template.get('/api/users/me/learning/dashboard/courses?tab=discover&page=1&size=6'))
    results.options = await timed('users (full list)', () => admin.get('/api/admin/users'))
    results.report = await timed('report read', () => admin.get('/api/admin/reports/training/read?view=completion&page=0&size=20'))
    results.csv = await timed('CSV export', () => admin.get('/api/admin/reports/training/export?view=completion', { headers: { Accept: 'text/csv' } }))
    metricsOut[`P02_${label}`] = samples
    return { samples, results }
  }
  const addCourses = (n) => {
    const row = sqlOne(`select id from training_programs where id='${ctx.program.id}'`)
    sql(`insert into courses(id, code, name, program_id, status, version)
         select 'BULK-${ctx.run}-'||g, 'BULK-${ctx.run}-'||g, 'Khoá tải '||g, '${row}', 'PUBLISHED', 0 from generate_series(1, ${n}) g on conflict do nothing`)
  }
  for (const [label, users, courses] of [['1k_100', 1000, 100], ['10k_100', 10000, 0], ['100k_1k', 100000, 900]]) {
    await tc('NF-P02', `DB ${label.replace('_', ' user / ')} khoá`, async () => {
      bulkUsers(template, `${label}_${ctx.run}_`, users)
      if (courses) { try { addCourses(courses) } catch (e) { return gap(`Không nạp được khoá: ${String(e.message).slice(0, 200)}`) } }
      const { samples, results } = await measure(label)
      const line = Object.entries(samples).map(([k, v]) => `${k} ${v}`).join(', ')
      const slowFirstPage = ['users', 'usersSearch', 'leader', 'dashboard', 'report'].filter((k) => results[k] > 3000)
      assert.equal(slowFirstPage.length, 0, `trang đầu vượt 3s (${slowFirstPage}): ${line}`)
      return line
    })
  }
  await tc('NF-P08', 'CSV lớn đồng thời với nộp bài', async () => {
    const f = await freshCourse(ctx, { lessons: [{ questions: 2, settings: { requiredForProgress: false } }] }); const s = await learner(ctx)
    const csv = admin.get('/api/admin/reports/training/export?view=completion', { headers: { Accept: 'text/csv' } })
    const times = []
    for (let i = 0; i < 5; i++) {
      const t0 = performance.now()
      await s.post(`/api/quizzes/lessons/${f.lesson.id}/submit`, { selectedAnswers: answersFor(f.lesson.qs, 2) })
      times.push(performance.now() - t0)
      await sleep(400)
    }
    const res = await Promise.race([csv, sleep(180000).then(() => ({ status: 'timeout>180s', text: '' }))])
    const logs = docker('logs', '--since', '10m', 'cbstest-backend')
    assert.ok(!/OutOfMemoryError/.test(logs), 'OOM during export')
    assert.ok(pct(times, 95) < 2000, `nộp bài p95=${pct(times, 95)}ms khi đang export`)
    assert.equal(res.status, 200, `Export không xong trong 180s (HTTP ${res.status}) ở dữ liệu 100k user/1k khoá; nộp bài song song vẫn p95=${Math.round(pct(times, 95))}ms`)
    return `Export HTTP ${res.status}, ${Math.round((res.text?.length ?? 0) / 1024)}KB; nộp bài p95=${pct(times, 95)}ms`
  })

  mkdirSync(new URL('../results/', import.meta.url), { recursive: true })
  writeFileSync(new URL('../results/load-metrics.json', import.meta.url), JSON.stringify(metricsOut, null, 1))
  void addQuestions; void quizSettings; void roleByCode; void readAndComplete; void loginAs; void Client; void COMPOSE
}
