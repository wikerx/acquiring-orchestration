package com.scott.payment.admin.entity.system;

import lombok.Data;

/**
 * 有效 Admin 角色及其可选商户范围值的只读投影。
 */
@Data
public class AdminRoleMerchantScopeDO {
    /**
     * 持久化的{@code dataScope}，用于还原当前记录的业务事实。
     */
    private String dataScope;
    /**
     * 持久化的范围值，用于还原当前记录的业务事实。
     */
    private String scopeValue;
}
