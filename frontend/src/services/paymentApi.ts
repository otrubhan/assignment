import type {PaymentFormData} from '@/types/payment.ts';
import type {
  CreatePaymentResponse, GetPaymentInfoResponse, PaymentApiError as PaymentApiErrorContract, BaseFetchOptions
} from '@/types/service.ts';
import { isCurrency } from '@/types/common.ts';
import { MAX_RETRIES, INITIAL_RETRY_DELAY_MS, MAX_RETRY_JITTER_MS, REQUEST_TIMEOUT_MS, RETRYABLE_STATUSES } from '@/types/constants.ts';


export class PaymentApiError extends Error implements PaymentApiErrorContract {
  constructor(message: string, readonly status?: number, readonly fieldErrors: Record<string, string> = {}) {
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
  if (typeof body !== 'object' || body === null) return fallback;

  const record = body as Record<string, unknown>;
  if (typeof record.reason === 'string') return record.reason;
  if (typeof record.message === 'string') return record.message;
  return fallback;
}

function mapResponseError(status: number, body: unknown): PaymentApiError {
  return new PaymentApiError(messageFromBody(body, `Payment request failed with HTTP ${status}.`), status, status === 400 ? fieldErrorsFromBody(body) : {});
}

async function readJson(response: Response, signal: AbortSignal): Promise<unknown> {
  try {
    return await response.json();
  } catch (error) {
    if (signal.aborted) throw error;
    return undefined;
  }
}

function isRetryableTransportError(error: unknown): boolean {
  return error instanceof TypeError || (error instanceof DOMException && error.name === 'AbortError');
}

function transportError(error: unknown, timedOut: boolean): PaymentApiError {
  if (!isRetryableTransportError(error)) {
    return new PaymentApiError('The payment request failed unexpectedly.');
  }

  return new PaymentApiError(timedOut ? 'The payment request timed out. Its final state could not be confirmed.' : 'The backend could not be reached. Its final state could not be confirmed.');
}

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => globalThis.setTimeout(resolve, milliseconds));
}

async function baseFetch(url: string, options: BaseFetchOptions, expectedStatus: number): Promise<unknown> {
  const waitBeforeRetry = async (retryNumber: number): Promise<void> => {
    const exponentialDelay = INITIAL_RETRY_DELAY_MS * (2 ** (retryNumber - 1));
    const jitter = Math.floor(Math.random() * MAX_RETRY_JITTER_MS);
    await delay(exponentialDelay + jitter);
  };

  for (let attempt = 0; attempt <= MAX_RETRIES; attempt += 1) {
    const controller = new AbortController();
    const timeoutId = globalThis.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
    let response: Response | undefined;
    let responseBody: unknown;
    let requestError: unknown;
    let requestFailed = false;
    let timedOut = false;

    try {
      response = await fetch(url, {
        ...options, signal: controller.signal,
      });
      responseBody = await readJson(response, controller.signal);
    } catch (error) {
      requestFailed = true;
      requestError = error;
      timedOut = controller.signal.aborted;
    } finally {
      globalThis.clearTimeout(timeoutId);
    }

    if (requestFailed) {
      if (isRetryableTransportError(requestError) && attempt < MAX_RETRIES) {
        await waitBeforeRetry(attempt + 1);
        continue;
      }
      throw transportError(requestError, timedOut);
    }

    if (!response) {
      throw new PaymentApiError('The payment request failed without a response.');
    }

    if (response.status === expectedStatus) return responseBody;

    const error = mapResponseError(response.status, responseBody);
    if (RETRYABLE_STATUSES.has(response.status) && attempt < MAX_RETRIES) {
      await waitBeforeRetry(attempt + 1);
      continue;
    }
    throw error;
  }

  throw new PaymentApiError('Payment request failed after retries.');
}

function createIdempotencyKey(): string {
  if (!globalThis.crypto?.randomUUID) {
    throw new PaymentApiError('Secure payment request IDs are not supported in this browser.');
  }
  return globalThis.crypto.randomUUID();
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function parseCreatePaymentResponse(body: unknown): CreatePaymentResponse {
  if (!isRecord(body) || typeof body.providerPaymentId !== 'string' || body.providerPaymentId.length === 0) {
    throw new PaymentApiError('The server returned an invalid payment response.', 201);
  }
  return {providerPaymentId: body.providerPaymentId};
}

function parsePaymentDetails(body: unknown): GetPaymentInfoResponse {
  if (!isRecord(body) ||
    typeof body.id !== 'string' ||
    typeof body.status !== 'string' ||
    !(typeof body.providerStatus === 'string' ||
      body.providerStatus === null ||
      body.providerStatus === undefined) ||
    typeof body.providerPaymentId !== 'string' ||
    body.providerPaymentId.length === 0 ||
    !(typeof body.lastCallbackOccurredAt === 'string' ||
      body.lastCallbackOccurredAt === null ||
      body.lastCallbackOccurredAt === undefined) ||
    typeof body.amount !== 'string' ||
    typeof body.currency !== 'string' ||
    !isCurrency(body.currency) ||
    typeof body.customerReference !== 'string') {
    throw new PaymentApiError('The server returned an invalid payment response.', 200);
  }
  return {
    id: body.id,
    status: body.status,
    providerStatus: body.providerStatus,
    providerPaymentId: body.providerPaymentId,
    lastCallbackOccurredAt: body.lastCallbackOccurredAt,
    amount: body.amount,
    currency: body.currency,
    customerReference: body.customerReference,
  };
}

export async function createPayment(data: PaymentFormData): Promise<CreatePaymentResponse> {
  // The backend must persist and honor this key before POST retries can be duplicate-safe.
  const body = await baseFetch(
    '/api/payments', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': createIdempotencyKey(),
      },
      body: JSON.stringify(data),
    }, 201);
  return parseCreatePaymentResponse(body);
}

export async function getPaymentInfo(providerPaymentId: string): Promise<GetPaymentInfoResponse> {
  const body = await baseFetch(
    `/api/payments/${encodeURIComponent(providerPaymentId)}`, {
      method: 'GET'
    }, 200);
  return parsePaymentDetails(body);
}
