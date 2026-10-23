package com.banksync.exception;
public class ConcurrentTransferException extends RuntimeException {
    public ConcurrentTransferException() { super("Account changed during transfer; refresh balances before retrying"); }
    public ConcurrentTransferException(Throwable cause) { super("Account changed during transfer; refresh balances before retrying", cause); }
}
