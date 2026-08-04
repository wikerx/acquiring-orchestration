package com.scott.payment.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.entity.TransactionIdempotencyDO;
import com.scott.payment.payment.mapper.TransactionIdempotencyMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionIdempotencyServiceTests
 * @date : 2026-08-04 14:00
 * @email : scott_x@163.com
 * @description : 验证首次交易请求幂等、商户订单流重开 CAS 和异步终态保护条件
 * @status : create
 */
@ExtendWith(MockitoExtension.class)
class DefaultTransactionIdempotencyServiceTests {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "idempotency-test"),
                TransactionIdempotencyDO.class);
    }

    @Mock
    private TransactionIdempotencyMapper idempotencyMapper;

    /** FAILED 流守卫必须同时带主键、scope、key、状态和版本条件才能重新占用。 */
    @Test
    void shouldRestartFailedMerchantOrderFlowWithVersionCas() {
        when(idempotencyMapper.update(isNull(), any())).thenReturn(1);
        DefaultTransactionIdempotencyService service = new DefaultTransactionIdempotencyService(idempotencyMapper);
        TransactionIdempotencyDO existing = record("MERCHANT_ORDER_FLOW", "M1:ORDER1", "FAILED");
        existing.setId(7L);
        existing.setVersion(3);
        TransactionIdempotencyDO replacement = record("MERCHANT_ORDER_FLOW", "M1:ORDER1", "PROCESSING");
        replacement.setMerchantOrderId("REQUEST-2");
        replacement.setTransactionDateTime(LocalDateTime.of(2026, 8, 4, 14, 0));
        replacement.setTransactionUtcTime(LocalDateTime.of(2026, 8, 4, 6, 0));
        replacement.setTransactionTimeZone("Asia/Shanghai");
        replacement.setRequestFingerprint("sha256:test");
        replacement.setExpireTime(LocalDateTime.of(2026, 9, 3, 14, 0));
        replacement.setUpdateTime(LocalDateTime.of(2026, 8, 4, 14, 0));

        boolean restarted = service.tryRestartFailedFlow(existing, replacement);

        assertThat(restarted).isTrue();
        LambdaUpdateWrapper<TransactionIdempotencyDO> wrapper = capturedUpdateWrapper();
        assertThat(wrapper.getSqlSet()).contains("version = version + 1");
        assertThat(wrapper.getSqlSegment()).contains("idempotency_scope", "idempotency_key", "transaction_status", "version");
    }

    /** 渠道终态同步只能更新两个首次交易 scope 中尚未终态的记录。 */
    @Test
    void shouldSynchronizeOnlyNonTerminalInitialTransactionRecords() {
        when(idempotencyMapper.update(isNull(), any())).thenReturn(2);
        DefaultTransactionIdempotencyService service = new DefaultTransactionIdempotencyService(idempotencyMapper);

        int affectedRows = service.synchronizeInitialTransactionStatus(
                "TX-1", PaymentTransactionStatusEnum.SUCCESS.getCode());

        assertThat(affectedRows).isEqualTo(2);
        LambdaUpdateWrapper<TransactionIdempotencyDO> wrapper = capturedUpdateWrapper();
        assertThat(wrapper.getSqlSet()).contains("version = version + 1");
        assertThat(wrapper.getSqlSegment()).contains(
                "transaction_id", "idempotency_scope", "transaction_type", "transaction_status", "deleted");
    }

    /** 非终态目标不得通过异步同步接口覆盖幂等事实。 */
    @Test
    void shouldRejectNonTerminalStatusSynchronization() {
        DefaultTransactionIdempotencyService service = new DefaultTransactionIdempotencyService(idempotencyMapper);

        int affectedRows = service.synchronizeInitialTransactionStatus(
                "TX-1", PaymentTransactionStatusEnum.PROCESSING.getCode());

        assertThat(affectedRows).isZero();
        verify(idempotencyMapper, never()).update(isNull(), any());
    }

    private TransactionIdempotencyDO record(String scope, String key, String status) {
        TransactionIdempotencyDO record = new TransactionIdempotencyDO();
        record.setIdempotencyScope(scope);
        record.setIdempotencyKey(key);
        record.setMerchantId("M1");
        record.setMerchantOrderNo("ORDER1");
        record.setTransactionType("PAYMENT");
        record.setTransactionStatus(status);
        record.setDeleted(0);
        return record;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private LambdaUpdateWrapper<TransactionIdempotencyDO> capturedUpdateWrapper() {
        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(idempotencyMapper).update(isNull(), captor.capture());
        return (LambdaUpdateWrapper<TransactionIdempotencyDO>) captor.getValue();
    }
}
