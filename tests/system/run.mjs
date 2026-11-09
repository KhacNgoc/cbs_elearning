// Entry point: node tests/system/run.mjs [suite ...]
// Requires the isolated stack: docker compose -p cbstest -f tests/system/docker-compose.test.yml up -d
import { writeFileSync, mkdirSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { results } from './lib.mjs'
import { seed } from './seed.mjs'

const all = ['auth', 'rbac', 'structure', 'enrollment', 'learning', 'quiz', 'flashcard', 'social', 'reports', 'ai', 'security', 'e2e', 'resilience', 'nonfunctional']
const wanted = process.argv.slice(2).length ? process.argv.slice(2) : all
const started = new Date()
const ctx = await seed()
console.log(`seeded run ${ctx.run}`)

mkdirSync(new URL('./results/', import.meta.url), { recursive: true })
const out = new URL(`./results/${wanted.length === all.length ? 'all' : wanted.join('-')}.json`, import.meta.url)
const save = () => writeFileSync(out, JSON.stringify({
  commit: execFileSync('git', ['rev-parse', '--short', 'HEAD'], { encoding: 'utf8' }).trim(),
  started: started.toISOString(), finished: new Date().toISOString(), results,
}, null, 1))

for (const name of wanted) {
  console.log(`\n== ${name}`)
  try {
    const suite = await import(`./suites/${name}.mjs`)
    await suite.run(ctx)
  } catch (e) {
    console.log(`\nSUITE ERROR ${name}: ${e?.stack ?? e}`)
    results.push({ id: `SUITE-${name}`, variant: 'suite aborted', status: 'Blocked', actual: String(e?.message ?? e).slice(0, 400) })
  }
  save()
}
const counts = results.reduce((m, r) => ({ ...m, [r.status]: (m[r.status] ?? 0) + 1 }), {})
console.log('\n', counts)
