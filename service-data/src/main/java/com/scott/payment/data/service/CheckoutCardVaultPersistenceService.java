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
 * 加密并持久化 checkout 卡资料，以数据库唯一约束承担 MQ 最终幂等。
 *
 * <p>重复键只有在消息号、商户、交易、checkout 尝试和分片时间全部一致时才按幂等成功处理；
 * 任一身份字段冲突都失败关闭，避免另一笔交易复用已存在的卡资料记录。</p>
 */
@Service
@ConditionalOnProperty(prefix = "data.card-vault", name = "enabled", havingValue = "true")
public class CheckoutCardVaultPersistenceService {

    /**
     * 卡资料库 Mapper。
     */
    private final DataCheckoutCardVaultMapper mapper;
    /**
     * 字段级信封加密服务。
     */
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
    @DS(DataSourceName.TRANSACTION)
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
            DataCheckoutCardVaultDO messageIdentity = mapper.selectIdentityByMessageId(
                    message.getMessageId(), message.getTransactionDateTime());
            DataCheckoutCardVaultDO transactionIdentity = mapper.selectIdentityByMerchantTransaction(
                    message.getMerchantId(), message.getTransactionId(),
                    message.getTransactionDateTime());
            if (allConflictingIdentitiesMatch(messageIdentity, transactionIdentity, message)) {
                return false;
            }
            throw new IllegalStateException("card vault idempotency key conflicts with another transaction", exception);
        }
    }

    /**
     * 将传输明文转换为只包含必要展示字段和加密列的持久化记录。
     *
     * @param message 卡资料消息元数据
     * @param plaintext 已校验且不含 CVV 的卡资料
     * @return 待插入卡资料记录
     */
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

    /**
     * 填充 PAN 的分离 GCM 密文列。
     *
     * @param record 目标持久化记录
     * @param value PAN 字段信封
     */
    private void applyPan(DataCheckoutCardVaultDO record, DataCardVaultCryptoService.FieldEnvelope value) {
        record.setPanCiphertext(value.ciphertext());
        record.setPanIv(value.iv());
        record.setPanAuthTag(value.authTag());
    }

    /**
     * 填充有效期的分离 GCM 密文列。
     *
     * @param record 目标持久化记录
     * @param value 有效期字段信封
     */
    private void applyExpiration(DataCheckoutCardVaultDO record, DataCardVaultCryptoService.FieldEnvelope value) {
        record.setExpirationCiphertext(value.ciphertext());
        record.setExpirationIv(value.iv());
        record.setExpirationAuthTag(value.authTag());
    }

    /**
     * 在商户提供持卡人姓名时填充分离 GCM 密文列。
     *
     * @param record 目标持久化记录
     * @param value 持卡人姓名字段信封；未提供时为空
     */
    private void applyCardholder(DataCheckoutCardVaultDO record, DataCardVaultCryptoService.FieldEnvelope value) {
        if (value == null) {
            return;
        }
        record.setCardholderNameCiphertext(value.ciphertext());
        record.setCardholderNameIv(value.iv());
        record.setCardholderNameAuthTag(value.authTag());
    }

    /**
     * 判断唯一键命中的历史记录是否确为同一条业务消息。
     *
     * @param existing 数据库已有身份投影
     * @param message 当前 MQ 消息
     * @return true 表示可以按幂等重复确认
     */
    private boolean sameIdentity(DataCheckoutCardVaultDO existing, CheckoutCardVaultStoreMessage message) {
        return existing != null
                && Objects.equals(existing.getMessageId(), message.getMessageId())
                && Objects.equals(existing.getMerchantId(), message.getMerchantId())
                && Objects.equals(existing.getCheckoutAttemptId(), message.getCheckoutAttemptId())
                && Objects.equals(existing.getTransactionId(), message.getTransactionId())
                && Objects.equals(existing.getTransactionDateTime(), message.getTransactionDateTime());
    }

    /**
     * 要求两个业务唯一键命中的全部记录都与当前消息身份一致。
     *
     * <p>消息号和商户交易号可能分别命中不同记录；只检查任意一条会把交叉唯一键冲突误判为幂等。</p>
     *
     * @param messageIdentity 消息号唯一键命中的记录
     * @param transactionIdentity 商户交易唯一键命中的记录
     * @param message 当前 MQ 消息
     * @return true 表示至少命中一条记录且所有命中记录均完整一致
     */
    private boolean allConflictingIdentitiesMatch(DataCheckoutCardVaultDO messageIdentity,
                                                  DataCheckoutCardVaultDO transactionIdentity,
                                                  CheckoutCardVaultStoreMessage message) {
        boolean conflictFound = messageIdentity != null || transactionIdentity != null;
        boolean messageKeyMatches = messageIdentity == null || sameIdentity(messageIdentity, message);
        boolean transactionKeyMatches = transactionIdentity == null || sameIdentity(transactionIdentity, message);
        return conflictFound && messageKeyMatches && transactionKeyMatches;
    }
}
