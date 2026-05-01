# Phase 10 Debug: Register Validation And 400

## Symptom

UAT Test 3 shows an uncaught promise `ZodError` in the console while registering, no inline password validation clue in the UI, and a backend `400 Bad Request` after correcting only the password length.

## Root Cause

Register has two independent validation defects:

- `@hookform/resolvers` 3.10.0 is incompatible with Zod 4.4.1 and rejects with `ZodError` instead of returning react-hook-form field errors.
- Frontend password validation only checks `min(8)`, while backend registration requires 8+ characters with at least one letter and one digit.

## Evidence

- The installed resolver throws/rejects on the same min-8 password case instead of returning a field-errors object.
- `RegisterPage.tsx` validates password with `z.string().min(8)` only.
- Backend `RegisterRequest` requires `^(?=.*[A-Za-z])(?=.*\\d).{8,}$`.

## Suggested Fix Direction

Align resolver/Zod versions, mirror the backend password rule in the frontend schema, and render backend problem details or field errors as inline errors or Turkish toast messages.
