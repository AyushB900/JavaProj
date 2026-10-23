package com.banksync;

import com.banksync.entity.Account;
import com.banksync.repository.*;
import com.banksync.service.TransferService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class DashboardControllerTest extends DatabaseIntegrationSupport {
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired TransactionRepository transactions;
    @Autowired TransferService transfers;
    @BeforeEach void seed() {
        transactions.deleteAllInBatch(); accounts.deleteAllInBatch();
        accounts.saveAllAndFlush(List.of(new Account("ACC-1001",new BigDecimal("1000.00")),
                new Account("ACC-1002",new BigDecimal("500.00"))));
    }
    @Test void dashboardReturnsExactDecimalStringsAndCommittedLedger() throws Exception {
        transfers.executeTransfer("ACC-1001","ACC-1002",new BigDecimal("25.50"));
        mvc.perform(get("/api/v1/dashboard")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBalance").value("1500.00"))
                .andExpect(jsonPath("$.totalTransferred").value("25.50"))
                .andExpect(jsonPath("$.accountCount").value(2))
                .andExpect(jsonPath("$.transactionCount").value(1))
                .andExpect(jsonPath("$.accounts[0].balance").value("974.50"))
                .andExpect(jsonPath("$.recentTransactions[0].amount").value("25.50"))
                .andExpect(jsonPath("$.recentTransactions[0].timestamp").isNotEmpty())
                .andExpect(jsonPath("$.accounts[0].version").doesNotExist());
    }
    @Test void historyPaginatesAndFiltersOnTheServer() throws Exception {
        for(int i=0;i<12;i++) transfers.executeTransfer("ACC-1001","ACC-1002",new BigDecimal("1.11"));
        mvc.perform(get("/api/v1/transactions?page=1&size=10&search=ACC-1002"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(get("/api/v1/transactions?search=NOT-FOUND"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
    }
    @Test void invalidHistoryParametersAreRejected() throws Exception {
        for(String query:List.of("page=-1","page=oops","size=0","size=51","search=bad%20value","search=%25")) {
            mvc.perform(get("/api/v1/transactions?"+query)).andExpect(status().isBadRequest());
        }
    }
    @Test void frontendAssetsAreServedByTheApplication() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/index.html")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(header().exists("Content-Security-Policy"));
        mvc.perform(get("/app.js")).andExpect(status().isOk());
        mvc.perform(get("/styles.css")).andExpect(status().isOk());
    }
    @Test void emptyWorkspaceReturnsUsableEmptyState() throws Exception {
        accounts.deleteAllInBatch();
        mvc.perform(get("/api/v1/dashboard")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBalance").value("0.00"))
                .andExpect(jsonPath("$.accounts.length()").value(0))
                .andExpect(jsonPath("$.recentTransactions.length()").value(0));
    }
}
