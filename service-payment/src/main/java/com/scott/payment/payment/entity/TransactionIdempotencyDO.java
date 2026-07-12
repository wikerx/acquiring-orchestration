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
 * @classname : TransactionIdempotencyDO
 * @date : 2026-07-12 18:20
 * @email : scott_x@163.com
 * @description : 交易幂等记录实体，位于 service-payment 持久化层，用数据库唯一约束兜底资金类请求的重复提交。
 * @status : create
 */
@Data
@TableName("transaction_idempotency")
public class TransactionIdempotencyDO implements Serializable {

    /**
     * 序列化版本号，用于测试、缓存或后续事件补偿场景的对象兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 数据库主键 ID，正式表使用系统统一主键规则。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 幂等业务范围，例如 PAYMENT_CREATE、CAPTURE、REFUND、CHANNEL_CALLBACK、MQ_CONSUME。
     */
    private String idempotencyScope;

    /**
     * 幂等键，同一范围内必须唯一；创建交易使用 merchantId + merchantOrderNo + transactionType。
     */
    private String idempotencyKey;

    /**
     * 商户号，用于幂等排查、后台查询和唯一键组成。
     */
    private String merchantId;

    /**
     * 商户订单号，用于商户侧幂等和查询。
     */
    private String merchantOrderNo;

    /**
     * 交易类型，对齐系统字典 transaction_type。
     */
    private String transactionType;

    /**
     * 同一原始交易生命周期主标识。
     */
    private String transactionOrderNo;

    /**
     * 当前交易动作单号。
     */
    private String transactionNo;

    /**
     * 交易状态，对齐系统字典 transaction_status。
     */
    private String transactionStatus;

    /**
     * 交易金额，最小币种单位，例如 USD 分。
     */
    private Long transactionAmountMinor;

    /**
     * 交易币种，ISO 4217 三位大写币种代码。
     */
    private String transactionCurrency;

    /**
     * 交易业务时间，参与季度分表路由，数据库字段必须使用 DATETIME(3)。
     */
    private LocalDateTime transactionDateTime;

    /**
     * 交易业务时间所属时区，默认 Asia/Shanghai。
     */
    private String timeZone;

    /**
     * 请求体安全摘要，用于问题排查；不得保存完整密文、卡号或 CVV。
     */
    private String requestFingerprint;

    /**
     * 幂等结果快照 JSON，用于重复请求直接返回首次处理结果。
     */
    private String resultSnapshot;

    /**
     * 幂等记录过期时间，过期前不允许同一幂等键创建第二笔资金交易。
     */
    private LocalDateTime expireTime;

    /**
     * 乐观锁版本号，用于后续处理中状态更新保护。
     */
    private Integer version;

    /**
     * 软删除标识，0 表示未删除。
     */
    private Integer deleted;

    /**
     * 记录创建时间，数据库字段必须使用 DATETIME(3)。
     */
    private LocalDateTime createTime;

    /**
     * 记录最后更新时间，数据库字段必须使用 DATETIME(3)。
     */
    private LocalDateTime updateTime;
}
