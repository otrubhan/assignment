<template>
  <section class="payment-status">
    <h2>Payment status</h2>
    <div class="details-section">
      <p v-if="paymentId"> <strong>Payment ID:</strong> {{ paymentId }} </p>
      <p>Sending {{ amount }} {{ currency }} to customer {{ customerReference }}</p>
    </div>
    <div class="status-section">
      <p><strong>Status:</strong> {{ status }}</p>
      <p v-if="showReason"><strong>Reason:</strong> {{ reason }}</p>
    </div>
    <div v-if="lastUpdatedInSeconds !== undefined || lastUpdatedInSeconds === 0" class="last-updated-section">
      <p>Last updated {{ lastUpdatedInSeconds }} seconds ago.</p>
    </div>
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

.details-section,
.status-section,
.last-updated-section {
  padding: .5rem .5rem;
}

.payment-status p {
  margin: 0.25rem 0;
  font-size: 0.95rem;
  line-height: 1.4;
}

.last-updated-section p {
  font-size: 0.85rem;
}</style>
