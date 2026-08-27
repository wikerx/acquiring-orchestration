/**
 * 费用领域纯计算内核。
 *
 * <p>本包负责标准费用、阶梯费用、原费用返还和 Admin 费用换汇预览。所有输入必须由调用方显式提供，
 * 计算器无状态、无副作用，不负责选择商户费用版本，也不持久化清分明细。</p>
 *
 * <p>禁止依赖 Spring、数据库、Redis、RocketMQ、Nacos 或真实结算汇率服务。Admin 预览结果不得用于资金入账。</p>
 */
package com.scott.payment.finance.fee.core;
