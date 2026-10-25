import type { Ref } from 'vue';
import { onUnmounted, ref } from 'vue';
import type { GetPaymentInfoResponse } from '@/types/service.ts';

export function usePaymentPolling() {
  const isPolling: Ref<boolean> = ref(false);
  let pollTimeoutId: number | undefined = undefined;

  const startPolling = async () => {
    if (!isPolling.value) return;

    try {
      const result: GetPaymentInfoResponse = await paymentApi.getPaymentInfo(payment.value.paymentId);
      data.value = response;
      error.value = undefined;

      if (shouldStop(response)) {
        stopPolling();
        return;
      }
    } catch (err) {
      error.value = err;

      if (shouldStopOnError(err)) {
        stopPolling();
        return;
      }
    }

    if (isPolling.value) {
      pollTimeoutId = window.setTimeout(startPolling, 2000);
    }
  };

  const stopPolling = () => {
    isPolling.value = false;
    if (pollTimeoutId !== undefined) {
      window.clearTimeout(pollTimeoutId);
      pollTimeoutId = undefined;
    }
  };

  onUnmounted(() => {
    stopPolling();
  });

  return {
    isPolling,
    startPolling,
    stopPolling,
  };
}
