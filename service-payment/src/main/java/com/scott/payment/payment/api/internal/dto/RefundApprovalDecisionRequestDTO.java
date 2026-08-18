package com.scott.payment.payment.api.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundApprovalDecisionRequestDTO
 * @date : 2026-08-06 15:30
 * @email : scott_x@163.com
 * @description : Payment 内部退款审批请求，携带 Admin 已认证操作人、命令幂等号和页面版本。
 * @status : create
 */
@Data
public class RefundApprovalDecisionRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 决策命令幂等号，不允许为空。 */
    @NotBlank
    @Size(max = 64)
    private String decisionRequestId;

    /** 页面读取的审批版本号，不允许为空。 */
    @NotNull
    private Integer expectedVersion;

    /** Admin 账号稳定标识，由 service-admin 从认证上下文写入。 */
    @NotBlank
    @Size(max = 128)
    private String operatorId;

    /** Admin 操作人显示名快照，可为空。 */
    @Size(max = 128)
    private String operatorName;

    /** 审批意见；拒绝时由工作流强制必填。 */
    @Size(max = 512)
    private String approvalReason;
}
