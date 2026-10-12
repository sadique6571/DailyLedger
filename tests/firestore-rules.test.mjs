import { readFile } from 'node:fs/promises';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, deleteDoc, serverTimestamp } from 'firebase/firestore';

const env = await initializeTestEnvironment({
  projectId: 'demo-daily-ledger',
  firestore: { rules: await readFile(new URL('../firestore.rules', import.meta.url), 'utf8') },
});
const alice = env.authenticatedContext('alice').firestore();
const bob = env.authenticatedContext('bob').firestore();
const guest = env.unauthenticatedContext().firestore();
const path = 'users/alice/backups/latest';
try {
  await env.clearFirestore();
  await assertFails(setDoc(doc(guest, path), { payload: 'guest-write' }));
  await env.withSecurityRulesDisabled(async context => {
    await setDoc(doc(context.firestore(), path), {
      payload: 'legacy-backup',
      updatedAt: serverTimestamp(),
    });
  });
  await assertSucceeds(getDoc(doc(alice, path)));
  await assertFails(getDoc(doc(bob, path)));
  await assertFails(getDoc(doc(guest, path)));
  await assertFails(setDoc(doc(bob, path), { payload: 'other-account-write' }));
  await assertFails(setDoc(doc(alice, path), { payload: 'update-write' }));
  await assertFails(deleteDoc(doc(alice, path)));
  await assertFails(deleteDoc(doc(bob, path)));
  console.log('Legacy Firestore backup is owner-readable and all client writes are blocked.');
} finally {
  await env.cleanup();
}
