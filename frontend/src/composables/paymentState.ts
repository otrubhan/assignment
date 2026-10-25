import type { Ref } from 'vue';
import { computed, ref } from 'vue';
import { createIdempotencyKey, createPayment, type CreatePaymentOutcome, toCreatePaymentOutcome } from '@/services/paymentApi.ts';
import { type PaymentData, type PaymentFormData, PaymentStatusEnum } from '@/types/payment.ts';

/** A create attempt whose outcome is not yet definite; resubmitting the same data must reuse its key. */
interface UnresolvedAttempt {
  request: PaymentFormData;
  idempotencyKey: string;
}

function isSameRequest(a: PaymentFormData, b: PaymentFormData): boolean {
  return a.amount === b.amount && a.currency === b.currency && a.customerReference === b.customerReference;
}

function applyCreateOutcome(payment: PaymentData, outcome: CreatePaymentOutcome): PaymentData {
  switch (outcome.kind) {
    case 'accepted':
      return { ...payment, paymentId: outcome.providerPaymentId, paymentStatus: PaymentStatusEnum.PENDING, errorReason: undefined };
    case 'rejected':
      return { ...payment, paymentStatus: PaymentStatusEnum.FAILED, errorReason: outcome.reason };
    case 'unreachable':
      return { ...payment, paymentStatus: PaymentStatusEnum.BE_NOT_REACHABLE, errorReason: outcome.reason };
    case 'unconfirmed':
      return { ...payment, paymentStatus: PaymentStatusEnum.UNKNOWN, errorReason: outcome.reason };
  }
}

function isDefinite(outcome: CreatePaymentOutcome): boolean {
  return outcome.kind === 'accepted' || outcome.kind === 'rejected';
}

export function usePayment() {
  const payment: Ref<PaymentData | undefined> = ref(undefined);
  const isSubmitting = computed(() => payment.value?.paymentStatus === PaymentStatusEnum.SUBMITTING);
  let unresolvedAttempt: UnresolvedAttempt | undefined;

  const resolveIdempotencyKey = (request: PaymentFormData): string => {
    if (unresolvedAttempt && isSameRequest(unresolvedAttempt.request, request)) {
      return unresolvedAttempt.idempotencyKey;
    }
    return createIdempotencyKey();
  };

  /**
   * Only one submission can be in flight (guarded by `isSubmitting`), so create responses cannot arrive out of order.
   * Resubmitting the same data after an inconclusive outcome reuses the idempotency key, so the backend deduplicates it.
   */
  const submit = async (request: PaymentFormData): Promise<void> => {
    if (isSubmitting.value) return;

    const submittedPayment: PaymentData = { ...request, paymentStatus: PaymentStatusEnum.SUBMITTING };
    payment.value = submittedPayment;

    let idempotencyKey: string;
    try {
      idempotencyKey = resolveIdempotencyKey(request);
    } catch (error) {
      payment.value = applyCreateOutcome(submittedPayment, toCreatePaymentOutcome(error));
      return;
    }

    const outcome = await createPayment(request, idempotencyKey).then(
      (response): CreatePaymentOutcome => ({ kind: 'accepted', providerPaymentId: response.providerPaymentId }),
      toCreatePaymentOutcome
    );

    unresolvedAttempt = isDefinite(outcome) ? undefined : { request: { ...request }, idempotencyKey };
    payment.value = applyCreateOutcome(submittedPayment, outcome);
  };

  return { payment, isSubmitting, submit };
}
