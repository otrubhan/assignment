import { BackendPaymentStatus, type CreatePaymentResponse, type GetPaymentInfoResponse, type PaymentApiErrorKind, type PaymentApiError as PaymentApiErrorContract } from '@/types/service.ts';
import { isCurrency, type PaymentFormData } from '@/types/payment.ts';
import { HttpError, requestJson } from '@/services/httpClient.ts';

export class PaymentApiError extends Error implements PaymentApiErrorContract {
  constructor(
    message: string,
    readonly status?: number,
    readonly fieldErrors: Record<string, string> = {},
    readonly kind: PaymentApiErrorKind = status === undefined ? 'network' : 'http',
    options?: ErrorOptions
  ) {
    super(message, options);
    this.name = 'PaymentApiError';
  }
}

/**
 * Outcome of a create-payment call, independent of how the UI presents it.
 * - accepted: backend created the payment
 * - rejected: backend definitively refused it; a new attempt is a new payment
 * - unreachable: no HTTP response; the payment may or may not exist
 * - unconfirmed: backend answered but the outcome is unclear (5xx, in-progress, unreadable 201)
 * For `unreachable` and `unconfirmed`, retry with the same idempotency key.
 */
export type CreatePaymentOutcome =
  | { kind: 'accepted'; providerPaymentId: string }
  | { kind: 'rejected' | 'unreachable' | 'unconfirmed'; reason: string };

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isStringRecord(value: unknown): value is Record<string, string> {
  return isRecord(value) && Object.values(value).every((item) => typeof item === 'string');
}

function fieldErrorsFromBody(body: unknown): Record<string, string> {
  if (!isRecord(body)) return {};
  if (isStringRecord(body.fieldErrors)) return body.fieldErrors;
  if (isStringRecord(body.failedFields)) return body.failedFields;
  if (Array.isArray(body.failedFields) && body.failedFields.every((field) => typeof field === 'string')) {
    return Object.fromEntries(body.failedFields.map((field) => [field, 'Invalid value.']));
  }
  return {};
}

function messageFromBody(body: unknown, fallback: string): string {
  if (!isRecord(body)) return fallback;
  if (typeof body.reason === 'string') return body.reason;
  if (typeof body.message === 'string') return body.message;
  return fallback;
}

function toPaymentApiError(error: unknown): PaymentApiError {
  if (error instanceof PaymentApiError) return error;
  if (!(error instanceof HttpError)) {
    return new PaymentApiError('The payment request failed unexpectedly.', undefined, {}, 'unexpected', { cause: error });
  }

  switch (error.kind) {
    case 'http':
      return new PaymentApiError(
        messageFromBody(error.body, `Payment request failed with HTTP ${error.status}.`),
        error.status,
        error.status === 400 ? fieldErrorsFromBody(error.body) : {},
        'http',
        { cause: error }
      );
    case 'timeout':
      return new PaymentApiError('The payment request timed out. Its final state could not be confirmed.', undefined, {}, 'timeout', { cause: error });
    case 'network':
      return new PaymentApiError('The backend could not be reached. Its final state could not be confirmed.', undefined, {}, 'network', { cause: error });
    case 'unexpected':
      return new PaymentApiError('The payment request failed unexpectedly.', undefined, {}, 'unexpected', { cause: error });
    case 'aborted':
      return new PaymentApiError('The payment request was cancelled.', undefined, {}, 'aborted', { cause: error });
  }
}

async function request(url: string, options: Parameters<typeof requestJson>[1], expectedStatus: number): Promise<unknown> {
  try {
    return await requestJson(url, options, expectedStatus);
  } catch (error) {
    throw toPaymentApiError(error);
  }
}

export function createIdempotencyKey(): string {
  if (!globalThis.crypto?.randomUUID) {
    throw new PaymentApiError('Secure payment request IDs are not supported in this browser.', undefined, {}, 'unsupported');
  }
  return globalThis.crypto.randomUUID();
}

export function parseCreatePaymentResponse(body: unknown): CreatePaymentResponse {
  if (!isRecord(body) || typeof body.providerPaymentId !== 'string' || body.providerPaymentId.length === 0) {
    throw new PaymentApiError('The server returned an invalid payment response.', 201, {}, 'invalid-response');
  }
  return { providerPaymentId: body.providerPaymentId };
}

/**
 * The same `idempotencyKey` must be passed when re-sending the same logical payment,
 * so the backend can deduplicate it. All retries within this call reuse it automatically.
 */
export async function createPayment(data: PaymentFormData, idempotencyKey: string = createIdempotencyKey()): Promise<CreatePaymentResponse> {
  const body = await request(
    '/api/payments',
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify(data),
    },
    201
  );
  return parseCreatePaymentResponse(body);
}

