package com.scott.payment.data.service;

import com.scott.payment.component.mq.message.CheckoutCardVaultStoreMessage;
import com.scott.payment.data.config.DataCardVaultProperties;
import com.scott.payment.data.entity.DataCheckoutCardVaultDO;
import com.scott.payment.data.mapper.DataCheckoutCardVaultMapper;
import com.scott.payment.data.security.DataCardVaultCryptoService;
import com.scott.payment.data.security.DataCheckoutCardVaultTransferService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 卡资料库数据库最终幂等测试。
 */
class CheckoutCardVaultPersistenceServiceTests {

    /**
     * 验证首次消费只写入加密卡资料且模型不存在 CVV 列。
     */
    @Test
    void shouldInsertEncryptedRecordWithoutCvvColumn() {
        DataCheckoutCardVaultMapper mapper = mock(DataCheckoutCardVaultMapper.class);
        when(mapper.insert(any())).thenReturn(1);
        CheckoutCardVaultPersistenceService service = service(mapper);

        boolean inserted = service.persist(message(), plaintext());

        ArgumentCaptor<DataCheckoutCardVaultDO> captor = ArgumentCaptor.forClass(DataCheckoutCardVaultDO.class);
        verify(mapper).insert(captor.capture());
        DataCheckoutCardVaultDO record = captor.getValue();
        assertThat(inserted).isTrue();
        assertThat(record.getPanCiphertext()).isNotBlank().doesNotContain(plaintext().cardNo());
        assertThat(record.getWrappedDekCiphertext()).isNotBlank();
        assertThat(DataCheckoutCardVaultDO.class.getDeclaredFields())
                .noneMatch(field -> field.getName().toLowerCase().contains("cvv")
                        || field.getName().toLowerCase().contains("securitycode"));
    }

    /**
     * 验证完全相同的数据库业务身份按幂等重复处理。
     */
    @Test
    void shouldTreatSameDatabaseIdentityAsDuplicate() {
        DataCheckoutCardVaultMapper mapper = mock(DataCheckoutCardVaultMapper.class);
        CheckoutCardVaultStoreMessage message = message();
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));
        DataCheckoutCardVaultDO existing = new DataCheckoutCardVaultDO();
        existing.setMessageId(message.getMessageId());
        existing.setMerchantId(message.getMerchantId());
        existing.setCheckoutAttemptId(message.getCheckoutAttemptId());
        existing.setTransactionId(message.getTransactionId());
        existing.setTransactionDateTime(message.getTransactionDateTime());
        when(mapper.selectIdentityByMessageId(message.getMessageId(), message.getTransactionDateTime()))
                .thenReturn(existing);
        when(mapper.selectIdentityByMerchantTransaction(
                message.getMerchantId(), message.getTransactionId(), message.getTransactionDateTime()))
                .thenReturn(existing);

        boolean inserted = service(mapper).persist(message, plaintext());

        assertThat(inserted).isFalse();
    }

    /**
     * 两个唯一键分别命中不同业务身份时必须失败关闭，不能按任意一条记录误判为幂等。
     */
    @Test
    void shouldRejectCrossConstraintIdentityConflict() {
        DataCheckoutCardVaultMapper mapper = mock(DataCheckoutCardVaultMapper.class);
        CheckoutCardVaultStoreMessage message = message();
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));
        DataCheckoutCardVaultDO sameMessageIdentity = identity(message);
        DataCheckoutCardVaultDO conflictingTransactionIdentity = identity(message);
        conflictingTransactionIdentity.setMessageId("OTHER-MESSAGE");
        conflictingTransactionIdentity.setCheckoutAttemptId("OTHER-ATTEMPT");
        when(mapper.selectIdentityByMessageId(message.getMessageId(), message.getTransactionDateTime()))
                .thenReturn(sameMessageIdentity);
        when(mapper.selectIdentityByMerchantTransaction(
                message.getMerchantId(), message.getTransactionId(), message.getTransactionDateTime()))
                .thenReturn(conflictingTransactionIdentity);

        assertThatThrownBy(() -> service(mapper).persist(message, plaintext()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("card vault idempotency key conflicts with another transaction");
    }

    /**
     * 构造使用测试 Mapper 和密码组件的卡资料持久化服务。
     */
    private CheckoutCardVaultPersistenceService service(DataCheckoutCardVaultMapper mapper) {
        DataCardVaultProperties properties = new DataCardVaultProperties();
        properties.setEnabled(true);
        properties.setPanHmacKeyVersion("hmac-v1");
        properties.setPanHmacPepper("test-pan-hmac-pepper-with-at-least-32-bytes");
        properties.setKekVersion("kek-v1");
        properties.setKekBase64(Base64.getEncoder().encodeToString(new byte[32]));
        return new CheckoutCardVaultPersistenceService(mapper, new DataCardVaultCryptoService(properties));
    }

    /**
     * 构造具有稳定消息和交易身份的卡资料消息。
     */
    private CheckoutCardVaultStoreMessage message() {
        CheckoutCardVaultStoreMessage message = new CheckoutCardVaultStoreMessage();
        message.setMessageId("CVA1001");
        message.setMerchantId("M1001");
        message.setCheckoutAttemptId("A1001");
        message.setTransactionId("T1001");
        message.setTransactionDateTime(LocalDateTime.of(2030, 1, 2, 3, 4, 5));
        return message;
    }

    /**
     * 从消息构造数据库身份投影。
     *
     * @param message 卡资料消息
     * @return 与消息身份完全一致的数据库投影
     */
    private DataCheckoutCardVaultDO identity(CheckoutCardVaultStoreMessage message) {
        DataCheckoutCardVaultDO existing = new DataCheckoutCardVaultDO();
        existing.setMessageId(message.getMessageId());
        existing.setMerchantId(message.getMerchantId());
        existing.setCheckoutAttemptId(message.getCheckoutAttemptId());
        existing.setTransactionId(message.getTransactionId());
        existing.setTransactionDateTime(message.getTransactionDateTime());
        return existing;
    }

    /**
     * 构造已校验且不包含 CVV 的测试卡资料。
     */
    private DataCheckoutCardVaultTransferService.CardVaultPlaintext plaintext() {
        return new DataCheckoutCardVaultTransferService.CardVaultPlaintext(
                "4111111111111111", "12", "2030", "Test User", "VISA");
    }
}
