# Transaction-banking simulator architecture

## Components
- `Account`, `PaymentTransaction`, `PaymentRequest`, and `AuditEvent` model the domain.
- `InMemoryBank` owns in-memory account, transaction, idempotency, and audit state and exposes query operations.
- `PaymentService` validates and posts payments.
- `PaymentSimulator` is an executable deterministic test harness using standard JDK concurrency tools.

## Lifecycle and idempotency
A new transaction is persisted and audited as `RECEIVED`. A valid request transitions to `VALIDATED`, then `POSTED`; validation failures transition directly to terminal `REJECTED` with a reason. The idempotency map is protected by a service lock: an identical fingerprint returns the original transaction, while a different fingerprint creates a separately auditable rejected conflict without replacing the original key mapping.

## Atomicity and concurrency
Balances are changed only after validation. The debit and credit account locks are held together while both changes occur, and `POSTED` is recorded only after both changes. For a two-account operation, locks are always acquired by ascending `accountId`, preventing opposite-direction deadlock. Concurrent maps protect repository access; the idempotency decision is serialized.

## Invariants
Amounts must be positive and representable at scale two. Payment, debit, and credit currencies must match. Rejections never change balances. Every posted transfer debits and credits the same amount, preserving total balance. `POSTED` and `REJECTED` are terminal.

## Limitations
State is process-local and lost on exit; there is no durable recovery, authentication, authorization, FX, or cross-process transactional coordination. The simulator deliberately favors clarity over throughput.
