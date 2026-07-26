package com.scott.payment.admin.dto.merchant;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantMenuGrantSaveRequest
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : Admin Merchant Menu Grant Save Request 传输模型，位于 运营后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
 * @status : create
 */
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
