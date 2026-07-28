package com.scott.payment.payment.domain.state;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.entity.TransactionOrderDO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionStateMachineServiceTests
 * @date : 2026-07-14 21:35
 * @email : scott_x@163.com
 * @description : 收单交易状态机测试，验证后续资金动作在进入渠道前必须满足原交易状态、类型、币种和可用金额边界。
 * @status : create
 */
class DefaultTransactionStateMachineServiceTests {

    /**
     * state Machine Service 依赖，用于 Default Transaction State Machine Service Tests 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final DefaultTransactionStateMachineService stateMachineService = new DefaultTransactionStateMachineService();

    /**
     * 授权成功且可请款金额充足时允许发起请款。
     */
    @Test
    void shouldAllowCaptureWhenSourceAuthorizationHasAvailableAmount() {
        assertThatCode(() -> stateMachineService.validateFollowUpAction(
                authorizationOrder(),
                PaymentTransactionTypeEnum.CAPTURE,
                new BigDecimal("5.00"),
                "USD"))
                .doesNotThrowAnyException();
    }

    /**
     * 请款金额超过原交易可请款金额时必须拒绝。
     */
    @Test
    void shouldRejectCaptureWhenAmountExceedsAvailableCaptureAmount() {
        assertParamInvalid(() -> stateMachineService.validateFollowUpAction(
                authorizationOrder(),
                PaymentTransactionTypeEnum.CAPTURE,
                new BigDecimal("20.00"),
                "USD"));
    }

    /**
     * 后续金额类动作的币种必须与原交易交易币种一致。
     */
    @Test
    void shouldRejectFollowUpWhenCurrencyDoesNotMatchSourceTransactionCurrency() {
        assertParamInvalid(() -> stateMachineService.validateFollowUpAction(
                authorizationOrder(),
                PaymentTransactionTypeEnum.CAPTURE,
                new BigDecimal("5.00"),
                "EUR"));
    }

    /**
     * 退款累计金额不能超过原交易可退金额。
     */
    @Test
    void shouldRejectRefundWhenAmountExceedsAvailableRefundAmount() {
        assertParamInvalid(() -> stateMachineService.validateFollowUpAction(
                paymentOrder(),
                PaymentTransactionTypeEnum.REFUND,
                new BigDecimal("15.00"),
                "USD"));
    }

    /**
     * 已经发生请款或退款的交易不能再执行撤销。
     */
    @Test
    void shouldRejectVoidWhenSourceTransactionHasCapturedAmount() {
        TransactionOrderDO orderDO = authorizationOrder();
        orderDO.setCapturedAmount(new BigDecimal("1.00"));

        assertParamInvalid(() -> stateMachineService.validateFollowUpAction(
                orderDO,
                PaymentTransactionTypeEnum.VOID,
                null,
                null));
    }

    /**
     * 一步支付交易不能作为请款源交易，避免重复捕获资金。
     */
    @Test
    void shouldRejectCaptureWhenSourceTypeIsPayment() {
        assertParamInvalid(() -> stateMachineService.validateFollowUpAction(
                paymentOrder(),
                PaymentTransactionTypeEnum.CAPTURE,
                new BigDecimal("1.00"),
                "USD"));
    }

    private void assertParamInvalid(ThrowingAction action) {
        assertThatThrownBy(action::execute)
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.PARAM_INVALID.getCode());
    }

    private TransactionOrderDO authorizationOrder() {
        TransactionOrderDO orderDO = baseOrder();
        orderDO.setTransactionType(PaymentTransactionTypeEnum.AUTHORIZATION.getCode());
        orderDO.setAuthorizedAmount(new BigDecimal("12.34"));
        orderDO.setCapturedAmount(BigDecimal.ZERO);
        orderDO.setRefundedAmount(BigDecimal.ZERO);
        orderDO.setAvailableCaptureAmount(new BigDecimal("12.34"));
        orderDO.setAvailableRefundAmount(BigDecimal.ZERO);
        return orderDO;
    }

    private TransactionOrderDO paymentOrder() {
        TransactionOrderDO orderDO = baseOrder();
        orderDO.setTransactionType(PaymentTransactionTypeEnum.PAYMENT.getCode());
        orderDO.setAuthorizedAmount(BigDecimal.ZERO);
        orderDO.setCapturedAmount(new BigDecimal("12.34"));
        orderDO.setRefundedAmount(BigDecimal.ZERO);
        orderDO.setAvailableCaptureAmount(BigDecimal.ZERO);
        orderDO.setAvailableRefundAmount(new BigDecimal("12.34"));
        return orderDO;
    }

    private TransactionOrderDO baseOrder() {
        TransactionOrderDO orderDO = new TransactionOrderDO();
        orderDO.setOperationId("OP260714180001");
        orderDO.setRootTransactionId("TX260714180001");
        orderDO.setLatestTransactionId("TX260714180001");
        orderDO.setTransactionStatus(PaymentTransactionStatusEnum.SUCCESS.getCode());
        orderDO.setTransactionCurrency("USD");
        return orderDO;
    }

    @FunctionalInterface
    private interface ThrowingAction {

        void execute();
    }
}
