// Security-rules tests: run against the Firestore emulator, e.g. from the repo root
//   npx firebase-tools emulators:exec --only firestore --project demo-skilltrack "npm --prefix web run test:rules"
import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    environment: 'node',
    include: ['rules/**/*.test.ts'],
    testTimeout: 20_000,
    hookTimeout: 30_000,
    fileParallelism: false,
  },
})
