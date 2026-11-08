// Shared helpers for the system test suite. Everything talks ONLY to the isolated
// cbstest stack; the guard below refuses to run against anything else.
import { execFileSync } from 'node:child_process'
import assert from 'node:assert/strict'

export const BASE = process.env.TEST_BASE_URL ?? 'http://127.0.0.1:19000'
export const MAIL = 'http://127.0.0.1:18025'
export const DB_CONTAINER = 'cbstest-db'
if (!/:19000$/.test(BASE) && !/:15173$/.test(BASE)) throw new Error(`Refusing to test non-isolated target ${BASE}`)

export class Client {
  constructor(label = 'anon') {
    this.label = label
    this.cookies = new Map()
  }
  absorb(res) {
    for (const line of res.headers.getSetCookie?.() ?? []) {
      const [pair, ...attrs] = line.split(';')
      const i = pair.indexOf('=')
      const name = pair.slice(0, i).trim()
      const value = pair.slice(i + 1).trim()
      const maxAge0 = attrs.some((a) => /max-age=0/i.test(a))
      if (maxAge0 || value === '') this.cookies.delete(name)
      else this.cookies.set(name, value)
    }
  }
  cookieHeader() {
    return [...this.cookies].map(([k, v]) => `${k}=${v}`).join('; ')
  }
  async ensureCsrf() {
    if (this.cookies.has('XSRF-TOKEN')) return
    const res = await fetch(`${BASE}/api/auth/csrf`, { headers: { Cookie: this.cookieHeader() } })
    this.absorb(res)
  }
  async req(method, path, body, opts = {}) {
    const headers = { Accept: 'application/json', ...(opts.headers ?? {}) }
    const write = !['GET', 'HEAD'].includes(method)
    if (write && opts.csrf !== false) {
      await this.ensureCsrf()
      headers['X-XSRF-TOKEN'] = this.cookies.get('XSRF-TOKEN')
    }
    const cookie = opts.cookie ?? this.cookieHeader()
    if (cookie) headers.Cookie = cookie
    let payload
    if (opts.form) payload = opts.form
    else if (opts.raw !== undefined) {
      payload = opts.raw
      headers['Content-Type'] ??= 'application/json'
    } else if (body !== undefined) {
      payload = JSON.stringify(body)
      headers['Content-Type'] = 'application/json'
    }
    const started = performance.now()
    const res = await fetch(`${BASE}${path}`, { method, headers, body: payload, redirect: 'manual' })
    this.absorb(res)
    const buffer = Buffer.from(await res.arrayBuffer())
    // A string cannot hold the largest exports (over 512 MB); callers measure those with `buffer`.
    const text = buffer.length > 256 * 1024 * 1024 ? '' : buffer.toString('utf8')
    let json
    try { json = JSON.parse(text) } catch { json = undefined }
    return { status: res.status, json, text, buffer, headers: res.headers, ms: performance.now() - started }
  }
  get(p, o) { return this.req('GET', p, undefined, o) }
  post(p, b, o) { return this.req('POST', p, b, o) }
  put(p, b, o) { return this.req('PUT', p, b, o) }
  patch(p, b, o) { return this.req('PATCH', p, b, o) }
  del(p, o) { return this.req('DELETE', p, undefined, o) }
  async login(username, password) {
    await this.ensureCsrf()
    const res = await this.post('/api/auth/login', { username, password })
    if (res.status !== 200) throw new Error(`login ${username}: ${res.status} ${res.text.slice(0, 200)}`)
    this.username = username
    this.password = password
    return res
  }
  // The full cookie header, for replaying an old session later.
  snapshot() { return this.cookieHeader() }
}

export async function loginAs(username, password) {
  const client = new Client(username)
  await client.login(username, password)
  return client
}

export function sql(query) {
  return execFileSync('docker', ['exec', '-i', DB_CONTAINER, 'psql', '-U', 'test', '-d', 'coretraining_test', '-AtX', '-F', '|', '-v', 'ON_ERROR_STOP=1'], { input: query, encoding: 'utf8', maxBuffer: 1 << 26 }).trim()
}
export const sqlOne = (q) => sql(q).split('\n')[0]
export const sqlNum = (q) => Number(sqlOne(q))

export async function mails() { return (await fetch(`${MAIL}/messages`)).json() }
export async function clearMails() { await fetch(`${MAIL}/messages`, { method: 'DELETE' }) }
export async function otpFor(email, previousCount = 0) {
  for (let i = 0; i < 40; i++) {
    const list = (await mails()).filter((m) => m.to.includes(email))
    if (list.length > previousCount) {
      const m = decodeBody(list.at(-1).raw).match(/\b(\d{6})\b/)
      if (m) return m[1]
    }
    await sleep(150)
  }
  throw new Error(`no OTP mail for ${email}`)
}
function decodeBody(raw) {
  const body = raw.split('\n\n').slice(1).join('\n\n')
  if (/content-transfer-encoding:\s*base64/i.test(raw)) {
    return Buffer.from(body.replace(/\s/g, ''), 'base64').toString('utf8')
  }
  return body.replace(/=\r?\n/g, '')
}

// ---- Result recording, one row per case/variant (template in section 8 of the scenarios doc).
export const results = []
export const PASS = 'Pass', FAIL = 'Fail', BLOCKED = 'Blocked', NA = 'N/A', GAP = 'Gap'

export async function tc(id, variant, fn) {
  const row = { id, variant, status: PASS, actual: '' }
  try {
    const out = await fn()
    if (out && typeof out === 'object' && [GAP, BLOCKED, NA].includes(out.status)) {
      row.status = out.status
      row.actual = out.actual ?? ''
    } else row.actual = typeof out === 'string' ? out : ''
  } catch (e) {
    row.status = FAIL
    row.actual = String(e?.message ?? e).replace(/\s+/g, ' ').slice(0, 500)
  }
  results.push(row)
  const mark = { Pass: '.', Fail: 'F', Blocked: 'B', 'N/A': 'n', Gap: 'G' }[row.status]
  process.stdout.write(row.status === PASS ? mark : `\n${mark} ${id} [${variant}] ${row.actual}\n`)
  return row.status === PASS
}
export const record = (id, variant, status, actual) => tc(id, variant, async () => ({ status, actual }))
export const gap = (actual) => ({ status: GAP, actual })
export const blocked = (actual) => ({ status: BLOCKED, actual })
export const na = (actual) => ({ status: NA, actual })

export function expectStatus(res, allowed, context = '') {
  const list = Array.isArray(allowed) ? allowed : [allowed]
  assert.ok(list.includes(res.status), `${context} expected HTTP ${list.join('/')} got ${res.status}: ${res.text?.slice(0, 220)}`)
  return res
}
// Rejected by a business/authorization rule: 4xx, never 2xx/5xx, and not a CSRF mistake.
export function expectRejected(res, context = '') {
  assert.ok(res.status >= 400 && res.status < 500, `${context} expected 4xx got ${res.status}: ${res.text?.slice(0, 220)}`)
  return res
}
export { assert }
export const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
export const uniq = (() => { let n = 0; return (p = 'x') => `${p}${Date.now().toString(36)}${(n++).toString(36)}` })()
export function multipart(fields, files) {
  const form = new FormData()
  for (const [k, v] of Object.entries(fields ?? {})) form.append(k, v)
  for (const f of files ?? []) form.append(f.field ?? 'file', new Blob([f.data], { type: f.type ?? 'application/octet-stream' }), f.name)
  return form
}
