package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchResultDTO;
import com.scott.payment.payment.config.ChannelMatchAbnormalProperties;
import com.scott.payment.payment.domain.reconciliation.ChannelMatchAbnormalTypeEnum;
import com.scott.payment.payment.entity.TransactionAbnormalEventDO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.mapper.TransactionAbnormalEventMapper;
import com.scott.payment.payment.service.TransactionChannelMatchService;
import com.scott.payment.payment.service.TransactionQueryService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalQuery;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalRecord;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.RequeryCommand;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.ResolveCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultChannelMatchAbnormalServiceTests
 * @date : 2026-08-06 00:00
 * @description : 勾兑异常服务测试，覆盖确定性去重键、脱敏建案、受控处置和正常状态机恢复后的案件关闭。
 * @status : create
 */
class DefaultChannelMatchAbnormalServiceTests {

    private static final LocalDateTime TRANSACTION_TIME = LocalDateTime.of(2026, 8, 6, 12, 0);

    /** 自动升级只写案件，不从建案命令接受目标交易状态。 */
    @Test
    void shouldBuildDeterministicCaseForReviewRequiredTransaction() {
        Fixture fixture = new Fixture();
        TransactionOperationDO operation = operation();
        TransactionOrderDO order = new TransactionOrderDO();
        order.setTransactionDateTime(TRANSACTION_TIME.minusMinutes(10));
        when(fixture.recordService.findOrder(TRANSACTION_TIME, "OP-1")).thenReturn(order);

        fixture.service.recordReviewRequired(operation,
                ChannelMatchAbnormalTypeEnum.QUERY_RESULT_UNKNOWN.getCode(),
                "QUERY_EXCEPTION", "QUERY_EXCEPTION", "REQ-1", TRANSACTION_TIME.plusHours(1));

        ArgumentCaptor<TransactionAbnormalEventDO> captor = ArgumentCaptor.forClass(TransactionAbnormalEventDO.class);
        verify(fixture.mapper).upsertOccurrence(captor.capture());
        TransactionAbnormalEventDO row = captor.getValue();
        assertThat(row.getDeduplicationKey()).isEqualTo("QUERY_RESULT_UNKNOWN:TX-1");
        assertThat(row.getEventStatus()).isEqualTo("OPEN");
        assertThat(row.getRootTransactionDateTime()).isEqualTo(TRANSACTION_TIME.minusMinutes(10));
        assertThat(row.getSourceTransactionDateTime()).isNull();
        assertThat(row.getPlatformAmount()).isEqualByComparingTo("12.34");
        assertThat(row.getRawReferenceJson()).contains("QUERY_EXCEPTION").doesNotContain("targetStatus");
    }

