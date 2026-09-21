package banking;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryBank {
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final Map<String, PaymentTransaction> transactions = new ConcurrentHashMap<>();
    private final Map<String, PaymentTransaction> idempotency = new ConcurrentHashMap<>();
    private final Map<String, List<AuditEvent>> audit = new ConcurrentHashMap<>();
    public void addAccount(Account account) { if (accounts.putIfAbsent(account.accountId(), account) != null) throw new IllegalArgumentException("duplicate account"); }
    Account account(String id) { return accounts.get(id); }
    void save(PaymentTransaction tx) { transactions.put(tx.transactionId(), tx); idempotency.put(tx.idempotencyKey(), tx); }
    void saveConflict(PaymentTransaction tx) { transactions.put(tx.transactionId(), tx); }
    PaymentTransaction idempotent(String key) { return idempotency.get(key); }
    public PaymentTransaction transaction(String id) { return transactions.get(id); }
    public PaymentTransaction transactionByIdempotencyKey(String key) { return idempotency.get(key); }
    void audit(PaymentTransaction tx, String reason) { audit.computeIfAbsent(tx.transactionId(), ignored -> Collections.synchronizedList(new ArrayList<>())).add(new AuditEvent(tx.transactionId(), tx.status(), tx.updatedAt(), reason)); }
    public List<AuditEvent> auditHistory(String id) {
        List<AuditEvent> events = audit.get(id);
        if (events == null) return List.of();
        synchronized (events) { return List.copyOf(events); }
    }
    public BigDecimal balance(String id) { Account a = account(id); if (a == null) throw new IllegalArgumentException("unknown account"); a.lock().lock(); try { return a.balanceUnsafe(); } finally { a.lock().unlock(); } }
}
