package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOpenApiAccessConfigDO
 * @date : 2026-07-18 00:00
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 入站访问配置实体，位于 component-db 共享数据层，用于控制商户维度 IP 白名单是否启用。
 * @status : create
 */
@Data
@TableName("merchant_openapi_access_config")
public class MerchantOpenApiAccessConfigDO {

    /**
     * 配置主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 商户号，对应 base_merchant_info.merchant_id。
     */
    private String merchantId;

    /**
     * 是否启用 OpenAPI 请求 IP 白名单校验，1 启用，0 关闭。
     */
    private Integer ipWhitelistEnabled;

    /**
     * 备注，记录启用背景或运维说明。
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
     * 软删除标识，0 未删除，删除时可写入主键 ID，避免历史删除记录阻塞商户配置重建。
     */
    private Long deleted;
}
