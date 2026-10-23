package com.banksync.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
@Entity @Immutable
@Table(name = "transfer_transactions")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {
    public enum Status { SUCCESS, FAILED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "source_account_number", nullable = false, length = 64, updatable = false)
    private String sourceAccountNumber;
    @Column(name = "destination_account_number", nullable = false, length = 64, updatable = false)
    private String destinationAccountNumber;
    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16, updatable = false)
    private Status status;
    public Transaction(String source, String destination, BigDecimal amount, Status status) {
        this.sourceAccountNumber = source;
        this.destinationAccountNumber = destination;
        this.amount = amount;
        this.status = status;
        this.timestamp = LocalDateTime.now(ZoneOffset.UTC);
    }
}
