package com.scott.payment.link.service;
import com.scott.payment.link.model.PaymentLinkManagementModels.AllowedMethod;
import java.util.List;

/** 链接管理只依赖展示能力契约，不依赖 Payment 进程或渠道执行器。 */
@FunctionalInterface
public interface PaymentLinkCapabilities {
    /** 多币种展示可以复用一次读取的配置快照；默认实现保持原调用语义。 */
    default PaymentLinkCapabilities snapshot(String merchantId) { return this; }
    /** 将请求的支付方式与商户当前路由能力取交集，按交易币种返回可用展示能力。 */
    List<AllowedMethod> resolve(String merchantId, List<AllowedMethod> requested, String currency);
}
