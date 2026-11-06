// Browser-pass helper: locks or unlocks a seeded test account. Usage: node ui-lock.mjs <username> <LOCKED|ACTIVE>
import { loginAs, sqlOne } from './lib.mjs'
import { currentUser, updateUser, ADMIN_PASSWORD } from './seed.mjs'

const [username, status] = process.argv.slice(2)
const admin = await loginAs('admin', ADMIN_PASSWORD)
const id = Number(sqlOne(`select id from users where username='${username}'`))
const res = await updateUser(admin, { id, username }, { status })
console.log(res.status, new Date().toISOString())
void currentUser
