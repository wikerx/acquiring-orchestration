package com.scott.payment.admin.dto.monitor;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateTableResultResponse
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : 分表表precreate表响应模型，位于 运营后台服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
 * @status : create
 */
@Data
public class ShardingTablePreCreateTableResultResponse {

    /**
     * 响应中的逻辑表，用于管理端或商户端展示当前处理结果。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private String logicalTable;

    /**
     * 模板表，用于定位邮件、通知或渠道参数模板。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private String templateTable;

    /**
     * 响应中的物理表，用于管理端或商户端展示当前处理结果。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private String physicalTable;

    /**
     * 响应中的{@code targetQuarter}，用于管理端或商户端展示当前处理结果。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private String targetQuarter;

    /**
     * 状态，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private String status;

    /**
     * {@code schemaCheckStatus}，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private String schemaCheckStatus;

    /** 分片时间字段 DATETIME(3) 校验状态。 */
    private String shardingTimeCheckStatus;

    /** 物理表 utf8mb4 字符集校验状态。 */
    private String charsetCheckStatus;

    /** 当前 AUTO_INCREMENT 是否处于季度安全号段。 */
    private String autoIncrementCheckStatus;

    /**
     * 响应中的自动自增开始，用于管理端或商户端展示当前处理结果。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long autoIncrementStart;

    /**
     * 响应中的自动自增当前，用于管理端或商户端展示当前处理结果。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long autoIncrementCurrent;

    /**
     * 响应中的自动自增最大，用于管理端或商户端展示当前处理结果。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long autoIncrementMax;

    /**
     * 响应中的说明，用于管理端或商户端展示当前处理结果。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private String message;
}
