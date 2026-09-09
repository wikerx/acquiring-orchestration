package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchResultDTO;
import com.scott.payment.payment.config.ChannelMatchAbnormalProperties;
import com.scott.payment.payment.domain.reconciliation.ChannelMatchAbnormalTypeEnum;
import com.scott.payment.payment.entity.TransactionAbnormalEventDO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.mapper.TransactionAbnormalEventMapper;
import com.scott.payment.payment.service.TransactionChannelMatchService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalRecord;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.RequeryCommand;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.ResolveCommand;
import lombok.extern.slf4j.Slf4j;
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
 * @email : scott_x@163.com
 * @description : 勾兑异常服务测试，覆盖确定性去重键、脱敏建案、受控处置和正常状态机恢复后的案件关闭。
 * @status : create
 */
@Slf4j
class DefaultChannelMatchAbnormalServiceTests {

    private static final LocalDateTime TRANSACTION_TIME = LocalDateTime.of(2026, 8, 6, 12, 0);

    /** 自动升级只写案件，不从建案命令接受目标交易状态。 */
    @Test
    void shouldBuildDeterministicCaseForReviewRequiredTransaction() {
        log.info("用例：验证勾兑异常使用稳定去重键建案且不接受目标交易状态");
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
        log.info("结果：案件以异常类型和交易号生成去重键，保存脱敏证据且未携带目标状态");
    }

    @Test
    void shouldPersistChannelMoneyWithoutCrossCurrencyDifference() {
        log.info("用例：验证跨币种渠道金额只保存事实快照而不计算差额");
        Fixture fixture = new Fixture();
        TransactionOperationDO operation = operation();
        ChannelPaymentResponse response = new ChannelPaymentResponse();
        response.setChannelCurrency(" eur ");
        response.setChannelAmount(new BigDecimal("12.50"));

        fixture.service.recordReviewRequired(
                operation,
                ChannelMatchAbnormalTypeEnum.CURRENCY_MISMATCH.getCode(),
                "CURRENCY_MISMATCH",
                "CURRENCY_MISMATCH",
                "REQ-1",
                response,
                TRANSACTION_TIME.plusHours(1));

        ArgumentCaptor<TransactionAbnormalEventDO> captor = ArgumentCaptor.forClass(TransactionAbnormalEventDO.class);
        verify(fixture.mapper).upsertOccurrence(captor.capture());
        TransactionAbnormalEventDO row = captor.getValue();
        assertThat(row.getPlatformCurrency()).isEqualTo("USD");
        assertThat(row.getPlatformAmount()).isEqualByComparingTo("12.34");
        assertThat(row.getChannelCurrency()).isEqualTo("EUR");
        assertThat(row.getChannelAmount()).isEqualByComparingTo("12.50");
        assertThat(row.getAmountDifference()).isNull();
        log.info("结果：平台与渠道币种不同时金额差额保持为空");
    }

    @Test
    void shouldPersistChannelMinusPlatformDifferenceForSameCurrency() {
        log.info("用例：验证同币种异常案件按渠道金额减平台金额保存差额");
        Fixture fixture = new Fixture();
        ChannelPaymentResponse response = new ChannelPaymentResponse();
        response.setChannelCurrency("USD");
        response.setChannelAmount(new BigDecimal("12.50"));

        fixture.service.recordReviewRequired(
                operation(),
                ChannelMatchAbnormalTypeEnum.AMOUNT_MISMATCH.getCode(),
                "AMOUNT_MISMATCH",
                "AMOUNT_MISMATCH",
                "REQ-1",
                response,
                TRANSACTION_TIME.plusHours(1));

        ArgumentCaptor<TransactionAbnormalEventDO> captor = ArgumentCaptor.forClass(TransactionAbnormalEventDO.class);
        verify(fixture.mapper).upsertOccurrence(captor.capture());
        TransactionAbnormalEventDO row = captor.getValue();
        assertThat(row.getChannelAmount()).isEqualByComparingTo("12.50");
        assertThat(row.getAmountDifference()).isEqualByComparingTo("0.16");
        log.info("结果：同币种金额差额按主币种BigDecimal精确计算为0.16");
    }

    /** 人工处置不能使用系统自动恢复类型，也不能携带任意交易终态。 */
    @Test
    void shouldRejectAutomaticResolutionTypeFromManualRequest() {
        log.info("用例：验证人工处置不能提交系统自动恢复类型");
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
        log.info("结果：非法人工处置在更新案件前被拒绝");
    }

    /** 渠道重查确认终态时只调用案件自动关闭，不由案件服务写交易状态。 */
    @Test
    void shouldAutoResolveCaseAfterNormalChannelMatchCompletes() {
        log.info("用例：验证渠道重查匹配后仅通过正常状态机关闭活动案件");
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
        log.info("结果：匹配成功后只关闭异常案件并返回最新案件状态");
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
        private final TransactionChannelMatchService matchService = mock(TransactionChannelMatchService.class);
        private final DefaultChannelMatchAbnormalService service;

        private Fixture() {
            ChannelMatchAbnormalProperties properties = new ChannelMatchAbnormalProperties();
            properties.setEnabled(true);
            service = new DefaultChannelMatchAbnormalService(
                    mapper, recordService, matchService, () -> "1001", properties);
        }
    }
}
