package com.scott.payment.admin.entity.base;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IP Library Entities 聚合类型，用于运营后台，集中定义同一业务域下的请求、响应、查询条件和持久化视图模型。
 */
public final class IpLibraryEntities {

    /**
     * 阻止仅提供静态能力的类型被实例化。
     */
    private IpLibraryEntities() {
    }

    /**
     * IP 库分片路由配置实体。
     */
    @Data
    @TableName("ip_library_split_model")
    public static class IpLibrarySplitModelDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String ipType;
        /**
         * 持久化的{@code shardNo}，用于还原当前记录的业务事实。
         */
        private Integer shardNo;
        private String tableName;
        /**
         * 持久化的范围开始，用于还原当前记录的业务事实。
         */
        private String rangeStart;
        /**
         * 持久化的范围结束，用于还原当前记录的业务事实。
         */
        private String rangeEnd;
        private String dataVersion;
        /**
         * 持久化的{@code activeFlag}，用于还原当前记录的业务事实。
         */
        private Integer activeFlag;
        private Long rowCount;
        private String loadStatus;
        /**
         * 持久化的开始时间，用于还原当前记录的业务事实。
         */
        private LocalDateTime startTime;
        /**
         * 持久化的结束时间，用于还原当前记录的业务事实。
         */
        private LocalDateTime endTime;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         */
        private LocalDateTime createTime;
        /**
         * 记录最后更新时间，持久化精度为毫秒。
         */
        private LocalDateTime updateTime;
        /**
         * 记录创建人账号标识，用于操作审计。
         */
        private String createBy;
        /**
         * 记录最后更新人账号标识，用于操作审计。
         */
        private String updateBy;
    }

    /**
     * IP 库分表查询行。
     */
    @Data
    public static class IpLibraryDataRow {
        private Long id;
        private String ipType;
        private String ipNumberStart;
        private String ipNumberEnd;
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
