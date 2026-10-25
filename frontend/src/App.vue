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
import type { CreatePaymentResponse, GetPaymentInfoResponse } from '@/types/service.ts';
import { createPayment, getPaymentInfo, PaymentApiError } from '@/services/paymentApi.ts';

const payment: Ref<PaymentData | undefined> = ref(undefined);
let activeRequest = 0;

async function onSubmit(paymentFormData: PaymentFormData) {
  payment.value = { ...paymentFormData, paymentStatus: PaymentStatusEnum.SUBMITTING };
  await sendCreatePaymentRequest(paymentFormData);
  if (payment.value.paymentStatus === PaymentStatusEnum.PENDING) {
    //TODO: if status is unknown, I don't know if the payment actually started
    await pollPaymentStatus();
  }
}

//TODO: verify out-of-order responses (activeRequest, requestVersion) and new request (!payment.value)
//TODO: verify all error types are handled
//TODO: rename fieldMessages and error.message, verify interfaces
async function sendCreatePaymentRequest(paymentFormData: PaymentFormData) {
  const requestVersion = ++activeRequest;
  try {
    const result: CreatePaymentResponse = await createPayment(paymentFormData);
    if (requestVersion !== activeRequest || !payment.value) return;

    payment.value.paymentId = result.providerPaymentId;
    payment.value.paymentStatus = PaymentStatusEnum.PENDING;
  } catch (error) {
    if (requestVersion !== activeRequest || !payment.value) return;

    if (error instanceof PaymentApiError) {
      const fieldMessages = Object.entries(error.fieldErrors).map(([field, message]) => `${field}: ${message}`);
      payment.value.errorReason = fieldMessages.join('; ') || error.message;
      payment.value.paymentStatus = error.status === undefined ? PaymentStatusEnum.BE_NOT_REACHABLE : PaymentStatusEnum.FAILED;
    } else {
      payment.value.errorReason = 'An unexpected error occurred while starting the payment.';
      payment.value.paymentStatus = PaymentStatusEnum.BE_NOT_REACHABLE;
    }
  }
}

async function pollPaymentStatus() {
  if (payment.value?.paymentStatus === PaymentStatusEnum.PENDING || payment.value?.paymentStatus === PaymentStatusEnum.UNKNOWN) {
    const timeoutId = window.setTimeout(() => sendGetPaymentRequest(), 2000);
  }
}

async function sendGetPaymentRequest() {
  try {
    if (payment.value?.paymentId) {
      const result: GetPaymentInfoResponse = await getPaymentInfo(payment.value.paymentId);
      payment.value.paymentStatus = result.status;
      await pollPaymentStatus();
    }
  } catch (error) {
    if (error instanceof PaymentApiError) {
      payment.value.errorReason = error.message;
      payment.value.paymentStatus = error.status === undefined ? PaymentStatusEnum.BE_NOT_REACHABLE : PaymentStatusEnum.FAILED;
    } else {
      payment.value.errorReason = 'An unexpected error occurred while checking the payment status.';
      payment.value.paymentStatus = PaymentStatusEnum.BE_NOT_REACHABLE;
      await pollPaymentStatus();
    }
  }
}
</script>
