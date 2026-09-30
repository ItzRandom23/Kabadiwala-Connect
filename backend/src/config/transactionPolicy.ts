/**
 * Multi-record pickup/settlement operations require several database round
 * trips. Atlas verification exceeded Prisma's five-second default before
 * mandatory audit writes and commit. Keep a bounded budget without moving
 * integrity checks or audit records outside the transaction.
 */
export const transactionOptions = { maxWait: 5_000, timeout: 20_000 };
