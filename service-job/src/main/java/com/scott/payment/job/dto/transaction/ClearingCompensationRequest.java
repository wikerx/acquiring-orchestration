package com.scott.payment.job.dto.transaction;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingCompensationRequest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 清分补偿 Job 参数；默认 DRY_RUN，显式指定 SHADOW_WRITE 才写影子恢复。
 * @status : create
 */
@Data
public class ClearingCompensationRequest {
    /**
     * 请求中的运行模式，用于限定本次操作的输入和校验范围。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private String mode;
    /**
     * 请求中的{@code beginTime}，用于限定本次操作的输入和校验范围。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private LocalDateTime beginTime;
    /**
     * 请求中的结束时间，用于限定本次操作的输入和校验范围。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private LocalDateTime endTime;
    /**
     * 限额，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private Integer limit;
    /**
     * {@code maxPages}，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private Integer maxPages;
}
