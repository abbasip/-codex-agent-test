package banking;

import java.math.BigDecimal;
import java.time.Instant;

public final class PaymentTransaction {
    private final String transactionId, idempotencyKey, debitAccount, creditAccount, currency;
    private final BigDecimal amount;
    private final Instant receivedAt;
    private volatile Instant updatedAt;
    private volatile PaymentStatus status;
    private volatile String rejectionReason;

    PaymentTransaction(String id, PaymentRequest request, BigDecimal amount) {
        transactionId = id; idempotencyKey = request.idempotencyKey(); debitAccount = request.debitAccount();
        creditAccount = request.creditAccount(); currency = request.currency(); this.amount = amount;
        receivedAt = updatedAt = Instant.now(); status = PaymentStatus.RECEIVED;
    }
    synchronized void transition(PaymentStatus next, String reason) {
        boolean allowed = (status == PaymentStatus.RECEIVED && (next == PaymentStatus.VALIDATED || next == PaymentStatus.REJECTED))
                || (status == PaymentStatus.VALIDATED && next == PaymentStatus.POSTED);
        if (!allowed) throw new IllegalStateException("invalid lifecycle transition " + status + " to " + next);
        status = next; rejectionReason = reason; updatedAt = Instant.now();
    }
    public String transactionId() { return transactionId; } public String idempotencyKey() { return idempotencyKey; }
    public String debitAccount() { return debitAccount; } public String creditAccount() { return creditAccount; }
    public BigDecimal amount() { return amount; } public String currency() { return currency; }
    public PaymentStatus status() { return status; } public Instant receivedAt() { return receivedAt; }
    public Instant updatedAt() { return updatedAt; } public String rejectionReason() { return rejectionReason; }
}
