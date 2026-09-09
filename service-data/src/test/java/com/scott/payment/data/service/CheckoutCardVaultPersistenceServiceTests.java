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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CheckoutCardVaultPersistenceServiceTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 卡资料库数据库最终幂等测试。
 * @status : create
 */
class CheckoutCardVaultPersistenceServiceTests {

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
        when(mapper.selectIdentity(message.getMessageId(), message.getMerchantId(), message.getTransactionId(),
                message.getTransactionDateTime())).thenReturn(existing);

        boolean inserted = service(mapper).persist(message, plaintext());

        assertThat(inserted).isFalse();
    }

    private CheckoutCardVaultPersistenceService service(DataCheckoutCardVaultMapper mapper) {
        DataCardVaultProperties properties = new DataCardVaultProperties();
        properties.setEnabled(true);
        properties.setPanHmacKeyVersion("hmac-v1");
        properties.setPanHmacPepper("test-pan-hmac-pepper-with-at-least-32-bytes");
        properties.setKekVersion("kek-v1");
        properties.setKekBase64(Base64.getEncoder().encodeToString(new byte[32]));
        return new CheckoutCardVaultPersistenceService(mapper, new DataCardVaultCryptoService(properties));
    }

    private CheckoutCardVaultStoreMessage message() {
        CheckoutCardVaultStoreMessage message = new CheckoutCardVaultStoreMessage();
        message.setMessageId("CVA1001");
        message.setMerchantId("M1001");
        message.setCheckoutAttemptId("A1001");
        message.setTransactionId("T1001");
        message.setTransactionDateTime(LocalDateTime.of(2030, 1, 2, 3, 4, 5));
        return message;
    }

    private DataCheckoutCardVaultTransferService.CardVaultPlaintext plaintext() {
        return new DataCheckoutCardVaultTransferService.CardVaultPlaintext(
                "4111111111111111", "12", "2030", "Test User", "VISA");
    }
}
