package com.scott.payment.admin.client.job.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateRemoteRequest
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : 分表表precreateremote请求模型，位于 运营后台服务，定义调用方必须提供或可选提供的字段，不直接执行业务逻辑。
 * @status : create
 */
@Data
public class ShardingTablePreCreateRemoteRequest {

    /**
     * {@code dryRun}，用于明确 {@code ShardingTablePreCreateRemoteRequest} 当前业务分支是否成立。
     * <p>
     * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：仅允许平台约定的启停取值；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private Boolean dryRun;

    /**
     * {@code includeCurrentQuarter}，用于明确 {@code ShardingTablePreCreateRemoteRequest} 当前业务分支是否成立。
     * <p>
     * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：仅允许平台约定的启停取值；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private Boolean includeCurrentQuarter = Boolean.TRUE;

    /**
     * {@code includeNextQuarter}，用于明确 {@code ShardingTablePreCreateRemoteRequest} 当前业务分支是否成立。
     * <p>
     * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：仅允许平台约定的启停取值；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private Boolean includeNextQuarter = Boolean.TRUE;

    /**
     * 逻辑表集合，承载 {@code ShardingTablePreCreateRemoteRequest} 当前请求或响应中的多值数据。
     * <p>
     * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
     * </p>
     */
    private List<String> logicalTables = new ArrayList<>();

    /**
     * {@code compareSchemaIfExists}，用于明确 {@code ShardingTablePreCreateRemoteRequest} 当前业务分支是否成立。
     * <p>
     * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：仅允许平台约定的启停取值；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private Boolean compareSchemaIfExists = Boolean.TRUE;

    /**
     * 执行本次管理操作的可信登录账号 ID，用于操作审计。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private String operatorId;

    /**
     * 执行本次管理操作时的账号显示名称快照，用于操作审计。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private String operatorName;
}
