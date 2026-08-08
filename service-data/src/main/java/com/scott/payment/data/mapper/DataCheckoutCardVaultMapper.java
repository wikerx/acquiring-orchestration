package com.scott.payment.data.mapper;

import com.scott.payment.data.entity.DataCheckoutCardVaultDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataCheckoutCardVaultMapper
 * @date : 2026-08-08 18:00
 * @email : scott_x@163.com
 * @description : 卡资料库季度逻辑表 Mapper，只负责带分片键的密文写入和重复消息身份核验。
 * @status : create
 */
public interface DataCheckoutCardVaultMapper {

    /**
     * 插入一条不含 CVV 的卡资料密文记录。
     *
     * @param record 密文持久化对象
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO transaction_card_vault (
                vault_record_id, message_id, merchant_id, checkout_attempt_id,
                transaction_id, transaction_date_time, card_brand, card_bin, card_last4,
                pan_hmac, pan_hmac_key_version, pan_ciphertext, pan_iv, pan_auth_tag,
                expiration_ciphertext, expiration_iv, expiration_auth_tag,
                cardholder_name_ciphertext, cardholder_name_iv, cardholder_name_auth_tag,
                wrapped_dek_ciphertext, wrapped_dek_iv, wrapped_dek_auth_tag, kek_version,
                version, deleted, create_time, update_time
            ) VALUES (
                #{record.vaultRecordId}, #{record.messageId}, #{record.merchantId},
                #{record.checkoutAttemptId}, #{record.transactionId}, #{record.transactionDateTime},
                #{record.cardBrand}, #{record.cardBin}, #{record.cardLast4},
                #{record.panHmac}, #{record.panHmacKeyVersion}, #{record.panCiphertext},
                #{record.panIv}, #{record.panAuthTag}, #{record.expirationCiphertext},
                #{record.expirationIv}, #{record.expirationAuthTag},
                #{record.cardholderNameCiphertext}, #{record.cardholderNameIv},
                #{record.cardholderNameAuthTag}, #{record.wrappedDekCiphertext},
                #{record.wrappedDekIv}, #{record.wrappedDekAuthTag}, #{record.kekVersion},
                #{record.version}, #{record.deleted}, #{record.createTime}, #{record.updateTime}
            )
            """)
    int insert(@Param("record") DataCheckoutCardVaultDO record);

    /**
     * 在唯一键冲突后按同一季度确认是否为相同业务消息。
     *
     * @param messageId MQ 消息号
     * @param merchantId 商户号
     * @param transactionId 平台交易号
     * @param transactionDateTime 交易分片时间
     * @return 已存在的记录身份；不存在表示唯一键被其他业务占用
     */
    @Select("""
            SELECT id, vault_record_id, message_id, merchant_id, checkout_attempt_id,
                   transaction_id, transaction_date_time
            FROM transaction_card_vault
            WHERE transaction_date_time = #{transactionDateTime}
              AND (message_id = #{messageId}
                   OR (merchant_id = #{merchantId} AND transaction_id = #{transactionId}))
            LIMIT 1
            """)
    DataCheckoutCardVaultDO selectIdentity(@Param("messageId") String messageId,
                                           @Param("merchantId") String merchantId,
                                           @Param("transactionId") String transactionId,
                                           @Param("transactionDateTime") LocalDateTime transactionDateTime);
}
