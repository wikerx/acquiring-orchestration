package com.scott.payment.component.web.config;

import com.scott.payment.component.web.handler.GlobalExceptionHandler;
import com.scott.payment.component.web.handler.UnifiedErrorController;
import com.scott.payment.component.web.version.ApiVersionWebMvcRegistrations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ComponentWebAutoConfiguration
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : Web 组件自动装配配置
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ComponentWebAutoConfiguration
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Component Web Auto Configuration，位于 component-library/component-web 的配置层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Configuration
public class ComponentWebAutoConfiguration {

    /**
     * 注册 Fastjson2 MVC 配置。
     *
     * @return Fastjson2 MVC 配置
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Bean
    @ConditionalOnMissingBean
    public FastJsonWebMvcConfig fastJsonWebMvcConfig() {
        return new FastJsonWebMvcConfig();
    }

    /**
     * 注册全局异常处理器。
     *
     * @return 全局异常处理器
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /**
     * 注册统一兜底错误控制器。
     *
     * @return 统一错误控制器
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Bean
    @ConditionalOnMissingBean
    public UnifiedErrorController unifiedErrorController() {
        return new UnifiedErrorController();
    }

    /**
     * 注册 API 版本路由映射。
     *
     * @return Web MVC 注册器
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Bean
    @ConditionalOnMissingBean(WebMvcRegistrations.class)
    public ApiVersionWebMvcRegistrations apiVersionWebMvcRegistrations() {
        return new ApiVersionWebMvcRegistrations();
    }
}
