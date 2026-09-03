package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionShippingInfoDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionShippingInfoMapper
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Mapper for the transaction shipping snapshot logical table.
 * @status : create
 */
public interface TransactionShippingInfoMapper extends BaseMapper<TransactionShippingInfoDO> {

    /** Query the initial shipping snapshot through the exact shard key. */
    @Select("""
            SELECT *
            FROM transaction_shipping_info
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
            LIMIT 1
            """)
    TransactionShippingInfoDO selectByTransaction(@Param("transactionId") String transactionId,
                                                   @Param("transactionDateTime") LocalDateTime transactionDateTime);
}
