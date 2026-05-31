import { getToken, clearToken } from './tokenStore';

// Empty (the default) means same-origin relative requests (`/api/v1/...`), which the
// dev/runtime proxy forwards to the gateway. This keeps the SPA portable: it works on
// localhost AND behind a reverse proxy / Cloudflare tunnel without rebaking the origin.
// Set VITE_API_BASE_URL only to target a gateway on a different origin from the browser.
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '';
const API_PREFIX = '/api/v1';

export interface ProblemDetail {
  type?: string;
  title?: string;
  detail?: string;
  instance?: string;
  errors?: Array<{ field: string; message: string }>;
}

export class ApiError extends Error {
  constructor(public status: number, public problem: ProblemDetail | null) {
    super(problem?.detail ?? problem?.title ?? `HTTP ${status}`);
    this.name = 'ApiError';
  }
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {}
): Promise<T> {
  const token = getToken();
  const headers = new Headers(init.headers);
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  headers.set('Accept', 'application/json, application/problem+json');

  const url = `${API_BASE}${API_PREFIX}${path}`;
  const res = await fetch(url, { ...init, headers });

  if (res.status === 401) {
    clearToken();
    const redirectUrl = window.location.pathname + window.location.search;
    window.dispatchEvent(
      new CustomEvent('auth:unauthorized', { detail: { redirectUrl } })
    );
    throw new ApiError(401, await safeProblem(res));
  }

  if (!res.ok) {
    throw new ApiError(res.status, await safeProblem(res));
  }

  if (res.status === 204) return undefined as T;

  const contentType = res.headers.get('content-type') ?? '';
  if (!contentType.includes('application/json')) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

async function safeProblem(res: Response): Promise<ProblemDetail | null> {
  const ct = res.headers.get('content-type') ?? '';
  if (!ct.includes('json')) return null;
  try {
    return (await res.json()) as ProblemDetail;
  } catch {
    return null;
  }
}
