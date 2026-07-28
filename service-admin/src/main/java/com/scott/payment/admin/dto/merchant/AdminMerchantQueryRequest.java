package com.scott.payment.admin.dto.merchant;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantQueryRequest
 * @date : 2026-06-19 22:06
 * @email : scott_x@163.com
 * @description : 管理后台商户信息分页查询请求 DTO
 * @status : create
 *
 * <p>用于商户管理列表分页检索，支持按关键字、状态、国家和结算币种过滤。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminMerchantQueryRequest extends PageRequest {

    /**
     * 关键字，支持匹配商户号、商户名称和商户简称，可为空。
     */
    private String keyword;

    /**
     * 商户状态过滤条件，可为空。
     */
    private Integer merchantStatus;

    /**
     * 国家代码过滤条件，可为空。
     */
    private String countryCode;

    /**
     * 结算币种过滤条件，可为空。
     */
    private String settlementCurrency;
}
