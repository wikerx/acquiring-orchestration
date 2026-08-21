package com.scott.payment.payment.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionQueryCommandDTO;
import com.scott.payment.payment.domain.state.PaymentCheckoutSessionStatusEnum;
import com.scott.payment.payment.service.impl.DefaultPaymentCheckoutService;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCheckoutDeadlineContractTests
 * @date : 2026-08-20 20:10
 * @email : scott_x@163.com
 * @description : 校验收银台四状态、24 小时支付提交截止、超时扫描范围及访问令牌长期查询契约
 * @status : create
 */
@Slf4j
class PaymentCheckoutDeadlineContractTests {

    /** 收银台数据库业务状态只能复用平台支付的四种状态。 */
    @Test
    void checkoutSessionShouldExposeOnlyFourPaymentStatuses() {
        log.info("用例开始：校验收银台数据库业务状态仅包含四种平台状态");
        assertThat(PaymentCheckoutSessionStatusEnum.values())
                .extracting(PaymentCheckoutSessionStatusEnum::getCode)
                .containsExactly("PENDING", "PROCESSING", "SUCCESS", "FAILED");
        log.info("用例结果：收银台业务状态已收敛为 PENDING、PROCESSING、SUCCESS、FAILED");
    }

    /** 首次提交和失败重试都必须在服务端付款截止时间之前完成 CAS。 */
    @Test
    void paymentSubmissionCasShouldEnforceServerDeadline() throws NoSuchMethodException {
        log.info("用例开始：校验首次提交和失败重试的 CAS SQL 均绑定服务端付款截止时间");
        Method method = PaymentCheckoutSessionMapper.class.getMethod(
                "markSubmittedCas",
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                LocalDateTime.class,
                String.class,
                Integer.class,
                LocalDateTime.class);

        String sql = updateSql(method);

        assertThat(sql)
                .contains("checkout_status IN ('PENDING', 'FAILED')")
                .contains("expire_time > #{now}")
                .contains("last_submit_time = #{now}");
        log.info("用例结果：支付提交 SQL 同时校验可提交状态、截止时间并记录最后提交时间");
    }

    /** 超时任务只能关闭从未提交支付且仍在等待付款人的订单。 */
    @Test
    void expirationScanShouldExcludeSubmittedAndProcessingSessions() throws NoSuchMethodException {
        log.info("用例开始：校验超时扫描只处理从未提交且仍等待付款的订单");
        Method selectMethod = PaymentCheckoutSessionMapper.class.getMethod(
                "selectExpireDue", LocalDateTime.class, int.class);
        Method updateMethod = PaymentCheckoutSessionMapper.class.getMethod(
                "markPaymentTimeoutCas", String.class, String.class, Integer.class, LocalDateTime.class);

        assertThat(selectSql(selectMethod))
                .contains("checkout_status = 'PENDING'")
                .contains("process_stage = 'WAITING_PAYER'")
                .contains("last_submit_time IS NULL")
                .contains("expire_time <= #{now}");
        assertThat(updateSql(updateMethod))
                .contains("checkout_status = 'FAILED'")
                .contains("checkout_status = 'PENDING'")
                .contains("process_stage = 'WAITING_PAYER'")
                .contains("last_submit_time IS NULL");
        log.info("用例结果：处理中及已有提交记录的订单均不在超时关单范围内");
    }

    /** 未撤销的收银台 Token 不因付款截止时间到达而失去结果查询能力。 */
    @Test
    void activeTokenShouldAllowNullExpiryForLongTermResultLookup() throws NoSuchMethodException {
        log.info("用例开始：校验未撤销的访问令牌允许长期查询最终订单结果");
        Method method = PaymentCheckoutTokenMapper.class.getMethod(
                "markUsed", String.class, String.class, String.class, LocalDateTime.class);

        assertThat(updateSql(method))
                .contains("token_status = 'ACTIVE'")
                .contains("(expire_time IS NULL OR expire_time > #{now})");
        log.info("用例结果：令牌支付资格与结果查询资格已分离，空失效时间可持续查询");
    }

    /** 会写入季度分片通知和 Outbox 的查询补偿与批量关单必须使用交易逻辑数据源。 */
    @Test
    void checkoutExpirationEntryPointsShouldUseTransactionDataSource() throws NoSuchMethodException {
        Method queryMethod = DefaultPaymentCheckoutService.class.getMethod(
                "querySession", PaymentCheckoutSessionQueryCommandDTO.class);
        Method expireMethod = DefaultPaymentCheckoutService.class.getMethod(
                "expireDue", LocalDateTime.class, int.class);

        assertThat(queryMethod.getAnnotation(DS.class).value()).isEqualTo(DataSourceName.TRANSACTION);
        assertThat(expireMethod.getAnnotation(DS.class).value()).isEqualTo(DataSourceName.TRANSACTION);
    }

    private String updateSql(Method method) {
        Update update = method.getAnnotation(Update.class);
        assertThat(update).isNotNull();
        return String.join("\n", update.value());
    }

    private String selectSql(Method method) {
        Select select = method.getAnnotation(Select.class);
        assertThat(select).isNotNull();
        return String.join("\n", select.value());
    }
}
