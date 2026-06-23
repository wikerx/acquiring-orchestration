package com.scott.payment.admin.dto.merchant;

import com.scott.payment.admin.dto.SysMenuDTO;
import com.scott.payment.admin.dto.SysPermissionDTO;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 管理后台商户菜单授权查询响应。
 *
 * <p>用于平台给指定商户配置商户端可见菜单和可用资源权限。</p>
 */
@Data
public class AdminMerchantMenuGrantQueryResponse implements Serializable {

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
