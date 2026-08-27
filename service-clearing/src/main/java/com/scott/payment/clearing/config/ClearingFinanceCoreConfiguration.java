package com.scott.payment.clearing.config;

import com.scott.payment.finance.reserve.core.ReserveCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 清分服务对纯领域计算组件的装配边界。
 *
 * <p>领域库保持无 Spring 依赖；清分服务启动时无条件注册无状态计算器，供保证金计算、调整和释放流程复用。</p>
 */
@Configuration(proxyBeanMethods = false)
public class ClearingFinanceCoreConfiguration {

    /** 注册无状态保证金计算器，不依赖 yml 或 Nacos 开关。 */
    @Bean
    public ReserveCalculator reserveCalculator() {
        return new ReserveCalculator();
    }
}
