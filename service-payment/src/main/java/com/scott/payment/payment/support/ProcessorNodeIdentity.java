package com.scott.payment.payment.support;

import com.scott.payment.component.core.util.net.IpAddressNormalizer;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ProcessorNodeIdentity
 * @date : 2026-10-17 00:00
 * @email : scott_x@163.com
 * @description : 支付处理节点审计快照，只读取部署系统显式注入的实例、主机和 Pod IP，不执行本机网卡探测。
 * @status : create
 */
public record ProcessorNodeIdentity(
        boolean persistenceEnabled,
        String serviceName,
        String instanceId,
        String hostName,
        String privateIp) {

    private static final String ENV_ENABLED = "PAYMENT_PROCESSOR_AUDIT_ENABLED";
    private static final String ENV_SERVICE_NAME = "SPRING_APPLICATION_NAME";
    private static final String ENV_INSTANCE_ID = "INSTANCE_ID";
    private static final String ENV_POD_NAME = "POD_NAME";
    private static final String ENV_HOST_NAME = "HOSTNAME";
    private static final String ENV_PRIVATE_IP = "POD_IP";
    private static final String DEFAULT_SERVICE_NAME = "service-payment";

    /**
     * 从当前进程环境变量解析节点身份；环境变量在进程生命周期内视为不可变配置。
     *
     * @return 当前支付服务节点身份
     */
    public static ProcessorNodeIdentity fromEnvironment() {
        return from(System.getenv());
    }

    /**
     * 从显式环境变量集合解析节点身份，供部署配置和单元测试复用。
     *
     * @param environment 进程环境变量
     * @return 规范化节点身份；未启用时只返回关闭状态
     */
    public static ProcessorNodeIdentity from(Map<String, String> environment) {
        Map<String, String> safeEnvironment = environment == null ? Map.of() : environment;
        boolean enabled = Boolean.parseBoolean(trimToNull(safeEnvironment.get(ENV_ENABLED)));
        if (!enabled) {
            return new ProcessorNodeIdentity(false, null, null, null, null);
        }
        String serviceName = firstText(safeEnvironment.get(ENV_SERVICE_NAME), DEFAULT_SERVICE_NAME);
        String instanceId = firstText(safeEnvironment.get(ENV_INSTANCE_ID), safeEnvironment.get(ENV_POD_NAME));
        String hostName = trimToNull(safeEnvironment.get(ENV_HOST_NAME));
        String privateIp = normalizeIp(safeEnvironment.get(ENV_PRIVATE_IP));
        return new ProcessorNodeIdentity(true, limit(serviceName, 64), limit(instanceId, 128),
                limit(hostName, 128), privateIp);
    }

    private static String normalizeIp(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return IpAddressNormalizer.normalizeExact(value.trim()).ipValue();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String firstText(String first, String fallback) {
        String value = trimToNull(first);
        return value == null ? trimToNull(fallback) : value;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String limit(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
