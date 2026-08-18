package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionLocatorDO
 * @date : 2026-08-14 12:35
 * @email : scott_x@163.com
 * @description : 交易定位实体，位于 service-payment 持久化层，通过非分表索引将商户交易标识映射到动作分片时间和生命周期根分片时间。
 * @status : create
 */
@Data
@TableName("transaction_locator")
public class TransactionLocatorDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 固定表自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 每一笔交易动作的平台交易 ID，全局唯一。 */
    private String transactionId;

    /** 同一交易生命周期共享的内部关联标识。 */
    private String operationId;

    /** 生命周期首笔交易的平台交易 ID。 */
    private String rootTransactionId;

    /** 平台商户号，用于隔离不同商户的交易定位。 */
    private String merchantId;

    /** 商户原始订单号，用于查询完整交易生命周期。 */
    private String merchantOrderNo;

    /** 当前动作交易类型。 */
    private String transactionType;

    /** 当前动作所在交易分表的毫秒级业务时间。 */
    private LocalDateTime transactionDateTime;

    /** 生命周期根主单所在交易分表的毫秒级业务时间。 */
    private LocalDateTime rootTransactionDateTime;

    /** 定位记录创建时间。 */
    private LocalDateTime createTime;

    /** 定位记录最后更新时间。 */
    private LocalDateTime updateTime;
}
