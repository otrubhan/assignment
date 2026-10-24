export const CURRENCIES = ['EUR', 'USD', 'CZK'] as const;

export type Currency = (typeof CURRENCIES)[number];

export function isCurrency(value: string): value is Currency {
  return CURRENCIES.some((currency) => currency === value);
}
