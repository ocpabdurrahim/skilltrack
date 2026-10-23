// firestore.rules against the Firestore emulator. Run from the repo root:
//   npx firebase-tools emulators:exec --only firestore --project demo-skilltrack "npm --prefix web run test:rules"
import {
  assertFails, assertSucceeds, initializeTestEnvironment, type RulesTestContext, type RulesTestEnvironment,
} from '@firebase/rules-unit-testing'
import {
  collection, collectionGroup, deleteDoc, doc, getDoc, getDocs, query, serverTimestamp, setDoc, Timestamp, where,
  writeBatch,
} from 'firebase/firestore'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest'

const PROJECT = 'demo-skilltrack'
const C = 'center1'

let env: RulesTestEnvironment

const user = (email: string, verified = true): RulesTestContext =>
  env.authenticatedContext(`uid-${email}`, { email, email_verified: verified })

const owner = () => user('owner@test.com')
const admin = () => user('admin@test.com')
const office = () => user('office@test.com')
const teacher = () => user('teacher@test.com')
const stranger = () => user('stranger@test.com')

const stamp = () => ({ deleted: false, updatedAt: serverTimestamp() })

const centerDoc = (uid: string, email: string) => ({
  name: 'Bright Future IT', tagline: '', address: '', phone: '', email: '', website: '', logo: '', signatory: 'Director',
  currencyCode: 'BDT', currencySymbol: '৳', grouping: 'SOUTH_ASIAN', studentPrefix: 'ST', receiptPrefix: 'MR',
  certificatePrefix: 'CERT', ownerUid: uid, ownerEmail: email, createdAt: 1, updatedAt: serverTimestamp(),
})

const member = (email: string, role: string, active = true) => ({
  email, name: email.split('@')[0], role, active, instructorId: null, addedBy: 'owner@test.com', createdAt: 1, ...stamp(),
})

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: PROJECT,
    firestore: { rules: readFileSync(resolve(__dirname, '../../firestore.rules'), 'utf8') },
  })
})

afterAll(async () => {
  await env?.cleanup()
})

/** A center with one member per role, written with rules switched off. */
async function seed() {
  await env.clearFirestore()
  await env.withSecurityRulesDisabled(async ctx => {
    const db = ctx.firestore()
    await setDoc(doc(db, 'centers', C), { ...centerDoc('uid-owner@test.com', 'owner@test.com'), updatedAt: Timestamp.now() })
    for (const [email, role, active] of [
      ['owner@test.com', 'OWNER', true], ['admin@test.com', 'ADMIN', true], ['office@test.com', 'OFFICE', true],
      ['teacher@test.com', 'TEACHER', true], ['gone@test.com', 'OFFICE', false],
    ] as const) {
      await setDoc(doc(db, 'centers', C, 'members', email), { ...member(email, role, active), updatedAt: Timestamp.now() })
    }
    await setDoc(doc(db, 'centers', C, 'payments', 'p1'), { amount: 100, deleted: false, updatedAt: Timestamp.now() })
    await setDoc(doc(db, 'centers', C, 'students', 's1'), { name: 'Rafi', deleted: false, updatedAt: Timestamp.now() })
  })
}

describe('creating a center', () => {
  beforeEach(() => env.clearFirestore())

  it('works as one batch of the center and the owner record', async () => {
    const db = user('new@test.com').firestore()
    const batch = writeBatch(db)
    batch.set(doc(db, 'centers', 'c2'), centerDoc('uid-new@test.com', 'new@test.com'))
    batch.set(doc(db, 'centers', 'c2', 'members', 'new@test.com'), member('new@test.com', 'OWNER'))
    await assertSucceeds(batch.commit())
    // ...after which the owner can work in it.
    await assertSucceeds(setDoc(doc(db, 'centers', 'c2', 'courses', 'k1'), { name: 'MS Office', ...stamp() }))
    await assertSucceeds(getDocs(collection(db, 'centers', 'c2', 'payments')))
  })

  it('needs the owner record in the same batch', async () => {
    const db = user('new@test.com').firestore()
    await assertFails(setDoc(doc(db, 'centers', 'c2'), centerDoc('uid-new@test.com', 'new@test.com')))
  })

  it('cannot be done for someone else, or without a verified email', async () => {
    const db = user('new@test.com').firestore()
    const batch = writeBatch(db)
    batch.set(doc(db, 'centers', 'c2'), centerDoc('uid-other', 'new@test.com'))
    batch.set(doc(db, 'centers', 'c2', 'members', 'new@test.com'), member('new@test.com', 'OWNER'))
    await assertFails(batch.commit())

    const unverified = user('new@test.com', false).firestore()
    const b2 = writeBatch(unverified)
    b2.set(doc(unverified, 'centers', 'c3'), centerDoc('uid-new@test.com', 'new@test.com'))
    b2.set(doc(unverified, 'centers', 'c3', 'members', 'new@test.com'), member('new@test.com', 'OWNER'))
    await assertFails(b2.commit())
  })
})

