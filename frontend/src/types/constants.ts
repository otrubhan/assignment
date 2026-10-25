export const CUST_REF_MAX_LENGTH = 30;

export const REQUEST_TIMEOUT_MS = 5_000;
export const MAX_RETRIES = 2;
export const INITIAL_RETRY_DELAY_MS = 250;
export const MAX_RETRY_JITTER_MS = 100;
export const MAX_RETRY_AFTER_MS = 5_000;
export const RETRYABLE_STATUSES = new Set([409, 429, 500, 502, 503, 504]);
