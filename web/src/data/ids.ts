// Same id rules as the Android app: 20 random letters and digits.
const ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'

export function newId(): string {
  const bytes = new Uint8Array(20)
  crypto.getRandomValues(bytes)
  let id = ''
  // 248 is the largest multiple of 62 below 256: rejecting above it keeps the letters uniform.
  for (let i = 0; id.length < 20; i++) {
    if (i === bytes.length) {
      crypto.getRandomValues(bytes)
      i = 0
    }
    if (bytes[i] < 248) id += ALPHABET[bytes[i] % 62]
  }
  return id
}
