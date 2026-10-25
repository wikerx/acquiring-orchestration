package com.scott.payment.component.core.security;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OutboundHttpUrlPolicy
 * @date : 2026-10-25 00:00
 * @email : scott_x@163.com
 * @description : 通用出站 HTTP URL 安全策略，负责结构校验、环境模式选择、DNS 全地址解析和非公网目标阻断，不决定具体业务错误码。
 * @status : create
 */
public final class OutboundHttpUrlPolicy {

    /** 出站目标校验模式。 */
    public enum Mode {
        /** 仅供明确的 dev/test 环境联调，允许 HTTP、私网和回环目标。 */
        DEVELOPMENT,
        /** UAT、生产和未知环境使用，只允许解析到公网地址的 HTTPS URL。 */
        PUBLIC_HTTPS_ONLY
    }

    /** DNS 解析边界，允许测试使用确定性地址集验证多地址策略。 */
    @FunctionalInterface
    public interface HostResolver {
        /** @return 主机名当前解析出的全部地址 */
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final Mode mode;
    private final HostResolver hostResolver;

    /** 使用系统 DNS 创建指定模式的策略。 */
    public OutboundHttpUrlPolicy(Mode mode) {
        this(mode, InetAddress::getAllByName);
    }

    /**
     * 创建可注入 DNS 解析器的策略。
     *
     * @param mode 环境安全模式
     * @param hostResolver DNS 全地址解析器
     */
    public OutboundHttpUrlPolicy(Mode mode, HostResolver hostResolver) {
        this.mode = Objects.requireNonNull(mode, "mode can not be null");
        this.hostResolver = Objects.requireNonNull(hostResolver, "hostResolver can not be null");
    }

    /**
     * 校验出站 HTTP URL。未知环境应由调用方传入 {@link Mode#PUBLIC_HTTPS_ONLY}。
     *
     * @param value 待校验 URL
     * @return 结构完整且符合当前模式的 URI
     * @throws IllegalArgumentException URL 不满足出站安全策略
     */
    public URI validate(String value) {
        URI uri = parse(value);
        if (uri == null) {
            throw new IllegalArgumentException("outbound URL is invalid");
        }
        if (mode == Mode.DEVELOPMENT) {
            return uri;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("outbound URL must use HTTPS");
        }
        try {
            resolveAllowedAddresses(uri.getHost());
        } catch (UnknownHostException | SecurityException exception) {
            throw new IllegalArgumentException("outbound URL host can not be resolved", exception);
        }
        return uri;
    }

    /**
     * 解析 HTTP 客户端即将连接的地址，并在严格模式下对同一批地址执行公网校验。
     *
     * <p>该方法应直接配置为网络客户端的 DNS Resolver，避免 URL 预校验后再次解析产生
     * DNS 重绑定窗口。返回副本，调用方不得修改策略持有的解析结果。</p>
     *
     * @param host HTTP 客户端准备连接的主机名
     * @return 当前解析出的全部允许地址
     * @throws UnknownHostException 主机无法解析或解析结果为空
     * @throws IllegalArgumentException 严格模式下任一地址不是公网单播地址
     */
    public InetAddress[] resolveAllowedAddresses(String host) throws UnknownHostException {
        if (host == null || host.isBlank()) {
            throw new UnknownHostException("outbound URL host is missing");
        }
        InetAddress[] addresses = hostResolver.resolve(host);
        if (addresses == null || addresses.length == 0) {
            throw new UnknownHostException("outbound URL host has no addresses");
        }
        if (mode == Mode.PUBLIC_HTTPS_ONLY
                && Arrays.stream(addresses).anyMatch(address -> !isPublicAddress(address))) {
            throw new IllegalArgumentException("outbound URL host is not public");
        }
        return addresses.clone();
    }

    /** 仅校验 URL 结构，不执行 DNS 解析或环境网络策略。 */
    public static boolean isStructurallyValid(String value) {
        return parse(value) != null;
    }

