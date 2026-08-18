package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionFinanceStateDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionFinanceStateMapper
 * @date : 2026-08-14 13:45
 * @email : scott_x@163.com
 * @description : 交易财务状态 Mapper，仅按平台交易号和真实分片时间访问逻辑表。
 * @status : create
 */
public interface TransactionFinanceStateMapper extends BaseMapper<TransactionFinanceStateDO> {

    /** 按当前交易动作读取已经形成的财务状态。 */
    @Select("""
            SELECT *
            FROM transaction_finance_state
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND deleted = 0
            ORDER BY id DESC
            LIMIT 1
            """)
    TransactionFinanceStateDO selectByTransaction(
            @Param("transactionId") String transactionId,
            @Param("transactionDateTime") LocalDateTime transactionDateTime);
}
