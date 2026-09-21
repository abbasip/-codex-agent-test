package banking;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

public final class PaymentSimulator {
    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    public static void main(String[] args) throws Exception {
        run("TEST 1", PaymentSimulator::successfulPayment);
        run("TEST 2", PaymentSimulator::insufficientFunds);
        run("TEST 3", PaymentSimulator::duplicateIdempotency);
        run("TEST 4", PaymentSimulator::idempotencyConflict);
        run("TEST 5", PaymentSimulator::currencyMismatch);
        run("TEST 6", PaymentSimulator::invalidAmounts);
        run("TEST 7", PaymentSimulator::concurrentDuplicates);
        run("TEST 8", PaymentSimulator::conservation);
        run("TEST 9", PaymentSimulator::auditHistory);
        run("TEST 10", PaymentSimulator::failedImmutability);
        run("TEST 11", PaymentSimulator::oppositeDirectionTransfers);
        System.out.println("ALL 11 TESTS PASSED");
    }
    private static void successfulPayment() { InMemoryBank b = bank("1000.00", "500.00"); PaymentTransaction t = service(b).submit(req("1", HUNDRED)); eq(PaymentStatus.POSTED,t.status(),"posted"); eq("900.00", b.balance("A"),"debit"); eq("600.00",b.balance("B"),"credit"); eq("1500.00",total(b),"total"); }
    private static void insufficientFunds() { InMemoryBank b=bank("50.00","10.00"); PaymentTransaction t=service(b).submit(req("2",HUNDRED)); rejected(t,"INSUFFICIENT_FUNDS"); eq("50.00",b.balance("A"),"debit unchanged"); eq("10.00",b.balance("B"),"credit unchanged"); }
    private static void duplicateIdempotency() { InMemoryBank b=bank("500.00","0.00"); PaymentService s=service(b); PaymentTransaction one=s.submit(req("3",HUNDRED)), two=s.submit(req("3",HUNDRED)); check(one==two,"duplicate must return original object"); eq("400.00",b.balance("A"),"debited once"); eq(1L,postedAudits(b,one),"one posting"); }
    private static void idempotencyConflict() { InMemoryBank b=bank("500.00","0.00"); PaymentService s=service(b); PaymentTransaction original=s.submit(req("4",HUNDRED)); PaymentTransaction conflict=s.submit(req("4",new BigDecimal("99.00"))); eq(PaymentStatus.POSTED,original.status(),"original posted"); rejected(conflict,"IDEMPOTENCY_CONFLICT"); check(same(b.balance("A"),new BigDecimal("400.00")),"conflict changed balance"); check(b.transactionByIdempotencyKey("4")==original,"original mapping replaced"); }
    private static void currencyMismatch() { InMemoryBank b=new InMemoryBank(); b.addAccount(account("A","USD","500.00")); b.addAccount(account("B","EUR","0.00")); PaymentTransaction t=service(b).submit(req("5",HUNDRED)); rejected(t,"CURRENCY_MISMATCH"); eq("500.00",b.balance("A"),"unchanged"); }
    private static void invalidAmounts() { InMemoryBank b=bank("500.00","0.00"); PaymentService s=service(b); rejected(s.submit(req("6a",new BigDecimal("0.00"))),"AMOUNT_MUST_BE_POSITIVE"); rejected(s.submit(req("6b",new BigDecimal("-1.00"))),"AMOUNT_MUST_BE_POSITIVE"); rejected(s.submit(req("6c",new BigDecimal("1.001"))),"INVALID_AMOUNT_SCALE"); eq("500.00",b.balance("A"),"invalid amount changed balance"); }
    private static void concurrentDuplicates() throws Exception { InMemoryBank b=bank("1000.00","0.00"); PaymentService s=service(b); ExecutorService x=Executors.newFixedThreadPool(50); List<Future<PaymentTransaction>> fs=new ArrayList<>(); for(int i=0;i<50;i++) fs.add(x.submit(()->s.submit(req("7",HUNDRED)))); x.shutdown(); check(x.awaitTermination(10,TimeUnit.SECONDS),"duplicate workload timeout"); Set<String> ids=new HashSet<>(); for(Future<PaymentTransaction> f:fs) ids.add(f.get().transactionId()); eq(1,ids.size(),"duplicate transactions"); PaymentTransaction t=b.transactionByIdempotencyKey("7"); eq(1L,postedAudits(b,t),"financial postings"); eq("900.00",b.balance("A"),"one debit"); eq("100.00",b.balance("B"),"one credit"); }
    private static void conservation() { InMemoryBank b=bank("1000.00","2000.00"); BigDecimal before=total(b); service(b).submit(req("8",HUNDRED)); check(same(before,total(b)),"total not conserved"); }
    private static void auditHistory() { InMemoryBank b=bank("500.00","0.00"); PaymentService s=service(b); PaymentTransaction ok=s.submit(req("9a",HUNDRED)), bad=s.submit(req("9b",new BigDecimal("0.00"))); eq(List.of(PaymentStatus.RECEIVED,PaymentStatus.VALIDATED,PaymentStatus.POSTED),statuses(b,ok),"success audit"); eq(List.of(PaymentStatus.RECEIVED,PaymentStatus.REJECTED),statuses(b,bad),"reject audit"); }
    private static void failedImmutability() { InMemoryBank b=bank("10.00","20.00"); BigDecimal before=total(b); PaymentTransaction t=service(b).submit(req("10",HUNDRED)); rejected(t,"INSUFFICIENT_FUNDS"); eq("10.00",b.balance("A"),"failed debit"); eq("20.00",b.balance("B"),"failed credit"); check(same(before,total(b)),"failed total"); }
    private static void oppositeDirectionTransfers() throws Exception { InMemoryBank b=bank("10000.00","10000.00"); PaymentService s=service(b); ExecutorService x=Executors.newFixedThreadPool(16); List<Future<PaymentTransaction>> fs=new ArrayList<>(); for(int i=0;i<50;i++){ final int n=i; fs.add(x.submit(()->s.submit(req("11a"+n,HUNDRED)))); fs.add(x.submit(()->s.submit(new PaymentRequest("11b"+n,"B","A",HUNDRED,"USD")))); } x.shutdown(); check(x.awaitTermination(10,TimeUnit.SECONDS),"opposite-direction workload timed out"); for(Future<PaymentTransaction> f:fs) eq(PaymentStatus.POSTED,f.get().status(),"transfer status"); eq("10000.00",b.balance("A"),"final A"); eq("10000.00",b.balance("B"),"final B"); eq("20000.00",total(b),"final total"); }
    private static InMemoryBank bank(String a,String c){ InMemoryBank b=new InMemoryBank(); b.addAccount(account("A","USD",a)); b.addAccount(account("B","USD",c)); return b; }
    private static Account account(String id,String currency,String balance){ return new Account(id,currency,new BigDecimal(balance)); }
    private static PaymentService service(InMemoryBank b){ return new PaymentService(b); }
    private static PaymentRequest req(String key,BigDecimal amount){ return new PaymentRequest(key,"A","B",amount,"USD"); }
    private static BigDecimal total(InMemoryBank b){ return b.balance("A").add(b.balance("B")); }
    private static List<PaymentStatus> statuses(InMemoryBank b,PaymentTransaction t){ return b.auditHistory(t.transactionId()).stream().map(AuditEvent::status).toList(); }
    private static long postedAudits(InMemoryBank b,PaymentTransaction t){ return statuses(b,t).stream().filter(s->s==PaymentStatus.POSTED).count(); }
    private static void rejected(PaymentTransaction t,String reason){ eq(PaymentStatus.REJECTED,t.status(),"rejected"); eq(reason,t.rejectionReason(),"reason"); }
    private static boolean same(BigDecimal a,BigDecimal b){ return a.compareTo(b)==0; }
    private static void eq(Object expected,Object actual,String label){ if(!Objects.equals(expected instanceof BigDecimal ? ((BigDecimal)expected).stripTrailingZeros() : expected, actual instanceof BigDecimal ? ((BigDecimal)actual).stripTrailingZeros() : actual)) throw new AssertionError(label+": expected "+expected+", got "+actual); }
    private static void eq(String expected,BigDecimal actual,String label){ eq(new BigDecimal(expected),actual,label); }
    private static void check(boolean condition,String message){ if(!condition) throw new AssertionError(message); }
    private static void run(String name, Checked test) throws Exception { test.run(); System.out.println(name+" PASSED"); }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
}
