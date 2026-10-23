package com.banksync.repository;
import com.banksync.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.Query;
public interface AccountRepository extends JpaRepository<Account, Long> {
    @Query("select coalesce(sum(a.balance), 0) from Account a")
    BigDecimal totalBalance();
    Optional<Account> findByAccountNumber(String accountNumber);
}
