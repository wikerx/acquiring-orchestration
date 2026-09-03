package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionProductItemDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionProductItemMapper
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Mapper for transaction product item snapshots.
 * @status : create
 */
public interface TransactionProductItemMapper extends BaseMapper<TransactionProductItemDO> {

    /** Query product lines in the original merchant order sequence. */
    @Select("""
            SELECT id, product_item_id, transaction_id, operation_id,
                   merchant_id, merchant_order_no, item_sequence,
                   product_name, quantity, item_currency, item_amount,
                   transaction_date_time, transaction_utc_time, transaction_time_zone,
                   create_time, update_time
            FROM transaction_product_item
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
            ORDER BY item_sequence ASC, id ASC
            """)
    List<TransactionProductItemDO> selectByTransaction(@Param("transactionId") String transactionId,
                                                       @Param("transactionDateTime") LocalDateTime transactionDateTime);
}
