package com.scott.payment.admin.dto.merchant;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantStatusRequest
 * @date : 2026-06-19 22:08
 * @email : scott_x@163.com
 * @description : 管理后台商户状态修改请求 DTO
 * @status : create
 *
 * <p>用于商户启停等状态变更操作，仅承载目标商户状态值。</p>
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantStatusRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户管理Admin Merchant Status 请求对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class AdminMerchantStatusRequest {

    /**
     * 商户目标状态，不允许为空。
     */
    @NotNull(message = "商户状态不能为空")
    private Integer merchantStatus;
}
