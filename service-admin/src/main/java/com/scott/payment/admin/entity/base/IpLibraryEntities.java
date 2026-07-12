package com.scott.payment.admin.entity.base;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IpLibraryEntities
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 基础数据Ip Library 实体集合，位于 service-admin 的数据实体层，用于说明职责边界、数据语义和关键业务约束。
 * @status : create
 */
public final class IpLibraryEntities {

    private IpLibraryEntities() {
    }

    /**
     * IP 库分片路由配置实体。
     */
    @Data
    @TableName("ip_library_split_model")
    public static class IpLibrarySplitModelDO {
        /**
         * 基础数据标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @TableId(type = IdType.AUTO)
        private Long id;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String ipType;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private Integer shardNo;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String tableName;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String rangeStart;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String rangeEnd;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String dataVersion;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer activeFlag;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Long rowCount;
        /**
         * 基础数据状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private String loadStatus;
        /**
         * 基础数据时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime startTime;
        /**
         * 基础数据时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime endTime;
        /**
         * 基础数据时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 基础数据时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updateTime;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String createBy;
        /**
         * 基础数据时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private String updateBy;
    }

    /**
     * IP 库分表查询行。
     */
    @Data
    public static class IpLibraryDataRow {
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
