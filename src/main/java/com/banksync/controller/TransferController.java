package com.banksync.controller;
import com.banksync.dto.TransferRequest;
import com.banksync.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/transfers")
public class TransferController {
    private final TransferService transferService;
    @PostMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transfer(@Valid @RequestBody TransferRequest request) {
        transferService.executeTransfer(request.fromAccountNumber(), request.toAccountNumber(), request.amount());
    }
}
