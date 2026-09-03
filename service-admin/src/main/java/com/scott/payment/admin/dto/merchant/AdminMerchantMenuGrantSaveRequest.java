package com.scott.payment.admin.dto.merchant;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantMenuGrantSaveRequest
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : admin商户菜单授权写操作请求模型，位于 运营后台服务，承载新增或编辑字段；权限、状态和唯一性由应用服务校验。
 * @status : create
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
