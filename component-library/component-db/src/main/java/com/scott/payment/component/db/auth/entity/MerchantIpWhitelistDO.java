package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantIpWhitelistDO
 * @date : 2026-07-18 00:00
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI IP 白名单实体，位于 component-db 共享数据层，仅保存精确匹配的 IPv4/IPv6 地址。
 * @status : create
 */
@Data
@TableName("merchant_ip_whitelist")
public class MerchantIpWhitelistDO {

    /**
     * 白名单主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 商户号，对应 base_merchant_info.merchant_id。
     */
    private String merchantId;

    /**
     * IP 类型，取值 IPv4 或 IPv6。
     */
    private String ipType;

    /**
     * 规范化后的精确 IP 地址。
     */
    private String ipValue;

    /**
     * 启停状态，1 启用，0 停用。
     */
    private Integer status;

    /**
     * 备注，记录商户提供的用途或来源说明。
     */
    private String remark;

    /**
     * 创建人账号或姓名。
     */
    private String createBy;

    /**
     * 更新人账号或姓名。
     */
    private String updateBy;

    /**
     * 创建时间，保留毫秒精度。
     */
    private LocalDateTime gmtCreate;

    /**
     * 更新时间，保留毫秒精度。
     */
    private LocalDateTime gmtModified;

    /**
     * 软删除标识，0 未删除，删除时写入主键 ID，避免历史删除记录阻塞同商户同 IP 重新录入。
     */
    private Long deleted;
}
