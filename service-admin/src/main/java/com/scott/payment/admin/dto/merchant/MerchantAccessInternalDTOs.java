package com.scott.payment.admin.dto.merchant;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantAccessInternalDTOs
 * @date : 2026-08-06 00:00
 * @description : service-merchant 调用 service-admin 的访问配置内部 DTO，商户号仅由商户服务认证上下文写入。
 * @status : create
 */
public final class MerchantAccessInternalDTOs {

    private MerchantAccessInternalDTOs() {
    }

    /** 商户来源网址内部提交请求。 */
    @Data
    public static class SourceUrlSubmitRequest {
        /** 待审核来源网址列表。 */
        private List<String> sourceUrls = new ArrayList<>();
        /** 商户提交说明。 */
        private String remark;
    }

    /** 商户 IP 白名单内部提交请求。 */
    @Data
    public static class IpWhitelistSubmitRequest {
        /** 待审核精确 IP 列表。 */
        private List<String> ipValues = new ArrayList<>();
        /** 商户提交说明。 */
        private String remark;
    }
}
