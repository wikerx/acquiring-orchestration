package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundManagementExportRow
 * @date : 2026-08-06 00:00
 * @email : scott_x@163.com
 * @description : Admin 退款管理导出行，包含运营核查需要的退款、审批和渠道摘要，不导出渠道原文。
 * @status : create
 */
@Data
public class RefundManagementExportRow {

    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.refund.transactionId", width = 28)
    private String refundTransactionId;
    /**
     * 原平台交易号，用于将请款、退款、撤销、增量授权等后续动作关联到原始交易。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与 transactionId 建立后续请款、退款、撤销和原交易之间的关联。
     * </p>
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.refund.sourceTransactionId", width = 28)
    private String sourceTransactionId;
    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与 merchantOrderNo、transactionId 共同限定商户交易归属。
     * </p>
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.refund.merchantId", width = 18)
    private String merchantId;
    /**
     * 商户订单号，由商户生成并在同一商户范围内用于交易幂等、查询和对账。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与 merchantId、transactionId 共同支持幂等、查询和对账。
     * </p>
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.refund.merchantOrderNo", width = 26)
    private String merchantOrderNo;
    /**
     * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.refund.transactionType", width = 16)
    private String transactionType;
    /**
     * 退款范围字段，保存 {@code RefundManagementExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.refund.refundScope", width = 14)
    private String refundScope;
    /**
     * 请求来源字段，保存 {@code RefundManagementExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：请求链路、回调链路或跨服务调用上下文。
     * </p>
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.refund.requestSource", width = 18)
    private String requestSource;
    /**
     * 交易金额，表示当前交易、费用、限额或统计口径下的金额值。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：必须与 currency 或同名币种字段一起解释。
     * </p>
     */
    @ExcelExportColumn(order = 8, headerKey = "excel.refund.amount", width = 18)
    private BigDecimal transactionAmount;
    /**
     * 交易币种，表示金额字段使用的币种。
     * <p>
     * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自平台支持币种；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
     * </p>
     */
    @ExcelExportColumn(order = 9, headerKey = "excel.refund.currency", width = 12)
    private String transactionCurrency;
    /**
     * 交易状态，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    @ExcelExportColumn(order = 10, headerKey = "excel.refund.status", width = 16)
    private String transactionStatus;
    /**
     * 审批状态，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    @ExcelExportColumn(order = 11, headerKey = "excel.refund.approvalStatus", width = 18)
    private String approvalStatus;
    /**
     * {@code applicantName}，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 12, headerKey = "excel.refund.applicant", width = 22)
    private String applicantName;
    /**
     * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 13, headerKey = "excel.refund.channelCode", width = 16)
    private String channelCode;
    /**
     * 渠道订单号，由渠道返回，用于渠道查询、回调匹配和对账。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 14, headerKey = "excel.refund.channelOrderNo", width = 26)
    private String channelOrderNo;
    /**
     * 交易受理时刻，按交易业务时区解释并保留毫秒精度。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 15, headerKey = "excel.refund.transactionTime", width = 22)
    private LocalDateTime transactionDateTime;
    /**
     * {@code completeTime}字段，保存 {@code RefundManagementExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 16, headerKey = "excel.refund.completeTime", width = 22)
    private LocalDateTime completeTime;
}
