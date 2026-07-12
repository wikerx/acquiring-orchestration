package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionIdempotencyDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionIdempotencyMapper
 * @date : 2026-07-12 18:20
 * @email : scott_x@163.com
 * @description : 交易幂等记录 Mapper，位于 service-payment 数据访问层，仅负责 transaction_idempotency 表访问。
 * @status : create
 */
public interface TransactionIdempotencyMapper extends BaseMapper<TransactionIdempotencyDO> {
}
