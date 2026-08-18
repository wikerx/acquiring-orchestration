package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionLocatorDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionLocatorMapper
 * @date : 2026-08-14 12:35
 * @email : scott_x@163.com
 * @description : 交易定位 Mapper，位于 service-payment 数据访问层，只访问非分表 transaction_locator 并始终携带 merchant_id 隔离条件。
 * @status : create
 */
public interface TransactionLocatorMapper extends BaseMapper<TransactionLocatorDO> {

    /**
     * 按商户和平台交易 ID 定位单笔动作。
     *
     * @param merchantId 平台商户号
     * @param transactionId 平台交易 ID
     * @return 定位记录，不存在或不属于该商户时返回 null
     */
    @Select("""
            SELECT id, transaction_id, operation_id, root_transaction_id,
                   merchant_id, merchant_order_no, transaction_type,
                   transaction_date_time, root_transaction_date_time,
                   create_time, update_time
            FROM transaction_locator
            WHERE merchant_id = #{merchantId}
              AND transaction_id = #{transactionId}
            LIMIT 1
            """)
    TransactionLocatorDO selectByTransactionId(@Param("merchantId") String merchantId,
                                                @Param("transactionId") String transactionId);

    /**
     * 按商户订单号定位生命周期根交易。
     *
     * @param merchantId 平台商户号
     * @param merchantOrderNo 商户原始订单号
     * @return 生命周期根定位记录，不存在时返回 null
     */
    @Select("""
            SELECT id, transaction_id, operation_id, root_transaction_id,
                   merchant_id, merchant_order_no, transaction_type,
                   transaction_date_time, root_transaction_date_time,
                   create_time, update_time
            FROM transaction_locator
            WHERE merchant_id = #{merchantId}
              AND merchant_order_no = #{merchantOrderNo}
              AND transaction_id = root_transaction_id
            ORDER BY transaction_date_time ASC, id ASC
            LIMIT 1
            """)
    TransactionLocatorDO selectRootByMerchantOrder(@Param("merchantId") String merchantId,
                                                    @Param("merchantOrderNo") String merchantOrderNo);
}
