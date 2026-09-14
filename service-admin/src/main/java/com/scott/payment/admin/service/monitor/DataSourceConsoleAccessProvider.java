package com.scott.payment.admin.service.monitor;

import com.scott.payment.admin.config.AdminDataSourceConsoleProperties;
import com.scott.payment.admin.dto.monitor.DataSourceMonitorResponse.ConsoleAccess;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataSourceConsoleAccessProvider
 * @date : 2026-09-14 12:05
 * @email : scott_x@163.com
 * @description : 解析管理端 Druid 外部控制台入口并汇总本地连接池类型；不探测远程地址，也不把控制台认证信息写入响应。
 * @status : create
 */
@Component
public class DataSourceConsoleAccessProvider {

    /** 控制台入口已配置，可由前端在新窗口打开。 */
    public static final String STATUS_CONFIGURED = "CONFIGURED";

    /** 控制台入口未启用或未填写地址。 */
    public static final String STATUS_NOT_CONFIGURED = "NOT_CONFIGURED";

    /** 控制台地址格式无效，需要运维修正配置。 */
    public static final String STATUS_MISCONFIGURED = "MISCONFIGURED";

    /** 外部数据源控制台提供方编码；只用于前后端展示契约，不代表当前 JVM 实际使用 Druid。 */
    private static final String PROVIDER_DRUID = "DRUID";

    /** 数据源控制台配置。 */
    private final AdminDataSourceConsoleProperties properties;

    /** 标准 JDBC Wrapper 连接池解析器，用于透过动态数据源代理识别实际 Hikari 连接池。 */
    private final DataSourcePoolInspector dataSourcePoolInspector;

    /**
     * 创建数据源控制台入口解析器。
     *
     * @param properties 数据源控制台配置
     * @param dataSourcePoolInspector 标准 JDBC Wrapper 连接池解析器
     */
    public DataSourceConsoleAccessProvider(AdminDataSourceConsoleProperties properties,
                                           DataSourcePoolInspector dataSourcePoolInspector) {
        this.properties = properties;
        this.dataSourcePoolInspector = dataSourcePoolInspector;
    }

    /**
     * 返回数据源监控页需要的控制台入口和本地连接池类型。
     *
     * <p>Druid 控制台只统计目标 JVM 内注册的 DruidDataSource；本方法展示的本地池类型用于提醒管理员，
     * 不用于阻止访问另一个服务实例上的 Druid 控制台。</p>
     *
     * @param dataSources 当前 Admin JVM 已注册的物理数据源
     * @return 不包含控制台账号密码的数据源控制台访问摘要
     */
    public ConsoleAccess snapshot(Collection<DataSource> dataSources) {
        ConsoleAccess access = new ConsoleAccess();
        access.setProvider(PROVIDER_DRUID);
        access.setLocalPoolTypes(resolvePoolTypes(dataSources));
        if (!properties.isEnabled()) {
            access.setStatus(STATUS_NOT_CONFIGURED);
            access.setReason("未启用 Druid 外部控制台；当前页面继续展示本地连接池和 SQL 指标");
            return access;
        }
        String configuredUrl = trimToNull(properties.getUrl());
        if (configuredUrl == null) {
            access.setStatus(STATUS_NOT_CONFIGURED);
            access.setReason("已启用 Druid 外部控制台，但未配置完整访问地址");
            return access;
        }
        String normalizedUrl = normalizeHttpUrl(configuredUrl);
        if (normalizedUrl == null) {
            access.setStatus(STATUS_MISCONFIGURED);
            access.setReason("Druid 控制台地址无效，仅支持不含账号密码的 HTTP/HTTPS 完整地址");
            return access;
        }
        access.setStatus(STATUS_CONFIGURED);
        access.setUrl(normalizedUrl);
        access.setReason("已配置外部 Druid 控制台；其数据仅代表目标地址所在 JVM 中的 DruidDataSource");
        return access;
    }

    /**
     * 汇总本地物理连接池实现类，避免把 Druid 控制台误解为 JDBC 或全平台默认监控。
     *
     * @param dataSources 当前 Admin JVM 已注册的物理数据源
     * @return 去重并排序后的连接池简短类名
     */
    private List<String> resolvePoolTypes(Collection<DataSource> dataSources) {
        if (dataSources == null || dataSources.isEmpty()) {
            return List.of();
        }
        return dataSources.stream()
                .filter(dataSource -> dataSource != null)
                .map(this::resolvePoolType)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /**
     * 优先返回代理背后的 Hikari 连接池类型，无法解包时保留当前数据源实现类型。
     *
     * @param dataSource 运行时数据源或代理，不允许为空
     * @return 面向运维展示的连接池或数据源简短类型名
     */
    private String resolvePoolType(DataSource dataSource) {
        HikariDataSource hikariDataSource = dataSourcePoolInspector.unwrapHikari(dataSource);
        return hikariDataSource == null
                ? dataSource.getClass().getSimpleName()
                : HikariDataSource.class.getSimpleName();
    }

    /**
     * 校验并规范化浏览器直达控制台地址；显式允许开发、测试和生产环境使用 HTTP、私网或回环主机。
     *
     * @param value 原始配置地址
     * @return 规范化地址；协议、主机或用户信息非法时返回 {@code null}
     */
    private String normalizeHttpUrl(String value) {
        if (value.contains("\\")) {
            return null;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme))
                    || !StringUtils.hasText(uri.getHost())
                    || StringUtils.hasText(uri.getUserInfo())) {
                return null;
            }
            return uri.normalize().toASCIIString();
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    /**
     * 去除配置值首尾空白，并把空字符串统一为 {@code null}。
     *
     * @param value 原始配置值
     * @return 规范化字符串或 {@code null}
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
