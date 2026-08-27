package com.scott.payment.payment.entity;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFeeVersionPointerDO
 * @date : 2026-08-25 22:40
 * @email : scott_x@163.com
 * @description : Payment 费用只读适配层的当前商户版本投影，仅承载 MERCHANT 方案与已生效版本身份，不作为可编辑费用实体。
 * @status : create
 */
@Data
public class MerchantFeeVersionPointerDO {

    /** 商户费用方案主键。 */
    private Long feePlanId;

    /** 当前 ACTIVE 且与方案 current_version_id 一致的版本主键。 */
    private Long feePlanVersionId;

    /** 方案内不可复用的版本号。 */
    private Integer feePlanVersionNo;
}
