package com.scott.payment.admin.dto.exporttask;

import com.scott.payment.admin.dto.export.DataSourceMonitorExportRow;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** service-data 读取数据源脱敏运行快照的内部契约。 */
public final class AdminDatasourceExportInternalDTOs {
    private AdminDatasourceExportInternalDTOs() {
    }

    /** 数据源快照分页请求。 */
    @Data
    public static class PageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private String locale;
        private Integer pageNo;
        private Integer pageSize;
    }

    /** 数据源脱敏快照分页响应。 */
    @Data
    public static class PageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<DataSourceMonitorExportRow> records = new ArrayList<>();
    }
}
