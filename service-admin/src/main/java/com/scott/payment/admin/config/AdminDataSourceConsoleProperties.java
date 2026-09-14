package com.scott.payment.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminDataSourceConsoleProperties
 * @date : 2026-09-14 12:05
 * @email : scott_x@163.com
 * @description : 管理端数据源外部控制台配置，仅声明控制台开关和浏览器访问地址，不建立数据库连接或代理控制台请求。
 * @status : create
 */
@Data
@Component
@ConfigurationProperties(prefix = "acquiring.monitor.datasource-console")
public class AdminDataSourceConsoleProperties {

    /** 是否在数据源监控页展示外部 Druid 控制台入口；不允许为空，默认关闭且非敏感。 */
    private boolean enabled;

    /**
     * 外部 Druid 控制台完整 HTTP/HTTPS 地址；启用时不允许为空，允许内网、私网和回环地址，
     * 不允许携带用户信息或使用其他协议，属于受权限保护的基础设施地址。
     */
    private String url;
}
