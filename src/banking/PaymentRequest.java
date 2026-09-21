package banking;

import java.math.BigDecimal;
import java.util.Objects;

public record PaymentRequest(String idempotencyKey, String debitAccount, String creditAccount,
                             BigDecimal amount, String currency) {
    public PaymentRequest {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(debitAccount, "debitAccount");
        Objects.requireNonNull(creditAccount, "creditAccount");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
    }
}
