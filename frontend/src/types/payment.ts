import type { Currency } from './common.ts';
import { BackendPaymentStatus } from './service.ts';

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

const BACKEND_TO_UI_STATUS: Record<BackendPaymentStatus, PaymentStatusEnum> = {
  [BackendPaymentStatus.PENDING]: PaymentStatusEnum.PENDING,
  [BackendPaymentStatus.SUCCEEDED]: PaymentStatusEnum.SUCCEEDED,
  [BackendPaymentStatus.FAILED]: PaymentStatusEnum.FAILED,
  [BackendPaymentStatus.UNKNOWN]: PaymentStatusEnum.UNKNOWN,
};

export function toPaymentStatus(status: BackendPaymentStatus): PaymentStatusEnum {
  return BACKEND_TO_UI_STATUS[status];
}
