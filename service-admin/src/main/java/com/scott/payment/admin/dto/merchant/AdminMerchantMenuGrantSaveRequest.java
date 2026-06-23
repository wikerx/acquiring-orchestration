package com.scott.payment.admin.dto.merchant;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 管理后台商户菜单授权保存请求。
 */
@Data
public class AdminMerchantMenuGrantSaveRequest implements Serializable {

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
