package com.scott.payment.merchant.service.impl;

import com.scott.payment.merchant.dto.SysOperLogRecordRequest;
import com.scott.payment.merchant.entity.SysOperLogDO;
import com.scott.payment.merchant.mapper.SysOperLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOperLogServiceImplTests
 * @date : 2026-07-30 18:35
 * @email : scott_x@163.com
 * @description : 验证商户端操作日志只把有业务幂等键的唯一冲突视为 MQ 重复消费
 * @status : create
 */
@Slf4j
class MerchantOperLogServiceImplTests {

    @Test
    void shouldTreatIdempotentKeyConflictAsCompletedMqDelivery() {
        log.info("测试商户端日志数据库幂等，关键输入: 非空 idempotentKey、唯一键冲突");
        SysOperLogMapper mapper = duplicateMapper();
        MerchantOperLogServiceImpl service = new MerchantOperLogServiceImpl(mapper);
        SysOperLogRecordRequest request = new SysOperLogRecordRequest();
        request.setIdempotentKey("MERCHANT-LOG-DB-001");

        assertThatCode(() -> service.recordOperLog(request)).doesNotThrowAnyException();
        log.info("商户端日志数据库幂等测试完成，结果: 唯一冲突按已持久化处理");
    }

    @Test
    void shouldPropagateDuplicateKeyWhenRequestHasNoIdempotentBoundary() {
        log.info("测试商户端日志异常边界，关键输入: idempotentKey 为空、数据库唯一冲突");
        SysOperLogMapper mapper = duplicateMapper();
        MerchantOperLogServiceImpl service = new MerchantOperLogServiceImpl(mapper);

        assertThatThrownBy(() -> service.recordOperLog(new SysOperLogRecordRequest()))
                .isInstanceOf(DuplicateKeyException.class);
        log.info("商户端日志异常边界测试完成，结果: 非 MQ 幂等冲突未被吞掉");
    }

    private SysOperLogMapper duplicateMapper() {
        SysOperLogMapper mapper = mock(SysOperLogMapper.class);
        doThrow(new DuplicateKeyException("uk_sys_oper_idempotent_key"))
                .when(mapper).insert(any(SysOperLogDO.class));
        return mapper;
    }
}
