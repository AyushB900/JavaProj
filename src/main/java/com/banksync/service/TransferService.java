package com.banksync.service;
import java.math.BigDecimal;
public interface TransferService {
    void executeTransfer(String fromAccountNumber, String toAccountNumber, BigDecimal amount);
}
