1) - I require Idempotency key for the POST /payments endpoint. If a request is sent without it, error response is sent.
   - Normally I would choose optimistic locking when concurrent requests are not expected to occur frequently, but in this case I use pessimistic locking for more simplicity.
   - Callback eventId is handled to by unique globally not just for payment. Valid eventId is suppressed in case of wrong timestamp ordering or final status check.
   - I record callback event IDs with global uniqueness, including valid callbacks suppressed by timestamp ordering or final-status protection.
      Suppression does not update providerStatus or lastCallbackOccurredAt. Unknown payments are rejected without recording an event.
2) I generated constraints in PaymentForm.vue component but did not specify how to handle them. AI mixed browser validation with custom handling.
    Some of the constraints were not enforced in the frontend.
3) Currently the Fake Provider is tightly coupled by calling its class directly by authorize() in one transaction with
   deduplication and saving the payment. Fake Provider also does not retry so if callback with CAPTURED or DECLINED is not received,
    polling won't know the payment is finished and will keep polling.