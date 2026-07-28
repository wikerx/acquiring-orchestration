package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExchangeRawRateExportRow
 * @date : 2026-07-03 19:00
 * @email : scott_x@163.com
 * @description : Exchange Raw Rate Export Row 协作组件，位于 运营后台服务，封装 exchangeraw汇率exportrow 相关的校验、转换、持久化访问或运行时协作入口。
 * @status : create
 */
public class ExchangeRawRateExportRow {

    @ExcelExportColumn(order = 1, headerKey = "excel.exchange.common.sourceCode", width = 18)
    /**
     * source Code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String sourceCode;

    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.exchange.common.baseCurrency", width = 14)
    private String baseCurrency;

    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.exchange.common.quoteCurrency", width = 14)
    private String quoteCurrency;

    @ExcelExportColumn(order = 4, headerKey = "excel.exchange.raw.spotBuyRate", width = 18)
    /**
     * spot Buy Rate，用于保存 Exchange Raw Rate Export Row 中与 spotbuy汇率 相关的业务属性。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private BigDecimal spotBuyRate;

    @ExcelExportColumn(order = 5, headerKey = "excel.exchange.raw.spotSellRate", width = 18)
    /**
     * spot Sell Rate，用于保存 Exchange Raw Rate Export Row 中与 spotsell汇率 相关的业务属性。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private BigDecimal spotSellRate;

    @ExcelExportColumn(order = 6, headerKey = "excel.exchange.raw.cashBuyRate", width = 18)
    /**
     * cash Buy Rate，用于保存 Exchange Raw Rate Export Row 中与 cashbuy汇率 相关的业务属性。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private BigDecimal cashBuyRate;

    @ExcelExportColumn(order = 7, headerKey = "excel.exchange.raw.cashSellRate", width = 18)
    /**
     * cash Sell Rate，用于保存 Exchange Raw Rate Export Row 中与 cashsell汇率 相关的业务属性。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private BigDecimal cashSellRate;

    @ExcelExportColumn(order = 8, headerKey = "excel.exchange.raw.middleRate", width = 18)
    /**
     * middle Rate，用于定位渠道商户号配置或渠道侧 MID。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private BigDecimal middleRate;

    @ExcelExportColumn(order = 9, headerKey = "excel.exchange.raw.publishTime", width = 22)
    /**
     * publish Time，用于保存 Exchange Raw Rate Export Row 中与 publishtime 相关的业务属性。
     * <p>
     * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private LocalDateTime publishTime;

    @ExcelExportColumn(order = 10, headerKey = "excel.exchange.raw.fetchTime", width = 22)
    /**
     * fetch Time，用于保存 Exchange Raw Rate Export Row 中与 fetchtime 相关的业务属性。
     * <p>
     * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private LocalDateTime fetchTime;

    @ExcelExportColumn(order = 11, headerKey = "excel.exchange.common.effectiveTime", width = 22)
    /**
     * effective Time，用于保存 Exchange Raw Rate Export Row 中与 effectivetime 相关的业务属性。
     * <p>
     * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private LocalDateTime effectiveTime;

    @ExcelExportColumn(order = 12, headerKey = "excel.exchange.raw.createMethod", width = 16)
    /**
     * create Method，表示支付方式、通知方式或调用方式。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String createMethod;

    @ExcelExportColumn(order = 13, headerKey = "excel.exchange.raw.batchNo", width = 18)
    /**
     * batch No，用于保存 Exchange Raw Rate Export Row 中与 batchno 相关的业务属性。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String batchNo;

    @ExcelExportColumn(order = 14, headerKey = "excel.exchange.common.rateStatus", width = 14)
    /**
     * rate Status，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private String rateStatus;

    @ExcelExportColumn(order = 15, headerKey = "excel.exchange.raw.voidReason", width = 30)
    /**
     * void Reason，用于保存 Exchange Raw Rate Export Row 中与 voidreason 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String voidReason;

    @ExcelExportColumn(order = 16, headerKey = "excel.exchange.common.createTime", width = 22)
    /**
     * create Time，用于保存 Exchange Raw Rate Export Row 中与 createtime 相关的业务属性。
     * <p>
     * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private LocalDateTime createTime;
}
