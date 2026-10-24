import type { PaymentFormData } from '@/types/payment.ts';
import type { CreatePaymentResponse, PaymentApiError as PaymentApiErrorContract } from '@/types/service.ts';

const REQUEST_TIMEOUT_MS = 5_000;
const MAX_RETRIES = 2;
const INITIAL_RETRY_DELAY_MS = 250;
const MAX_RETRY_JITTER_MS = 100;

export class PaymentApiError extends Error implements PaymentApiErrorContract {
  constructor(
    message: string,
    readonly status?: number,
    readonly fieldErrors: Record<string, string> = {}
  ) {
    super(message);
    this.name = 'PaymentApiError';
  }
}

function isStringRecord(value: unknown): value is Record<string, string> {
  return typeof value === 'object' && value !== null && Object.values(value).every((item) => typeof item === 'string');
}

function fieldErrorsFromBody(body: unknown): Record<string, string> {
  if (typeof body !== 'object' || body === null) return {};

  const record = body as Record<string, unknown>;
  if (isStringRecord(record.fieldErrors)) return record.fieldErrors;
  if (isStringRecord(record.failedFields)) return record.failedFields;
  if (Array.isArray(record.failedFields) && record.failedFields.every((field) => typeof field === 'string')) {
    return Object.fromEntries(record.failedFields.map((field) => [field, 'Invalid value.']));
  }
  return {};
}

function messageFromBody(body: unknown, fallback: string): string {
  if (typeof body === 'object' && body !== null) {
    const record = body as Record<string, unknown>;
    if (typeof record.reason === 'string') return record.reason;
    if (typeof record.message === 'string') return record.message;
  }
  return fallback;
}

async function readJson(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return undefined;
  }
}

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
}

function retryDelay(retryNumber: number): number {
  return INITIAL_RETRY_DELAY_MS * 2 ** (retryNumber - 1) + Math.floor(Math.random() * MAX_RETRY_JITTER_MS);
}

function isRetryableStatus(status: number): boolean {
  return status === 500 || status === 502 || status === 503 || status === 504;
}

function isRetryableTransportError(error: unknown): boolean {
  return error instanceof TypeError || (error instanceof DOMException && error.name === 'AbortError');
}

function createIdempotencyKey(): string {
  if (!globalThis.crypto?.randomUUID) {
    throw new PaymentApiError('Secure payment request IDs are not supported in this browser.');
  }
  return globalThis.crypto.randomUUID();
}

export async function createPayment(data: PaymentFormData): Promise<CreatePaymentResponse> {
  const idempotencyKey = createIdempotencyKey();

  // The backend must persist and honor this key before POST retries can be duplicate-safe.
  for (let attempt = 0; attempt <= MAX_RETRIES; attempt += 1) {
    const controller = new AbortController();
    const timeoutId = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

    try {
      const response = await fetch('/api/payments', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Key': idempotencyKey,
        },
        body: JSON.stringify(data),
        signal: controller.signal,
      });

      const body = await readJson(response);
      if (response.status === 201) {
        if (typeof body !== 'object' || body === null || typeof (body as Record<string, unknown>).providerPaymentId !== 'string') {
          throw new PaymentApiError('The server returned an invalid payment response.', response.status);
        }
        return { providerPaymentId: (body as CreatePaymentResponse).providerPaymentId };
      }

      const fieldErrors = response.status === 400 ? fieldErrorsFromBody(body) : {};
      const error = new PaymentApiError(messageFromBody(body, `Payment request failed with HTTP ${response.status}.`), response.status, fieldErrors);

      if (isRetryableStatus(response.status) && attempt < MAX_RETRIES) {
        await delay(retryDelay(attempt + 1));
        continue;
      }
      throw error;
    } catch (error) {
      if (error instanceof PaymentApiError) throw error;
      if (!isRetryableTransportError(error)) throw error;

      const timedOut = controller.signal.aborted;
      if (attempt < MAX_RETRIES) {
        await delay(retryDelay(attempt + 1));
        continue;
      }

      throw new PaymentApiError(
        timedOut
          ? 'The payment request timed out. Its final state could not be confirmed.'
          : 'The backend could not be reached. Its final state could not be confirmed.'
      );
    } finally {
      window.clearTimeout(timeoutId);
    }
  }

  throw new PaymentApiError('Payment request failed after retries.');
}
