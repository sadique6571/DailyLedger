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
const revision = '12345678-1234-1234-1234-123456789abc';

const encryptedSnapshot = () => ({
  formatVersion: 4,
  revision,
  payload: 'ZW5jcnlwdGVkLXBheWxvYWQ=',
  contentHash: 'a'.repeat(64),
  updatedAt: serverTimestamp(),
});

try {
  await env.clearFirestore();
  await assertFails(setDoc(doc(guest, path), encryptedSnapshot()));
  await assertSucceeds(setDoc(doc(alice, path), encryptedSnapshot()));
  await assertSucceeds(getDoc(doc(alice, path)));
  await assertFails(getDoc(doc(bob, path)));
  await assertFails(getDoc(doc(guest, path)));
  await assertFails(setDoc(doc(bob, path), encryptedSnapshot()));
  await assertFails(setDoc(doc(alice, path), { ...encryptedSnapshot(), revision: 'not-a-uuid' }));
  await assertFails(setDoc(doc(alice, path), { ...encryptedSnapshot(), contentHash: 'not-a-hash' }));
  await assertFails(setDoc(doc(alice, path), { ...encryptedSnapshot(), payload: '' }));
  await assertFails(setDoc(doc(alice, path), { ...encryptedSnapshot(), payload: 'not base64!' }));
  await assertFails(setDoc(doc(alice, path), { ...encryptedSnapshot(), payload: 'a'.repeat(900001) }));
  await assertFails(setDoc(doc(alice, path), { ...encryptedSnapshot(), unexpected: 'field' }));
  await assertFails(setDoc(doc(alice, path), { ...encryptedSnapshot(), formatVersion: 1 }));
  await env.withSecurityRulesDisabled(async context => {
    await setDoc(doc(context.firestore(), path), {
      formatVersion: 1,
      revision,
      payload: 'legacy-backup',
      contentHash: 'b'.repeat(64),
      updatedAt: serverTimestamp(),
    });
  });
  await assertSucceeds(getDoc(doc(alice, path)));
  await assertFails(getDoc(doc(bob, path)));
  await assertFails(setDoc(doc(alice, path), { ...encryptedSnapshot(), payload: '' }));
  await assertSucceeds(deleteDoc(doc(alice, path)));
  console.log('Firestore backup rule checks passed.');
} finally {
  await env.cleanup();
}
