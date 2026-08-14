package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionPayerInfoDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/** Mapper for plaintext payer snapshots in the transaction payer logical table. */
public interface TransactionPayerInfoMapper extends BaseMapper<TransactionPayerInfoDO> {

    /** Query the initial payer snapshot through the exact shard key. */
    @Select("""
            SELECT id, payer_info_id, transaction_id, operation_id,
                   payer_id, first_name, last_name, phone, email, country, state, city, street, postal,
                   ip_address, session_id, browser_info_json, user_agent,
                   payer_email_hash, payer_phone_hash, ip_address_hash,
                   transaction_date_time, transaction_utc_time, transaction_time_zone,
                   create_time, update_time
            FROM transaction_payer_info
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
            LIMIT 1
            """)
    TransactionPayerInfoDO selectByTransaction(@Param("transactionId") String transactionId,
                                               @Param("transactionDateTime") LocalDateTime transactionDateTime);
}
