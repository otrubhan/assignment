<template>
  <section>
    <h2>Start a payment</h2>
    <form class="payment-form" novalidate @submit.prevent="handleSubmit">
      <div class="field">
        <label for="amount">Amount</label>
        <input
          id="amount"
          :value="amount"
          type="number"
          inputmode="decimal"
          step="0.01"
          required
          :aria-invalid="errors.amount ? 'true' : undefined"
          :aria-describedby="errors.amount ? 'amount-error' : undefined"
          @input="updateAmount"
        />
        <p v-if="errors.amount" id="amount-error" class="error" role="alert">
          {{ errors.amount }}
        </p>
      </div>

      <div class="field">
        <label for="currency">Currency</label>
        <select
          id="currency"
          v-model="currency"
          required
          :aria-invalid="errors.currency ? 'true' : undefined"
          :aria-describedby="errors.currency ? 'currency-error' : undefined"
        >
          <option value="" disabled>Select a currency</option>
          <option v-for="option in CURRENCIES" :key="option" :value="option">{{ option }}</option>
        </select>
        <p v-if="errors.currency" id="currency-error" class="error" role="alert">
          {{ errors.currency }}
        </p>
      </div>

      <div class="field">
        <label for="customer-reference">Customer reference</label>
        <input
          id="customer-reference"
          v-model="customerReference"
          type="text"
          :maxlength="CUST_REF_MAX_LENGTH"
          required
          :aria-invalid="errors.customerReference ? 'true' : undefined"
          :aria-describedby="errors.customerReference ? 'customer-reference-error' : undefined"
        />
        <p v-if="errors.customerReference" id="customer-reference-error" class="error" role="alert">
          {{ errors.customerReference }}
        </p>
      </div>

      <button type="submit" :disabled="submitting">Start payment</button>
    </form>
  </section>
</template>

<script setup lang="ts">
import type { Ref } from 'vue';
import { reactive, ref } from 'vue';
import { CURRENCIES, isCurrency, type Currency, type PaymentFormData } from '@/types/payment.ts';
import { CUST_REF_MAX_LENGTH } from '@/types/constants.ts';

withDefaults(defineProps<{ submitting?: boolean }>(), {
  submitting: false,
});

const emit = defineEmits<{
  submit: [paymentData: PaymentFormData];
}>();

const amount: Ref<string> = ref('');
const currency: Ref<Currency | ''> = ref('');
const customerReference: Ref<string> = ref('');

const errors = reactive({
  amount: '',
  currency: '',
  customerReference: '',
});

function updateAmount(event: Event) {
  amount.value = (event.currentTarget as HTMLInputElement).value;
}

function clearErrors() {
  errors.amount = '';
  errors.currency = '';
  errors.customerReference = '';
}

function validateInput() {
  const numericAmount = Number(amount.value);
  if (!Number.isFinite(numericAmount) || numericAmount <= 0) {
    errors.amount = 'Enter an amount as number greater than 0.';
  }

  if (!isCurrency(currency.value)) {
    errors.currency = 'Select a currency.';
  }

  if (customerReference.value.length < 1 || customerReference.value.length > CUST_REF_MAX_LENGTH) {
    errors.customerReference = 'Customer reference is required and must be shorter than ' + CUST_REF_MAX_LENGTH + ' characters.';
  }
}

function handleSubmit() {
  clearErrors();
  validateInput();

  if (errors.amount || errors.currency || errors.customerReference) return;

  emit('submit', {
    amount: amount.value,
    currency: currency.value as Currency,
    customerReference: customerReference.value,
  });
}
</script>

<style scoped>
.payment-form {
  display: grid;
  gap: 1rem;
  max-width: 28rem;
}

.field {
  display: grid;
  gap: 0.35rem;
}

input,
select,
button {
  box-sizing: border-box;
  font: inherit;
  padding: 0.55rem;
}

.error {
  color: #b42318;
  margin: 0;
}
</style>
