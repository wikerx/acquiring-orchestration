package com.scott.payment.component.db.auth.event;

/**
 * 登录账号邮箱变更事件；仅携带账号定位和语言信息，由具体应用在事务提交后触发新的邮箱所有权验证。
 *
 * @param appCode 应用编码
 * @param accountId 账号主键
 * @param locale 邮件语言
 */
public record AccountEmailChangedEvent(String appCode, Long accountId, String locale) {
}
