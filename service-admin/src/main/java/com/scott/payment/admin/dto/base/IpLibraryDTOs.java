package com.scott.payment.admin.dto.base;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IP Library DTOs 聚合类型，用于运营后台，集中定义同一业务域下的请求、响应、查询条件和持久化视图模型。
 */
public final class IpLibraryDTOs {

    /**
     * 阻止仅提供静态能力的类型被实例化。
     */
    private IpLibraryDTOs() {
    }

    /**
     * IP 库分页查询请求。
     */
    @Data
    public static class IpLibraryQueryRequest {
        /**
         * 查询页码，从 1 开始。
         */
        private int pageNo = 1;
        /**
         * 每页记录数，由接口上限约束避免无界查询。
         */
        private int pageSize = 10;
        private String ipType;
        private String ipAddress;
        /**
         * 返回安全页码。
         */
        public int safePageNo() {
            return pageNo <= 0 ? 1 : pageNo;
        }

        /**
         * 返回安全分页大小。
         */
        public int safePageSize() {
            if (pageSize <= 0) {
                return 10;
            }
            return Math.min(pageSize, 200);
        }
    }

    /**
     * IP 精确命中查询请求。
     */
    @Data
    public static class IpLibraryLookupRequest {
        @NotBlank(message = "ipAddress is required")
        private String ipAddress;
        private String ipType;
    }

    /**
     * IP 库列表展示对象。
     */
    @Data
    public static class IpLibraryRecordResponse {
        private Long id;
        private String ipType;
        private String ipNumberStart;
        private String ipNumberEnd;
        private String ipAddressStart;
        private String ipAddressEnd;
        private String countryAlpha2;
        private String countryAlpha3;
        private String countryNumeric;
        private String countryName;
        private String stateProvince;
        /**
         * 城市，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         */
        private String city;
        private String dataVersion;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         */
        private LocalDateTime createTime;
        /**
         * 记录创建人账号标识，用于操作审计。
         */
        private String createBy;
    }
}
