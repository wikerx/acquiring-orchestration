package com.scott.payment.admin.dto;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserAccountQueryRequest
 * @date : 2026-06-07 08:26
 * @email : scott_x@163.com
 * @description : sys用户账号查询条件模型，位于 运营后台服务，承载筛选字段、时间范围和分页边界，不包含数据范围授权结果。
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysUserAccountQueryRequest extends PageRequest {

    /**
     * 请求中的登录账号，用于限定本次操作的输入和校验范围。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private String loginAccount;
    /**
     * 手机号，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
     * <p>
     * 单位：无；格式：电话号码字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private String mobile;
    /**
     * 系统管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
     */
    private String email;
    /**
     * 部门ID，用于定位 {@code SysUserAccountQueryRequest} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private Long deptId;
    /**
     * 状态，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private Integer status;
}
