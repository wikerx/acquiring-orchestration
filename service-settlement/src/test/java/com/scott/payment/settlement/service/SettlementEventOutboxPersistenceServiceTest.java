package com.scott.payment.settlement.service;

import com.scott.payment.settlement.entity.SettlementEventOutboxDO;
import com.scott.payment.settlement.mapper.SettlementEventOutboxMapper;
import com.scott.payment.settlement.support.SettlementWorkerIdentity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 Outbox 发送失败使用有上限指数退避，避免 Broker 故障时每分钟固定冲击。 */
class SettlementEventOutboxPersistenceServiceTest {

    @Test
    void shouldUseOneMinuteForFirstFailureAndCapBackoffAtThirtyMinutes() {
        SettlementEventOutboxMapper mapper = mock(SettlementEventOutboxMapper.class);
        SettlementWorkerIdentity worker = mock(SettlementWorkerIdentity.class);
        SettlementEventOutboxPersistenceService service =
                new SettlementEventOutboxPersistenceService(mapper, worker);
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 14, 0);
        SettlementEventOutboxDO first = processingRow(0);
        SettlementEventOutboxDO repeated = processingRow(12);
        when(mapper.markFailed(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(1);

        assertThat(service.markFailed(first, "BROKER_UNAVAILABLE", now)).isTrue();
        assertThat(service.markFailed(repeated, "BROKER_UNAVAILABLE", now)).isTrue();

        ArgumentCaptor<LocalDateTime> retryTime = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper, org.mockito.Mockito.times(2)).markFailed(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
                retryTime.capture(), org.mockito.ArgumentMatchers.eq(now));
        assertThat(retryTime.getAllValues()).containsExactly(now.plusMinutes(1), now.plusMinutes(30));
    }

    private SettlementEventOutboxDO processingRow(int retryCount) {
        SettlementEventOutboxDO row = new SettlementEventOutboxDO();
        row.setEventNo("EVENT-" + retryCount);
        row.setProcessingOwner("worker-1");
        row.setVersion(1L);
        row.setRetryCount(retryCount);
        return row;
    }
}
