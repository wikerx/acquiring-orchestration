package com.scott.payment.payment.service;

import com.scott.payment.payment.config.RefundManagementProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 退款审批策略服务，只解释已确认的 NONE、PARTIAL_ONLY、ALL 策略，不做跨币种金额比较。
 */
@Service
public class RefundApprovalPolicyService {

    /**
     * {@code POLICY_NONE}常量，统一 {@code RefundApprovalPolicyService} 内部使用的配置值、状态码或协议字段。
     */
    private static final String POLICY_NONE = "NONE";
    /**
     * {@code POLICY_PARTIAL_ONLY}常量，统一 {@code RefundApprovalPolicyService} 内部使用的配置值、状态码或协议字段。
     */
    private static final String POLICY_PARTIAL_ONLY = "PARTIAL_ONLY";
    /**
     * {@code POLICY_ALL}常量，统一 {@code RefundApprovalPolicyService} 内部使用的配置值、状态码或协议字段。
     */
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

    /**
     * 返回当前生效的退款审批策略编码；空配置按 NONE 处理。
     * @return 当前方法生成或规范化后的文本值
     */
    public String currentPolicyCode() {
        return normalizePolicy(properties.getApprovalPolicy());
    }

    /**
     * @return 审批有效分钟数，至少一分钟
     */
    public long approvalExpireMinutes() {
        return Math.max(1L, properties.getApprovalExpireMinutes());
    }

    /**
     * 规范化退款审批策略。
     */
    private String normalizePolicy(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : POLICY_NONE;
    }
}
