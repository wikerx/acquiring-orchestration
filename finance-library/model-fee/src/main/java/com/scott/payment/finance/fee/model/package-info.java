/**
 * 费用领域不可变契约。
 *
 * <p>本包定义费用规则快照、阶梯规则、费用组件、退款返费和 Admin 费用换汇预览的命令及结果。
 * 百分比费用事实使用标签币种，固定费和上下限使用 USD；跨币种限额只能标记为等待结算汇率，不能在清分阶段猜测汇率。</p>
 *
 * <p>禁止放入计算器实现、费用版本查询、Mapper、DO、Redis、RocketMQ 和真实结算批次模型。</p>
 */
package com.scott.payment.finance.fee.model;
