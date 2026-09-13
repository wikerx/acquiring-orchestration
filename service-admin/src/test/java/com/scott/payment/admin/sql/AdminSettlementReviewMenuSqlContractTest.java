package com.scott.payment.admin.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementReviewMenuSqlContractTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Admin 结算预审菜单迁移合同，保护路由、最小权限和默认角色授权边界。
 * @status : create
 */
class AdminSettlementReviewMenuSqlContractTest {

    private static final Set<String> MANAGEMENT_PERMISSIONS = Set.of(
            "settlement:transaction-candidate:list",
            "settlement:transaction-candidate:detail",
            "settlement:reserve-candidate:list",
            "settlement:reserve-candidate:detail",
            "settlement:review-order:list",
            "settlement:review-order:detail",
            "settlement:transaction-review:create",
            "settlement:reserve-review:create",
            "settlement:review-order:approve",
            "settlement:review-order:reject",
            "settlement:review-order:cancel",
            "settlement:review-order:recover",
            "settlement:review-order:export",
            "settlement:review-order:voucher-download",
            "settlement:transaction-batch:list",
            "settlement:reserve-batch:list",
            "settlement:batch:list",
            "settlement:batch:detail",
            "settlement:batch:retry",
            "settlement:batch:cancel",
            "settlement:batch:summary:list",
            "settlement:batch:summary:export",
            "settlement:batch:voucher-download",
            "settlement:reversal-order:list",
            "settlement:reversal-order:detail",
            "settlement:reversal-order:create",
            "settlement:reversal-order:approve",
            "settlement:reversal-order:reject",
            "reconciliation:record:detail",
            "settlement:result-item:transaction-detail",
            "settlement:result-item:list",
            "settlement:result-item:export",
            "settlement:reserve-item:transaction-detail",
            "settlement:reserve-item:list",
            "settlement:reserve-item:export",
            "settlement:posting:list",
            "settlement:posting:export",
            "settlement:profile:list",
            "settlement:profile:detail",
            "settlement:profile:update");

    private static final Set<String> PAGE_COMPONENT_PATHS = Set.of(
            "settlement/transaction-candidate",
            "settlement/reserve-candidate",
            "settlement/review-order",
            "settlement/reversal-order",
            "settlement/result-item",
            "settlement/reserve-item",
            "settlement/posting",
            "settlement/profile");

