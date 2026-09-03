package com.scott.payment.clearing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingTierPeriodReplayDO
 * @date : 2026-08-26 19:30
 * @email : scott_x@163.com
 * @description : 商户不可变费用版本月度阶梯重放控制实体；保存双人复核、稳定游标和可恢复进度，不保存汇率或余额。
 * @status : create
 */
@Data
@TableName("clearing_tier_period_replay")
public class ClearingTierPeriodReplayDO {

    /** 数据库自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 阶梯期间重放业务号。 */
    private String replayNo;
    /** 调用方幂等请求键。 */
    private String requestKey;
    /** 目标平台商户号。 */
    private String merchantId;
    /** 目标费用方案 ID。 */
    private Long feePlanId;
    /** 目标不可变费用版本 ID。 */
    private Long feePlanVersionId;
    /** 触发重放的阶梯规则 ID。 */
    private Long triggerFeeRuleId;
    /** 月度期间键，格式 yyyy-MM。 */
    private String periodKey;
    /** 期间半开区间起点。 */
    private LocalDateTime periodStart;
    /** 期间半开区间终点。 */
    private LocalDateTime periodEnd;
    /** 运营提交原因，不得包含敏感支付数据。 */
    private String reason;
    /** service-admin 注入的可信提交人。 */
    private String submitOperator;
    /** service-admin 注入的可信复核人；待复核时为空。 */
    private String reviewOperator;
    /** 复核意见；待复核时为空。 */
    private String reviewComment;
    /** 重放状态，流转受 Maker-Checker 和版本 CAS 保护。 */
    private String replayStatus;
    /** 冻结的稳定动作项总数。 */
    private Integer itemCount;
    /** 已按序完成的动作项数量。 */
    private Integer completedCount;
    /** 最近完成项原清分完成时间，作为恢复游标。 */
    private LocalDateTime lastClearingCompleteTime;
    /** 最近完成项交易号，作为同时间稳定游标。 */
    private String lastTransactionId;
    /** 最近一次稳定错误码；无错误时为空。 */
    private String lastErrorCode;
    /** 最近一次非敏感错误摘要；无错误时为空。 */
    private String lastErrorMessage;
    /** 重放控制行 CAS 版本。 */
    private Long version;
    /** 复核 UTC 时间；待复核时为空。 */
    private LocalDateTime reviewTime;
    /** 全部动作完成 UTC 时间；未完成时为空。 */
    private LocalDateTime completedTime;
    /** 创建 UTC 时间。 */
    private LocalDateTime createTime;
    /** 最后更新 UTC 时间。 */
    private LocalDateTime updateTime;
}
