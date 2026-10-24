import type { Currency } from './common.ts';

export enum PaymentStatusEnum {
  SUBMITTING = 'SUBMITTING',
  PENDING = 'PENDING',
  SUCCEEDED = 'SUCCEEDED',
  FAILED = 'FAILED',
  UNKNOWN = 'UNKNOWN',
  BE_NOT_REACHABLE = 'BACKEND NOT REACHABLE',
}

export interface PaymentFormData {
  amount: string;
  currency: Currency;
  customerReference: string;
}

export interface PaymentData extends PaymentFormData {
  paymentId?: string;
  paymentStatus: PaymentStatusEnum;
  errorReason?: string;
}
