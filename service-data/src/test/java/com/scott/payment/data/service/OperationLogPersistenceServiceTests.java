package com.scott.payment.data.service;

import com.scott.payment.component.mq.message.OperationLogMessage;
import com.scott.payment.data.entity.DataOperationLogDO;
import com.scott.payment.data.mapper.DataOperationLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperationLogPersistenceServiceTests
 * @date : 2026-08-01 14:40
 * @email : scott_x@163.com
 * @description : 操作日志数据库映射和唯一键重复吸收测试，确保异步消费保留原业务时间
 * @status : create
 */
@Slf4j
class OperationLogPersistenceServiceTests {

    /** 消息字段应完整映射，且原操作时间不能被消费时间覆盖。 */
    @Test
    void shouldPersistMappedAuditFieldsAndOriginalOperationTime() {
        log.info("测试操作日志字段映射，关键输入: 原操作时间和 Merchant 默认操作人类型");
        DataOperationLogMapper mapper = mock(DataOperationLogMapper.class);
        OperationLogPersistenceService service = new OperationLogPersistenceService(mapper);
        OperationLogMessage message = message();
        LocalDateTime operationTime = LocalDateTime.of(2026, 8, 1, 10, 30, 15, 123_000_000);
        message.setOperationTime(operationTime);

        service.persist(message, "MERCHANT-LOG-003");

        ArgumentCaptor<DataOperationLogDO> captor = ArgumentCaptor.forClass(DataOperationLogDO.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getIdempotentKey()).isEqualTo("MERCHANT-LOG-003");
        assertThat(captor.getValue().getOperatorType()).isEqualTo(2);
        assertThat(captor.getValue().getOperatedAt()).isEqualTo(operationTime);
        assertThat(captor.getValue().getModuleName()).isEqualTo("Merchant Profile");
        log.info("操作日志字段映射完成，结果: 原操作时间与默认操作人类型正确保留");
    }

    /** 数据库唯一键命中表示同一审计消息已经写入，应正常确认重复消费。 */
    @Test
    void shouldTreatDatabaseUniqueKeyAsCompletedDuplicate() {
        log.info("测试操作日志数据库最终幂等，关键输入: uk_sys_oper_idempotent_key 冲突");
        DataOperationLogMapper mapper = mock(DataOperationLogMapper.class);
        doThrow(new DuplicateKeyException("duplicate idempotent_key"))
                .when(mapper).insert(any(DataOperationLogDO.class));
        OperationLogPersistenceService service = new OperationLogPersistenceService(mapper);

        assertThatCode(() -> service.persist(message(), "MERCHANT-LOG-003"))
                .doesNotThrowAnyException();
        log.info("操作日志数据库最终幂等完成，结果: 重复消息被唯一索引安全吸收");
    }

    /** 超长错误摘要必须在消费持久化边界收敛到数据库列长度。 */
    @Test
    void shouldTruncateErrorMessageToDatabaseColumnLength() {
        log.info("测试操作日志错误摘要长度保护，关键输入: 8192 字符错误摘要");
        DataOperationLogMapper mapper = mock(DataOperationLogMapper.class);
        OperationLogPersistenceService service = new OperationLogPersistenceService(mapper);
        OperationLogMessage message = message();
        message.setErrorMessage("E".repeat(8192));

        service.persist(message, "ADMIN-LOG-LONG-ERROR");

        ArgumentCaptor<DataOperationLogDO> captor = ArgumentCaptor.forClass(DataOperationLogDO.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getErrorMsg())
                .hasSize(1000)
                .isEqualTo("E".repeat(1000));
        log.info("操作日志错误摘要长度保护完成，结果: 持久化字段长度为 1000");
    }

    /** 超长错误码必须在消费持久化边界收敛，兼容已经投递到 RocketMQ 的历史消息。 */
    @Test
    void shouldTruncateErrorCodeToDatabaseColumnLength() {
        log.info("测试操作日志错误码长度保护，关键输入: 34 字符异常类名错误码");
        DataOperationLogMapper mapper = mock(DataOperationLogMapper.class);
        OperationLogPersistenceService service = new OperationLogPersistenceService(mapper);
        OperationLogMessage message = message();
        message.setErrorCode("InvalidDataAccessApiUsageException");

        service.persist(message, "ADMIN-LOG-LONG-ERROR-CODE");

        ArgumentCaptor<DataOperationLogDO> captor = ArgumentCaptor.forClass(DataOperationLogDO.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getErrorCode())
                .hasSize(OperationLogMessage.ERROR_CODE_MAX_LENGTH)
                .isEqualTo("InvalidDataAccessApiUsageExcepti");
        log.info("操作日志错误码长度保护完成，结果: 持久化字段长度为 {}",
                OperationLogMessage.ERROR_CODE_MAX_LENGTH);
    }

    /** 创建不包含敏感内容的操作日志消息。 */
    private OperationLogMessage message() {
        OperationLogMessage message = new OperationLogMessage();
        message.setMessageId("MSG-MERCHANT-LOG-003");
        message.setSystemCode("MERCHANT");
        message.setOperationModule("Merchant Profile");
        message.setOperationName("Update Profile");
        message.setOperationType("2");
        message.setOperationStatus(1);
        return message;
    }
}
