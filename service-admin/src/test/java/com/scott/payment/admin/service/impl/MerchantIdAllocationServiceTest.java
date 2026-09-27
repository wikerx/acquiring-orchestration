package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.entity.merchant.MerchantIdSequenceDO;
import com.scott.payment.admin.mapper.MerchantIdSequenceMapper;
import com.scott.payment.component.core.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantIdAllocationServiceTest
 * @date : 2026-09-17 18:10
 * @email : scott_x@163.com
 * @description : 商户号分配服务单元测试，覆盖年度格式、序列上限以及数据库 CAS 失败保护。
 * @status : create
 */
@ExtendWith(MockitoExtension.class)
@Slf4j
class MerchantIdAllocationServiceTest {

    /** 固定业务时钟；时间为 2026-09-17 16:30:45.123（Asia/Shanghai）；非敏感测试数据。 */
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-17T08:30:45.123Z"),
            ZoneId.of("Asia/Shanghai")
    );

    @Mock
    private MerchantIdSequenceMapper sequenceMapper;

    /** 验证六位商户号由两位年份和四位年度流水组成。 */
    @Test
    void shouldAllocateSixDigitMerchantId() {
        log.info("测试六位商户号分配，关键输入: businessYear=26, currentSequence=47");
        MerchantIdSequenceDO sequence = sequence("26", 47, 3L);
        when(sequenceMapper.selectForUpdate("26")).thenReturn(sequence);
        when(sequenceMapper.increment("26", 47, 3L)).thenReturn(1);
        MerchantIdAllocationService service = new MerchantIdAllocationService(sequenceMapper, FIXED_CLOCK);

        String merchantId = service.allocate();

        assertThat(merchantId).isEqualTo("260048");
        verify(sequenceMapper).insertIfAbsent("26");
        log.info("六位商户号分配完成，结果: merchantId={}", merchantId);
    }

    /** 验证四位年度流水达到上限后拒绝继续分配，避免编号扩展为七位。 */
    @Test
    void shouldRejectExhaustedAnnualSequence() {
        log.info("测试商户号年度容量保护，关键输入: currentSequence=9999");
        when(sequenceMapper.selectForUpdate("26")).thenReturn(sequence("26", 9_999, 9L));
        MerchantIdAllocationService service = new MerchantIdAllocationService(sequenceMapper, FIXED_CLOCK);

        assertThatThrownBy(service::allocate)
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("本年度六位商户号已用尽");
        log.info("商户号年度容量保护完成，结果: 已拒绝超限分配");
    }

    /** 验证数据库递增未命中预期版本时立即失败，禁止返回未实际占用的商户号。 */
    @Test
    void shouldRejectSequenceCasFailure() {
        log.info("测试商户号序列并发保护，关键输入: expectedVersion=5");
        when(sequenceMapper.selectForUpdate("26")).thenReturn(sequence("26", 18, 5L));
        when(sequenceMapper.increment("26", 18, 5L)).thenReturn(0);
        MerchantIdAllocationService service = new MerchantIdAllocationService(sequenceMapper, FIXED_CLOCK);

        assertThatThrownBy(service::allocate)
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("并发更新失败");
        log.info("商户号序列并发保护完成，结果: 未返回未占用编号");
    }

    private MerchantIdSequenceDO sequence(String businessYear, int currentSequence, long version) {
        MerchantIdSequenceDO row = new MerchantIdSequenceDO();
        row.setBusinessYear(businessYear);
        row.setCurrentSequence(currentSequence);
        row.setVersion(version);
        return row;
    }
}
