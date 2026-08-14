package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionAuthenticationInfoDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 交易认证审计 Mapper。认证流程按 authentication_info_id 实现阶段级数据库幂等。
 */
public interface TransactionAuthenticationInfoMapper extends BaseMapper<TransactionAuthenticationInfoDO> {

    /**
     * 写入或推进同一认证阶段。AUTHENTICATED/FAILED 为不可逆终态，重复回跳不得退回处理中。
     */
    @Insert("""
            INSERT INTO transaction_authentication_info
            (
              authentication_info_id, transaction_id, operation_id, authentication_type,
              authentication_status, authentication_source, three_ds_version,
              three_ds_transaction_id, three_ds_server_transaction_id, acs_transaction_id,
              ds_transaction_id, eci, cavv, xid, liability_shift, challenge_required,
              challenge_status, authentication_redirect_url_hash, authentication_result_code,
              authentication_result_message, authentication_time, authentication_extra_json,
              transaction_date_time, transaction_utc_time, transaction_time_zone,
              create_time, update_time
            )
            VALUES
            (
              #{row.authenticationInfoId}, #{row.transactionId}, #{row.operationId},
              #{row.authenticationType}, #{row.authenticationStatus}, #{row.authenticationSource},
              #{row.threeDsVersion}, #{row.threeDsTransactionId},
              #{row.threeDsServerTransactionId}, #{row.acsTransactionId}, #{row.dsTransactionId},
              #{row.eci}, NULL, #{row.xid}, #{row.liabilityShift}, #{row.challengeRequired},
              #{row.challengeStatus}, #{row.authenticationRedirectUrlHash},
              #{row.authenticationResultCode}, #{row.authenticationResultMessage},
              #{row.authenticationTime}, #{row.authenticationExtraJson},
              #{row.transactionDateTime}, #{row.transactionUtcTime}, #{row.transactionTimeZone},
              #{row.createTime}, #{row.updateTime}
            )
            ON DUPLICATE KEY UPDATE
              three_ds_version = CASE WHEN authentication_status IN ('AUTHENTICATED', 'FAILED')
                THEN three_ds_version ELSE COALESCE(VALUES(three_ds_version), three_ds_version) END,
              three_ds_transaction_id = CASE WHEN authentication_status IN ('AUTHENTICATED', 'FAILED')
                THEN three_ds_transaction_id ELSE COALESCE(VALUES(three_ds_transaction_id), three_ds_transaction_id) END,
              three_ds_server_transaction_id = CASE WHEN authentication_status IN ('AUTHENTICATED', 'FAILED')
                THEN three_ds_server_transaction_id ELSE COALESCE(VALUES(three_ds_server_transaction_id), three_ds_server_transaction_id) END,
              acs_transaction_id = CASE WHEN authentication_status IN ('AUTHENTICATED', 'FAILED')
                THEN acs_transaction_id ELSE COALESCE(VALUES(acs_transaction_id), acs_transaction_id) END,
              ds_transaction_id = CASE WHEN authentication_status IN ('AUTHENTICATED', 'FAILED')
                THEN ds_transaction_id ELSE COALESCE(VALUES(ds_transaction_id), ds_transaction_id) END,
              eci = CASE WHEN authentication_status IN ('AUTHENTICATED', 'FAILED')
                THEN eci ELSE COALESCE(VALUES(eci), eci) END,
              cavv = NULL,
              liability_shift = CASE WHEN authentication_status IN ('AUTHENTICATED', 'FAILED')
                THEN liability_shift ELSE COALESCE(VALUES(liability_shift), liability_shift) END,
              challenge_required = CASE WHEN authentication_status IN ('AUTHENTICATED', 'FAILED')
                THEN challenge_required ELSE COALESCE(VALUES(challenge_required), challenge_required) END,
              challenge_status = CASE WHEN authentication_status IN ('AUTHENTICATED', 'FAILED')
                THEN challenge_status ELSE COALESCE(VALUES(challenge_status), challenge_status) END,
              authentication_redirect_url_hash = CASE WHEN authentication_status IN ('AUTHENTICATED', 'FAILED')
                THEN authentication_redirect_url_hash
                ELSE COALESCE(VALUES(authentication_redirect_url_hash), authentication_redirect_url_hash) END,
              authentication_result_code = CASE WHEN authentication_status IN ('AUTHENTICATED', 'FAILED')
                THEN authentication_result_code ELSE VALUES(authentication_result_code) END,
              authentication_result_message = CASE WHEN authentication_status IN ('AUTHENTICATED', 'FAILED')
                THEN authentication_result_message ELSE VALUES(authentication_result_message) END,
              authentication_time = CASE WHEN authentication_status IN ('AUTHENTICATED', 'FAILED')
                THEN authentication_time ELSE VALUES(authentication_time) END,
              authentication_extra_json = CASE WHEN authentication_status IN ('AUTHENTICATED', 'FAILED')
                THEN authentication_extra_json ELSE VALUES(authentication_extra_json) END,
              update_time = CASE WHEN authentication_status IN ('AUTHENTICATED', 'FAILED')
                THEN update_time ELSE VALUES(update_time) END,
              authentication_status = CASE WHEN authentication_status IN ('AUTHENTICATED', 'FAILED')
                THEN authentication_status ELSE VALUES(authentication_status) END
            """)
    int upsertPhase(@Param("row") TransactionAuthenticationInfoDO row);

    /** 按阶段幂等 ID 和精确分片时间查询认证摘要。 */
    @Select("""
            SELECT *
            FROM transaction_authentication_info
            WHERE authentication_info_id = #{authenticationInfoId}
              AND transaction_date_time = #{transactionDateTime}
            LIMIT 1
            """)
    TransactionAuthenticationInfoDO selectByAuthenticationInfoId(
            @Param("authenticationInfoId") String authenticationInfoId,
            @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /** 按平台交易号和真实分片时间读取最后一条 3DS 安全结果。 */
    @Select("""
            SELECT *
            FROM transaction_authentication_info
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
            ORDER BY id DESC
            LIMIT 1
            """)
    TransactionAuthenticationInfoDO selectLatestByTransaction(
            @Param("transactionId") String transactionId,
            @Param("transactionDateTime") LocalDateTime transactionDateTime);
}
