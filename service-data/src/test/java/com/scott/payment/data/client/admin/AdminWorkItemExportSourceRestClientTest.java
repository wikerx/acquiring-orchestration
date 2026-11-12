package com.scott.payment.data.client.admin;

import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundAccountPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundDeductionPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundLedgerPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundRechargePageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.BusinessRatePageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.RawRatePageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.UsageSnapshotPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeePlanPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeeSimulationPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DepartmentPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DictDataPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DictTypePageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.HolidayCalendarPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.PostPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SystemUserPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SystemConfigPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.MerchantIpWhitelistPageRequest;
import com.scott.payment.data.config.export.DataAdminClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Admin 导出来源 REST 客户端契约测试，验证精确路径和内部签名头不会漂移。
 */
class AdminWorkItemExportSourceRestClientTest {

    private static final String BASE_URL = "http://127.0.0.1:18080";
    private static final String EMPTY_PAGE = """
            {"code":"T200","message":"Success","data":{"total":0,"pageNo":1,"pageSize":100,"records":[]}}
            """;

    /**
     * 四类资金导出必须调用各自的精确白名单路径，并携带完整 HMAC 身份头。
     */
    @Test
    void shouldCallFundExportSourcePathsWithInternalSignature() {
        RestTemplate direct = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(direct).build();
        DataAdminClientProperties properties = properties();
        AdminWorkItemExportSourceRestClient client = new AdminWorkItemExportSourceRestClient(
                direct, new RestTemplate(), properties);
        List<String> paths = List.of(
                "/internal/admin/fund/accounts/export-page",
                "/internal/admin/fund/ledgers/export-page",
                "/internal/admin/fund/recharges/export-page",
                "/internal/admin/fund/deductions/export-page");
        paths.forEach(path -> expectSignedPost(server, path));

        assertThat(client.loadFundAccountPage(new FundAccountPageRequest()).getRecords()).isEmpty();
        assertThat(client.loadFundLedgerPage(new FundLedgerPageRequest()).getRecords()).isEmpty();
        assertThat(client.loadFundRechargePage(new FundRechargePageRequest()).getRecords()).isEmpty();
        assertThat(client.loadFundDeductionPage(new FundDeductionPageRequest()).getRecords()).isEmpty();

        server.verify();
    }

    /**
     * 三类汇率事实导出必须调用各自的精确白名单路径，并携带完整 HMAC 身份头。
     */
    @Test
    void shouldCallExchangeExportSourcePathsWithInternalSignature() {
        RestTemplate direct = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(direct).build();
        AdminWorkItemExportSourceRestClient client = new AdminWorkItemExportSourceRestClient(
                direct, new RestTemplate(), properties());
        List<String> paths = List.of(
                "/internal/admin/exchange/raw-rates/export-page",
                "/internal/admin/exchange/business-rates/export-page",
                "/internal/admin/exchange/usage-snapshots/export-page");
        paths.forEach(path -> expectSignedPost(server, path));

        assertThat(client.loadRawRatePage(new RawRatePageRequest()).getRecords()).isEmpty();
        assertThat(client.loadBusinessRatePage(new BusinessRatePageRequest()).getRecords()).isEmpty();
        assertThat(client.loadUsageSnapshotPage(new UsageSnapshotPageRequest()).getRecords()).isEmpty();

        server.verify();
    }

    /**
     * 组织、字典与日历导出必须调用五条精确白名单路径，并携带 Data-Admin 内部签名。
     */
    @Test
    void shouldCallSystemReferenceExportSourcePathsWithInternalSignature() {
        RestTemplate direct = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(direct).build();
        AdminWorkItemExportSourceRestClient client = new AdminWorkItemExportSourceRestClient(
                direct, new RestTemplate(), properties());
        List<String> paths = List.of(
                "/internal/admin/system/departments/export-page",
                "/internal/admin/system/posts/export-page",
                "/internal/admin/system/dict-types/export-page",
                "/internal/admin/system/dict-data/export-page",
                "/internal/admin/system/holiday-calendar/export-page");
        paths.forEach(path -> expectSignedPost(server, path));

        assertThat(client.loadDepartmentPage(new DepartmentPageRequest()).getRecords()).isEmpty();
        assertThat(client.loadPostPage(new PostPageRequest()).getRecords()).isEmpty();
        assertThat(client.loadDictTypePage(new DictTypePageRequest()).getRecords()).isEmpty();
        assertThat(client.loadDictDataPage(new DictDataPageRequest()).getRecords()).isEmpty();
        assertThat(client.loadHolidayCalendarPage(new HolidayCalendarPageRequest()).getRecords()).isEmpty();

        server.verify();
    }

