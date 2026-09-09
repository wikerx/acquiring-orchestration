package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionBillingInfoDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionBillingInfoMapper
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Mapper for the transaction billing snapshot logical table.
 * @status : create
 */
public interface TransactionBillingInfoMapper extends BaseMapper<TransactionBillingInfoDO> {

    /** Query the initial billing snapshot through the exact shard key. */
    @Select("""
            SELECT id, billing_info_id, transaction_id, operation_id,
                   first_name, last_name, email, phone,
                   billing_country, billing_state, billing_city, street, billing_postal_code,
                   transaction_date_time, transaction_utc_time, transaction_time_zone,
                   create_time, update_time
            FROM transaction_billing_info
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
            LIMIT 1
            """)
    TransactionBillingInfoDO selectByTransaction(@Param("transactionId") String transactionId,
                                                 @Param("transactionDateTime") LocalDateTime transactionDateTime);
}
