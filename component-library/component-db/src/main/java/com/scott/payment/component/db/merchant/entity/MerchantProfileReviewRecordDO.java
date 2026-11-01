package com.scott.payment.component.db.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户资料审核不可变轨迹实体，同时支持开户审核和资料变更审核
 */
@Data
@TableName("merchant_review_record")
public class MerchantProfileReviewRecordDO {


    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     */
    private String merchantId;
    /**
     * 持久化的审核范围，用于还原当前记录的业务事实。
     */
    private String reviewScope;
    /**
     * 持久化的请求编号，用于还原当前记录的业务事实。
     */
    private String requestNo;
    /**
     * 持久化的审核动作，用于还原当前记录的业务事实。
     */
    private String reviewAction;

    private String fromStatus;

    private String toStatus;
    /**
     * 持久化的{@code reviewComment}，用于还原当前记录的业务事实。
     */
    private String reviewComment;
    /**
     * 执行本次管理操作的可信登录账号 ID，用于操作审计。
     */
    private String operatorId;
    /**
     * 执行本次管理操作时的账号显示名称快照，用于操作审计。
     */
    private String operatorName;
    /**
     * 持久化的{@code gmtCreate}，用于还原当前记录的业务事实。
     */
    private LocalDateTime gmtCreate;
}