// 4xx statuses that do not prove the request was refused.
const INCONCLUSIVE_CLIENT_STATUSES = new Set([408, 409, 425, 429]);

export function toCreatePaymentOutcome(error: unknown): CreatePaymentOutcome {
  const apiError = toPaymentApiError(error);
  const fieldMessages = Object.entries(apiError.fieldErrors).map(([field, message]) => `${field}: ${message}`);
  const reason = fieldMessages.join('; ') || apiError.message;

  switch (apiError.kind) {
    case 'unsupported':
      return { kind: 'rejected', reason };
    case 'invalid-response':
      return { kind: 'unconfirmed', reason };
    case 'timeout':
    case 'network':
    case 'unexpected':
    case 'aborted':
      return { kind: 'unreachable', reason };
    case 'http': {
      const status = apiError.status ?? 0;
      const definite = status >= 400 && status < 500 && !INCONCLUSIVE_CLIENT_STATUSES.has(status);
      return { kind: definite ? 'rejected' : 'unconfirmed', reason };
    }
  }
}

/**
 * Outcome of a single payment-status read, independent of how the UI presents it.
 * - status: backend returned the current payment status
 * - unreachable: no HTTP response (timeout/network); try again later
 * - transient: backend answered but could not serve the status now (5xx, 409/429, unreadable body); try again later
 * - rejected: backend definitively refused the read (e.g. 404); retrying will not help
 * - aborted: the read was cancelled by the caller; nothing should be applied
 */
export type PaymentStatusOutcome =
  | { kind: 'status'; status: BackendPaymentStatus }
  | { kind: 'unreachable' | 'transient' | 'rejected'; reason: string }
  | { kind: 'aborted' };

export function toPaymentStatusOutcome(error: unknown): PaymentStatusOutcome {
  const apiError = toPaymentApiError(error);

  switch (apiError.kind) {
    case 'aborted':
      return { kind: 'aborted' };
    case 'timeout':
    case 'network':
    case 'unexpected':
      return { kind: 'unreachable', reason: apiError.message };
    case 'invalid-response':
      return { kind: 'transient', reason: apiError.message };
    case 'unsupported':
      return { kind: 'rejected', reason: apiError.message };
    case 'http': {
      const status = apiError.status ?? 0;
      const definite = status >= 400 && status < 500 && !INCONCLUSIVE_CLIENT_STATUSES.has(status);
      return { kind: definite ? 'rejected' : 'transient', reason: apiError.message };
    }
  }
}

/** Never rejects; all failures are mapped into the returned outcome. */
export async function fetchPaymentStatus(providerPaymentId: string, signal?: AbortSignal): Promise<PaymentStatusOutcome> {
  try {
    const details = await getPaymentInfo(providerPaymentId, signal);
    return { kind: 'status', status: details.status };
  } catch (error) {
    return toPaymentStatusOutcome(error);
  }
}

export async function getPaymentInfo(providerPaymentId: string, signal?: AbortSignal): Promise<GetPaymentInfoResponse> {
  const body = await request(
    `/api/payments/${encodeURIComponent(providerPaymentId)}`,
    {
      method: 'GET',
      ...(signal && { signal }),
    },
    200
  );
  return parsePaymentDetails(body);
}

const BACKEND_STATUSES: ReadonlySet<string> = new Set(Object.values(BackendPaymentStatus));

// Our backend only reports its own four statuses; anything unexpected is treated as UNKNOWN rather than breaking the UI.
function parseBackendStatus(status: string): BackendPaymentStatus {
  return BACKEND_STATUSES.has(status) ? (status as BackendPaymentStatus) : BackendPaymentStatus.UNKNOWN;
}

export function parsePaymentDetails(body: unknown): GetPaymentInfoResponse {
  if (
    !isRecord(body) ||
    typeof body.id !== 'string' ||
    typeof body.status !== 'string' ||
    !(typeof body.providerStatus === 'string' || body.providerStatus === null || body.providerStatus === undefined) ||
    typeof body.providerPaymentId !== 'string' ||
    body.providerPaymentId.length === 0 ||
    !(typeof body.lastCallbackOccurredAt === 'string' || body.lastCallbackOccurredAt === null || body.lastCallbackOccurredAt === undefined) ||
    typeof body.amount !== 'string' ||
    typeof body.currency !== 'string' ||
    !isCurrency(body.currency) ||
    typeof body.customerReference !== 'string'
  ) {
    throw new PaymentApiError('The server returned an invalid payment response.', 200, {}, 'invalid-response');
  }
  return {
    id: body.id,
    status: parseBackendStatus(body.status),
    providerStatus: body.providerStatus,
    providerPaymentId: body.providerPaymentId,
    lastCallbackOccurredAt: body.lastCallbackOccurredAt,
    amount: body.amount,
    currency: body.currency,
    customerReference: body.customerReference,
  };
}
