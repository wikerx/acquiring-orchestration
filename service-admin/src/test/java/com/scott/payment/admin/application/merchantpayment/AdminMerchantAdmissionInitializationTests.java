package com.scott.payment.admin.application.merchantpayment;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminMerchantAdmissionInitializationTests {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 20, 12, 0);

    @Test
    void disabledOptionDoesNotReferenceTheNewColumns() {
        new AdminMerchantAdmissionInitialization(jdbc, false).initialize("200045", now);

        verifyNoInteractions(jdbc);
    }

    @Test
    void enabledCreationRequiresOneMerchantRowToBeInitialized() {
        Map<String, Object> parameters = Map.of("merchantId", "200045", "now", now);
        when(jdbc.update(anyString(), eq(parameters))).thenReturn(1, 0);
        var initialization = new AdminMerchantAdmissionInitialization(jdbc, true);

        initialization.initialize("200045", now);
        assertThatThrownBy(() -> initialization.initialize("200045", now)).isInstanceOf(IllegalStateException.class);
        verify(jdbc, org.mockito.Mockito.times(2)).update(anyString(), eq(parameters));
    }
}
