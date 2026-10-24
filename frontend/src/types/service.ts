export interface CreatePaymentResponse {
  providerPaymentId: string;
}

export interface PaymentApiError extends Error {
  readonly status?: number;
  readonly fieldErrors: Record<string, string>;
}
