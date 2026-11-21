package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionIdempotencyDO;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 交易幂等记录 Mapper，位于 service-payment 数据访问层，仅负责 transaction_idempotency 表访问。
 */
public interface TransactionIdempotencyMapper extends BaseMapper<TransactionIdempotencyDO> {

    /**
     * 读取数据库时钟，统一幂等归属租约的时间判断。
     */
    @Select("SELECT CURRENT_TIMESTAMP(3)")
    LocalDateTime currentDatabaseTime();
}
