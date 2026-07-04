package com.scott.payment.admin.dto.base;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IpLibraryDTOs
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 基础数据Ip Library  DTO 集合，位于 service-admin 的接口传输层，用于说明职责边界、数据语义和关键业务约束。
 * @status : create
 */
public final class IpLibraryDTOs {

    private IpLibraryDTOs() {
    }

    /**
     * IP 库分页查询请求。
     */
    @Data
    public static class IpLibraryQueryRequest {
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private int pageNo = 1;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private int pageSize = 10;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String ipType;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String ipAddress;
        /**
         * 返回安全页码。
         */
        /**
         * 执行基础数据相关处理，保持当前层级的职责边界和返回语义。
         * @return 处理后的业务结果或页面展示数据。
         */
        public int safePageNo() {
            return pageNo <= 0 ? 1 : pageNo;
        }

        /**
         * 返回安全分页大小。
         */
        /**
         * 执行基础数据相关处理，保持当前层级的职责边界和返回语义。
         * @return 处理后的业务结果或页面展示数据。
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
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "ipAddress is required")
        private String ipAddress;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String ipType;
    }

    /**
     * IP 库列表展示对象。
     */
    @Data
    public static class IpLibraryRecordResponse {
        /**
         * 基础数据标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long id;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String ipType;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String ipNumberStart;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String ipNumberEnd;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String ipAddressStart;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String ipAddressEnd;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String countryAlpha2;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String countryAlpha3;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String countryNumeric;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String countryName;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String stateProvince;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String city;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String dataVersion;
        /**
         * 基础数据时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String createBy;
    }
}
