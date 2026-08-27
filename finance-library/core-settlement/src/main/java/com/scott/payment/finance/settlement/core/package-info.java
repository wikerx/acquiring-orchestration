/**
 * 结算纯计算内核，负责批次汇率归一、原币种换汇和跨币种费用限额求值。
 * 本包不读取费用配置或基础设施，不生成结算批次，也不执行资金账户入账。
 */
package com.scott.payment.finance.settlement.core;
