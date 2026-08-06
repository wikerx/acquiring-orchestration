package com.scott.payment.admin.dto.merchant;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.scott.payment.component.core.model.PageRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantIpWhitelistDTOs
 * @date : 2026-07-18 00:00
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI IP 白名单管理 DTO 集合，位于 service-admin 接口传输层，仅承载后台精确 IP 配置和商户开关配置。
 * @status : create
 */
public final class AdminMerchantIpWhitelistDTOs {

    private AdminMerchantIpWhitelistDTOs() {
    }

    /**
     * 商户 IP 白名单分页查询条件。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class MerchantIpWhitelistQuery extends PageRequest {
        /**
         * 商户号精确筛选。
         */
        private String merchantId;
        /**
         * 商户号、主体名称或简称模糊筛选。
         */
        private String merchantKeyword;
        /**
         * IP 地址精确或模糊筛选，后台排查使用。
         */
        private String ipValue;
        /**
         * IP 类型，IPv4 或 IPv6。
         */
        private String ipType;
        /**
         * IP 记录状态，1 启用，0 停用。
         */
        private Integer status;
        /**
         * 审核状态，0 待审核、1 审核通过、2 审核拒绝。
         */
        private Integer approvalStatus;
        /**
         * 提交来源，ADMIN 或 MERCHANT。
         */
        private String submitSource;
        /**
         * 商户维度白名单开关，1 启用，0 关闭。
         */
        private Integer ipWhitelistEnabled;
    }

    /**
     * 商户 IP 白名单新增请求。
     */
    @Data
    public static class MerchantIpWhitelistCreateRequest {
        /**
         * 商户号，对应 base_merchant_info.merchant_id。
         */
        @NotBlank(message = "merchantId is required")
        private String merchantId;
        /**
         * 精确 IP 地址集合；每个地址落一条记录，不接受 CIDR 或范围表达式。
         */
        private List<String> ipValues = new ArrayList<>();
        /**
         * 新增记录状态，1 启用，0 停用；为空默认启用。
         */
        private Integer status;
        /**
         * 备注。
         */
        private String remark;
    }

    /**
     * 商户 IP 白名单编辑请求。
     */
    @Data
    public static class MerchantIpWhitelistUpdateRequest {
        /**
         * 精确 IP 地址，不接受 CIDR 或范围表达式。
         */
        @NotBlank(message = "ipValue is required")
        private String ipValue;
        /**
         * IP 记录状态，1 启用，0 停用。
         */
        private Integer status;
        /**
         * 备注。
         */
        private String remark;
    }

    /**
     * IP 记录启停请求。
     */
    @Data
    public static class MerchantIpWhitelistStatusRequest {
        /**
         * IP 记录状态，1 启用，0 停用。
         */
        @NotNull(message = "status is required")
        private Integer status;
    }

    /**
     * 商户端 IP 白名单提交请求，商户号由认证上下文传递，不接受页面输入。
     */
    @Data
    public static class MerchantIpWhitelistSubmissionRequest {
        /**
         * 精确 IP 地址集合；每个地址落一条待审核记录。
         */
        private List<String> ipValues = new ArrayList<>();
        /**
         * 商户提交说明。
         */
        private String remark;
    }

    /**
     * IP 白名单审批请求。
     */
    @Data
    public static class MerchantIpWhitelistApprovalRequest {
        /**
         * 审核结果，仅允许 1 审核通过或 2 审核拒绝。
         */
        @NotNull(message = "approvalStatus is required")
        private Integer approvalStatus;
        /**
         * 审批说明；审核拒绝时必须填写拒绝原因。
         */
        private String approvalRemark;
        /**
         * 审核通过后的交易状态，1 允许、0 禁止；为空默认允许。
         */
        private Integer status;
    }

    /**
     * 商户维度 IP 白名单开关请求。
     */
    @Data
    public static class MerchantIpWhitelistConfigRequest {
        /**
         * 商户号，对应 base_merchant_info.merchant_id。
         */
        @NotBlank(message = "merchantId is required")
        private String merchantId;
        /**
         * 是否启用 OpenAPI 请求 IP 白名单校验，1 启用，0 关闭。
         */
        @NotNull(message = "ipWhitelistEnabled is required")
        private Integer ipWhitelistEnabled;
        /**
         * 配置备注。
         */
        private String remark;
    }

    /**
     * 商户 IP 白名单列表响应。
     */
    @Data
    public static class MerchantIpWhitelistResponse {
        /**
         * 白名单记录主键，前端按字符串处理避免 JS 精度丢失。
         */
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        /**
         * 商户号。
         */
        private String merchantId;
        /**
         * 商户主体名称。
         */
        private String merchantName;
        /**
         * 商户简称。
         */
        private String merchantShortName;
        /**
         * IP 类型，IPv4 或 IPv6。
         */
        private String ipType;
        /**
         * 规范化后的精确 IP 地址。
         */
        private String ipValue;
        /**
         * IP 记录状态，1 启用，0 停用。
         */
        private Integer status;
        /**
         * 审核状态，0 待审核、1 审核通过、2 审核拒绝。
         */
        private Integer approvalStatus;
        /**
         * 审批说明，审核拒绝时包含拒绝原因。
         */
        private String approvalRemark;
        /**
         * 提交来源，ADMIN 或 MERCHANT。
         */
        private String submitSource;
        /**
         * 审核人账号或姓名。
         */
        private String reviewBy;
        /**
         * 审核时间。
         */
        private LocalDateTime reviewTime;
        /**
         * 商户维度白名单开关，1 启用，0 关闭。
         */
        private Integer ipWhitelistEnabled;
        /**
         * IP 记录备注。
         */
        private String remark;
        /**
         * 商户开关备注。
         */
        private String configRemark;
        /**
         * 创建人。
         */
        private String createBy;
        /**
         * 更新人。
         */
        private String updateBy;
        /**
         * 创建时间。
         */
        private LocalDateTime gmtCreate;
        /**
         * 更新时间。
         */
        private LocalDateTime gmtModified;
        /**
         * 同一商户在当前查询条件下命中的 IP 白名单明细；列表页按商户聚合展示时使用。
         */
        private List<MerchantIpWhitelistItem> ipWhitelists = new ArrayList<>();
    }

    /**
     * 商户 IP 白名单聚合列表中的单个 IP 明细。
     */
    @Data
    public static class MerchantIpWhitelistItem {
        /**
         * 白名单记录主键，前端按字符串处理避免 JS 精度丢失。
         */
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        /**
         * IP 类型，IPv4 或 IPv6。
         */
        private String ipType;
        /**
         * 规范化后的精确 IP 地址。
         */
        private String ipValue;
        /**
         * IP 记录状态，1 启用，0 停用。
         */
        private Integer status;
        /**
         * 审核状态，0 待审核、1 审核通过、2 审核拒绝。
         */
        private Integer approvalStatus;
        /**
         * 审批说明，审核拒绝时包含拒绝原因。
         */
        private String approvalRemark;
        /**
         * 提交来源，ADMIN 或 MERCHANT。
         */
        private String submitSource;
        /**
         * 审核人账号或姓名。
         */
        private String reviewBy;
        /**
         * 审核时间。
         */
        private LocalDateTime reviewTime;
        /**
         * IP 记录备注。
         */
        private String remark;
        /**
         * 更新人。
         */
        private String updateBy;
        /**
         * 更新时间。
         */
        private LocalDateTime gmtModified;
    }
}
