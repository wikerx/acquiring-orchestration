package com.scott.payment.payment.service.dto;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantTransactionSnapshotDTO
 * @date : 2026-08-14 12:45
 * @email : scott_x@163.com
 * @description : 商户可见交易快照聚合模型，承载首次请求冻结的商品、账单、付款人和收货资料。
 * @status : create
 */
@Data
public class MerchantTransactionSnapshotDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * {@code subMerchantInfo}字段，保存 {@code MerchantTransactionSnapshotDTO} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private PaymentCreateCommandDTO.SubMerchantInfoDTO subMerchantInfo;
    /**
     * {@code goodsInfo}集合，承载 {@code MerchantTransactionSnapshotDTO} 当前请求或响应中的多值数据。
     * <p>
     * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
     * </p>
     */
    private List<PaymentCreateCommandDTO.GoodsInfoDTO> goodsInfo = new ArrayList<>();
    /**
     * 账单持卡人信息字段，保存 {@code MerchantTransactionSnapshotDTO} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private PaymentCreateCommandDTO.BillingCardHolderInfoDTO billingCardHolderInfo;
    /**
     * {@code payerInfo}字段，保存 {@code MerchantTransactionSnapshotDTO} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private PaymentCreateCommandDTO.PayerInfoDTO payerInfo;
    /**
     * {@code shippingInfo}字段，保存 {@code MerchantTransactionSnapshotDTO} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private PaymentCreateCommandDTO.ShippingInfoDTO shippingInfo;
}