    /** 人工处置不能使用系统自动恢复类型，也不能携带任意交易终态。 */
    @Test
    void shouldRejectAutomaticResolutionTypeFromManualRequest() {
        Fixture fixture = new Fixture();
        ResolveCommand command = new ResolveCommand();
        command.setTransactionDateTime(TRANSACTION_TIME);
        command.setExpectedVersion(2);
        command.setResolutionType("AUTO_RECOVERED");
        command.setReason("manual close");

        assertThatThrownBy(() -> fixture.service.resolve("ABN-1", command))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("not allowed");

        verify(fixture.mapper, never()).resolve(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /** 渠道重查确认终态时只调用案件自动关闭，不由案件服务写交易状态。 */
    @Test
    void shouldAutoResolveCaseAfterNormalChannelMatchCompletes() {
        Fixture fixture = new Fixture();
        AbnormalRecord open = record("OPEN", 3);
        AbnormalRecord resolved = record("RESOLVED", 4);
        when(fixture.mapper.selectRecord("ABN-1", TRANSACTION_TIME)).thenReturn(open, resolved);
        TransactionChannelMatchResultDTO matchResult = new TransactionChannelMatchResultDTO();
        matchResult.setMatchedCount(1);
        when(fixture.matchService.matchOne("TX-1", TRANSACTION_TIME)).thenReturn(matchResult);
        when(fixture.mapper.resolveActiveByTransaction(
                org.mockito.ArgumentMatchers.eq("TX-1"),
                org.mockito.ArgumentMatchers.eq(TRANSACTION_TIME),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(1);
        RequeryCommand command = new RequeryCommand();
        command.setTransactionDateTime(TRANSACTION_TIME);
        command.setExpectedVersion(3);

        AbnormalRecord result = fixture.service.requery("ABN-1", command);

        assertThat(result.getEventStatus()).isEqualTo("RESOLVED");
        verify(fixture.matchService).matchOne("TX-1", TRANSACTION_TIME);
        verify(fixture.mapper).resolveActiveByTransaction(
                org.mockito.ArgumentMatchers.eq("TX-1"),
                org.mockito.ArgumentMatchers.eq(TRANSACTION_TIME),
                org.mockito.ArgumentMatchers.contains("MANUAL_REQUERY"),
                org.mockito.ArgumentMatchers.any());
    }

    /** 页面查询时间必须按所选时区换算为交易库固定时区。 */
    @Test
    void shouldConvertQueryRangeToTransactionStorageTimezone() {
        Fixture fixture = new Fixture();
        AbnormalQuery query = new AbnormalQuery();
        query.setBeginTime(LocalDateTime.of(2026, 8, 6, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 8, 6, 1, 0));
        query.setQueryTimeZone("UTC");

        fixture.service.search(query);

        ArgumentCaptor<LocalDateTime> beginCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(fixture.mapper).count(org.mockito.ArgumentMatchers.eq(query), beginCaptor.capture(), endCaptor.capture());
        assertThat(beginCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 8, 6, 8, 0));
        assertThat(endCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 8, 6, 9, 0, 0, 1_000_000));
    }

    /** 非法页面时区必须返回参数错误，不得进入异常案件查询。 */
    @Test
    void shouldRejectInvalidQueryTimezone() {
        Fixture fixture = new Fixture();
        AbnormalQuery query = new AbnormalQuery();
        query.setQueryTimeZone("UTC+25:00");

        assertThatThrownBy(() -> fixture.service.search(query))
                .hasMessageContaining("queryTimeZone is invalid");
    }

    private TransactionOperationDO operation() {
        TransactionOperationDO operation = new TransactionOperationDO();
        operation.setTransactionId("TX-1");
        operation.setSourceTransactionId("SOURCE-TX-1");
        operation.setOperationId("OP-1");
        operation.setMerchantId("M-1");
        operation.setMerchantOrderNo("MO-1");
        operation.setTransactionType("PAYMENT");
        operation.setTransactionStatus("PROCESSING");
        operation.setTransactionCurrency("USD");
        operation.setTransactionAmount(new BigDecimal("12.34"));
        operation.setCurrencyExponent(2);
        operation.setChannelCode("MPGS");
        operation.setChannelOrderNo("CO-1");
        operation.setChannelTransactionId("CT-1");
        operation.setChannelMatchCount(12);
        operation.setTransactionDateTime(TRANSACTION_TIME);
        return operation;
    }

    private AbnormalRecord record(String status, int version) {
        AbnormalRecord record = new AbnormalRecord();
        record.setAbnormalEventId("ABN-1");
        record.setTransactionId("TX-1");
        record.setTransactionDateTime(TRANSACTION_TIME);
        record.setRootTransactionDateTime(TRANSACTION_TIME.minusMinutes(10));
        record.setEventStatus(status);
        record.setVersion(version);
        return record;
    }

    private static class Fixture {
        private final TransactionAbnormalEventMapper mapper = mock(TransactionAbnormalEventMapper.class);
        private final TransactionRecordService recordService = mock(TransactionRecordService.class);
        private final TransactionQueryService queryService = mock(TransactionQueryService.class);
        private final TransactionChannelMatchService matchService = mock(TransactionChannelMatchService.class);
        private final DefaultChannelMatchAbnormalService service;

        private Fixture() {
            ChannelMatchAbnormalProperties properties = new ChannelMatchAbnormalProperties();
            properties.setEnabled(true);
            TransactionShardingProperties shardingProperties = new TransactionShardingProperties();
            service = new DefaultChannelMatchAbnormalService(
                    mapper, recordService, queryService, matchService,
                    () -> "1001", properties, shardingProperties);
        }
    }
}
