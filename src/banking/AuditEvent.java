package banking;

import java.time.Instant;

public record AuditEvent(String transactionId, PaymentStatus status, Instant timestamp, String reason) { }
