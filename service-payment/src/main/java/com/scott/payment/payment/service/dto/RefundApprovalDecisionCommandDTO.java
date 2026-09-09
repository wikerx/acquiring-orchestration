package com.scott.payment.payment.service.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundApprovalDecisionCommandDTO
 * @date : 2026-08-06 00:00
 * @email : scott_x@163.com
 * @description : 退款审批内部决策命令，使用稳定请求号吸收重复提交，并携带认证后的 Admin 操作人快照。
 * @status : create
 */
@Data
public class RefundApprovalDecisionCommandDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 审批单号，不允许为空。 */
    private String approvalId;

    /** 决策请求幂等号，不允许为空。 */
    private String decisionRequestId;

    /** 页面读取的审批版本号，用于阻止基于旧页面提交决策。 */
    private Integer expectedVersion;

    /** 已认证 Admin 账号稳定标识，不允许为空。 */
    private String operatorId;

    /** 审批人显示名称快照，可为空。 */
    private String operatorName;

    /** 审批意见；拒绝时必填，最长 512 个字符。 */
    private String reason;
}
