package com.scott.payment.data.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.mq.message.CheckoutCardVaultStoreMessage;
import com.scott.payment.data.entity.DataCheckoutCardVaultDO;
import com.scott.payment.data.mapper.DataCheckoutCardVaultMapper;
import com.scott.payment.data.security.DataCardVaultCryptoService;
import com.scott.payment.data.security.DataCheckoutCardVaultTransferService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CheckoutCardVaultPersistenceService
 * @date : 2026-08-08 18:00
 * @email : scott_x@163.com
 * @description : 卡资料库事务写入服务，以消息号和商户交易号唯一约束承担 MQ 最终幂等。
 * @status : create
 */
@Service
@DS(DataSourceName.TRANSACTION)
@ConditionalOnProperty(prefix = "data.card-vault", name = "enabled", havingValue = "true")
public class CheckoutCardVaultPersistenceService {

    /** 卡资料库 Mapper。 */
    private final DataCheckoutCardVaultMapper mapper;
    /** 字段级信封加密服务。 */
    private final DataCardVaultCryptoService cryptoService;

    /**
     * 创建卡资料库事务写入服务。
     *
     * @param mapper 卡资料 Mapper
     * @param cryptoService 字段加密服务
     */
    public CheckoutCardVaultPersistenceService(DataCheckoutCardVaultMapper mapper,
                                               DataCardVaultCryptoService cryptoService) {
        this.mapper = mapper;
        this.cryptoService = cryptoService;
    }

    /**
     * 加密并写入一条卡资料；重复消息只接受完全相同的商户、交易和尝试身份。
     *
     * @param message 卡资料密文消息元数据
     * @param plaintext 当前消费调用栈内解密的无 CVV 卡资料
     * @return true 表示首次插入，false 表示幂等命中
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean persist(CheckoutCardVaultStoreMessage message,
                           DataCheckoutCardVaultTransferService.CardVaultPlaintext plaintext) {
        DataCheckoutCardVaultDO record = buildRecord(message, plaintext);
        try {
            if (mapper.insert(record) != 1) {
                throw new IllegalStateException("card vault record was not inserted");
            }
            return true;
        } catch (DuplicateKeyException exception) {
            DataCheckoutCardVaultDO existing = mapper.selectIdentity(
                    message.getMessageId(), message.getMerchantId(), message.getTransactionId(),
                    message.getTransactionDateTime());
            if (sameIdentity(existing, message)) {
                return false;
            }
            throw new IllegalStateException("card vault idempotency key conflicts with another transaction", exception);
        }
    }

    private DataCheckoutCardVaultDO buildRecord(
            CheckoutCardVaultStoreMessage message,
            DataCheckoutCardVaultTransferService.CardVaultPlaintext plaintext) {
        DataCardVaultCryptoService.EncryptedCardData encrypted = cryptoService.encrypt(
                message.getMerchantId(), message.getTransactionId(), plaintext);
        LocalDateTime now = LocalDateTime.now();
        DataCheckoutCardVaultDO record = new DataCheckoutCardVaultDO();
        record.setVaultRecordId(UUID.randomUUID().toString().replace("-", ""));
        record.setMessageId(message.getMessageId());
        record.setMerchantId(message.getMerchantId());
        record.setCheckoutAttemptId(message.getCheckoutAttemptId());
        record.setTransactionId(message.getTransactionId());
        record.setTransactionDateTime(message.getTransactionDateTime());
        record.setCardBrand(plaintext.cardBrand());
        record.setCardBin(plaintext.cardNo().substring(0, Math.min(6, plaintext.cardNo().length())));
        record.setCardLast4(plaintext.cardNo().substring(plaintext.cardNo().length() - 4));
        record.setPanHmac(encrypted.panHmac());
        record.setPanHmacKeyVersion(encrypted.panHmacKeyVersion());
        applyPan(record, encrypted.pan());
        applyExpiration(record, encrypted.expiration());
        applyCardholder(record, encrypted.cardholderName());
        record.setWrappedDekCiphertext(encrypted.wrappedDek().ciphertext());
        record.setWrappedDekIv(encrypted.wrappedDek().iv());
        record.setWrappedDekAuthTag(encrypted.wrappedDek().authTag());
        record.setKekVersion(encrypted.kekVersion());
        record.setVersion(0);
        record.setDeleted(0);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        return record;
    }

    private void applyPan(DataCheckoutCardVaultDO record, DataCardVaultCryptoService.FieldEnvelope value) {
        record.setPanCiphertext(value.ciphertext());
        record.setPanIv(value.iv());
        record.setPanAuthTag(value.authTag());
    }

    private void applyExpiration(DataCheckoutCardVaultDO record, DataCardVaultCryptoService.FieldEnvelope value) {
        record.setExpirationCiphertext(value.ciphertext());
        record.setExpirationIv(value.iv());
        record.setExpirationAuthTag(value.authTag());
    }

    private void applyCardholder(DataCheckoutCardVaultDO record, DataCardVaultCryptoService.FieldEnvelope value) {
        if (value == null) {
            return;
        }
        record.setCardholderNameCiphertext(value.ciphertext());
        record.setCardholderNameIv(value.iv());
        record.setCardholderNameAuthTag(value.authTag());
    }

    private boolean sameIdentity(DataCheckoutCardVaultDO existing, CheckoutCardVaultStoreMessage message) {
        return existing != null
                && Objects.equals(existing.getMessageId(), message.getMessageId())
                && Objects.equals(existing.getMerchantId(), message.getMerchantId())
                && Objects.equals(existing.getCheckoutAttemptId(), message.getCheckoutAttemptId())
                && Objects.equals(existing.getTransactionId(), message.getTransactionId())
                && Objects.equals(existing.getTransactionDateTime(), message.getTransactionDateTime());
    }
}
