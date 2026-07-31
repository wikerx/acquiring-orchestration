package com.scott.payment.risk.config;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskFrequencyMode
 * @date : 2026-07-30 23:05
 * @email : scott_x@163.com
 * @description : 交易频率窗口迁移模式，与累计金额计数模式独立配置，避免两个业务语义被同一开关同时切换
 * @status : create
 */
public enum RiskFrequencyMode {

    /**
     * 使用历史双 String 固定窗口，保持当前生产决策语义。
     */
    LEGACY,

    /**
     * 历史固定窗口参与决策，同时写入单 ZSet 滑动窗口并统计差异。
     */
    SHADOW,

    /**
     * 使用单 ZSet 滑动窗口参与真实风控决策。
     */
    SLIDING_WINDOW
}
