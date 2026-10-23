package com.banksync.exception;
public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException() { super("One or both accounts do not exist"); }
    public AccountNotFoundException(Throwable cause) { super("One or both accounts do not exist", cause); }
}
