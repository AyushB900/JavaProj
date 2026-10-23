package com.banksync;

import com.banksync.entity.Account;
import com.banksync.entity.Transaction;
import com.banksync.exception.InsufficientFundsException;
import com.banksync.repository.AccountRepository;
import com.banksync.repository.TransactionRepository;
import com.banksync.service.TransferService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TransferControllerConcurrencyTest extends DatabaseIntegrationSupport {
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @MockitoSpyBean TransactionRepository transactions;
    @Autowired TransferService transfers;
    @Autowired ObjectMapper json;

    @BeforeEach
    void seed() {
        reset(transactions);
        transactions.deleteAllInBatch();
        accounts.deleteAllInBatch();
        accounts.saveAllAndFlush(List.of(new Account("SOURCE", new BigDecimal("100.00")),
                new Account("DEST", BigDecimal.ZERO), new Account("OTHER", BigDecimal.ZERO)));
    }
    @AfterEach void resetSpy() { reset(transactions); }

    @Test
    void successfulTransferCommitsExactBalancesAndLedger() throws Exception {
        mvc.perform(post("/api/v1/transfers").contentType("application/json")
                .content(payload("SOURCE", "DEST", "12.34"))).andExpect(status().isNoContent());
        assertBalance("SOURCE", "87.66"); assertBalance("DEST", "12.34");
        assertThat(transactions.findAll()).singleElement().satisfies(t -> {
            assertThat(t.getAmount()).isEqualByComparingTo("12.34");
            assertThat(t.getSourceAccountNumber()).isEqualTo("SOURCE");
            assertThat(t.getDestinationAccountNumber()).isEqualTo("DEST");
            assertThat(t.getStatus()).isEqualTo(Transaction.Status.SUCCESS);
            assertThat(t.getTimestamp()).isNotNull();
        });
        assertThat(accounts.findByAccountNumber("SOURCE").orElseThrow().getVersion()).isEqualTo(1L);
    }

    @Test
    void tenConcurrentTransfersCannotDoubleSpend() throws Exception {
        List<Integer> results = burst(i -> payload("SOURCE", "DEST", "30.00"));
        long successful = countSuccessful(results);
        assertThat(successful).isBetween(1L, 3L);
        assertBalance("SOURCE", new BigDecimal("100.00").subtract(new BigDecimal("30.00").multiply(BigDecimal.valueOf(successful))).toPlainString());
        assertBalance("DEST", new BigDecimal("30.00").multiply(BigDecimal.valueOf(successful)).toPlainString());
        assertLedgerAndConservation(successful, "100.00");
    }

    @Test
    void concurrentDifferentDestinationsCannotSpendTheSameFunds() throws Exception {
        AtomicInteger destSuccess = new AtomicInteger();
        AtomicInteger otherSuccess = new AtomicInteger();
        List<Integer> results = burst(i -> {
            return payload("SOURCE", i % 2 == 0 ? "DEST" : "OTHER", "40.00");
        }, (i, status) -> {
            if (status == 204) (i % 2 == 0 ? destSuccess : otherSuccess).incrementAndGet();
        });
        long successful = countSuccessful(results);
        assertThat(successful).isBetween(1L, 2L);
        assertBalance("SOURCE", new BigDecimal("100.00").subtract(new BigDecimal("40.00").multiply(BigDecimal.valueOf(successful))).toPlainString());
        assertBalance("DEST", BigDecimal.valueOf(destSuccess.get()).multiply(new BigDecimal("40.00")).toPlainString());
        assertBalance("OTHER", BigDecimal.valueOf(otherSuccess.get()).multiply(new BigDecimal("40.00")).toPlainString());
        assertLedgerAndConservation(successful, "100.00");
    }

    @Test
    void oppositeDirectionTransfersConserveMoneyAndMatchAcceptedRequests() throws Exception {
        Account destination = accounts.findByAccountNumber("DEST").orElseThrow();
        destination.setBalance(new BigDecimal("100.00")); accounts.saveAndFlush(destination);
        AtomicInteger forward = new AtomicInteger(); AtomicInteger reverse = new AtomicInteger();
        List<Integer> results = burst(i -> i % 2 == 0 ? payload("SOURCE", "DEST", "10.00") : payload("DEST", "SOURCE", "10.00"),
            (i, status) -> { if (status == 204) (i % 2 == 0 ? forward : reverse).incrementAndGet(); });
        long successful = countSuccessful(results);
        assertThat(successful).isGreaterThanOrEqualTo(1);
        BigDecimal delta = BigDecimal.valueOf(forward.get() - reverse.get()).multiply(new BigDecimal("10.00"));
        assertBalance("SOURCE", new BigDecimal("100.00").subtract(delta).toPlainString());
        assertBalance("DEST", new BigDecimal("100.00").add(delta).toPlainString());
        assertLedgerAndConservation(successful, "200.00");
    }

    @Test
    void insufficientFundsRollsBackWithoutLedger() throws Exception {
        mvc.perform(post("/api/v1/transfers").contentType("application/json")
                .content(payload("SOURCE", "DEST", "100.01")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        assertUnchanged();
        assertThatThrownBy(() -> transfers.executeTransfer("SOURCE", "DEST", new BigDecimal("101.00")))
                .isInstanceOf(InsufficientFundsException.class);
        assertUnchanged();
    }

    @Test
    void ledgerFailureRollsBackBothAlreadyFlushedBalances() throws Exception {
        doThrow(new DataIntegrityViolationException("Simulated ledger storage failure"))
                .when(transactions).saveAndFlush(any(Transaction.class));
        mvc.perform(post("/api/v1/transfers").contentType("application/json")
                .content(payload("SOURCE", "DEST", "10.00")))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.trace").doesNotExist());
        assertUnchanged();
    }

    @Test
    void missingAccountReturnsSanitized404() throws Exception {
        mvc.perform(post("/api/v1/transfers").contentType("application/json")
                .content(payload("SOURCE", "MISSING", "1.00")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        assertUnchanged();
    }

    @Test
    void malformedAndInvalidRequestsDoNotChangeMoney() throws Exception {
        List<String> invalid = List.of(
                payload("SOURCE", "DEST", "0"), payload("SOURCE", "DEST", "-1"),
                payload("SOURCE", "DEST", "1.001"), payload("SOURCE", "DEST", "100000000000000000.00"),
                payload("SOURCE", "SOURCE", "1.00"), payload("", "DEST", "1.00"),
                payload("SOURCE", "bad account", "1.00"),
                "{\"fromAccountNumber\":\"SOURCE\",\"toAccountNumber\":\"DEST\",\"amount\":null}",
                "{\"fromAccountNumber\":\"SOURCE\",\"toAccountNumber\":\"DEST\"}",
                "{\"fromAccountNumber\":\"SOURCE\",\"toAccountNumber\":\"DEST\",\"amount\":1,\"extra\":1}", "{", "null");
        for (String body : invalid) {
            mvc.perform(post("/api/v1/transfers").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").exists())
                    .andExpect(jsonPath("$.trace").doesNotExist());
            assertUnchanged();
        }
    }

    @Test
    void serviceRejectsInvalidMoneyEvenWithoutController() {
        for (BigDecimal amount : Arrays.asList(null, BigDecimal.ZERO, new BigDecimal("-1"), new BigDecimal("0.001"))) {
            assertThatThrownBy(() -> transfers.executeTransfer("SOURCE", "DEST", amount)).isInstanceOf(IllegalArgumentException.class);
            assertUnchanged();
        }
    }

    @Test
    void staleVersionCannotOverwriteACommittedBalance() {
        Account first = accounts.findByAccountNumber("SOURCE").orElseThrow();
        Account stale = accounts.findByAccountNumber("SOURCE").orElseThrow();
        first.setBalance(new BigDecimal("90.00"));
        accounts.saveAndFlush(first);
        stale.setBalance(new BigDecimal("80.00"));
        assertThatThrownBy(() -> accounts.saveAndFlush(stale))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
        assertBalance("SOURCE", "90.00");
        assertThat(transactions.count()).isZero();
    }

    @Test
    void destinationOverflowRollsBack() throws Exception {
        Account destination = accounts.findByAccountNumber("DEST").orElseThrow();
        destination.setBalance(new BigDecimal("99999999999999999.99")); accounts.saveAndFlush(destination);
        mvc.perform(post("/api/v1/transfers").contentType("application/json").content(payload("SOURCE", "DEST", "0.01")))
                .andExpect(status().isBadRequest());
        assertBalance("SOURCE", "100.00"); assertBalance("DEST", "99999999999999999.99");
        assertThat(transactions.count()).isZero();
    }

    private interface Body { String get(int index) throws Exception; }
    private interface Result { void accept(int index, int status); }
    private List<Integer> burst(Body body) throws Exception { return burst(body, (i, s) -> {}); }
    private List<Integer> burst(Body body, Result observer) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch ready = new CountDownLatch(10), start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < 10; index++) {
                final int i = index;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(15, TimeUnit.SECONDS)) throw new AssertionError("Start barrier timed out");
                    int status = mvc.perform(post("/api/v1/transfers").contentType("application/json").content(body.get(i)))
                            .andReturn().getResponse().getStatus();
                    observer.accept(i, status);
                    return status;
                }));
            }
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) statuses.add(f.get(30, TimeUnit.SECONDS));
            assertThat(statuses).hasSize(10).allMatch(s -> s == 204 || s == 409 || s == 422);
            return statuses;
        } finally {
            start.countDown(); pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
    private long countSuccessful(List<Integer> statuses) { return statuses.stream().filter(s -> s == 204).count(); }
    private String payload(String from, String to, String amount) throws Exception {
        return json.writeValueAsString(Map.of("fromAccountNumber", from, "toAccountNumber", to, "amount", new BigDecimal(amount)));
    }
    private void assertBalance(String account, String balance) {
        assertThat(accounts.findByAccountNumber(account).orElseThrow().getBalance()).isEqualByComparingTo(balance);
    }
    private void assertUnchanged() { assertBalance("SOURCE", "100.00"); assertBalance("DEST", "0.00"); assertThat(transactions.count()).isZero(); }
    private void assertLedgerAndConservation(long successful, String total) {
        assertThat(transactions.count()).isEqualTo(successful);
        assertThat(transactions.findAll()).allMatch(t -> t.getStatus() == Transaction.Status.SUCCESS);
        assertThat(accounts.findAll()).allMatch(a -> a.getBalance().signum() >= 0);
        assertThat(accounts.findAll().stream().map(Account::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(total);
    }
}
