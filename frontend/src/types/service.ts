import type { Currency } from './common.ts';

export interface CreatePaymentResponse {
  providerPaymentId: string;
}

export enum PaymentStatusEnum {
  PENDING = 'PENDING',
  SUCCEEDED = 'SUCCEEDED',
  FAILED = 'FAILED',
  UNKNOWN = 'UNKNOWN',
}

export interface GetPaymentInfoResponse {
  id: string;
  status: PaymentStatusEnum;
  providerStatus: string | null | undefined;
  providerPaymentId: string;
  lastCallbackOccurredAt: string | null | undefined;
  amount: string;
  currency: Currency;
  customerReference: string;
}

export interface PaymentApiError extends Error {
  readonly status?: number;
  readonly fieldErrors: Record<string, string>;
}

export interface BaseFetchOptions {
  method: 'GET' | 'POST';
  headers?: HeadersInit;
  body?: string;
}
