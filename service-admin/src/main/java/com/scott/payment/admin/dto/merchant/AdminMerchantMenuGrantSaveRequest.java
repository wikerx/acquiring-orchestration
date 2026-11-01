package com.scott.payment.admin.dto.merchant;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 管理端商户菜单授权写操作请求模型，用于运营后台，承载新增或编辑字段；权限、状态和唯一性由应用服务校验。
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
