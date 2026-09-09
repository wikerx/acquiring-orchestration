package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FeeSimulationExportRow
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 管理端费用试算逐项明细导出，保留输入、汇率、费用和净结算完整快照。
 * @status : create
 */
@Data
public class FeeSimulationExportRow {
    /**
     * {@code simulationNo}字段，保存 {@code FeeSimulationExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.fee.simulationNo", width = 24)
    private String simulationNo;
    /**
     * {@code lineNo}字段，保存 {@code FeeSimulationExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.fee.lineNo", width = 10)
    private Integer lineNo;
    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与 merchantOrderNo、transactionId 共同限定商户交易归属。
     * </p>
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.fee.merchantId", width = 20)
    private String merchantId;
    /**
     * 方案版本ID，用于定位 {@code FeeSimulationExportRow} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.fee.planVersionId", width = 16)
    private Long planVersionId;
    /**
     * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.fee.transactionType", width = 20)
    private String transactionType;
    /**
     * 支付类型，用于区分 {@code FeeSimulationExportRow} 记录的处理类别、配置维度或外部协议枚举。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.fee.paymentType", width = 20)
    private String paymentType;
    /**
     * 支付方式，表示支付方式、通知方式或调用方式。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.fee.paymentMethod", width = 20)
    private String paymentMethod;
    /**
     * {@code selectedRiskServices}字段，保存 {@code FeeSimulationExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 8, headerKey = "excel.fee.selectedRiskServices", width = 24)
    private String selectedRiskServices;
    /**
     * 标签金额，表示当前交易、费用、限额或统计口径下的金额值。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：必须与 currency 或同名币种字段一起解释。
     * </p>
     */
    @ExcelExportColumn(order = 9, headerKey = "excel.fee.labelAmount", width = 18)
    private BigDecimal labelAmount;
    /**
     * 标签币种，表示金额字段使用的币种。
     * <p>
     * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自平台支持币种；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
     * </p>
     */
    @ExcelExportColumn(order = 10, headerKey = "excel.fee.labelCurrency", width = 14)
    private String labelCurrency;
    /**
     * {@code labelToUsdRate}字段，保存 {@code FeeSimulationExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 11, headerKey = "excel.fee.labelToUsdRate", width = 18)
    private BigDecimal labelToUsdRate;
    /**
     * 明细类型，用于区分 {@code FeeSimulationExportRow} 记录的处理类别、配置维度或外部协议枚举。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 12, headerKey = "excel.fee.itemType", width = 16)
    private String itemType;
    /**
     * 费用类别，用于区分交易手续费、退款费、风控费、争议费和结算换汇费。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 13, headerKey = "excel.fee.feeCategory", width = 22)
    private String feeCategory;
    /**
     * {@code calculationStatus}，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    @ExcelExportColumn(order = 14, headerKey = "excel.fee.calculationStatus", width = 18)
    private String calculationStatus;
    /**
     * 是否计入费用合计；1 表示计入，0 表示仅展示该费用明细。
     * <p>
     * 单位：无；格式：布尔值或 0/1 标识；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：仅允许平台约定的真假取值；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 15, headerKey = "excel.fee.includedInFeeTotal", width = 16)
    private String includedInFeeTotal;
    /**
     * 风控服务类型，用于区分内部风控、外部风控和 3DS 服务费用。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 16, headerKey = "excel.fee.riskServiceType", width = 18)
    private String riskServiceType;
    /**
     * 费用规则名称，用于运营识别同一费用版本内的原子匹配规则。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 17, headerKey = "excel.fee.ruleName", width = 24)
    private String ruleName;
    /**
     * 费用计算模式，决定当前规则采用标准费率还是阶梯费率。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 18, headerKey = "excel.fee.feeMode", width = 16)
    private String feeMode;
    /**
     * 计费触发点，明确费用在请求、成功、失败或其它受控事件发生时计提。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 19, headerKey = "excel.fee.chargeTrigger", width = 20)
    private String chargeTrigger;
    /**
     * 费用上下限应用结果，用于标识未触发限制、命中最低费用或命中最高费用。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    @ExcelExportColumn(order = 20, headerKey = "excel.fee.appliedLimit", width = 16)
    private String appliedLimit;
    /**
     * 百分比费用换算为 USD 后与固定单笔费相加得到的原始费用，尚未应用最低和最高限制。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 21, headerKey = "excel.fee.rawFeeUsd", width = 18)
    private BigDecimal rawFeeUsd;
    /**
     * {@code detailFeeUsd}字段，保存 {@code FeeSimulationExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 22, headerKey = "excel.fee.detailFeeUsd", width = 18)
    private BigDecimal detailFeeUsd;
    /**
     * {@code detailFormula}字段，保存 {@code FeeSimulationExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 23, headerKey = "excel.fee.detailFormula", width = 42)
    private String detailFormula;
    /**
     * 应用最低和最高限制后的最终费用，币种恒为 USD。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 24, headerKey = "excel.fee.finalFeeUsd", width = 18)
    private BigDecimal finalFeeUsd;
    /**
     * 试算保证金金额，按规则计算并换算为 USD，仅用于预览不产生资金流水。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：必须与 currency 或同名币种字段一起解释。
     * </p>
     */
    @ExcelExportColumn(order = 25, headerKey = "excel.fee.reserveAmountUsd", width = 18)
    private BigDecimal reserveAmountUsd;
    /**
     * {@code netSettlementUsd}字段，保存 {@code FeeSimulationExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 26, headerKey = "excel.fee.netSettlementUsd", width = 20)
    private BigDecimal netSettlementUsd;
    /**
     * {@code feeTotalFormula}，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 27, headerKey = "excel.fee.feeTotalFormula", width = 42)
    private String feeTotalFormula;
    /**
     * {@code netSettlementFormula}字段，保存 {@code FeeSimulationExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 28, headerKey = "excel.fee.netSettlementFormula", width = 46)
    private String netSettlementFormula;
    /**
     * 汇率来源字段，保存 {@code FeeSimulationExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 29, headerKey = "excel.fee.rateSource", width = 22)
    private String rateSource;
    /**
     * 汇率生效时间字段，保存 {@code FeeSimulationExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 30, headerKey = "excel.fee.rateEffectiveTime", width = 22)
    private LocalDateTime rateEffectiveTime;
    /**
     * {@code rateValuationTime}字段，保存 {@code FeeSimulationExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 31, headerKey = "excel.fee.rateValuationTime", width = 22)
    private LocalDateTime rateValuationTime;
    /**
     * {@code detailSnapshotStatus}，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    @ExcelExportColumn(order = 32, headerKey = "excel.fee.detailSnapshotStatus", width = 20)
    private String detailSnapshotStatus;
    /**
     * 执行本次管理操作时的账号显示名称快照，用于操作审计。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 33, headerKey = "excel.fee.operator", width = 18)
    private String operatorName;
    /**
     * 记录创建时刻，持久化精度为毫秒。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
     * </p>
     */
    @ExcelExportColumn(order = 34, headerKey = "excel.fee.createTime", width = 22)
    private LocalDateTime createTime;
}