    /**
     * 四类费用配置与试算导出必须调用精确白名单路径，并携带 Data-Admin 内部签名。
     */
    @Test
    void shouldCallFeeExportSourcePathsWithInternalSignature() {
        RestTemplate direct = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(direct).build();
        AdminWorkItemExportSourceRestClient client = new AdminWorkItemExportSourceRestClient(
                direct, new RestTemplate(), properties());
        List<String> paths = List.of(
                "/internal/admin/fees/templates/export-page",
                "/internal/admin/fees/merchants/export-page",
                "/internal/admin/fees/reviews/export-page",
                "/internal/admin/fees/simulation-records/export-page");
        paths.forEach(path -> expectSignedPost(server, path));

        assertThat(client.loadFeeTemplatePage(new FeePlanPageRequest()).getRecords()).isEmpty();
        assertThat(client.loadMerchantFeePage(new FeePlanPageRequest()).getRecords()).isEmpty();
        assertThat(client.loadFeeReviewPage(new FeePlanPageRequest()).getRecords()).isEmpty();
        assertThat(client.loadFeeSimulationPage(new FeeSimulationPageRequest()).getRecords()).isEmpty();

        server.verify();
    }

    /** 系统账户、参数与商户访问控制导出必须调用三条精确签名路径。 */
    @Test
    void shouldCallSystemAndMerchantAccessExportSourcePathsWithInternalSignature() {
        RestTemplate direct = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(direct).build();
        AdminWorkItemExportSourceRestClient client = new AdminWorkItemExportSourceRestClient(
                direct, new RestTemplate(), properties());
        List<String> paths = List.of(
                "/internal/admin/system/users/export-page",
                "/internal/admin/system/configs/export-page",
                "/internal/admin/merchant/ip-whitelists/export-page");
        paths.forEach(path -> expectSignedPost(server, path));

        assertThat(client.loadSystemUserPage(new SystemUserPageRequest()).getRecords()).isEmpty();
        assertThat(client.loadSystemConfigPage(new SystemConfigPageRequest()).getRecords()).isEmpty();
        assertThat(client.loadMerchantIpWhitelistPage(new MerchantIpWhitelistPageRequest()).getRecords()).isEmpty();

        server.verify();
    }

    /**
     * 注册精确路径、方法、调用方和动态签名头的请求预期。
     */
    private void expectSignedPost(MockRestServiceServer server, String path) {
        server.expect(once(), requestTo(BASE_URL + path))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(InternalServiceSignature.HEADER_CALLER, "service-data"))
                .andExpect(request -> {
                    assertThat(request.getHeaders().getFirst(InternalServiceSignature.HEADER_TIMESTAMP))
                            .isNotBlank();
                    assertThat(request.getHeaders().getFirst(InternalServiceSignature.HEADER_NONCE))
                            .isNotBlank();
                    assertThat(request.getHeaders().getFirst(InternalServiceSignature.HEADER_SIGNATURE))
                            .isNotBlank();
                })
                .andRespond(withSuccess(EMPTY_PAGE, MediaType.APPLICATION_JSON));
    }

    /**
     * 创建满足内部调用身份和超时约束的测试配置。
     */
    private DataAdminClientProperties properties() {
        DataAdminClientProperties properties = new DataAdminClientProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setInternalCaller("service-data");
        properties.setInternalSecret("unit-test-data-admin-secret-at-least-32-bytes");
        return properties;
    }
}