    @Test
    void migrationShouldMatchControllerRoutesAndGrantOnlySuperAdminByDefault() throws IOException {
        String migration = readRepositoryFile(
                "service-admin/src/main/resources/sql/settlement-review-management.sql");
        String controller = readRepositoryFile(
                "service-admin/src/main/java/com/scott/payment/admin/api/transaction/AdminSettlementReviewController.java");
        String reversalController = readRepositoryFile(
                "service-admin/src/main/java/com/scott/payment/admin/api/transaction/"
                        + "AdminSettlementReversalController.java");
        String reportingController = readRepositoryFile(
                "service-admin/src/main/java/com/scott/payment/admin/api/transaction/"
                        + "AdminSettlementReportingController.java");
        String batchController = readRepositoryFile(
                "service-admin/src/main/java/com/scott/payment/admin/api/transaction/"
                        + "AdminSettlementController.java");
        String profileController = readRepositoryFile(
                "service-admin/src/main/java/com/scott/payment/admin/api/transaction/"
                        + "AdminSettlementProfileController.java");
        String controllers = controller + reversalController + reportingController + batchController
                + profileController;

        assertThat(migration).contains(
                "START TRANSACTION", "COMMIT", "NOT EXISTS",
                "SET @admin_app_id", "app_code = 'ADMIN'",
                "SET parent_id = 0, menu_name = BINARY '结算管理'",
                "icon = 'CreditCard'",
                "'Tickets' icon", "'Lock'", "'Checked'",
                "'RefreshLeft'", "'DocumentCopy'", "'Key'", "'Document'", "'Setting'",
                "'admin:settlement:view', '结算管理查看'",
                "'settlement:transaction-settlement:view'",
                "'settlement:reserve-settlement:view'",
                "permission.resource_path = '/settlement'",
                "SET menu.parent_id = parent.id",
                "menu.menu_name = BINARY item.menu_name",
                "'admin_settlement_transaction_candidate_v1' menu_code, '交易结算' menu_name",
                "'admin_settlement_reserve_candidate_v1', '保证金结算'",
                "'admin_settlement_batch_v1', '结算批次兼容锚点', 'BUTTON'",
                "menu.route_path = NULL",
                "menu.component_path = NULL",
                "menu.menu_type = 'BUTTON'",
                "INSERT IGNORE INTO sys_role_menu", "INSERT IGNORE INTO sys_role_permission",
                "role.role_code = 'SUPER_ADMIN'",
                "Converge permissions created by earlier transaction-settlement menu migrations",
                "item.permission_code = permission.permission_code",
                "SET permission.menu_id = menu.id",
                "permission.permission_name = BINARY item.permission_name",
                "permission.description = BINARY item.description",
                "/admin/settlement/transaction-candidates/search",
                "/admin/settlement/transaction-candidates/*",
                "/admin/settlement/reserve-candidates/search",
                "/admin/settlement/reserve-candidates/*",
                "/admin/settlement/review-orders/search",
                "/admin/settlement/review-orders/export",
                "/admin/settlement/review-orders/*/voucher",
                "/admin/settlement/transaction-review-**",
                "/admin/settlement/reserve-review-**",
                "/admin/settlement/review-orders/*/approve*",
                "/admin/settlement/review-orders/*/reject*",
                "/admin/settlement/review-orders/*/cancel*",
                "/admin/settlement/review-decision-tasks/*/resume",
                "/admin/settlement/batches/transaction/search",
                "/admin/settlement/batches/reserve/search",
                "/admin/settlement/batches/search",
                "/admin/settlement/batches/*/retry",
                "/admin/settlement/batches/*/cancel",
                "/admin/settlement/batches/*/summaries",
                "/admin/settlement/batches/*/summaries/export",
                "/admin/settlement/reversal-orders/search",
                "/admin/settlement/reversal-orders/*/approve",
                "/admin/settlement/reversal-orders/*/reject",
                "/admin/settlement/reconciliation-records/transactions/*",
                "/admin/settlement/result-items/transactions/*",
                "/admin/settlement/result-items/search",
                "/admin/settlement/result-items/export",
                "/admin/settlement/reserve-items/transactions/*",
                "/admin/settlement/reserve-items/search",
                "/admin/settlement/reserve-items/export",
                "/admin/settlement/postings/search",
                "/admin/settlement/postings/export",
                "/admin/settlement/profiles/search",
                "/admin/settlement/profiles/*",
                "role.role_code = 'ADMIN_OPERATOR'",
                "'查看交易对账'",
                "'查看交易结算'",
                "'查看保证金结算'",
                "'下载结算预审单据'",
                "'查看正式批次结算汇总'",
                "'导出正式批次结算汇总'",
                "'admin_transaction_operation_v1', 6",
                "permission.permission_code = 'reconciliation:record:detail'",
                "permission.permission_code = 'settlement:batch:reverse'",
                "admin_transaction_settlement_v1",
                "Preserve existing batch users",
                "settlement:transaction-batch:list",
                "settlement:reserve-batch:list",
                "Retire the duplicate transaction-management entry",
                "SET deleted = id, status = 0");
        MANAGEMENT_PERMISSIONS.forEach(permission -> {
            assertThat(migration).contains(permission);
            assertThat(controllers)
                    .contains("@RequiresPermission(\"" + permission + "\")");
        });
        PAGE_COMPONENT_PATHS.forEach(componentPath -> assertThat(migration).contains(componentPath));
        assertThat(controllers).doesNotContain("@RequiresPermission(\"settlement:batch:reverse\")");
        assertThat(migration).doesNotContain(
                "正式结算批次权限",
                "'admin_settlement_batch_v1', '正式结算批次',",
                "'/settlement/batches', 'transaction/settlement'");
        assertThat(migration).doesNotContain("app_id = 1", "SELECT 1, parent.id", "SELECT 1, menu.id");
        assertThat(migration).doesNotContain(
                "WalletCards", "ListChecks", "ShieldCheck", "ClipboardCheck", "Landmark",
                "Undo2", "ReceiptText", "BookOpenCheck", "Settings2");
        assertThat(migration.toUpperCase()).doesNotContain(
                "DELETE FROM", "DROP TABLE", "TRUNCATE TABLE");
    }

    private String readRepositoryFile(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        Path path = Files.exists(direct) ? direct : Path.of("..").resolve(relativePath).normalize();
        return Files.readString(path);
    }
}
