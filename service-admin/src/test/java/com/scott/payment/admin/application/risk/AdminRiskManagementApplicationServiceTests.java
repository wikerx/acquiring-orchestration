package com.scott.payment.admin.application.risk;

import com.scott.payment.admin.application.risk.cache.RiskRuleCacheInvalidationCoordinator;
import com.scott.payment.admin.dto.risk.RiskDTOs;
import com.scott.payment.admin.mapper.RiskManagementMapper;
import com.scott.payment.admin.support.risk.RiskListValueNormalizer;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 管理端风控评估记录查询测试。
 */
class AdminRiskManagementApplicationServiceTests {

    @Test
    void shouldDisplayZeroHitsForHistoricalNonPassEvaluations() {
        RiskManagementMapper mapper = mock(RiskManagementMapper.class);
        Map<String, Object> rejected = evaluation("REJECT", 7);
        Map<String, Object> passed = evaluation("PASS", 3);
        when(mapper.countEvaluations(isNull(), isNull(), isNull(), isNull())).thenReturn(2L);
        when(mapper.selectEvaluations(isNull(), isNull(), isNull(), isNull(), anyLong(), anyLong()))
                .thenReturn(List.of(rejected, passed));
        AdminRiskManagementApplicationService service = new AdminRiskManagementApplicationService(
                mapper,
                mock(RiskListValueNormalizer.class),
                mock(AdminRiskImportLogService.class),
                mock(RiskRuleCacheInvalidationCoordinator.class),
                mock(ExcelExportService.class),
                mock(ExcelI18nMessageResolver.class),
                mock(ExcelLocaleResolver.class)
        );

        List<Map<String, Object>> records = service.pageEvaluations(new RiskDTOs.EvaluationQueryRequest())
                .getRecords();

        assertThat(records).hasSize(2);
        assertThat(records.get(0)).containsEntry("hit_count", 0);
        assertThat(records.get(1)).containsEntry("hit_count", 3);
    }

    private Map<String, Object> evaluation(String decision, int hitCount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("decision_result", decision);
        row.put("hit_count", hitCount);
        return row;
    }
}
