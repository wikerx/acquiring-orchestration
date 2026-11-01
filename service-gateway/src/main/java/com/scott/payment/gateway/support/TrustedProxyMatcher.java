package com.scott.payment.gateway.support;

import com.scott.payment.component.core.util.net.IpAddressNormalizer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Gateway 可信代理 CIDR 匹配器，同时支持 IPv4 和 IPv6 且不解析域名
 */
public class TrustedProxyMatcher {

    private final List<CidrBlock> blocks;

    /**
     * 解析受信任代理 CIDR 配置；非法配置立即阻止服务启动。
     *
     * @param cidrs 受信任 IPv4 或 IPv6 CIDR 列表
     */
    public TrustedProxyMatcher(List<String> cidrs) {
        this.blocks = cidrs == null ? List.of() : cidrs.stream().map(this::parse).toList();
    }

    /**
     * 判断规范化 IP 是否属于任一可信代理网段。
     */
    public boolean matches(String ipAddress) {
        if (blocks.isEmpty()) {
            return false;
        }
        byte[] address = bytes(ipAddress);
        return blocks.stream().anyMatch(block -> block.matches(address));
    }

    /**
     * 返回规范化后的精确 IP；非法内容直接抛出，调用方应忽略整条不可信转发链。
     */
    public String normalize(String ipAddress) {
        return IpAddressNormalizer.normalizeExact(ipAddress).ipValue();
    }

    /**
     * 将单个 CIDR 配置解析为二进制网段和前缀长度。
     *
     * @param cidr IPv4 或 IPv6 CIDR
     * @return 已校验网段
     */
    private CidrBlock parse(String cidr) {
        if (cidr == null) {
            throw new IllegalArgumentException("Trusted proxy CIDR can not be null");
        }
        String[] parts = cidr.trim().split("/", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Trusted proxy CIDR is invalid");
        }
        String normalized = normalize(parts[0]);
        byte[] network = bytes(normalized);
        int prefix;
        try {
            prefix = Integer.parseInt(parts[1]);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Trusted proxy CIDR prefix is invalid", exception);
        }
        if (prefix <= 0 || prefix > network.length * 8) {
            throw new IllegalArgumentException("Trusted proxy CIDR prefix is out of range");
        }
        return new CidrBlock(network, prefix);
    }

    /**
     * 将精确 IP 文本转换为地址字节，不接受域名。
     *
     * @param ipAddress 已规范化的 IPv4 或 IPv6
     * @return 地址字节
     */
    private byte[] bytes(String ipAddress) {
        try {
            return InetAddress.getByName(IpAddressNormalizer.normalizeExact(ipAddress).ipValue()).getAddress();
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("IP address is invalid", exception);
        }
    }

    /**
     * 已解析的受信任代理网段。
     *
     * @param network 网络地址字节
     * @param prefixLength 前缀位数
     */
    private record CidrBlock(byte[] network, int prefixLength) {
        /**
         * 判断候选地址是否位于当前网段。
         *
         * @param candidate 候选地址字节
         * @return true 表示地址族和前缀均匹配
         */
        private boolean matches(byte[] candidate) {
            if (candidate.length != network.length) {
                return false;
            }
            int completeBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int index = 0; index < completeBytes; index++) {
                if (candidate[index] != network[index]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (candidate[completeBytes] & mask) == (network[completeBytes] & mask);
        }
    }
}
