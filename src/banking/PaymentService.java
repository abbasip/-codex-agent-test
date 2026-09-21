package banking;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public final class PaymentService {
    private final InMemoryBank bank;
    private final Object idempotencyLock = new Object();
    public PaymentService(InMemoryBank bank) { this.bank = Objects.requireNonNull(bank); }

    public PaymentTransaction submit(PaymentRequest request) {
        synchronized (idempotencyLock) {
            PaymentTransaction existing = bank.idempotent(request.idempotencyKey());
            if (existing != null) {
                if (sameFingerprint(existing, request)) return existing;
                return rejectConflict(request);
            }
            PaymentTransaction tx = new PaymentTransaction(UUID.randomUUID().toString(), request, request.amount());
            bank.save(tx); bank.audit(tx, null);
            process(tx);
            return tx;
        }
    }

    private PaymentTransaction rejectConflict(PaymentRequest request) {
        PaymentTransaction conflict = new PaymentTransaction(UUID.randomUUID().toString(), request, request.amount());
        bank.saveConflict(conflict); bank.audit(conflict, null);
        reject(conflict, "IDEMPOTENCY_CONFLICT");
        return conflict;
    }
    private boolean sameFingerprint(PaymentTransaction tx, PaymentRequest request) {
        return tx.debitAccount().equals(request.debitAccount()) && tx.creditAccount().equals(request.creditAccount())
                && tx.currency().equals(request.currency()) && tx.amount().compareTo(request.amount()) == 0;
    }
    private void process(PaymentTransaction tx) {
        final BigDecimal amount;
        try { amount = Money.requireTwoDecimals(tx.amount()); }
        catch (IllegalArgumentException e) { reject(tx, "INVALID_AMOUNT_SCALE"); return; }
        if (amount.signum() <= 0) { reject(tx, "AMOUNT_MUST_BE_POSITIVE"); return; }
        Account debit = bank.account(tx.debitAccount());
        if (debit == null) { reject(tx, "DEBIT_ACCOUNT_NOT_FOUND"); return; }
        Account credit = bank.account(tx.creditAccount());
        if (credit == null) { reject(tx, "CREDIT_ACCOUNT_NOT_FOUND"); return; }
        if (debit == credit) { reject(tx, "IDENTICAL_ACCOUNTS"); return; }
        if (!tx.currency().equals(debit.currency()) || !tx.currency().equals(credit.currency()) || !debit.currency().equals(credit.currency())) {
            reject(tx, "CURRENCY_MISMATCH"); return;
        }
        Account first = debit.accountId().compareTo(credit.accountId()) < 0 ? debit : credit;
        Account second = first == debit ? credit : debit;
        first.lock().lock(); second.lock().lock();
        try {
            if (debit.balanceUnsafe().compareTo(amount) < 0) { reject(tx, "INSUFFICIENT_FUNDS"); return; }
            tx.transition(PaymentStatus.VALIDATED, null); bank.audit(tx, null);
            // Both account locks remain held until both balance changes have been applied.
            debit.debitUnsafe(amount); credit.creditUnsafe(amount);
            tx.transition(PaymentStatus.POSTED, null); bank.audit(tx, null);
        } finally { second.lock().unlock(); first.lock().unlock(); }
    }
    private void reject(PaymentTransaction tx, String reason) { tx.transition(PaymentStatus.REJECTED, reason); bank.audit(tx, reason); }
}
