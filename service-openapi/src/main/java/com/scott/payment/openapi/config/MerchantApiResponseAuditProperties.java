package com.scott.payment.openapi.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 商户响应加密审计投递配置。
 *
 * <p>HTTP 保留原同步回写路径用于兼容和回滚；MQ 使用本地可靠 Outbox 后由 service-data
 * 异步更新审计表。配置只改变附属审计的交付方式，不得改变商户响应或支付交易结果。</p>
 */
@ConfigurationProperties(prefix = "openapi.merchant-api-response-audit")
public class MerchantApiResponseAuditProperties implements InitializingBean {

    /**
     * 审计投递模式，默认保持历史 HTTP 行为。
     */
    private Mode mode = Mode.HTTP;

    /**
     * 获取当前审计投递模式。
     *
     * @return HTTP 或 MQ
     */
    public Mode getMode() {
        return mode;
    }

    /**
     * 设置审计投递模式。
     *
     * @param mode HTTP 或 MQ
     */
    public void setMode(Mode mode) {
        this.mode = mode;
    }

    /**
     * 启动时拒绝空模式，避免错误配置静默切换投递语义。
     */
    @Override
    public void afterPropertiesSet() {
        if (mode == null) {
            throw new IllegalStateException("OpenAPI merchant response audit mode is required");
        }
    }

    /**
     * 商户响应审计交付模式。
     */
    public enum Mode {
        /**
         * 兼容模式，同步调用 service-payment 内部回写接口。
         */
        HTTP,

        /**
         * 性能模式，可靠入队后由 service-data 异步持久化。
         */
        MQ
    }
}
