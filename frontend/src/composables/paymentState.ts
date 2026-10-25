import type { Ref } from 'vue';
import { computed, readonly, ref } from 'vue';
import {
  createIdempotencyKey,
  createPayment,
  type CreatePaymentOutcome,
  fetchPaymentStatus,
  type PaymentStatusOutcome,
  toCreatePaymentOutcome,
} from '@/services/paymentApi.ts';
import { usePolling, type PollResult } from '@/composables/polling.ts';
import { type PaymentData, type PaymentFormData, PaymentStatusEnum, toPaymentStatus } from '@/types/payment.ts';
import { STATUS_POLL_INTERVAL_MS } from '@/types/constants.ts';

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

function applyStatusOutcome(payment: PaymentData, outcome: PaymentStatusOutcome): PaymentData {
  switch (outcome.kind) {
    case 'status':
      return { ...payment, paymentStatus: toPaymentStatus(outcome.status), errorReason: undefined };
    case 'unreachable':
      return { ...payment, paymentStatus: PaymentStatusEnum.BE_NOT_REACHABLE, errorReason: outcome.reason };
    case 'rejected':
      return { ...payment, paymentStatus: PaymentStatusEnum.UNKNOWN, errorReason: outcome.reason };
    case 'transient':
    case 'aborted':
      return payment;
  }
}

const POLLED_STATUSES: ReadonlySet<PaymentStatusEnum> = new Set([PaymentStatusEnum.PENDING, PaymentStatusEnum.UNKNOWN]);

/** Keep polling while the outcome is not final (PENDING/UNKNOWN) or the read failed temporarily. */
function shouldKeepPolling(outcome: PaymentStatusOutcome): boolean {
  switch (outcome.kind) {
    case 'status':
      return POLLED_STATUSES.has(toPaymentStatus(outcome.status));
    case 'unreachable':
    case 'transient':
      return true;
    case 'rejected':
    case 'aborted':
      return false;
  }
}

export function usePayment() {
  const payment: Ref<PaymentData | undefined> = ref(undefined);
  const isSubmitting = computed(() => payment.value?.paymentStatus === PaymentStatusEnum.SUBMITTING);
  const statusPolling = usePolling(STATUS_POLL_INTERVAL_MS);
  let unresolvedAttempt: UnresolvedAttempt | undefined;

  const resolveIdempotencyKey = (request: PaymentFormData): string => {
    if (unresolvedAttempt && isSameRequest(unresolvedAttempt.request, request)) {
      return unresolvedAttempt.idempotencyKey;
    }
    return createIdempotencyKey();
  };

  /**
   * Polls GET /payments/{id} every STATUS_POLL_INTERVAL_MS while the payment is PENDING/UNKNOWN,
   * stops on SUCCEEDED/FAILED, on a definite read rejection, on a new submission and on unmount.
   */
  const startStatusPolling = (providerPaymentId: string): void => {
    statusPolling.start(async (signal): Promise<PollResult> => {
      const outcome = await fetchPaymentStatus(providerPaymentId, signal);
      // Drop responses that belong to a stopped poll or a payment that is no longer shown.
      if (signal.aborted || !payment.value || payment.value.paymentId !== providerPaymentId) return 'stop';

      payment.value = applyStatusOutcome(payment.value, outcome);
      return shouldKeepPolling(outcome) ? 'continue' : 'stop';
    });
  };

  /**
   * Only one submission can be in flight (guarded by `isSubmitting`), so create responses cannot arrive out of order.
   * Resubmitting the same data after an inconclusive outcome reuses the idempotency key, so the backend deduplicates it.
   * Starting a new payment stops polling of the previous one.
   */
  const submit = async (request: PaymentFormData): Promise<void> => {
    if (isSubmitting.value) return;

    statusPolling.stop();
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
    if (outcome.kind === 'accepted') startStatusPolling(outcome.providerPaymentId);
  };

  return { payment: readonly(payment), isSubmitting, isPolling: statusPolling.isPolling, submit };
}
