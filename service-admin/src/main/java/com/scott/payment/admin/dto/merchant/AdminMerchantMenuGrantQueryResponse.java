package com.scott.payment.admin.dto.merchant;

import com.scott.payment.admin.dto.SysMenuDTO;
import com.scott.payment.admin.dto.SysPermissionDTO;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantMenuGrantQueryResponse
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : AdminMerchantMenuGrantQueryResponse 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
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
