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
     * 交易状态，1 允许交易，0 禁止交易。
     */
    private Integer status;

    /**
     * 审核状态：0 待审核，1 审核通过，2 审核拒绝。
     */
    private Integer approvalStatus;

    /**
     * 审批说明；审核拒绝时必须记录拒绝原因。
     */
    private String approvalRemark;

    /**
     * 提交来源：ADMIN 管理端直接新增，MERCHANT 商户端提交。
     */
    private String submitSource;

    /**
     * 审核人账号或姓名。
     */
    private String reviewBy;

    /**
     * 审核完成时间，保留毫秒精度。
     */
    private LocalDateTime reviewTime;

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
