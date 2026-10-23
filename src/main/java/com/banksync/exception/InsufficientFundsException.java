package com.banksync.exception;
public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException() { super("Insufficient funds"); }
    public InsufficientFundsException(Throwable cause) { super("Insufficient funds", cause); }
}
