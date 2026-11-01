package com.scott.payment.risk.config;

import com.scott.payment.risk.mapper.RiskSchemaReadinessMapper;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 风控服务启动门禁，在接收交易前验证实时规则和审计所需数据库对象完整；缺失时失败关闭并提示迁移文件。
 */
@Component
public class RiskSchemaReadinessVerifier implements SmartInitializingSingleton {

    /**
     * 需要由运维评审并执行的数据库迁移草案。
     */
    static final String MIGRATION_FILE = "docs/sql/20261017_02_risk_merchant_function_config_migration_draft.sql";

    /**
     * 按表声明运行时强依赖列，使用稳定顺序生成可读启动错误。
     */
    private static final Map<String, Set<String>> REQUIRED_COLUMNS = requiredColumns();

    private final RiskSchemaReadinessMapper readinessMapper;

    /**
     * 创建数据库契约启动门禁。
     *
     * @param readinessMapper information_schema 只读查询 Mapper
     */
    public RiskSchemaReadinessVerifier(RiskSchemaReadinessMapper readinessMapper) {
        this.readinessMapper = readinessMapper;
    }

    /**
     * 所有单例创建完成后校验数据库契约；任何查询异常或对象缺失均阻止服务启动。
     */
    @Override
    public void afterSingletonsInstantiated() {
        List<String> present;
        try {
            present = readinessMapper.selectPresentRequiredColumns();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Risk schema readiness check failed; review and apply "
                    + MIGRATION_FILE, exception);
        }
        List<String> missing = missingObjects(present == null ? Set.of() : new LinkedHashSet<>(present));
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Risk schema is not ready; missing " + String.join(", ", missing)
                    + "; review and apply " + MIGRATION_FILE);
        }
    }

    /**
     * 按稳定表列清单计算启动前缺失的数据库对象。
     *
     * @param present information_schema 已确认存在的表列标识
     * @return 按声明顺序排列的缺表和缺列说明
     */
    private List<String> missingObjects(Set<String> present) {
        List<String> missing = new ArrayList<>();
        REQUIRED_COLUMNS.forEach((table, columns) -> {
            if (!present.contains("table:" + table)) {
                missing.add("table " + table);
                return;
            }
            columns.stream()
                    .filter(column -> !present.contains("column:" + table + "." + column))
                    .map(column -> "column " + table + "." + column)
                    .forEach(missing::add);
        });
        return missing;
    }

    /**
     * 声明风控运行时启动所需的最小数据库契约。
     *
     * @return 保持稳定顺序的表列清单
     */
    private static Map<String, Set<String>> requiredColumns() {
        Map<String, Set<String>> required = new LinkedHashMap<>();
        required.put("risk_merchant_function_config",
                Set.of("merchant_id", "module_type", "function_code", "enabled", "deleted"));
        required.put("risk_evaluation_record", Set.of("decision_reason_code"));
        required.put("risk_evaluation_hit_detail", Set.of("reason_code"));
        return required;
    }
}
