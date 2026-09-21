package banking;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

public final class Account {
    private final String accountId;
    private final String currency;
    private final ReentrantLock lock = new ReentrantLock();
    private BigDecimal availableBalance;

    public Account(String accountId, String currency, BigDecimal availableBalance) {
        this.accountId = Objects.requireNonNull(accountId);
        this.currency = Objects.requireNonNull(currency);
        this.availableBalance = Money.requireTwoDecimals(availableBalance);
        if (this.availableBalance.signum() < 0) throw new IllegalArgumentException("initial balance cannot be negative");
    }
    public String accountId() { return accountId; }
    public String currency() { return currency; }
    ReentrantLock lock() { return lock; }
    BigDecimal balanceUnsafe() { return availableBalance; }
    void debitUnsafe(BigDecimal amount) { availableBalance = availableBalance.subtract(amount); }
    void creditUnsafe(BigDecimal amount) { availableBalance = availableBalance.add(amount); }
}
