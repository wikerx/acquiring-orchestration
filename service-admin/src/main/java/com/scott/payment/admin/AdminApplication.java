package com.scott.payment.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminApplication
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Admin Application，位于 service-admin 的业务组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@SpringBootApplication(scanBasePackages = "com.scott.payment")
public class AdminApplication {

    /**
     * 启动后台管理服务。
     *
     * @param args 命令行参数
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param args 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
