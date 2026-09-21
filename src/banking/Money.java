package banking;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class Money {
    private Money() { }
    static BigDecimal requireTwoDecimals(BigDecimal amount) {
        if (amount == null || amount.scale() > 2) throw new IllegalArgumentException("amount must be representable at 2 decimal places");
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }
}
