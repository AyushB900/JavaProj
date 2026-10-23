package com.banksync.repository;
import com.banksync.entity.Transaction;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    @Query("select coalesce(sum(t.amount), 0) from Transaction t where t.status = :status")
    BigDecimal totalTransferred(@Param("status") Transaction.Status status);
    List<Transaction> findTop100ByOrderByTimestampDescIdDesc();
    @Query("select t from Transaction t where lower(t.sourceAccountNumber) like lower(concat('%', :search, '%')) or lower(t.destinationAccountNumber) like lower(concat('%', :search, '%'))")
    Page<Transaction> search(@Param("search") String search, Pageable pageable);
}
