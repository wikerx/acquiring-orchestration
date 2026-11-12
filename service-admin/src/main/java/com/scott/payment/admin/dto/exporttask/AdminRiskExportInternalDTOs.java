package com.scott.payment.admin.dto.exporttask;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** service-data 分页读取风控配置安全投影的内部契约。 */
public final class AdminRiskExportInternalDTOs {
    private AdminRiskExportInternalDTOs() {
    }

    /** 风控配置导出来源分页请求。 */
    @Data
    public static class PageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private String locale;
        private String moduleType;
        private String functionCode;
        private RiskConfigQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /** 风控配置筛选条件快照。 */
    @Data
    public static class RiskConfigQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantScope;
        private String merchantId;
        private String ruleName;
        private String matchValue;
        private String countryAlpha2;
        private String sourceUrl;
        private String sourceHost;
        private String limitType;
        private String ruleType;
        private String channelCode;
        private String paymentMethod;
        private String cardBrand;
        private String currency;
        private String triggerAction;
        private Integer status;
        private Integer approvalStatus;
        private String submitSource;
    }

    /** 风控配置动态列定义。 */
    @Data
    public static class DynamicColumn implements Serializable {
        private static final long serialVersionUID = 1L;
        private String key;
        private String header;
        private int width;
        private String align;
    }

    /** 风控配置动态列分页响应；行只包含脱敏后的导出字段。 */
    @Data
    public static class PageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private String title;
        private List<DynamicColumn> columns = new ArrayList<>();
        private List<Map<String, Object>> records = new ArrayList<>();
    }
}
