package com.scott.payment.admin.application.risk;

import com.scott.payment.admin.api.risk.AdminRiskImportExceptionHandler;
import com.scott.payment.admin.api.risk.AdminRiskImportReportController;
import com.scott.payment.admin.mapper.RiskManagementMapper;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * 导入回滚反馈、原创建账号隔离及完整错误报告的行为回归。
 */
class AdminRiskImportReportApplicationServiceTests {
    private static final String BATCH = "RISK-BLACK-email-qa0123456789";
    private final RiskManagementMapper mapper = mock(RiskManagementMapper.class);
    private final AdminRiskImportReportApplicationService service = new AdminRiskImportReportApplicationService(mapper);

    /**
     * 每次验证后清理可信认证上下文。
     */
    @AfterEach
    void clearAuth() { InternalAuthContextHolder.clear(); }

    /**
     * 回滚响应保持业务失败码、成功数零，并只携带五条摘要和实际文件行数。
     */
    @Test
    void returnsStructuredRollbackWithoutPretendingPartialSuccess() {
        var errors = IntStream.rangeClosed(2, 8)
                .mapToObj(row -> new AdminRiskImportLogService.ImportRowError(row, "***", "invalid"))
                .toList();
        var response = new AdminRiskImportExceptionHandler().rejected(
                new RiskImportRejectedException(BATCH, 12, errors, "import rejected"));
        assertThat(response.getCode()).isNotEqualTo("T200");
        assertThat(response.getData().isRolledBack()).isTrue();
        assertThat(response.getData().getSuccessCount()).isZero();
        assertThat(response.getData().getFailureCount()).isEqualTo(7);
        assertThat(response.getData().getTotalCount()).isEqualTo(12);
        assertThat(response.getData().getErrors()).hasSize(5).allMatch(message -> !message.contains("***"));
    }

    /**
     * 原账号复用具体功能导入权限，报告只包含行号和错误说明。
     */
    @Test
    void streamsEveryErrorPageWithoutRawInputAndWithCsvProtection() throws Exception {
        authenticate(7L, List.of("risk:blacklist:email:import"));
        when(mapper.selectImportFailureBatch(BATCH)).thenReturn(batch("account:7"));
        when(mapper.selectImportFailureRows(BATCH, 0, 0))
                .thenReturn(IntStream.rangeClosed(1, 500).mapToObj(i -> row(i, "=formula," + i)).toList());
        when(mapper.selectImportFailureRows(BATCH, 500, 500))
                .thenReturn(List.of(row(501, "invalid qa@example.invalid 1234567890123456")));
        var response = new MockHttpServletResponse();
        service.download(BATCH, response);
        String csv = response.getContentAsString();
        assertThat(csv.lines()).hasSize(502);
        assertThat(csv).contains("501,\"line 501: invalid *** ***\"")
                .doesNotContain("qa@example.invalid", "1234567890123456", "raw_content");
        assertThat(csv).contains("1,\"line 1: =formula,1\"");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    /**
     * 经真实 MVC 参数解析器绑定批次号，避免打包后缺少参数名元数据导致下载失败。
     */
    @Test
    void bindsBatchNumberThroughMvcBeforeStreamingReport() throws Exception {
        authenticate(7L, List.of("risk:blacklist:email:import"));
        when(mapper.selectImportFailureBatch(BATCH)).thenReturn(batch("account:7"));
        when(mapper.selectImportFailureRows(BATCH, 0, 0)).thenReturn(List.of(row(3, "invalid")));
        var mvc = standaloneSetup(new AdminRiskImportReportController(service)).build();
        var response = mvc.perform(get("/admin/risk/imports/{batchNo}/failures", BATCH))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getContentAsString()).contains("3,\"line 3: invalid\"");
        assertThat(response.getHeader("Content-Disposition")).contains(BATCH + "-failures.csv");
        verify(mapper).selectImportFailureBatch(BATCH);
    }

    /**
     * 猜测其他账号批次不能获取文件，即使拥有全局功能权限。
     */
    @Test
    void rejectsOtherAccountBeforeReadingRowsOrWritingHeaders() {
        authenticate(8L, List.of("*:*:*"));
        when(mapper.selectImportFailureBatch(BATCH)).thenReturn(batch("account:7"));
        var response = new MockHttpServletResponse();
        assertThatThrownBy(() -> service.download(BATCH, response)).isInstanceOf(ServiceException.class);
        verify(mapper, never()).selectImportFailureRows(anyString(), anyInt(), anyLong());
        assertThat(response.getHeader("Content-Disposition")).isNull();
    }

    /**
     * 只具备其他风控功能导入权限时，不能下载本功能的失败文件。
     */
    @Test
    void requiresMatchingFunctionPermission() {
        authenticate(7L, List.of("risk:whitelist:email:import"));
        when(mapper.selectImportFailureBatch(BATCH)).thenReturn(batch("account:7"));
        assertThatThrownBy(() -> service.download(BATCH, new MockHttpServletResponse())).isInstanceOf(ServiceException.class);
        verify(mapper, never()).selectImportFailureRows(anyString(), anyInt(), anyLong());
    }

    /**
     * 旧批次的显示名称不是稳定身份，不能因为同名获得下载权限。
     */
    @Test
    void rejectsLegacyDisplayNameOwnership() {
        authenticate(7L, List.of("*:*:*"));
        when(mapper.selectImportFailureBatch(BATCH)).thenReturn(batch("QA"));
        assertThatThrownBy(() -> service.download(BATCH, new MockHttpServletResponse())).isInstanceOf(ServiceException.class);
    }

    /**
     * 未登录请求在访问批次前即被拒绝。
     */
    @Test
    void rejectsUnauthenticatedDownload() {
        assertThatThrownBy(() -> service.download(BATCH, new MockHttpServletResponse())).isInstanceOf(ServiceException.class);
        verifyNoInteractions(mapper);
    }

    /**
     * 构造不含个人数据的账号上下文。
     */
    private void authenticate(long id, List<String> permissions) {
        var account = new InternalAuthAccount();
        account.setAccountId(id);
        account.setRealName("QA");
        account.setPermissions(permissions);
        InternalAuthContextHolder.set(account);
    }

    /**
     * 返回数据库实际列名契约对应的失败批次。
     */
    private Map<String, Object> batch(String owner) {
        return Map.of("module_type", "BLACK", "function_code", "email", "status", 3, "operator", owner);
    }

    /**
     * 构造顺序稳定且只包含允许输出列的错误行。
     */
    private Map<String, Object> row(int id, String message) {
        return Map.of("id", id, "row_no", id, "error_message", message);
    }
}
