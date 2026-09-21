package com.scott.payment.component.db.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantRelatedPersonDO
 * @date : 2026-09-21 09:20
 * @email : scott_x@163.com
 * @description : 商户相关人员共享实体，证件号仅以密文和脱敏值持久化
 * @status : create
 */
@Data
@TableName("merchant_related_person")
public class MerchantRelatedPersonDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String merchantId;
    private String fullName;
    private String personRoles;
    private String nationality;
    private LocalDate dateOfBirth;
    private String residenceCountry;
    private String residentialAddress;
    private String idType;
    private String idNumberCipher;
    private String idNumberMasked;
    private LocalDate idExpiryDate;
    private BigDecimal ownershipPercentage;
    private Integer controllerFlag;
    private Integer pepFlag;
    private String email;
    private String phone;
    private Integer displayOrder;
    private LocalDateTime gmtCreate;
    private LocalDateTime gmtModified;
    private Integer deleted;
}
