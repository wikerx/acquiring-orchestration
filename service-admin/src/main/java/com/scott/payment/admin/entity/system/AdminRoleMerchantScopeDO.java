package com.scott.payment.admin.entity.system;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRoleMerchantScopeDO
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 有效 Admin 角色及其可选商户范围值的只读投影。
 * @status : create
 */
@Data
public class AdminRoleMerchantScopeDO {
    /**
     * 持久化的{@code dataScope}，用于还原当前记录的业务事实。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String dataScope;
    /**
     * 持久化的范围值，用于还原当前记录的业务事实。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String scopeValue;
}
