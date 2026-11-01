package com.scott.payment.data.service.impl;

import com.scott.payment.component.core.security.OutboundHttpUrlPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

/**
 * 校验商户回调的出站协议、DNS 全地址和公网边界。
 *
 * <p>预发送校验阻止明显非法 URL，连接池 DNS Resolver 再校验实际用于建连的地址，
 * 从而覆盖校验与连接之间的 DNS 重绑定窗口。</p>
 */
@Component
public class MerchantCallbackTargetValidator {

    /**
     * 根据运行环境确定公网 HTTPS 或开发放宽策略的共享 URL 规则。
     */
    private final OutboundHttpUrlPolicy outboundPolicy;

    /**
     * 创建失败关闭的严格模式校验器，避免非 Spring 装配绕过生产网络边界。
     */
    public MerchantCallbackTargetValidator() {
        this(new OutboundHttpUrlPolicy(OutboundHttpUrlPolicy.Mode.PUBLIC_HTTPS_ONLY));
    }

    /**
     * 按 Spring active profiles 创建校验器；未知环境默认严格拒绝。
     *
     * @param environment Spring 运行环境
     */
    @Autowired
    public MerchantCallbackTargetValidator(Environment environment) {
        this(new OutboundHttpUrlPolicy(
                OutboundHttpUrlPolicy.modeForProfiles(environment.getActiveProfiles())));
    }

    /**
     * 创建使用指定 URL 策略的校验器，供地址解析安全测试使用。
     *
     * @param outboundPolicy 出站 URL 策略
     */
    MerchantCallbackTargetValidator(OutboundHttpUrlPolicy outboundPolicy) {
        this.outboundPolicy = outboundPolicy;
    }

    /**
     * 校验本次实际出站地址。dev/test 允许 HTTP、私网和回环目标；UAT、生产及未知环境只允许公网 HTTPS。
     *
     * @param targetUrl 通知任务冻结的原始回调地址
     */
    public void validate(String targetUrl) {
        try {
            outboundPolicy.validate(targetUrl);
        } catch (IllegalArgumentException exception) {
            throw new MerchantCallbackTargetRejectedException(exception);
        }
    }

    /**
     * 解析实际建连地址并复用环境网络策略；必须由商户回调 HTTP 客户端的 DNS Resolver 调用。
     *
     * @param host 回调 URL 主机名
     * @return 当前允许建立连接的全部地址
     */
    public InetAddress[] resolveAllowedAddresses(String host) {
        try {
            return outboundPolicy.resolveAllowedAddresses(host);
        } catch (Exception exception) {
            throw new MerchantCallbackTargetRejectedException(exception);
        }
    }
}
