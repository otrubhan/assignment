<template>
  <main id="app">
    <h1>Payment status inbox</h1>
    <PaymentForm :submitting="isSubmitting" @submit="onSubmit" />
    <PaymentStatus
      v-if="payment"
      :amount="payment.amount"
      :currency="payment.currency"
      :customer-reference="payment.customerReference"
      :status="payment.paymentStatus"
      :reason="payment.errorReason"
      :payment-id="payment.paymentId"
    />
  </main>
</template>

<script setup lang="ts">
import PaymentForm from '@/components/PaymentForm.vue';
import PaymentStatus from '@/components/PaymentStatus.vue';
import { usePayment } from '@/composables/paymentState.ts';
import type { PaymentFormData } from '@/types/payment.ts';

const { payment, isSubmitting, submit } = usePayment();

async function onSubmit(paymentFormData: PaymentFormData) {
  await submit(paymentFormData);
}
</script>
