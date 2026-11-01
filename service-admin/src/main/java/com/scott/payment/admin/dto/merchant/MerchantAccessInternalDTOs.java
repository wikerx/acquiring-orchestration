package com.scott.payment.admin.dto.merchant;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * service-merchant 调用 service-admin 的访问配置内部 DTO，商户号仅由商户服务认证上下文写入。
 */
public final class MerchantAccessInternalDTOs {

    /**
     * 阻止仅提供静态能力的类型被实例化。
     */
    private MerchantAccessInternalDTOs() {
    }

    /**
     * 商户来源网址内部提交请求。
     */
    @Data
    public static class SourceUrlSubmitRequest {
        /**
         * 待审核来源网址列表。
         */
        private List<String> sourceUrls = new ArrayList<>();
        /**
         * 商户提交说明。
         */
        private String remark;
    }

    /**
     * 商户 IP 白名单内部提交请求。
     */
    @Data
    public static class IpWhitelistSubmitRequest {
        /**
         * 待审核精确 IP 列表。
         */
        private List<String> ipValues = new ArrayList<>();
        /**
         * 商户提交说明。
         */
        private String remark;
    }
}
