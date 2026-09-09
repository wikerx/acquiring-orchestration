package com.scott.payment.settlement.service.impl;

import com.scott.payment.settlement.domain.model.SettlementBatchStatus;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.mapper.SettlementBatchMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultSettlementBatchLeaseServiceTest
 * @date : 2026-08-26 22:30
 * @email : scott_x@163.com
 * @description : 验证处理实例只能通过批次行锁和 version CAS 获取有限期数据库租约，不依赖 Redis 或 JVM 锁。
 * @status : create
 */
class DefaultSettlementBatchLeaseServiceTest {

    private SettlementBatchMapper batchMapper;
    private DefaultSettlementBatchLeaseService service;

    @BeforeEach
    void setUp() {
        batchMapper = mock(SettlementBatchMapper.class);
        service = new DefaultSettlementBatchLeaseService(batchMapper);
    }

    /** 锁读到可处理批次后必须带原 version 获取租约并返回更新后的版本。 */
    @Test
    void shouldAcquireDatabaseLeaseWithVersionCas() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 1, 0);
        LocalDateTime deadline = now.plusMinutes(2);
        SettlementBatchDO batch = new SettlementBatchDO();
        batch.setSettlementBatchNo("SB20260826-00000008");
        batch.setBatchStatus(SettlementBatchStatus.CLAIMED.name());
        batch.setCandidateCount(2);
        batch.setVersion(3L);
        when(batchMapper.selectNextProcessableForUpdate(now)).thenReturn(batch);
        when(batchMapper.acquireProcessingLease(
                batch.getSettlementBatchNo(), "instance-1", now, deadline, 3L)).thenReturn(1);

        Optional<SettlementBatchDO> acquired = service.acquireNext("instance-1", now, deadline);

        assertThat(acquired).containsSame(batch);
        assertThat(batch.getProcessingOwner()).isEqualTo("instance-1");
        assertThat(batch.getProcessingDeadline()).isEqualTo(deadline);
        assertThat(batch.getVersion()).isEqualTo(4L);
    }
}
