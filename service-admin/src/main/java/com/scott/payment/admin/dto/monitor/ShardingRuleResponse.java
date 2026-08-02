package com.scott.payment.admin.dto.monitor;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingRuleResponse
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : Sharding Rule Response 传输模型，位于 运营后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
 * @status : create
 */
public class ShardingRuleResponse {

    /**
     * 监控治理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private String ruleKey;

    /** 当前实例加载的交易分片规则版本。 */
    private String ruleVersion;

    /** 当前实例规则 checksum 前缀。 */
    private String ruleChecksumPrefix;

    /** 当前实例已验证并登记的季度节点。 */
    private List<String> verifiedPhysicalNodes = new ArrayList<>();

    /** 当前季度是否已登记为实际节点。 */
    private Boolean currentNodeRegistered;

    /** 下一季度是否已登记为实际节点。 */
    private Boolean nextNodeRegistered;

    /**
     * logical Table，用于保存 Sharding Rule Response 中与 logicaltable 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String logicalTable;

    /**
     * template Table，用于定位邮件、通知或渠道参数模板。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String templateTable;

    /**
     * enabled，表示当前配置项或业务能力的启停开关。
     * <p>
     * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：仅允许平台约定的启停取值；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private Boolean enabled;

    /**
     * ID Column，用于保存 Sharding Rule Response 中与 IDcolumn 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String idColumn;

    /**
     * sharding Column，用于保存 Sharding Rule Response 中与 shardingcolumn 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String shardingColumn;

    /**
     * actual Data Source，用于保存 Sharding Rule Response 中与 actualdata来源 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String actualDataSource;

    /**
     * description，用于保存人工备注、交易说明或配置补充说明。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String description;

    /**
     * start Year，用于保存 Sharding Rule Response 中与 startyear 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private Integer startYear;

    /**
     * start Quarter，用于保存 Sharding Rule Response 中与 startquarter 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private Integer startQuarter;

    /**
     * end Year，用于保存 Sharding Rule Response 中与 endyear 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private Integer endYear;

    /**
     * end Quarter，用于保存 Sharding Rule Response 中与 endquarter 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private Integer endQuarter;

    /**
     * table Name Format，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String tableNameFormat;

    /**
     * current Physical Table，用于保存 Sharding Rule Response 中与 currentphysicaltable 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String currentPhysicalTable;

    /**
     * next Physical Table，用于保存 Sharding Rule Response 中与 nextphysicaltable 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String nextPhysicalTable;

    /**
     * physical Table Count，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private Integer physicalTableCount;

    /**
     * physical Tables，用于保存 Sharding Rule Response 中与 physicaltables 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private List<String> physicalTables = new ArrayList<>();
}
