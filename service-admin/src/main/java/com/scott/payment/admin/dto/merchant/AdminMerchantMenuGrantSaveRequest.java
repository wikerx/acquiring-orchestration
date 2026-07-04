package com.scott.payment.admin.dto.merchant;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantMenuGrantSaveRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户管理Admin Merchant Menu Grant Save 请求对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class AdminMerchantMenuGrantSaveRequest implements Serializable {

    /**
     * 商户管理固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 授权菜单ID。
     */
    private List<Long> menuIds = Collections.emptyList();

    /**
     * 授权权限ID。
     */
    private List<Long> permissionIds = Collections.emptyList();
}
