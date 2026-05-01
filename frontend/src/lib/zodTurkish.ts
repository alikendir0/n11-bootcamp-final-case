/**
 * Registers a global Turkish error map with zod 4.
 * Import this file once (from main.tsx) as a side-effect import:
 *   import './lib/zodTurkish';
 * All zod schemas throughout the app will then emit Turkish error messages.
 */
import { z } from 'zod';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const turkishErrorMap = (issue: any): { message: string } => {
  // Required: field missing entirely (undefined/null where string expected)
  if (issue.code === 'invalid_type') {
    return { message: 'Bu alan zorunludur.' };
  }

  // Email format validation
  if (issue.code === 'invalid_format' && issue.format === 'email') {
    return { message: 'Geçerli bir e-posta adresi giriniz.' };
  }

  // String too short — covers min(1) "required" and min(N) length checks
  if (issue.code === 'too_small' && issue.origin === 'string') {
    const min = (issue as { minimum: number }).minimum;
    if (min <= 1) return { message: 'Bu alan zorunludur.' };
    if (min === 8) return { message: 'Şifre en az 8 karakter olmalıdır.' };
    return { message: `En az ${min} karakter giriniz.` };
  }

  // String too long
  if (issue.code === 'too_big' && issue.origin === 'string') {
    const max = (issue as { maximum: number }).maximum;
    return { message: `En fazla ${max} karakter giriniz.` };
  }

  // Fallback — generic Turkish error; field-level messages override the tone
  return { message: 'Geçersiz değer.' };
};

// Register globally with zod 4 — z.config is idempotent (last-writer-wins on same key)
z.config({ customError: turkishErrorMap });

export {};  // module side-effect import
