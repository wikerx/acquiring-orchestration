package com.scott.payment.component.db.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantProfileReviewRecordDO
 * @date : 2026-09-20 12:00
 * @email : scott_x@163.com
 * @description : 商户资料审核不可变轨迹实体，同时支持开户审核和资料变更审核
 * @status : create
 */
@Data
@TableName("merchant_review_record")
public class MerchantProfileReviewRecordDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String merchantId;
    private String reviewScope;
    private String requestNo;
    private String reviewAction;
    private String fromStatus;
    private String toStatus;
    private String reviewComment;
    private String operatorId;
    private String operatorName;
    private LocalDateTime gmtCreate;
}
