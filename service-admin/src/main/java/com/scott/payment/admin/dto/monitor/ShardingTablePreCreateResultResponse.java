package com.scott.payment.admin.dto.monitor;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateResultResponse
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : Sharding Table Pre Create Result Response 传输模型，位于 运营后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
 * @status : create
 */
public class ShardingTablePreCreateResultResponse {

    /**
     * dry Run，用于保存 Sharding Table Pre Create Result Response 中与 dryrun 相关的业务属性。
     * <p>
     * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：仅允许平台约定的启停取值；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private Boolean dryRun;

    /**
     * timezone，用于保存 Sharding Table Pre Create Result Response 中与 时区配置 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String timezone;

    /**
     * strategy，用于保存 Sharding Table Pre Create Result Response 中与 strategy 相关的业务属性。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String strategy;

    /**
     * current Quarter，用于保存 Sharding Table Pre Create Result Response 中与 currentquarter 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String currentQuarter;

    /** 待人工评审的候选规则版本。 */
    private String candidateRuleVersion;

    /** 候选规则 SHA-256 checksum。 */
    private String candidateRuleChecksum;

    /** 仅由已建表且全量校验通过的季度组成的候选节点。 */
    private List<String> verifiedPhysicalNodes = new ArrayList<>();

    /** 是否允许进入人工发布和滚动重启步骤。 */
    private Boolean publicationReady = Boolean.FALSE;

    /** 阻止候选规则发布的治理原因。 */
    private List<String> publicationBlockers = new ArrayList<>();

    /** 下一步受控操作标识，不会由接口自动执行。 */
    private String nextAction;

    /**
     * target Quarters，用于保存 Sharding Table Pre Create Result Response 中与 targetquarters 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private List<String> targetQuarters = new ArrayList<>();

    /**
     * created Tables，用于保存 Sharding Table Pre Create Result Response 中与 createdtables 相关的业务属性。
     * <p>
     * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private List<String> createdTables = new ArrayList<>();

    /**
     * skipped Tables，用于保存 Sharding Table Pre Create Result Response 中与 skippedtables 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private List<String> skippedTables = new ArrayList<>();

    /**
     * failed Tables，用于保存 Sharding Table Pre Create Result Response 中与 failedtables 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private List<String> failedTables = new ArrayList<>();

    /**
     * schema Mismatch Tables，用于保存 Sharding Table Pre Create Result Response 中与 schemamismatchtables 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private List<String> schemaMismatchTables = new ArrayList<>();

    /**
     * warnings，用于保存 Sharding Table Pre Create Result Response 中与 warnings 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private List<String> warnings = new ArrayList<>();

    /**
     * table Results，用于保存 Sharding Table Pre Create Result Response 中与 tableresults 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private List<ShardingTablePreCreateTableResultResponse> tableResults = new ArrayList<>();
}
