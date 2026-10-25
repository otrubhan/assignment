import type { Currency } from './common.ts';

export interface CreatePaymentResponse {
  providerPaymentId: string;
}

export enum BackendPaymentStatus {
  PENDING = 'PENDING',
  SUCCEEDED = 'SUCCEEDED',
  FAILED = 'FAILED',
  UNKNOWN = 'UNKNOWN',
}

export interface GetPaymentInfoResponse {
  id: string;
  status: BackendPaymentStatus;
  providerStatus: string | null | undefined;
  providerPaymentId: string;
  lastCallbackOccurredAt: string | null | undefined;
  amount: string;
  currency: Currency;
  customerReference: string;
}

/**
 * - http: backend responded with an unexpected HTTP status
 * - timeout / network / unexpected: no usable HTTP response
 * - invalid-response: expected status but unreadable body
 * - unsupported: request could not be prepared (nothing was sent)
 */
export type PaymentApiErrorKind = 'http' | 'timeout' | 'network' | 'unexpected' | 'invalid-response' | 'unsupported';

export interface PaymentApiError extends Error {
  readonly kind: PaymentApiErrorKind;
  readonly status?: number;
  readonly fieldErrors: Record<string, string>;
}