    /**
     * 根据 Spring active profiles 选择安全模式。只有全部活动 profile 均为 dev/test 时放宽。
     *
     * @param activeProfiles 当前活动 profile
     * @return 环境安全模式；无 profile 或出现未知 profile 时严格处理
     */
    public static Mode modeForProfiles(String... activeProfiles) {
        if (activeProfiles == null || activeProfiles.length == 0) {
            return Mode.PUBLIC_HTTPS_ONLY;
        }
        for (String activeProfile : activeProfiles) {
            if (activeProfile == null) {
                return Mode.PUBLIC_HTTPS_ONLY;
            }
            String normalizedProfile = activeProfile.trim().toLowerCase(Locale.ROOT);
            if (!("dev".equals(normalizedProfile) || "test".equals(normalizedProfile))) {
                return Mode.PUBLIC_HTTPS_ONLY;
            }
        }
        return Mode.DEVELOPMENT;
    }

    /**
     * 判断地址是否属于允许出站连接的公网单播范围。
     *
     * @param address DNS 解析结果
     * @return 公网单播地址返回 true；私网、回环、链路本地、文档、保留或组播地址返回 false
     */
    public static boolean isPublicAddress(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        if (address instanceof Inet4Address) {
            return isPublicIpv4(address.getAddress());
        }
        if (address instanceof Inet6Address) {
            return isPublicIpv6(address.getAddress());
        }
        return false;
    }

    private static URI parse(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            int port = uri.getPort();
            if (!uri.isAbsolute() || uri.isOpaque() || scheme == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getHost().isBlank()
                    || uri.getRawAuthority() == null || uri.getUserInfo() != null
                    || uri.getFragment() != null || port == 0 || port > 65_535) {
                return null;
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean isPublicIpv4(byte[] address) {
        long value = ((long) address[0] & 0xff) << 24
                | ((long) address[1] & 0xff) << 16
                | ((long) address[2] & 0xff) << 8
                | ((long) address[3] & 0xff);
        return !inIpv4Range(value, 0x00000000L, 8)
                && !inIpv4Range(value, 0x0a000000L, 8)
                && !inIpv4Range(value, 0x64400000L, 10)
                && !inIpv4Range(value, 0x7f000000L, 8)
                && !inIpv4Range(value, 0xa9fe0000L, 16)
                && !inIpv4Range(value, 0xac100000L, 12)
                && !inIpv4Range(value, 0xc0000000L, 24)
                && !inIpv4Range(value, 0xc0000200L, 24)
                && !inIpv4Range(value, 0xc0586300L, 24)
                && !inIpv4Range(value, 0xc0a80000L, 16)
                && !inIpv4Range(value, 0xc6120000L, 15)
                && !inIpv4Range(value, 0xc6336400L, 24)
                && !inIpv4Range(value, 0xcb007100L, 24)
                && value < 0xe0000000L;
    }

    private static boolean inIpv4Range(long value, long network, int prefixLength) {
        long mask = prefixLength == 0 ? 0L : (0xffffffffL << (32 - prefixLength)) & 0xffffffffL;
        return (value & mask) == (network & mask);
    }

    private static boolean isPublicIpv6(byte[] address) {
        if ((address[0] & 0xe0) != 0x20) {
            return false;
        }
        return !matchesPrefix(address, new int[]{0x20, 0x01, 0x00}, 23)
                && !matchesPrefix(address, new int[]{0x20, 0x01, 0x0d, 0xb8}, 32)
                && !matchesPrefix(address, new int[]{0x20, 0x02}, 16)
                && !matchesPrefix(address, new int[]{0x3f, 0xff, 0x00}, 20);
    }

    private static boolean matchesPrefix(byte[] address, int[] network, int prefixLength) {
        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;
        for (int index = 0; index < fullBytes; index++) {
            if ((address[index] & 0xff) != network[index]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xff << (8 - remainingBits) & 0xff;
        return ((address[fullBytes] & 0xff) & mask) == (network[fullBytes] & mask);
    }
}
