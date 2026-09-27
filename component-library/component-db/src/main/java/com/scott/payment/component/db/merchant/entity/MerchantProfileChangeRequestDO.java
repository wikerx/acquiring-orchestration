package com.scott.payment.component.db.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantProfileChangeRequestDO
 * @date : 2026-09-20 12:00
 * @email : scott_x@163.com
 * @description : 商户资料变更申请实体，使用加密快照隔离待审核资料与当前生效主档
 * @status : create
 */
@Data
@TableName("merchant_profile_change_request")
public class MerchantProfileChangeRequestDO {

    /** 自增主键，仅用于数据库关联。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外稳定的变更申请编号。 */
    private String requestNo;

    /** 申请所属平台商户号。 */
    private String merchantId;

    /** DRAFT、PENDING_REVIEW、SUPPLEMENT_REQUIRED、APPROVED、REJECTED 或 WITHDRAWN。 */
    private String status;

    /** 活动申请标识；活动申请为 1，终态为 null，用于唯一约束。 */
    private Integer activeFlag;

    /** 创建申请时的正式资料加密快照。 */
    private String currentSnapshotCipher;

    /** 商户拟变更资料的加密快照。 */
    private String proposedSnapshotCipher;

    /** 发生变化的稳定字段编码 JSON 数组。 */
    private String changedFieldsJson;

    /** 商户提交说明。 */
    private String submitComment;

    /** 管理端审核意见或补件说明。 */
    private String reviewComment;

    /** 提交商户账号 ID。 */
    private String submittedBy;

    /** 审核管理账号 ID。 */
    private String reviewedBy;

    /** 提交审核时间。 */
    private LocalDateTime submittedAt;

    /** 审核完成时间。 */
    private LocalDateTime reviewedAt;

    /** 乐观锁版本号。 */
    private Integer version;

    /** 创建时间。 */
    private LocalDateTime gmtCreate;

    /** 修改时间。 */
    private LocalDateTime gmtModified;

    /** 软删除标识：0 有效，1 删除。 */
    private Integer deleted;
}
