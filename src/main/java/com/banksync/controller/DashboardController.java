package com.banksync.controller;
import com.banksync.entity.Transaction;
import com.banksync.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.*;
import org.springframework.transaction.annotation.*;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.List;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1")
public class DashboardController {
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final Environment environment;
    public record AccountView(String accountNumber, String balance) {}
    public record TransactionView(String id, String sourceAccountNumber, String destinationAccountNumber,
            String amount, Instant timestamp, String status) {
        static TransactionView from(Transaction t) {
            return new TransactionView(t.getId().toString(), t.getSourceAccountNumber(), t.getDestinationAccountNumber(),
                    t.getAmount().toPlainString(), t.getTimestamp().toInstant(ZoneOffset.UTC), t.getStatus().name());
        }
    }
    public record Dashboard(String environment, String totalBalance, String totalTransferred,
            long accountCount, long transactionCount, List<AccountView> accounts,
            List<TransactionView> recentTransactions, Instant refreshedAt) {}
    public record History(List<TransactionView> items, long totalElements, int totalPages, int page) {}

    @GetMapping("/dashboard")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Dashboard dashboard() {
        var page = accounts.findAll(PageRequest.of(0, 100, Sort.by("id")));
        return new Dashboard(environment.matchesProfiles("demo & !prod") ? "Demo workspace" : "Private workspace",
                accounts.totalBalance().setScale(2).toPlainString(), transactions.totalTransferred(Transaction.Status.SUCCESS).setScale(2).toPlainString(),
                page.getTotalElements(), transactions.count(), page.getContent().stream()
                    .map(a -> new AccountView(a.getAccountNumber(), a.getBalance().toPlainString())).toList(),
                transactions.findTop100ByOrderByTimestampDescIdDesc().stream().map(TransactionView::from).toList(), Instant.now());
    }
    @GetMapping("/transactions")
    @Transactional(readOnly = true)
    public History history(@RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "10") int size,
                           @RequestParam(defaultValue = "") String search) {
        if (page < 0 || size < 1 || size > 50 || !search.matches("[A-Za-z0-9-]{0,64}")) {
            throw new IllegalArgumentException("Invalid page, page size, or account search");
        }
        var results = transactions.search(search, PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("timestamp"), Sort.Order.desc("id"))));
        return new History(results.getContent().stream().map(TransactionView::from).toList(),
                results.getTotalElements(), results.getTotalPages(), page);
    }
}
