package com.scott.payment.admin.dto.merchant;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商户信息分页查询请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminMerchantQueryRequest extends PageRequest {

    private String keyword;

    private Integer merchantStatus;

    private String countryCode;

    private String settlementCurrency;
}
