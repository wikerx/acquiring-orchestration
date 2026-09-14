package com.scott.payment.admin.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMonitorWorkbenchSchemaContractTest
 * @date : 2026-09-14 12:30
 * @email : scott_x@163.com
 * @description : 系统监控菜单层级、权限和告警处理表结构 SQL 契约测试。
 * @status : create
 */
class AdminMonitorWorkbenchSchemaContractTest {

    @Test
    void shouldKeepMonitorMenusTwoLevelsAndInRequiredOrder() throws IOException {
        String schema = Files.readString(modulePath("src/main/resources/sql/admin-system-schema.sql"));

        List<String> orderedMenus = List.of(
                "'monitor_overview', '监控总览'",
                "'monitor_online', '在线用户'",
                "'monitor_server', '服务监控'",
                "'monitor_api', 'API监控'",
                "'monitor_trace', '交易链路'",
                "'monitor_channel_health', '渠道健康监控'",
                "'monitor_webhook', 'Webhook监控'",
                "'monitor_cache', '缓存监控'",
                "'monitor_datasource', '数据源监控'",
                "'monitor_druid_console', 'Druid 控制台'",
                "'monitor_job', '任务调度'",
                "'monitor_job_log', '任务日志'",
                "'monitor_job_node', '执行节点'",
                "'monitor_log_search', '日志检索'",
                "'monitor_alert', '告警中心'",
                "'security_intercept_event_v1', '安全拦截事件'",
                "'monitor_rocketmq', 'RocketMQ 控制台'",
                "'monitor_nacos', 'Nacos 控制台'"
        );

        int previous = -1;
        for (String menu : orderedMenus) {
            int current = schema.indexOf(menu, previous + 1);
            assertThat(current).as("menu %s should exist after the previous item", menu).isGreaterThan(previous);
            previous = current;
        }

        assertThat(schema).contains(
                "'system_monitor', 'monitor_overview'",
                "'system_monitor', 'security_intercept_event_v1'",
                "'system_monitor', 'monitor_druid_console', 'Druid 控制台', 'MENU', '/monitor/druid', 'monitor/druid/index', 'monitor:datasource:view'",
                "datasource_menu.menu_code = 'monitor_datasource'",
                "druid_menu.menu_code = 'monitor_druid_console'",
                "'monitor_sharding', '分表管理', 'CATALOG', '/monitor/sharding'"
        );
        assertThat(schema).doesNotContain("'system_monitor', 'monitor_sharding'");
    }

    @Test
    void shouldCreateAlertStateTablesAndWorkbenchPermissions() throws IOException {
        String schema = Files.readString(modulePath("src/main/resources/sql/admin-system-schema.sql"));

        assertThat(schema).contains(
                "CREATE TABLE IF NOT EXISTS monitor_alert_handle_state",
                "UNIQUE KEY uk_monitor_alert_handle_source (source_type, source_id)",
                "CREATE TABLE IF NOT EXISTS monitor_alert_handle_history",
                "KEY idx_monitor_alert_history_source_time (source_type, source_id, operated_at)",
                "'system:monitor:overview:query'",
                "'system:online:forceLogout'",
                "'system:monitor:service:query'",
                "'system:monitor:api:query'",
                "'system:monitor:trace:query'",
                "'system:monitor:channel:query'",
                "'system:monitor:webhook:query'",
                "'system:monitor:log:query'",
                "'system:monitor:alert:query'",
                "'system:monitor:alert:handle'",
                "'system:monitor:security:query'",
                "'system:monitor:service:query', '服务运行指标查询', 'API', 'GET', '/admin/monitor/workbench/services'",
                "'system:online:forceLogout', '在线用户强制下线', 'BUTTON', 'DELETE', '/admin/monitor/online/*'",
                "'system:monitor:alert:query', '告警中心查询', 'API', 'POST', '/admin/monitor/workbench/alerts/search'",
                "'system:monitor:alert:handle', '告警处置', 'API', 'PUT', '/admin/monitor/workbench/alerts/*/*/**'"
        );
        assertThat(schema).doesNotContain(
                "'/admin/monitor/workbench/services*'",
                "'/admin/monitor/workbench/alerts*'"
        );
    }

    private Path modulePath(String relativePath) {
        Path direct = Path.of(relativePath);
        return Files.exists(direct) ? direct : Path.of("service-admin").resolve(relativePath);
    }
}