describe('an existing center', () => {
  beforeEach(seed)

  it('is closed to strangers', async () => {
    const db = stranger().firestore()
    await assertFails(getDoc(doc(db, 'centers', C)))
    await assertFails(getDocs(collection(db, 'centers', C, 'students')))
    await assertFails(setDoc(doc(db, 'centers', C, 'students', 's2'), { name: 'x', ...stamp() }))
    await assertFails(getDocs(collection(db, 'centers', C, 'members')))
  })

  it('cannot be taken over', async () => {
    const db = stranger().firestore()
    await assertFails(setDoc(doc(db, 'centers', C, 'members', 'stranger@test.com'), member('stranger@test.com', 'OWNER')))
    await assertFails(setDoc(doc(db, 'centers', C, 'members', 'stranger@test.com'), member('stranger@test.com', 'ADMIN')))
    const batch = writeBatch(db)
    batch.set(doc(db, 'centers', C), centerDoc('uid-stranger@test.com', 'stranger@test.com'))
    batch.set(doc(db, 'centers', C, 'members', 'stranger@test.com'), member('stranger@test.com', 'OWNER'))
    await assertFails(batch.commit())
  })

  it('lets admins manage staff but never the owner', async () => {
    const db = admin().firestore()
    await assertSucceeds(setDoc(doc(db, 'centers', C, 'members', 'new@test.com'), member('new@test.com', 'TEACHER')))
    await assertFails(setDoc(doc(db, 'centers', C, 'members', 'boss@test.com'), member('boss@test.com', 'OWNER')))
    await assertFails(setDoc(doc(db, 'centers', C, 'members', 'owner@test.com'), member('owner@test.com', 'ADMIN')))
    await assertFails(setDoc(doc(db, 'centers', C, 'members', 'owner@test.com'), member('owner@test.com', 'OWNER')))
    // The id must be the email it describes.
    await assertFails(setDoc(doc(db, 'centers', C, 'members', 'x@test.com'), member('y@test.com', 'OFFICE')))
    // Office staff cannot manage staff.
    await assertFails(setDoc(doc(office().firestore(), 'centers', C, 'members', 'new2@test.com'), member('new2@test.com', 'OFFICE')))
  })

  it('lets the owner edit their own record but not demote themselves', async () => {
    const db = owner().firestore()
    await assertSucceeds(setDoc(doc(db, 'centers', C, 'members', 'owner@test.com'), { ...member('owner@test.com', 'OWNER'), name: 'Boss' }))
    await assertFails(setDoc(doc(db, 'centers', C, 'members', 'owner@test.com'), member('owner@test.com', 'ADMIN')))
    await assertFails(setDoc(doc(db, 'centers', C, 'members', 'owner@test.com'), member('owner@test.com', 'OWNER', false)))
  })

  it('lets owners and admins edit the center but not its owner', async () => {
    const ok = { ...centerDoc('uid-owner@test.com', 'owner@test.com'), name: 'New name' }
    await assertSucceeds(setDoc(doc(admin().firestore(), 'centers', C), ok))
    await assertFails(setDoc(doc(admin().firestore(), 'centers', C), { ...ok, ownerUid: 'uid-admin@test.com' }))
    await assertFails(setDoc(doc(office().firestore(), 'centers', C), ok))
  })

  it('gives office staff students and money but not setup or teaching', async () => {
    const db = office().firestore()
    await assertSucceeds(setDoc(doc(db, 'centers', C, 'students', 's2'), { name: 'Mim', ...stamp() }))
    await assertSucceeds(setDoc(doc(db, 'centers', C, 'payments', 'p2'), { amount: 5000, ...stamp() }))
    await assertSucceeds(setDoc(doc(db, 'centers', C, 'photos', 's2'), { data: 'data:image/jpeg;base64,AA', ...stamp() }))
    await assertSucceeds(getDocs(collection(db, 'centers', C, 'expenses')))
    await assertFails(setDoc(doc(db, 'centers', C, 'courses', 'k1'), { name: 'x', ...stamp() }))
    await assertFails(setDoc(doc(db, 'centers', C, 'attendance', 'b1_20261023'), { marks: {}, ...stamp() }))
    await assertFails(setDoc(doc(db, 'centers', C, 'exams', 'x1'), { marks: {}, ...stamp() }))
  })

  it('gives teachers attendance and exams, and no money', async () => {
    const db = teacher().firestore()
    await assertSucceeds(setDoc(doc(db, 'centers', C, 'attendance', 'b1_20261023'), { marks: { e1: 'P' }, ...stamp() }))
    await assertSucceeds(setDoc(doc(db, 'centers', C, 'exams', 'x1'), { marks: { e1: 40 }, ...stamp() }))
    await assertSucceeds(getDocs(collection(db, 'centers', C, 'students')))
    await assertSucceeds(getDocs(query(collection(db, 'centers', C, 'students'), where('updatedAt', '>=', Timestamp.fromMillis(0)))))
    await assertSucceeds(getDocs(collection(db, 'centers', C, 'members')))
    for (const name of ['payments', 'expenses', 'expenseHeads', 'instructorPayments']) {
      await assertFails(getDocs(collection(db, 'centers', C, name)))
    }
    await assertFails(getDoc(doc(db, 'centers', C, 'payments', 'p1')))
    await assertFails(setDoc(doc(db, 'centers', C, 'students', 's1'), { name: 'x', ...stamp() }))
    await assertFails(setDoc(doc(db, 'centers', C, 'payments', 'p2'), { amount: 1, ...stamp() }))
  })

  it('accepts big batches (the staff record is looked up once per request)', async () => {
    const db = office().firestore()
    const batch = writeBatch(db)
    for (let i = 0; i < 60; i++) batch.set(doc(db, 'centers', C, i % 2 ? 'expenseHeads' : 'students', `x${i}`), { name: `n${i}`, ...stamp() })
    await assertSucceeds(batch.commit())
  })

  it('needs the server time on every write, and never deletes', async () => {
    const db = owner().firestore()
    await assertFails(setDoc(doc(db, 'centers', C, 'students', 's3'), { name: 'x', deleted: false, updatedAt: Timestamp.fromMillis(1) }))
    await assertFails(setDoc(doc(db, 'centers', C, 'students', 's3'), { name: 'x', deleted: false }))
    await assertFails(deleteDoc(doc(db, 'centers', C, 'students', 's1')))
    // A tombstone is an ordinary write.
    await assertSucceeds(setDoc(doc(db, 'centers', C, 'students', 's1'), { deleted: true, updatedAt: serverTimestamp() }, { merge: true }))
  })

  it('shuts out inactive staff', async () => {
    const db = user('gone@test.com').firestore()
    await assertFails(getDocs(collection(db, 'centers', C, 'students')))
    await assertFails(setDoc(doc(db, 'centers', C, 'students', 's9'), { name: 'x', ...stamp() }))
    // ...but they can still see their own record, so the app can explain.
    await assertSucceeds(getDoc(doc(db, 'centers', C, 'members', 'gone@test.com')))
  })

  it('lets people find their own centers, and nobody else\'s', async () => {
    const db = teacher().firestore()
    await assertSucceeds(getDocs(query(collectionGroup(db, 'members'), where('email', '==', 'teacher@test.com'))))
    await assertFails(getDocs(query(collectionGroup(db, 'members'), where('email', '==', 'owner@test.com'))))
    await assertFails(getDocs(collectionGroup(db, 'members')))
    const outsider = stranger().firestore()
    await assertSucceeds(getDocs(query(collectionGroup(outsider, 'members'), where('email', '==', 'stranger@test.com'))))
  })
})
