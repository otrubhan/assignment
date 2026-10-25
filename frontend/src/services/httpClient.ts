import { INITIAL_RETRY_DELAY_MS, MAX_RETRIES, MAX_RETRY_AFTER_MS, MAX_RETRY_JITTER_MS, REQUEST_TIMEOUT_MS, RETRYABLE_STATUSES } from '@/types/constants.ts';

export type HttpFailureKind = 'http' | 'timeout' | 'network' | 'unexpected';

export class HttpError extends Error {
  constructor(
    message: string,
    readonly kind: HttpFailureKind,
    readonly status?: number,
    readonly body?: unknown,
    options?: ErrorOptions
  ) {
    super(message, options);
    this.name = 'HttpError';
  }
}

export interface HttpRequestOptions {
  method: 'GET' | 'POST';
  headers?: Record<string, string>;
  body?: string;
}

export interface RetryPolicy {
  maxRetries: number;
  initialDelayMs: number;
  maxJitterMs: number;
  timeoutMs: number;
  maxRetryAfterMs: number;
  retryableStatuses: ReadonlySet<number>;
}

export const DEFAULT_RETRY_POLICY: RetryPolicy = {
  maxRetries: MAX_RETRIES,
  initialDelayMs: INITIAL_RETRY_DELAY_MS,
  maxJitterMs: MAX_RETRY_JITTER_MS,
  timeoutMs: REQUEST_TIMEOUT_MS,
  maxRetryAfterMs: MAX_RETRY_AFTER_MS,
  retryableStatuses: RETRYABLE_STATUSES,
};

type AttemptResult = { ok: true; status: number; body: unknown; retryAfterMs?: number } | { ok: false; error: HttpError };

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => globalThis.setTimeout(resolve, milliseconds));
}

function parseRetryAfterMs(header: string | null): number | undefined {
  if (!header) return undefined;
  const seconds = Number(header);
  if (Number.isFinite(seconds)) return Math.max(0, seconds * 1000);
  const date = Date.parse(header);
  return Number.isNaN(date) ? undefined : Math.max(0, date - Date.now());
}

function backoffMs(retryNumber: number, policy: RetryPolicy, retryAfterMs?: number): number {
  const exponential = policy.initialDelayMs * 2 ** (retryNumber - 1) + Math.floor(Math.random() * policy.maxJitterMs);
  return retryAfterMs === undefined ? exponential : Math.max(exponential, Math.min(retryAfterMs, policy.maxRetryAfterMs));
}

async function readJson(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return undefined;
  }
}

async function attempt(url: string, options: HttpRequestOptions, timeoutMs: number): Promise<AttemptResult> {
  const controller = new AbortController();
  const timeoutId = globalThis.setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, { ...options, signal: controller.signal });
    const body = await readJson(response);
    if (controller.signal.aborted) throw new DOMException('Aborted', 'AbortError');
    return { ok: true, status: response.status, body, retryAfterMs: parseRetryAfterMs(response.headers.get('Retry-After')) };
  } catch (error) {
    if (controller.signal.aborted) return { ok: false, error: new HttpError('Request timed out.', 'timeout', undefined, undefined, { cause: error }) };
    if (error instanceof TypeError) return { ok: false, error: new HttpError('Network request failed.', 'network', undefined, undefined, { cause: error }) };
    return { ok: false, error: new HttpError('Request failed unexpectedly.', 'unexpected', undefined, undefined, { cause: error }) };
  } finally {
    globalThis.clearTimeout(timeoutId);
  }
}

/**
 * Sends a request with per-attempt timeout and bounded exponential-backoff retries.
 * Retries reuse identical options (incl. headers such as Idempotency-Key).
 * Resolves with the parsed JSON body on `expectedStatus`, otherwise rejects with HttpError.
 */
export async function requestJson(url: string, options: HttpRequestOptions, expectedStatus: number, policy: RetryPolicy = DEFAULT_RETRY_POLICY): Promise<unknown> {
  for (let attemptNumber = 0; ; attemptNumber += 1) {
    const result = await attempt(url, options, policy.timeoutMs);
    const canRetry = attemptNumber < policy.maxRetries;

    if (!result.ok) {
      const transient = result.error.kind === 'timeout' || result.error.kind === 'network';
      if (!transient || !canRetry) throw result.error;
      await delay(backoffMs(attemptNumber + 1, policy));
      continue;
    }

    if (result.status === expectedStatus) return result.body;

    if (!policy.retryableStatuses.has(result.status) || !canRetry) {
      throw new HttpError(`Request failed with HTTP ${result.status}.`, 'http', result.status, result.body);
    }
    await delay(backoffMs(attemptNumber + 1, policy, result.retryAfterMs));
  }
}
