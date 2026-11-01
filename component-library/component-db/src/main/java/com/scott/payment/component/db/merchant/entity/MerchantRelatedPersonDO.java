package com.scott.payment.component.db.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商户相关人员共享实体，证件号仅以密文和脱敏值持久化
 */
@Data
@TableName("merchant_related_person")
public class MerchantRelatedPersonDO {


    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     */
    private String merchantId;

    private String fullName;
    /**
     * 持久化的{@code personRoles}，用于还原当前记录的业务事实。
     */
    private String personRoles;
    /**
     * 持久化的{@code nationality}，用于还原当前记录的业务事实。
     */
    private String nationality;
    /**
     * 持久化的{@code dateOfBirth}，用于还原当前记录的业务事实。
     */
    private LocalDate dateOfBirth;

    private String residenceCountry;
    /**
     * 持久化的{@code residentialAddress}，用于还原当前记录的业务事实。
     */
    private String residentialAddress;
    /**
     * ID类型，用于区分 {@code MerchantRelatedPersonDO} 记录的处理类别、配置维度或外部协议枚举。
     */
    private String idType;
    /**
     * 持久化的{@code idNumberCipher}，用于还原当前记录的业务事实。
     */
    private String idNumberCipher;
    /**
     * 持久化的{@code idNumberMasked}，用于还原当前记录的业务事实。
     */
    private String idNumberMasked;
    /**
     * 持久化的{@code idExpiryDate}，用于还原当前记录的业务事实。
     */
    private LocalDate idExpiryDate;
    /**
     * 持久化的{@code ownershipPercentage}，用于还原当前记录的业务事实。
     */
    private BigDecimal ownershipPercentage;
    /**
     * 持久化的{@code controllerFlag}，用于还原当前记录的业务事实。
     */
    private Integer controllerFlag;
    /**
     * 持久化的{@code pepFlag}，用于还原当前记录的业务事实。
     */
    private Integer pepFlag;
    /**
     * 邮件，表示业务联系人或付款人的邮箱地址，展示和日志输出必须脱敏。
     */
    private String email;
    /**
     * 电话，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
     */
    private String phone;
    /**
     * 持久化的展示订单，用于还原当前记录的业务事实。
     */
    private Integer displayOrder;
    /**
     * 持久化的{@code gmtCreate}，用于还原当前记录的业务事实。
     */
    private LocalDateTime gmtCreate;
    /**
     * 持久化的{@code gmtModified}，用于还原当前记录的业务事实。
     */
    private LocalDateTime gmtModified;
    /**
     * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
     */
    private Integer deleted;
}
