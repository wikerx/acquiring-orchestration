package com.scott.payment.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSettlementProfileDO
 * @date : 2026-08-26 22:00
 * @email : scott_x@163.com
 * @description : 商户结算日历和目标账户的权威档案；活动档案唯一，候选激活后只引用冻结 ID，不回写历史档案。
 * @status : create
 */
@Data
@TableName("merchant_settlement_profile")
public class MerchantSettlementProfileDO {
    /** 结算档案数据库主键，插入前允许为空。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 对外审计使用的稳定档案号。 */
    private String settlementProfileNo;
    /** 平台商户号。 */
    private String merchantId;
    /** 目标商户资金账户主键。 */
    private Long settlementAccountId;
    /** 目标结算 ISO 币种。 */
    private String targetCurrency;
    /** 目标币种 ISO 小数位。 */
    private Integer targetCurrencyExponent;
    /** 商户日切使用的 IANA 时区。 */
    private String businessTimeZone;
    /** 每日结算日切时间，不包含时区。 */
    private LocalTime dailyCutoffTime;
    /** AUTO_POST、AUTO_REVIEW 或 MANUAL。 */
    private String processingMode;
    /** ACTIVE、RETIRED 或 SUSPENDED。 */
    private String profileStatus;
    /** 活动档案固定为 1，非活动档案为空，用于数据库唯一约束。 */
    private Integer activeSlot;
    /** 档案业务生效日期。 */
    private LocalDate effectiveDate;
    /** 档案业务失效日期，长期有效时为空。 */
    private LocalDate expireDate;
    /** 档案并发与审计版本。 */
    private Long version;
    /** 结算档案创建时间，数据库精度为毫秒。 */
    private LocalDateTime createTime;
    /** 结算档案最近更新时间，数据库精度为毫秒。 */
    private LocalDateTime updateTime;
}
