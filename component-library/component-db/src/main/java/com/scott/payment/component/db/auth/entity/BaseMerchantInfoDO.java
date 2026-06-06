package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : BaseMerchantInfoDO
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 商户基础信息轻量实体，用于商户系统账号绑定校验
 * @status : create
 */
@Data
@TableName("base_merchant_info")
public class BaseMerchantInfoDO {

    /**
     * 主键ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 支付框架颁发的商户号。
     */
    private String merchantId;

    /**
     * 商户主体名称。
     */
    private String merchantName;

    /**
     * 商户状态。1 表示正常，2 表示冻结，3 表示关闭。
     */
    private Integer merchantStatus;

    /**
     * 删除标识。
     */
    private Integer deleted;
}
