package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysMenuDeleteRequest
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : Sys Menu Delete Request 传输模型，位于 运营后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
 * @status : create
 */
public class SysMenuDeleteRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "菜单ID不能为空")
    /**
     * menu ID，用于定位 Sys Menu Delete Request 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private Long menuId;
}
