package com.scott.payment.merchant.dto.access;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantAccessConfigDTOs
 * @date : 2026-08-06 00:00
 * @description : 商户店铺网址和 IP 白名单门户 DTO，不包含可由页面指定的商户号。
 * @status : create
 */
public final class MerchantAccessConfigDTOs {

    private MerchantAccessConfigDTOs() {
    }

    /** 店铺网址提交请求。 */
    @Data
    public static class SourceUrlSubmitRequest {
        /** 待审核店铺网址列表。 */
        private List<String> sourceUrls = new ArrayList<>();
        /** 提交说明。 */
        private String remark;
    }

    /** IP 白名单提交请求。 */
    @Data
    public static class IpWhitelistSubmitRequest {
        /** 待审核精确 IP 列表。 */
        private List<String> ipValues = new ArrayList<>();
        /** 提交说明。 */
        private String remark;
    }

    /** 店铺网址记录。 */
    @Data
    public static class SourceUrlItem {
        private String id;
        private String merchantId;
        private String sourceUrl;
        /** 供管理端响应反序列化使用，不允许出现在商户端响应中。 */
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        private String sourceHost;
        private Integer status;
        private Integer approvalStatus;
        private String approvalRemark;
        private String submitSource;
        private String reviewBy;
        private LocalDateTime reviewTime;
        private String remark;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    /** IP 白名单记录。 */
    @Data
    public static class IpWhitelistItem {
        private String id;
        private String merchantId;
        private String ipType;
        private String ipValue;
        private Integer status;
        private Integer approvalStatus;
        private String approvalRemark;
        private String submitSource;
        private String reviewBy;
        private LocalDateTime reviewTime;
        private String remark;
        private LocalDateTime gmtCreate;
        private LocalDateTime gmtModified;
    }
}
