package com.banksync;
import com.banksync.config.SecurityConfiguration;
import com.banksync.controller.TransferController;
import com.banksync.service.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@WebMvcTest(TransferController.class)
@Import(SecurityConfiguration.class)
@ActiveProfiles("prod")
@TestPropertySource(properties = {"BANKSYNC_API_USER=test-service", "BANKSYNC_API_PASSWORD=test-only-password-12345"})
class ProductionSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean TransferService transfers;
    private static final String BODY = "{\"fromAccountNumber\":\"SOURCE\",\"toAccountNumber\":\"DEST\",\"amount\":1.00}";
    @Test void unauthenticatedTransfersAreRejected() throws Exception {
        mvc.perform(post("/api/v1/transfers").contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
        verifyNoInteractions(transfers);
    }
    @Test void incorrectCredentialsAreRejected() throws Exception {
        mvc.perform(post("/api/v1/transfers").header("Authorization", basic("wrong"))
                .contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
        verifyNoInteractions(transfers);
    }
    @Test void authenticatedServiceMayTransfer() throws Exception {
        mvc.perform(post("/api/v1/transfers").header("Authorization", basic("test-only-password-12345"))
                .contentType("application/json").content(BODY)).andExpect(status().isNoContent());
        verify(transfers).executeTransfer(eq("SOURCE"), eq("DEST"), argThat(a -> a.compareTo(new java.math.BigDecimal("1")) == 0));
    }
    private String basic(String password) {
        return "Basic " + Base64.getEncoder().encodeToString(("test-service:" + password).getBytes(StandardCharsets.UTF_8));
    }
}
