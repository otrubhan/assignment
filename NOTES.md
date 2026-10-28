1) - I require Idempotency key for the POST /payments endpoint. If a request is sent without it, error response is sent.
   - Normally I would choose optimistic locking when concurrent requests are not expected to occur frequently, but in this case I use pessimistic locking for more simplicity.
   - I decided not to record callback events. The main entity in this app is the Payment. The Payment is supposed to have current valid state. When an event is handled, previous state does not matter.
      If an event is out-of-order or invalid, it is ignored and not processed at all.
2) I generated constraints in PaymentForm.vue component but did not specify how to handle them. AI mixed browser validation with custom handling.
    Some of the constraints were not enforced in the frontend.
3) Currently the Fake Provider is tightly coupled by calling its class directly by authorize() in one transaction with
   deduplication and saving the payment. Fake Provider also does not retry so if callback with CAPTURED or DECLINED is not received,
    polling won't know the payment is finished and will keep polling.