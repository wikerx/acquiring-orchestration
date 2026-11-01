package com.scott.payment.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * service-gateway 收银台入口签名配置，共享密钥只允许通过环境变量或配置中心注入。
 */
@ConfigurationProperties(prefix = "acquiring.gateway-ingress")
public class GatewayIngressProperties {

    /**
     * 与两个收银台下游服务共享的环境专属 HMAC 密钥，不允许写入日志。
     */
    private String secret;

    /**
     * 受保护外部请求正文最大字节数。
     */
    private int maxRequestBodyBytes = 1024 * 1024;

    /**
     * @return 外部注入的 HMAC 共享密钥
     */
    public String getSecret() {
        return secret;
    }

    /**
     * @param secret 外部注入的 HMAC 共享密钥
     */
    public void setSecret(String secret) {
        this.secret = secret;
    }

    /**
     * 查询最大请求报文体字节数；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 网关服务 既有权限、数据范围和空结果约定。
     * </p>
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public int getMaxRequestBodyBytes() {
        return maxRequestBodyBytes;
    }

    /**
     * 写入最大请求报文体字节数，保持配置属性或测试夹具中的字段值与调用方输入一致。
     * @param maxRequestBodyBytes 网关允许缓存和转发的最大入口报文体字节数
     */
    public void setMaxRequestBodyBytes(int maxRequestBodyBytes) {
        this.maxRequestBodyBytes = maxRequestBodyBytes;
    }
}
