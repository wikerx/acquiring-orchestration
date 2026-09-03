package com.scott.payment.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantLimitReservationDO
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 商户累计限额预占持久化记录。
 * @status : create
 */
@Data
@TableName("risk_merchant_limit_reservation")
public class MerchantLimitReservationDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 数据库自增主键，仅用于持久化定位。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 平台交易号，是累计限额预占生命周期的业务定位键。
     */
    private String transactionId;

    /**
     * 发起预占的风控评估流水号，用于审计关联。
     */
    private String riskRecordNo;

    /**
     * 商户号，限定限额规则和累计桶的租户归属。
     */
    private String merchantId;

    /**
     * 命中的商户累计限额规则主键。
     */
    private Long ruleId;

    /**
     * 限额周期类型：DAILY、WEEKLY 或 MONTHLY。
     */
    private String limitType;

    /**
     * ISO 4217 币种代码；同一累计桶不允许混合币种。
     */
    private String currency;

    /**
     * 周期桶稳定标识，与商户、规则和币种共同确定 Redis 计数槽位。
     */
    private String periodBucket;

    /**
     * 累计周期起始时间，使用系统业务时区且包含边界。
     */
    private LocalDateTime periodBeginTime;

    /**
     * 累计周期结束时间，使用系统业务时区且不包含边界。
     */
    private LocalDateTime periodEndTime;

    /**
     * 六位小数整数单位的预占金额。
     */
    private Long amountUnits;

    /**
     * 预占发生时的 LEGACY、SHADOW 或 CLUSTER_SAFE 模式。
     */
    private String counterMode;

    /** 预占状态：PREPARING、RESERVED、CONFIRMED 或 CANCELLED。 */
    private String reservationStatus;

    /** 取消或补偿原因，最长 256 个字符。 */
    private String cancelReason;

    /** 非终态预占的对账截止时间，使用系统业务时区。 */
    private LocalDateTime expiresAt;

    /** Redis 原子预占成功时间。 */
    private LocalDateTime reservedTime;

    /** 预占确认到终态的时间。 */
    private LocalDateTime confirmedTime;

    /** Redis 回滚完成并取消到终态的时间。 */
    private LocalDateTime cancelledTime;

    /** 乐观锁版本号，用于并发状态迁移。 */
    private Integer version;

    /** 软删除标识：0 表示有效，1 表示删除。 */
    private Integer deleted;

    /** 记录创建时间，数据库精度为毫秒。 */
    private LocalDateTime createTime;

    /** 最近状态更新时间，数据库精度为毫秒。 */
    private LocalDateTime updateTime;
}
