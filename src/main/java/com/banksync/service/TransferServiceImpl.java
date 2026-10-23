package com.banksync.service;
import com.banksync.entity.Account;
import com.banksync.entity.Transaction;
import com.banksync.exception.*;
import com.banksync.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
@Service @RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {
    private static final BigDecimal MAX_BALANCE = new BigDecimal("99999999999999999.99");
    private final AccountRepository accounts;
    private final TransactionRepository transactions;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executeTransfer(String fromAccountNumber, String toAccountNumber, BigDecimal amount) {
        validateAccountNumber(fromAccountNumber);
        validateAccountNumber(toAccountNumber);
        if (fromAccountNumber.equals(toAccountNumber)) {
            throw new IllegalArgumentException("Source and destination accounts must differ");
        }
        if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_BALANCE) > 0) {
            throw new IllegalArgumentException("Amount must be positive and fit within 19 digits including two decimals");
        }
        final BigDecimal money;
        try { money = amount.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Amount must have at most two decimal places"); }
        try {
            Account source = accounts.findByAccountNumber(fromAccountNumber).orElseThrow(AccountNotFoundException::new);
            Account destination = accounts.findByAccountNumber(toAccountNumber).orElseThrow(AccountNotFoundException::new);
            if (source.getBalance().compareTo(money) < 0) throw new InsufficientFundsException();
            if (destination.getBalance().add(money).compareTo(MAX_BALANCE) > 0) {
                throw new IllegalArgumentException("Destination balance limit exceeded");
            }
            source.setBalance(source.getBalance().subtract(money));
            destination.setBalance(destination.getBalance().add(money));
            // Stable update order reduces deadlock risk for opposite-direction transfers.
            List<Account> ordered = source.getId() < destination.getId()
                    ? List.of(source, destination) : List.of(destination, source);
            accounts.saveAllAndFlush(ordered);
            transactions.saveAndFlush(new Transaction(fromAccountNumber, toAccountNumber, money, Transaction.Status.SUCCESS));
        } catch (ObjectOptimisticLockingFailureException conflict) {
            // Never swallow or retry inside this rollback-only transaction.
            throw new ConcurrentTransferException(conflict);
        }
    }
    private static void validateAccountNumber(String value) {
        if (value == null || !value.matches("[A-Za-z0-9-]{1,64}")) {
            throw new IllegalArgumentException("Account numbers must contain 1–64 letters, digits, or hyphens");
        }
    }
}
