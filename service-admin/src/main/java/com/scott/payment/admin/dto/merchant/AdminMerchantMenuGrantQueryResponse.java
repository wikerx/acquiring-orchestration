package com.scott.payment.admin.dto.merchant;

import com.scott.payment.admin.dto.SysMenuDTO;
import com.scott.payment.admin.dto.SysPermissionDTO;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantMenuGrantQueryResponse
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户管理Admin Merchant Menu Grant Query 响应对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class AdminMerchantMenuGrantQueryResponse implements Serializable {

    /**
     * 商户管理固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 商户号。
     */
    private String merchantId;

    /**
     * 商户系统完整菜单树。
     */
    private List<SysMenuDTO> menus = Collections.emptyList();

    /**
     * 商户系统可授权权限清单。
     */
    private List<SysPermissionDTO> permissions = Collections.emptyList();

    /**
     * 当前商户已授权菜单ID。
     */
    private List<Long> checkedMenuIds = Collections.emptyList();

    /**
     * 当前商户已授权权限ID。
     */
    private List<Long> checkedPermissionIds = Collections.emptyList();

    /**
     * 当前商户已授权权限编码。
     */
    private List<String> checkedPermissionCodes = Collections.emptyList();
}
