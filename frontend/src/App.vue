<template>
  <main id="app">
    <h1>Payment status inbox</h1>
    <PaymentForm @submit="onSubmit" />
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
import { ref, type Ref } from 'vue';
import PaymentForm from '@/components/PaymentForm.vue';
import PaymentStatus from '@/components/PaymentStatus.vue';
import { type PaymentData, type PaymentFormData, PaymentStatusEnum } from '@/types/payment.ts';

const payment: Ref<PaymentData | undefined> = ref(undefined);

function onSubmit(paymentFormData: PaymentFormData) {
  payment.value = { ...paymentFormData, paymentStatus: PaymentStatusEnum.SUBMITTING };
  // send form data to backend
  // handle backend response and update status accordingly
  // start interval to poll for status updates
}
</script>

<style scoped></style>
