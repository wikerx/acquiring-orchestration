package com.scott.payment.component.core.util.net;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IpAddressNormalizer
 * @date : 2026-07-18 00:00
 * @email : scott_x@163.com
 * @description : IP 地址规范化工具，位于 component-core 通用工具层，仅接受精确 IPv4/IPv6 地址，不解析主机名、CIDR 或范围表达式。
 * @status : create
 */
public final class IpAddressNormalizer {

    /**
     * {@code IPV4}常量，统一 {@code IpAddressNormalizer} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String IPV4 = "IPv4";
    /**
     * {@code IPV6}常量，统一 {@code IpAddressNormalizer} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String IPV6 = "IPv6";
    /**
     * {@code IPV4_PATTERN}常量，统一 {@code IpAddressNormalizer} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String IPV4_PATTERN = "^[0-9.]+$";
    /**
     * {@code IPV6_PATTERN}常量，统一 {@code IpAddressNormalizer} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String IPV6_PATTERN = "^[0-9A-Fa-f:.]+$";

    private IpAddressNormalizer() {
    }

    /**
     * 解析精确值，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 仅返回规范化或计算结果，不直接提交交易状态。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 构造、转换或解析后的业务值
     */
    public static NormalizedIp normalizeExact(String value) {
        if (value == null) {
            throw new IllegalArgumentException("IP 不能为空");
        }
        String candidate = value.trim();
        if (candidate.isEmpty()) {
            throw new IllegalArgumentException("IP 不能为空");
        }
        if (candidate.contains("/") || candidate.contains("-") || candidate.contains(",") || candidate.contains(" ")) {
            throw new IllegalArgumentException("仅支持精确 IP，不支持 CIDR、范围或批量表达式");
        }
        if (candidate.contains(":")) {
            return normalizeIpv6(candidate);
        }
        return normalizeIpv4(candidate);
    }

    private static NormalizedIp normalizeIpv4(String candidate) {
        if (!candidate.matches(IPV4_PATTERN)) {
            throw new IllegalArgumentException("IPv4 地址格式不正确");
        }
        String[] segments = candidate.split("\\.", -1);
        if (segments.length != 4) {
            throw new IllegalArgumentException("IPv4 地址格式不正确");
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isEmpty() || segment.length() > 3) {
                throw new IllegalArgumentException("IPv4 地址格式不正确");
            }
            int number;
            try {
                number = Integer.parseInt(segment);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("IPv4 地址格式不正确", ex);
            }
            if (number < 0 || number > 255) {
                throw new IllegalArgumentException("IPv4 地址格式不正确");
            }
            if (i > 0) {
                builder.append('.');
            }
            builder.append(number);
        }
        return new NormalizedIp(IPV4, builder.toString());
    }

    private static NormalizedIp normalizeIpv6(String candidate) {
        if (!candidate.matches(IPV6_PATTERN)) {
            throw new IllegalArgumentException("IPv6 地址格式不正确");
        }
        InetAddress address;
        try {
            address = InetAddress.getByName(candidate);
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("IPv6 地址格式不正确", ex);
        }
        if (!(address instanceof Inet6Address)) {
            throw new IllegalArgumentException("IPv6 地址格式不正确");
        }
        return new NormalizedIp(IPV6, address.getHostAddress().toLowerCase(Locale.ROOT));
    }

    /**
     * 规范化后的 IP 地址及类型。
     *
     * @param ipType  IP 类型，IPv4 或 IPv6
     * @param ipValue 规范化后的精确 IP 地址
     */
    public record NormalizedIp(String ipType, String ipValue) {
        /**
         * 判断是否为 IPv4。
         *
         * @return true 表示 IPv4
         */
        public boolean ipv4() {
            return IPV4.equals(ipType);
        }

        /**
         * 判断是否为 IPv6。
         *
         * @return true 表示 IPv6
         */
        public boolean ipv6() {
            return IPV6.equals(ipType);
        }
    }
}
