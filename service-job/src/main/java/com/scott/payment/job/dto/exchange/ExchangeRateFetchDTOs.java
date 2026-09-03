package com.scott.payment.job.dto.exchange;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExchangeRateFetchDTOs
 * @date : 2026-07-03 19:00
 * @email : scott_x@163.com
 * @description : Exchange Rate Fetch DTOs 聚合类型，位于 调度任务服务，集中定义同一业务域下的请求、响应、查询条件和持久化视图模型。
 * @status : create
 */
public final class ExchangeRateFetchDTOs {

    private ExchangeRateFetchDTOs() {
    }

    /**
     * 汇率拉取任务参数。
     */
    @Data
    public static class ExchangeRateFetchRequest {
        /**
         * 来源编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String sourceCode;

        /**
         * {@code dryRun}，用于明确 {@code ExchangeRateFetchRequest} 当前业务分支是否成立。
         * <p>
         * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：仅允许平台约定的启停取值；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Boolean dryRun;
    }

    /**
     * 汇率源解析出的单条原始报价。
     */
    @Data
    public static class RawRateItem {

        /** 外部汇率源返回的币种名称，例如中行页面中的“美元”。 */

        private String sourceCurrencyName;

        /** 平台 ISO 4217 原始币种代码，由汇率源币种映射表补齐。 */

        private String baseCurrency;

        /** 平台 ISO 4217 目标币种代码，中行来源固定为 CNY。 */

        private String quoteCurrency;

        /**
         * {@code cashBuyRate}字段，保存 原始汇率明细 当前处理所需的业务取值。
         * <p>
         * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private BigDecimal cashBuyRate;

        /**
         * {@code cashSellRate}字段，保存 原始汇率明细 当前处理所需的业务取值。
         * <p>
         * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private BigDecimal cashSellRate;

        /**
         * {@code spotBuyRate}字段，保存 原始汇率明细 当前处理所需的业务取值。
         * <p>
         * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private BigDecimal spotBuyRate;

        /**
         * {@code spotSellRate}字段，保存 原始汇率明细 当前处理所需的业务取值。
         * <p>
         * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private BigDecimal spotSellRate;

        /**
         * {@code middleRate}，用于定位渠道商户号配置或渠道侧 MID。
         * <p>
         * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private BigDecimal middleRate;

        /**
         * {@code publishTime}字段，保存 原始汇率明细 当前处理所需的业务取值。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private LocalDateTime publishTime;
        /**
         * {@code skipReason}字段，保存 原始汇率明细 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private String skipReason;
    }

    /**
     * 汇率拉取任务执行统计结果。
     */
    @Data
    public static class ExchangeRateFetchResult {
        /**
         * 批次号，用于关联同一次导入、抓取、清分或结算处理的记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private String batchNo;
        /**
         * 来源编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private String sourceCode;
        /**
         * {@code fetchStatus}，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private String fetchStatus;
        /**
         * 合计计数，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private int totalCount;
        /**
         * 成功计数，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private int successCount;
        /**
         * 重复计数，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private int duplicateCount;
        /**
         * {@code skipCount}，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private int skipCount;
        /**
         * 内部错误摘要，用于运营排障；禁止包含密钥、卡数据和完整报文。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private String errorMessage;
        /**
         * {@code warnings}集合，承载 {@code ExchangeRateFetchResult} 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<String> warnings = new ArrayList<>();
    }
}
