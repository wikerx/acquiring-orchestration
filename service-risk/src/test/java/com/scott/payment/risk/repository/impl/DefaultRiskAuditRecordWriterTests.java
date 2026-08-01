package com.scott.payment.risk.repository.impl;

import com.scott.payment.risk.mapper.RiskRuntimeMapper;
import com.scott.payment.risk.mq.message.RiskEvaluationAuditMessage;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultRiskAuditRecordWriterTests
 * @date : 2026-07-30 18:40
 * @email : scott_x@163.com
 * @description : 验证 risk_record_no 唯一约束命中后不重复写入风控命中明细
 * @status : create
 */
@Slf4j
class DefaultRiskAuditRecordWriterTests {

    @Test
    void shouldTreatExistingRiskRecordAsCompletedDuplicate() {
        log.info("测试风控审计数据库幂等，关键输入: 主记录唯一冲突、消息包含同一 riskRecordNo");
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RiskRuntimeMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapper);
        when(mapper.insertEvaluationRecord(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new DuplicateKeyException("duplicate risk record"));
        DefaultRiskAuditRecordWriter writer = new DefaultRiskAuditRecordWriter(provider);
        RiskEvaluationAuditMessage message = new RiskEvaluationAuditMessage();
        message.setRiskRecordNo("RK202607280004");

        assertThatCode(() -> writer.write(message)).doesNotThrowAnyException();

        verify(mapper, never()).insertEvaluationHit(any(), any(), any());
        log.info("风控审计数据库幂等测试完成，结果: 唯一冲突按已完成处理且未重复写明细");
    }
}
