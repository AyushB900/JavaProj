package com.banksync.config;
import com.banksync.entity.Account;
import com.banksync.repository.AccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.List;
@Configuration @Profile("demo & !prod")
public class DemoDataConfiguration {
    @Bean
    CommandLineRunner seedDemoAccounts(AccountRepository accounts, TransactionTemplate tx) {
        return args -> tx.executeWithoutResult(status -> {
            if (accounts.count() == 0) {
                accounts.saveAllAndFlush(List.of(new Account("ACC-1001", new BigDecimal("1000.00")),
                        new Account("ACC-1002", new BigDecimal("500.00"))));
            }
        });
    }
}
