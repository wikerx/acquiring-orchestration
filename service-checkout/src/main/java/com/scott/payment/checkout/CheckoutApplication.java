package com.scott.payment.checkout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CheckoutApplication
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : Checkout 服务启动类
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CheckoutApplication
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Checkout Application，位于 service-checkout 的业务组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@SpringBootApplication(scanBasePackages = "com.scott.payment")
public class CheckoutApplication {

    /**
     * 启动收银台服务。
     *
     * @param args JVM 启动参数
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param args 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    public static void main(String[] args) {
        SpringApplication.run(CheckoutApplication.class, args);
    }
}
