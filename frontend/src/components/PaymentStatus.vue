<template>
  <section class="payment-status">
    <h2>Payment status</h2>
    <p>Sending {{ amount }} {{ currency }} to customer {{ customerReference }}</p>
    <p>Status: {{ status }}</p>
    <p v-if="showReason">Reason: {{ reason }}</p>
    <p v-if="paymentId">PaymentID: {{ paymentId }}</p>
    <p v-if="lastUpdatedInSeconds !== undefined || lastUpdatedInSeconds === 0"> Last updated {{ lastUpdatedInSeconds }} seconds ago. </p>
  </section>
</template>

<script lang="ts" setup>
import type { ComputedRef } from 'vue';
import { computed } from 'vue';
import { PaymentStatusEnum } from '@/types/payment.ts';
import type { Currency } from '@/types/common.ts';

const props = defineProps<{
  amount: string;
  currency: Currency;
  customerReference: string;
  status: PaymentStatusEnum;
  reason?: string;
  paymentId?: string;
  lastUpdatedInSeconds?: number;
}>();

const showReason: ComputedRef<boolean> = computed(() => props.status === PaymentStatusEnum.FAILED || props.status === PaymentStatusEnum.BE_NOT_REACHABLE);
</script>

<style scoped>
.payment-status {
  max-width: 28rem;
}

.payment-status p {
  margin: 0.5rem 0;
}
</style>
