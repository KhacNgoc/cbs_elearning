// 4.1 Authentication, sessions, accounts (F-A01..A09) and auth-related security (NF-S01/S02/S08).
import { execFileSync } from 'node:child_process'
import { Client, BASE, tc, assert, expectStatus, expectRejected, sql, sqlOne, sqlNum, otpFor, mails, sleep, uniq, gap, loginAs } from '../lib.mjs'
import { createUser, currentUser, learner, USER_PASSWORD, ACTIVE_PASSWORD, ok } from '../seed.mjs'

const NEW_PW = 'Brand-New-Pass-9'
const otpRow = (user) => `user_id=${user.id}`
const forgot = (c, user) => c.post('/api/auth/forgot-password-otp', { username: user.username, email: user.email })
const reset = (c, user, otp, newPassword = NEW_PW) => c.post('/api/auth/reset-password-otp', { username: user.username, otp, newPassword })
const mailCount = async (email) => (await mails()).filter((m) => m.to.includes(email)).length
const canLogin = async (username, password) => (await new Client().post('/api/auth/login', { username, password })).status === 200

export async function run(ctx) {
  const { admin } = ctx
  const anon = new Client()

  await tc('F-A01', 'đăng nhập đúng, session, không trả token trong body', async () => {
    const c = await learner(ctx)
    const res = await c.login(c.user.username, ACTIVE_PASSWORD)
    assert.equal(res.json.accessToken ?? null, null, 'login body leaks token')
    assert.ok(!/"password"/i.test(res.text), 'login body contains a password field')
    const s = expectStatus(await c.get('/api/auth/session'), 200)
    assert.equal(s.json.username, c.user.username)
    assert.ok(s.json.permissions.includes('LEARN'))
    assert.ok(!s.json.permissions.includes('USER_MANAGE'), 'learner got admin permission')
  })

  const longName = 'a'.repeat(5000)
  for (const [name, body, pwd] of [
    ['sai mật khẩu', { username: 'admin', password: 'wrong-password' }],
    ['username không tồn tại', { username: uniq('nobody'), password: 'whatever-1' }],
    ['username rỗng', { username: '', password: 'x' }],
    ['password rỗng', { username: 'admin', password: '' }],
    ['khoảng trắng', { username: '   ', password: '   ' }],
    ['chuỗi rất dài', { username: longName, password: longName }],
    ['thiếu trường', {}],
  ]) {
    await tc('F-A02', name, async () => {
      const c = new Client()
      const res = expectRejected(await c.post('/api/auth/login', body), name)
      assert.ok(!c.cookies.has('token') && !/\$2[aby]\$/.test(res.text), 'session or hash leaked')
      assert.ok(![...c.cookies.keys()].some((k) => /jwt|auth|token$/i.test(k) && k !== 'XSRF-TOKEN'), 'auth cookie issued')
    })
  }

  await tc('F-A03', 'chặn API nghiệp vụ khi mustChangePassword; đổi hợp lệ thì dùng được', async () => {
    const user = await createUser(admin, { unitId: ctx.A.id })
    const c = await loginAs(user.username, USER_PASSWORD)
    const s = await c.get('/api/auth/session')
    assert.equal(s.json.mustChangePassword, true)
    for (const path of ['/api/users/me/learning', '/api/courses', '/api/notifications']) {
      const blockedRes = await c.get(path)
      assert.ok(blockedRes.status === 403 || blockedRes.status === 401, `${path} returned ${blockedRes.status} while password change is pending`)
    }
    expectStatus(await c.post('/api/users/me/change-password', { newPassword: NEW_PW }), 200)
    const c2 = await loginAs(user.username, NEW_PW)
    expectStatus(await c2.get('/api/users/me/learning'), 200)
    assert.equal(await canLogin(user.username, USER_PASSWORD), false, 'old password still works')
  })

  await tc('F-A04', 'OTP: reset 1 lần, huỷ OTP, mật khẩu và token cũ mất hiệu lực', async () => {
    const c = await learner(ctx)
    const oldCookie = c.snapshot()
    const before = await mailCount(c.user.email)
    expectStatus(await forgot(new Client(), c.user), 200)
    const otp = await otpFor(c.user.email, before)
    expectStatus(await reset(new Client(), c.user, otp), 200)
    assert.equal(await canLogin(c.user.username, NEW_PW), true, 'new password rejected')
    assert.equal(await canLogin(c.user.username, ACTIVE_PASSWORD), false, 'old password accepted')
    expectRejected(await reset(new Client(), c.user, otp, 'Another-Pass-77'), 'reused OTP')
    assert.equal(await canLogin(c.user.username, 'Another-Pass-77'), false, 'OTP reused')
    const replay = await new Client().get('/api/auth/session', { cookie: oldCookie })
    assert.equal(replay.status, 401, `old token after reset returned ${replay.status}`)
  })

  await tc('F-A05', 'khoá tài khoản thu hồi phiên ở mọi tab', async () => {
    const c = await learner(ctx)
    const tab2 = await loginAs(c.user.username, ACTIVE_PASSWORD)
    const dto = await currentUser(admin, c.user)
    expectStatus(await admin.put(`/api/admin/users/${c.user.id}`, { ...dto, status: 'LOCKED' }), 200)
    for (const client of [c, tab2]) {
      const res = await client.get('/api/users/me/learning')
      assert.ok(res.status === 401 || res.status === 403, `locked session returned ${res.status}`)
    }
    assert.equal(await canLogin(c.user.username, ACTIVE_PASSWORD), false, 'locked account can log in')
  })

  await tc('F-A05', 'đăng xuất thu hồi mọi phiên; phát lại cookie cũ bị từ chối', async () => {
    const c = await learner(ctx)
    const old = c.snapshot()
    const other = await loginAs(c.user.username, ACTIVE_PASSWORD)
    expectStatus(await c.post('/api/auth/logout'), 204)
    assert.equal((await new Client().get('/api/auth/session', { cookie: old })).status, 401)
    assert.equal((await other.get('/api/auth/session')).status, 401, 'second tab still valid after logout')
  })

  await tc('F-A06', 'OTP trước hạn 5 phút hợp lệ', async () => {
    const c = await learner(ctx)
    const before = await mailCount(c.user.email)
    await forgot(new Client(), c.user)
    const otp = await otpFor(c.user.email, before)
    sql(`update password_reset_otps set expiry_date = (now() at time zone 'Asia/Ho_Chi_Minh') + interval '5 seconds' where ${otpRow(c.user)}`)
    expectStatus(await reset(new Client(), c.user, otp), 200)
  })

  await tc('F-A06', 'OTP sau hạn 5 phút bị từ chối, mật khẩu không đổi', async () => {
    const c = await learner(ctx)
    const before = await mailCount(c.user.email)
    await forgot(new Client(), c.user)
    const otp = await otpFor(c.user.email, before)
    sql(`update password_reset_otps set expiry_date = (now() at time zone 'Asia/Ho_Chi_Minh') - interval '1 second' where ${otpRow(c.user)}`)
    expectRejected(await reset(new Client(), c.user, otp))
    assert.equal(await canLogin(c.user.username, ACTIVE_PASSWORD), true, 'password changed by expired OTP')
  })

  await tc('F-A06', 'sai 5 lần rồi nhập đúng vẫn bị khoá', async () => {
    const c = await learner(ctx)
    const before = await mailCount(c.user.email)
    await forgot(new Client(), c.user)
    const otp = await otpFor(c.user.email, before)
    const wrong = otp === '000000' ? '111111' : '000000'
    for (let i = 0; i < 5; i++) expectRejected(await reset(new Client(), c.user, wrong))
    assert.equal(sqlNum(`select attempts from password_reset_otps where ${otpRow(c.user)}`), 5)
    expectRejected(await reset(new Client(), c.user, otp), 'correct OTP after 5 failures')
    assert.equal(await canLogin(c.user.username, ACTIVE_PASSWORD), true)
  })

  await tc('F-A06', 'xin lại OTP thì mã cũ mất hiệu lực', async () => {
    const c = await learner(ctx)
    const before = await mailCount(c.user.email)
    await forgot(new Client(), c.user)
    const oldOtp = await otpFor(c.user.email, before)
    // The resend throttle is 1 minute of OTP age; move the clock on the isolated DB only.
    sql(`update password_reset_otps set expiry_date = (now() at time zone 'Asia/Ho_Chi_Minh') + interval '3 minutes' where ${otpRow(c.user)}`)
    const mid = await mailCount(c.user.email)
    await forgot(new Client(), c.user)
    const newOtp = await otpFor(c.user.email, mid)
    if (newOtp !== oldOtp) expectRejected(await reset(new Client(), c.user, oldOtp), 'old OTP')
    expectStatus(await reset(new Client(), c.user, newOtp), 200)
  })

  await tc('F-A07', 'email sai/không tồn tại: thông báo không lộ tài khoản', async () => {
    const c = await learner(ctx)
    const real = await forgot(new Client(), c.user)
    const wrongEmail = await new Client().post('/api/auth/forgot-password-otp', { username: c.user.username, email: 'other@test.local' })
    const noUser = await new Client().post('/api/auth/forgot-password-otp', { username: uniq('nobody'), email: 'other@test.local' })
    assert.equal(wrongEmail.status, real.status, 'status reveals account/email match')
    assert.equal(noUser.status, real.status, 'status reveals username existence')
    assert.equal(wrongEmail.text, real.text)
    assert.equal(noUser.text, real.text)
  })

  await tc('F-A07', 'SMTP tắt: báo lỗi rõ, không báo gửi thành công', async () => {
    const c = await learner(ctx)
    execFileSync('docker', ['stop', 'cbstest-mail'], { stdio: 'ignore' })
    try {
      const res = await forgot(new Client(), c.user)
      assert.ok(res.status >= 500 || res.status === 503 || res.status === 400, `SMTP down still answered ${res.status}: ${res.text.slice(0, 120)}`)
      assert.ok(!/\b(jakarta|smtp|exception|stack)\b/i.test(res.text), 'internal error leaked')
    } finally {
      execFileSync('docker', ['start', 'cbstest-mail'], { stdio: 'ignore' })
      await sleep(800)
    }
  })

  for (const [name, client] of [['không đăng nhập', anon], ['có đăng nhập', admin]]) {
    await tc('F-A08', `register ${name}`, async () => {
      const username = uniq('reg')
      const res = await client.post('/api/auth/register', { username, password: 'Some-Pass-123', fullName: 'x', email: `${username}@test.local` })
      assert.ok(res.status === 401 || res.status === 403, `register returned ${res.status}`)
      assert.equal(sqlNum(`select count(*) from users where username='${username}'`), 0)
    })
    await tc('F-A08', `reset-password-direct ${name}`, async () => {
      const res = await client.post('/api/auth/reset-password-direct', { username: 'quantrh', newPassword: 'Some-Pass-123', employeeId: 'x', email: 'x@test.local' })
      assert.ok(res.status === 401 || res.status === 403, `reset-direct returned ${res.status}`)
      assert.equal(await canLogin('quantrh', 'Some-Pass-123'), false)
    })
  }

  for (const [pw, accept] of [['1234567', false], ['12345678', true], ['', false]]) {
    await tc('F-A09', `đổi mật khẩu độ dài ${pw.length}`, async () => {
      const c = await learner(ctx)
      const res = await c.post('/api/users/me/change-password', { newPassword: pw })
      if (accept) expectStatus(res, 200)
      else expectRejected(res)
    })
  }
  await tc('F-A09', 'reset qua OTP phải áp cùng chính sách 8 ký tự', async () => {
    const c = await learner(ctx)
    const before = await mailCount(c.user.email)
    await forgot(new Client(), c.user)
    const otp = await otpFor(c.user.email, before)
    const res = await reset(new Client(), c.user, otp, '1234567')
    assert.ok(res.status >= 400, `7-character password accepted via OTP reset (policy min 8): HTTP ${res.status}`)
  })
  await tc('F-A09', 'hồ sơ: sửa trường hợp lệ; chèn role/unit/points không có hiệu lực', async () => {
    const c = await learner(ctx)
    const res = await c.patch('/api/users/me/profile', { fullName: 'Tên mới', email: `${c.user.username}.new@test.local`, role: 'ROLE_ADMIN', roles: ['ROLE_ADMIN'], unitId: ctx.B.id, points: 99999, status: 'ACTIVE', scopeUnitIds: [ctx.B.id] })
    expectStatus(res, 200)
    const row = sqlOne(`select role, points, unit_id, full_name from users where id=${c.user.id}`).split('|')
    assert.notEqual(row[0], 'ROLE_ADMIN'); assert.equal(Number(row[1]), 0); assert.equal(row[2], ctx.A.id); assert.equal(row[3], 'Tên mới')
    const s = await c.get('/api/auth/session')
    assert.equal(s.json.systemAdmin ?? false, false)
  })

  // ---- NF-S01: token handling.
  const protectedPaths = ['/api/users/me', '/api/courses', '/api/admin/users', '/api/notifications', '/api/users/me/flashcard-plans', '/uploads/documents/x.pdf']
  for (const path of protectedPaths) {
    await tc('NF-S01', `không cookie: ${path}`, async () => {
      const res = await anon.get(path)
      assert.ok(res.status === 401 || res.status === 403, `returned ${res.status}`)
    })
  }
  const adminCookie = admin.cookies.get('accessToken') ?? [...admin.cookies].find(([k]) => k !== 'XSRF-TOKEN')?.[1]
  const cookieName = [...admin.cookies.keys()].find((k) => k !== 'XSRF-TOKEN')
  const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url')
  const parts = adminCookie.split('.')
  const payload = JSON.parse(Buffer.from(parts[1], 'base64url').toString())
  const forged = {
    'token sai chữ ký': `${parts[0]}.${parts[1]}.${parts[2].slice(0, -4)}AAAA`,
    'alg none': `${b64({ alg: 'none' })}.${parts[1]}.`,
    'token hết hạn giả exp': `${parts[0]}.${b64({ ...payload, exp: 1 })}.${parts[2]}`,
    'sửa tokenVersion': `${parts[0]}.${b64({ ...payload, ver: (payload.ver ?? 0) + 1 })}.${parts[2]}`,
    'rác': 'not-a-jwt',
  }
  for (const [name, token] of Object.entries(forged)) {
    await tc('NF-S01', name, async () => {
      const res = await new Client().get('/api/admin/users', { cookie: `${cookieName}=${token}` })
      assert.ok(res.status === 401 || res.status === 403, `returned ${res.status}`)
    })
  }

  // ---- NF-S02: CSRF and CORS.
  await tc('NF-S02', 'ghi thiếu CSRF bị chặn', async () => {
    const res = await admin.req('POST', '/api/admin/training-domains', { code: uniq('NOCSRF'), name: 'x' }, { csrf: false })
    assert.equal(res.status, 403)
  })
  await tc('NF-S02', 'ghi sai CSRF bị chặn', async () => {
    const code = uniq('BADCSRF')
    const res = await admin.req('POST', '/api/admin/training-domains', { code, name: 'x' }, { csrf: false, headers: { 'X-XSRF-TOKEN': 'wrong' } })
    assert.equal(res.status, 403)
    assert.equal(sqlNum(`select count(*) from training_domains where code='${code}'`), 0)
  })
  await tc('NF-S02', 'token CSRF trước login không còn hợp lệ sau login', async () => {
    const c = await learner(ctx)
    const fresh = new Client()
    await fresh.ensureCsrf()
    const before = fresh.cookies.get('XSRF-TOKEN')
    await fresh.login(c.user.username, ACTIVE_PASSWORD)
    await fresh.ensureCsrf()
    assert.notEqual(fresh.cookies.get('XSRF-TOKEN'), before, 'CSRF token not rotated on login')
    const stale = await fresh.req('PATCH', '/api/users/me/profile', { fullName: 'x' }, { csrf: false, headers: { 'X-XSRF-TOKEN': before } })
    assert.equal(stale.status, 403, `stale CSRF returned ${stale.status}`)
  })
  await tc('NF-S02', 'CORS không cấp credentials cho origin lạ', async () => {
    const res = await fetch(`${BASE}/api/users/me`, { method: 'OPTIONS', headers: { Origin: 'https://evil.example', 'Access-Control-Request-Method': 'GET' } })
    assert.equal(res.headers.get('access-control-allow-origin'), null)
    assert.ok(res.status === 403 || res.headers.get('access-control-allow-credentials') !== 'true')
  })
  await tc('NF-S02', 'CORS cho phép origin đã cấu hình', async () => {
    const res = await fetch(`${BASE}/api/users/me`, { method: 'OPTIONS', headers: { Origin: 'http://localhost:15173', 'Access-Control-Request-Method': 'GET' } })
    assert.equal(res.headers.get('access-control-allow-origin'), 'http://localhost:15173')
    assert.equal(res.headers.get('access-control-allow-credentials'), 'true')
  })

  // ---- NF-S08: cookies and leak checks.
  await tc('NF-S08', 'cookie auth HttpOnly + SameSite; login không trả JWT', async () => {
    const c = new Client()
    await c.ensureCsrf()
    const res = await fetch(`${BASE}/api/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': c.cookies.get('XSRF-TOKEN'), Cookie: c.cookieHeader() }, body: JSON.stringify({ username: 'admin', password: 'Test-Admin-Pass-1' }) })
    const set = res.headers.getSetCookie().find((l) => !l.startsWith('XSRF-TOKEN'))
    assert.match(set, /HttpOnly/i); assert.match(set, /SameSite=Lax/i)
    const body = await res.text()
    assert.ok(!/eyJ[A-Za-z0-9_-]{10,}\./.test(body), 'JWT in login body')
  })
  await tc('NF-S08', 'cờ Secure trên HTTPS production-like', async () => gap('Stack test chạy HTTP với JWT_COOKIE_SECURE=false; Secure chỉ kiểm được khi có HTTPS (cần staging). Giá trị mặc định trong .env.production là false.'))
  await tc('NF-S08', 'log không chứa mật khẩu/OTP/token', async () => {
    const logs = execFileSync('docker', ['logs', 'cbstest-backend'], { encoding: 'utf8', maxBuffer: 1 << 28, stdio: ['ignore', 'pipe', 'pipe'] })
    for (const secret of [USER_PASSWORD, NEW_PW, 'Test-Admin-Pass-1', adminCookie]) assert.ok(!logs.includes(secret), `log contains secret ${secret.slice(0, 5)}…`)
    assert.ok(!/otp[^a-z]{0,6}\d{6}/i.test(logs), 'log contains an OTP')
  })
}
