package com.scott.payment.data.service;

import com.scott.payment.component.mq.message.RiskAuditHitMessage;
import com.scott.payment.component.mq.message.RiskEvaluationAuditMessage;
import com.scott.payment.data.mapper.DataRiskAuditMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 风控审计主记录唯一键和同事务命中明细写入测试
 */
@Slf4j
class RiskAuditPersistenceServiceTests {

    /**
     * 主记录唯一键冲突时不得重复插入任何命中明细。
     */
    @Test
    void shouldTreatExistingRiskRecordAsCompletedDuplicate() {
        log.info("测试风控审计数据库最终幂等，关键输入: risk_record_no 唯一键冲突");
        DataRiskAuditMapper mapper = mock(DataRiskAuditMapper.class);
        when(mapper.insertEvaluationRecord(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new DuplicateKeyException("duplicate risk record"));
        when(mapper.countByRiskRecordNo("RK202608010001")).thenReturn(1);
        RiskAuditPersistenceService service = new RiskAuditPersistenceService(mapper);

        assertThatCode(() -> service.persist(message())).doesNotThrowAnyException();

        verify(mapper, never()).insertEvaluationHit(any(), any(), any());
        log.info("风控审计数据库最终幂等完成，结果: 未重复写入命中明细");
    }

    /**
     * 唯一键冲突但 riskRecordNo 不存在时必须上抛，避免误确认未持久化的评估事实。
     */
    @Test
    void shouldRethrowConflictWhenRiskRecordDoesNotExist() {
        DataRiskAuditMapper mapper = mock(DataRiskAuditMapper.class);
        DuplicateKeyException conflict = new DuplicateKeyException("unrelated unique key");
        when(mapper.insertEvaluationRecord(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(conflict);
        when(mapper.countByRiskRecordNo("RK202608010001")).thenReturn(0);
        RiskAuditPersistenceService service = new RiskAuditPersistenceService(mapper);

        assertThatThrownBy(() -> service.persist(message())).isSameAs(conflict);
    }

    /**
     * 新评估记录应在同一调用中写入主记录和有效命中明细。
     */
    @Test
    void shouldPersistRecordAndValidHitDetails() {
        log.info("测试风控审计完整写入，关键输入: 1 条有效明细和 1 条空明细");
        DataRiskAuditMapper mapper = mock(DataRiskAuditMapper.class);
        RiskAuditPersistenceService service = new RiskAuditPersistenceService(mapper);
        RiskEvaluationAuditMessage message = message();

        service.persist(message);

        verify(mapper).insertEvaluationRecord(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(mapper).insertEvaluationHit(
                org.mockito.ArgumentMatchers.eq("RK202608010001"),
                org.mockito.ArgumentMatchers.eq(message.getHits().get(0)),
                any());
        log.info("风控审计完整写入完成，结果: 主记录和有效明细均已提交给 Mapper");
    }

    /**
     * 创建只包含脱敏命中值的风控审计消息。
     */
    private RiskEvaluationAuditMessage message() {
        RiskAuditHitMessage hit = new RiskAuditHitMessage();
        hit.setModuleType("BLACK");
        hit.setFunctionCode("cardBin");
        hit.setReasonCode("NO_MATCH");
        hit.setHitValueMasked("411111******1111");
        RiskEvaluationAuditMessage message = new RiskEvaluationAuditMessage();
        message.setRiskRecordNo("RK202608010001");
        message.setDecisionResult("BLOCK");
        message.setDecisionReasonCode("BLACKLIST_HIT");
        message.setHits(List.of(hit, new RiskAuditHitMessage()));
        return message;
    }
}
