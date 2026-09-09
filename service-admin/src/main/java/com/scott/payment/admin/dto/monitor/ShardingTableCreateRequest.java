package com.scott.payment.admin.dto.monitor;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTableCreateRequest
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : 分表表写操作请求模型，位于 运营后台服务，承载新增或编辑字段；权限、状态和唯一性由应用服务校验。
 * @status : create
 */
@Data
public class ShardingTableCreateRequest {

    /**
     * {@code includeCurrentQuarter}，用于明确 {@code ShardingTableCreateRequest} 当前业务分支是否成立。
     * <p>
     * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：仅允许平台约定的启停取值；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private Boolean includeCurrentQuarter = Boolean.TRUE;

    /**
     * {@code includeNextQuarter}，用于明确 {@code ShardingTableCreateRequest} 当前业务分支是否成立。
     * <p>
     * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：仅允许平台约定的启停取值；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private Boolean includeNextQuarter = Boolean.TRUE;

    /**
     * 逻辑表集合，承载 {@code ShardingTableCreateRequest} 当前请求或响应中的多值数据。
     * <p>
     * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
     * </p>
     */
    private List<String> logicalTables = new ArrayList<>();

    /**
     * {@code compareSchemaIfExists}，用于明确 {@code ShardingTableCreateRequest} 当前业务分支是否成立。
     * <p>
     * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：仅允许平台约定的启停取值；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private Boolean compareSchemaIfExists = Boolean.TRUE;
}
