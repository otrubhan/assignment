import { getCurrentScope, onScopeDispose, readonly, ref } from 'vue';
import { fetchPaymentStatus, type PaymentStatusOutcome } from '@/services/paymentApi.ts';
import { STATUS_POLL_INTERVAL_MS } from '@/types/constants.ts';

/** Applies one status read and returns whether polling should continue. Never called after polling was stopped. */
export type StatusOutcomeHandler = (outcome: PaymentStatusOutcome) => boolean;

/**
 * Polls GET /payments/{id} every STATUS_POLL_INTERVAL_MS. The next request is scheduled only after the previous one
 * finished, so requests never overlap. Stops on `stop()`, when `start()` is called for another payment, and on unmount.
 */
export function usePolling() {
  const isPolling = ref(false);
  let timeoutId: ReturnType<typeof setTimeout> | undefined;
  let controller: AbortController | undefined;

  const stop = (): void => {
    globalThis.clearTimeout(timeoutId);
    timeoutId = undefined;
    controller?.abort();
    controller = undefined;
    isPolling.value = false;
  };

  const start = (providerPaymentId: string, onOutcome: StatusOutcomeHandler): void => {
    stop();
    isPolling.value = true;
    const current = new AbortController();
    controller = current;

    const tick = async (): Promise<void> => {
      const outcome = await fetchPaymentStatus(providerPaymentId, current.signal);
      if (current.signal.aborted) return;
      if (onOutcome(outcome)) timeoutId = globalThis.setTimeout(tick, STATUS_POLL_INTERVAL_MS);
      else stop();
    };

    timeoutId = globalThis.setTimeout(tick, STATUS_POLL_INTERVAL_MS);
  };

  if (getCurrentScope()) onScopeDispose(stop);

  return { start, stop, isPolling: readonly(isPolling) };
}
