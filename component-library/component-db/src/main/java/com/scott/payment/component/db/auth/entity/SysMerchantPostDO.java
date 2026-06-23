package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户岗位数据库实体。
 */
@Data
@TableName("sys_merchant_post")
public class SysMerchantPostDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String merchantId;
    private String postCode;
    private String postName;
    private Integer sortNo;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    private Long deleted;
}
