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
     * IPV4 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String IPV4 = "IPv4";
    /**
     * IPV6 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String IPV6 = "IPv6";
    /**
     * IPV4 PATTERN 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String IPV4_PATTERN = "^[0-9.]+$";
    /**
     * IPV6 PATTERN 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String IPV6_PATTERN = "^[0-9A-Fa-f:.]+$";

    private IpAddressNormalizer() {
    }

    /**
     * 将输入规范化为可持久化和可精确匹配的 IP 地址。
     *
     * @param value 原始 IP 字符串
     * @return 规范化结果
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

    /**
     * 标准化 normalize Ipv4 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 IpAddressNormalizer 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param candidate 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 标准化后的业务字段值
     */
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

    /**
     * 标准化 normalize Ipv6 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 IpAddressNormalizer 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param candidate 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 标准化后的业务字段值
     */
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
