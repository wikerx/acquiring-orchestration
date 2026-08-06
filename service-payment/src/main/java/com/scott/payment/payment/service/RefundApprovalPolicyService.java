package com.scott.payment.payment.service;

import com.scott.payment.payment.config.RefundManagementProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundApprovalPolicyService
 * @date : 2026-08-06 00:00
 * @description : 退款审批策略服务，只解释已确认的 NONE、PARTIAL_ONLY、ALL 策略，不做跨币种金额比较。
 * @status : create
 */
@Service
public class RefundApprovalPolicyService {

    private static final String POLICY_NONE = "NONE";
    private static final String POLICY_PARTIAL_ONLY = "PARTIAL_ONLY";
    private static final String POLICY_ALL = "ALL";

    private final RefundManagementProperties properties;

    /**
     * 创建退款审批策略服务。
     *
     * @param properties 退款管理配置
     */
    public RefundApprovalPolicyService(RefundManagementProperties properties) {
        this.properties = properties;
    }

    /**
     * 判断当前退款范围是否需要人工审批。
     *
     * @param refundScope FULL 或 PARTIAL
     * @return true 表示申请受理后进入 WAITING_APPROVAL
     */
    public boolean requiresApproval(String refundScope) {
        if (!properties.isEnabled() || !properties.isApprovalEnabled()) {
            return false;
        }
        String policy = normalizePolicy(properties.getApprovalPolicy());
        return switch (policy) {
            case POLICY_NONE -> false;
            case POLICY_PARTIAL_ONLY -> "PARTIAL".equalsIgnoreCase(refundScope);
            case POLICY_ALL -> true;
            default -> throw new IllegalStateException("unsupported refund approval policy: " + policy);
        };
    }

    /** @return 当前生效的规范化策略编码 */
    public String currentPolicyCode() {
        return normalizePolicy(properties.getApprovalPolicy());
    }

    /** @return 审批有效分钟数，至少一分钟 */
    public long approvalExpireMinutes() {
        return Math.max(1L, properties.getApprovalExpireMinutes());
    }

    private String normalizePolicy(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : POLICY_NONE;
    }
}
