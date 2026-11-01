package com.scott.payment.component.core.util.net;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * IP 地址规范化工具，位于 component-core 通用工具层，仅接受精确 IPv4/IPv6 地址，不解析主机名、CIDR 或范围表达式。
 */
public final class IpAddressNormalizer {


    private static final String IPV4 = "IPv4";

    private static final String IPV6 = "IPv6";

    private static final String IPV4_PATTERN = "^[0-9.]+$";

    private static final String IPV6_PATTERN = "^[0-9A-Fa-f:.]+$";

    /**
     * 禁止实例化精确 IP 规范化工具类。
     */
    private IpAddressNormalizer() {
    }


    /**
     * 校验并规范化单个精确 IPv4 或 IPv6 地址，不接受主机名、CIDR、范围或批量表达式。
     *
     * @param value 待规范化 IP 文本
     * @return 包含 IP 类型和规范地址的结果
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
     * 将已规范化的 IPv4 或 IPv6 转换为无符号十进制数值。
     *
     * <p>该数值同时用于数据库区间查询和 Redis Key。Key 必须额外保留 IP 类型，
     * 因为 {@code 0.0.0.1} 与 {@code ::1} 的无符号数值均为 {@code 1}。</p>
     *
     * @param normalizedIp {@link #normalizeExact(String)} 返回的规范化结果
     * @return IPv4 32 位或 IPv6 128 位无符号十进制数值
     */
    public static String toUnsignedNumber(NormalizedIp normalizedIp) {
        if (normalizedIp == null) {
            throw new IllegalArgumentException("Normalized IP must not be null");
        }
        try {
            return new BigInteger(1, InetAddress.getByName(normalizedIp.ipValue()).getAddress()).toString();
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("IP address is invalid", exception);
        }
    }

    /**
     * 校验并去除 IPv4 分段中的多余前导零。
     *
     * @param candidate 仅包含十进制数字和点号的 IPv4 候选值
     * @return 类型为 IPv4 的规范化地址
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
     * 使用 JDK 地址解析器校验并压缩 IPv6 文本。
     *
     * @param candidate 仅包含十六进制字符、冒号和点号的 IPv6 候选值
     * @return 类型为 IPv6 的规范化地址
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
