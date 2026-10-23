package com.banksync.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record TransferRequest(
    @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9-]+") String fromAccountNumber,
    @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9-]+") String toAccountNumber,
    @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount
) {}
