package com.scott.payment.openapi.vo.checkout;

import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : HostedCheckoutSessionCreateVO
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 商户创建 Hosted Checkout 会话响应，只回显文档 8.1 定义的请求快照和平台生成的 checkoutUrl。
 * @status : create
 */
@Data
public class HostedCheckoutSessionCreateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 响应中的商户信息，用于管理端或商户端展示当前处理结果。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private PaymentCreateVO.MerchantInfoVO merchantInfo;
    /**
     * 响应中的订单信息，用于管理端或商户端展示当前处理结果。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private PaymentCreateVO.OrderInfoVO orderInfo;
    /**
     * {@code goodsInfo}集合，承载 {@code HostedCheckoutSessionCreateVO} 当前请求或响应中的多值数据。
     * <p>
     * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
     * </p>
     */
    private List<PaymentCreateVO.GoodsInfoVO> goodsInfo;
    /**
     * 响应中的账单持卡人信息，用于管理端或商户端展示当前处理结果。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private PaymentCreateVO.BillingCardHolderInfoVO billingCardHolderInfo;
    /**
     * 响应中的{@code payerInfo}，用于管理端或商户端展示当前处理结果。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private PaymentCreateVO.PayerInfoVO payerInfo;
    /**
     * 响应中的{@code shippingInfo}，用于管理端或商户端展示当前处理结果。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private PaymentCreateVO.ShippingInfoVO shippingInfo;
    /**
     * 响应中的交易信息，用于管理端或商户端展示当前处理结果。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private TransactionInfoVO transactionInfo;
    /**
     * 收银台URL，表示回调、通知、来源站点或远程接口地址。
     * <p>
     * 单位：无；格式：HTTP/HTTPS URL 或服务路径；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：长度和协议由调用方校验；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private String checkoutUrl;

    /** 创建会话请求中允许回显的交易扩展字段。 */
    @Data
    public static class TransactionInfoVO implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * 说明，用于保存人工备注、交易说明或配置补充说明。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String description;
        /**
         * 回调地址，表示回调、通知、来源站点或远程接口地址。
         * <p>
         * 单位：无；格式：HTTP/HTTPS URL 或服务路径；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和协议由调用方校验；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 transactionId、operationId 和通知状态共同定位异步回调处理。
         * </p>
         */
        private String callbackUrl;
        /**
         * 重定向地址URL，表示回调、通知、来源站点或远程接口地址。
         * <p>
         * 单位：无；格式：HTTP/HTTPS URL 或服务路径；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和协议由调用方校验；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String redirectUrl;
        /**
         * 响应中的{@code language}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String language;
    }
}
