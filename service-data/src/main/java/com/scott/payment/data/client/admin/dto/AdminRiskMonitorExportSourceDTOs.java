package com.scott.payment.data.client.admin.dto;

import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.RiskConfigQuery;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Data 调用 Admin 风控与数据源导出来源的强类型内部模型。 */
public final class AdminRiskMonitorExportSourceDTOs {
    private AdminRiskMonitorExportSourceDTOs() {
    }

    /** 风控配置来源分页请求。 */
    @Data
    public static class RiskPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private String locale;
        private String moduleType;
        private String functionCode;
        private RiskConfigQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /** 动态 Excel 列定义。 */
    @Data
    public static class DynamicColumn implements Serializable {
        private static final long serialVersionUID = 1L;
        private String key;
        private String header;
        private int width;
        private String align;
    }

    /** 风控配置动态列分页响应。 */
    @Data
    public static class RiskPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private String title;
        private List<DynamicColumn> columns = new ArrayList<>();
        private List<Map<String, Object>> records = new ArrayList<>();
    }

    /** 数据源快照来源分页请求。 */
    @Data
    public static class DatasourcePageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private String locale;
        private Integer pageNo;
        private Integer pageSize;
    }

    /** 数据源快照分页响应。 */
    @Data
    public static class DatasourcePageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<DatasourceRow> records = new ArrayList<>();
    }

    /** 数据源脱敏安全行，不包含账号、密码和业务表内容。 */
    @Data
    public static class DatasourceRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String dataSourceKey;
        private String groupName;
        private String role;
        private String poolName;
        private String databaseName;
        private String jdbcUrl;
        private String running;
        private String reachable;
        private Integer activeConnections;
        private Integer idleConnections;
        private Integer totalConnections;
        private Integer threadsAwaitingConnection;
        private Integer maximumPoolSize;
        private Integer minimumIdle;
        private String relatedShardingTables;
    }
}
