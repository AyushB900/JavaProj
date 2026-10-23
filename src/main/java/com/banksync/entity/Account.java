package com.banksync.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
@Entity
@Table(name = "accounts")
@Getter @Setter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "account_number", nullable = false, unique = true, length = 64, updatable = false)
    private String accountNumber;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;
    /**
     * Hibernate includes this token in each UPDATE predicate and increments it on success.
     * If another transaction changed the row since it was read, zero rows match the old
     * version. Hibernate raises an optimistic-lock exception and the entire transfer rolls
     * back, including the other balance update and ledger insert. No partial debit commits.
     */
    @Version @Column(nullable = false)
    private Long version;
    public Account(String accountNumber, BigDecimal balance) {
        this.accountNumber = accountNumber;
        this.balance = balance;
    }
}
