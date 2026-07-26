package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.message.BaseMqMessage;
import com.scott.payment.component.mq.producer.MqProducer;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.mq.message.TransactionEventMessage;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionEventOutboxRelayServiceTests
 * @date : 2026-07-12 22:43
 * @email : scott_x@163.com
 * @description : DefaultTransactionEventOutboxRelayServiceTests 自动化测试类，用于验证对应模块的业务规则、异常边界和回归场景，位于 支付核心服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
class DefaultTransactionEventOutboxRelayServiceTests {

    @Test
    void shouldPublishDueEventAndMarkSent() {
        InMemoryEventOutboxService eventOutboxService = new InMemoryEventOutboxService(event());
        CapturingMqProducer mqProducer = new CapturingMqProducer(false);
        DefaultTransactionEventOutboxRelayService relayService = new DefaultTransactionEventOutboxRelayService(
                eventOutboxService,
                mqProducer);

        int successCount = relayService.publishDueEvents(LocalDateTime.of(2026, 7, 12, 10, 0), 10);

        assertThat(successCount).isEqualTo(1);
        assertThat(mqProducer.sent).isTrue();
        assertThat(mqProducer.message).isInstanceOf(TransactionEventMessage.class);
        assertThat(((TransactionEventMessage) mqProducer.message).getTransactionDateTime())
                .isEqualTo(LocalDateTime.of(2026, 7, 12, 10, 0));
        assertThat(eventOutboxService.eventDO.getEventStatus()).isEqualTo("SENT");
        assertThat(eventOutboxService.eventDO.getSentTime()).isNotNull();
    }

    @Test
    void shouldMarkFailedWhenMqSendThrowsException() {
        InMemoryEventOutboxService eventOutboxService = new InMemoryEventOutboxService(event());
        CapturingMqProducer mqProducer = new CapturingMqProducer(true);
        DefaultTransactionEventOutboxRelayService relayService = new DefaultTransactionEventOutboxRelayService(
                eventOutboxService,
                mqProducer);

        int successCount = relayService.publishDueEvents(LocalDateTime.of(2026, 7, 12, 10, 0), 10);

        assertThat(successCount).isZero();
        assertThat(eventOutboxService.eventDO.getEventStatus()).isEqualTo("FAILED");
        assertThat(eventOutboxService.eventDO.getFailReason()).contains("send failed");
        assertThat(eventOutboxService.eventDO.getNextRetryTime()).isNotNull();
    }

    @Test
    void shouldNotRepublishAlreadySentEventWhenOutboxIsScannedAgain() {
        TransactionEventOutboxDO eventDO = event();
        InMemoryEventOutboxService eventOutboxService = new InMemoryEventOutboxService(eventDO);
        CapturingMqProducer mqProducer = new CapturingMqProducer(false);
        DefaultTransactionEventOutboxRelayService relayService = new DefaultTransactionEventOutboxRelayService(
                eventOutboxService,
                mqProducer);

        int firstSuccessCount = relayService.publishDueEvents(LocalDateTime.of(2026, 7, 12, 10, 0), 10);
        int secondSuccessCount = relayService.publishDueEvents(LocalDateTime.of(2026, 7, 12, 10, 0), 10);

        assertThat(firstSuccessCount).isEqualTo(1);
        assertThat(secondSuccessCount).isZero();
        assertThat(mqProducer.sendCount).isEqualTo(1);
        assertThat(eventDO.getEventStatus()).isEqualTo("SENT");
    }

    private TransactionEventOutboxDO event() {
        TransactionEventMessage message = new TransactionEventMessage();
        message.setMessageId("TX202607121000000010001");
        message.setCreatedAt(LocalDateTime.of(2026, 7, 12, 10, 0));
        message.setTransactionId("TX202607121000000010001");
        message.setOperationId("OP202607121000000010001");
        message.setMerchantId("200001");
        message.setMerchantOrderNo("M202607120001");
        message.setTransactionType("AUTHORIZATION");
        message.setTransactionStatus("SUCCESS");
        message.setEventType("TRANSACTION_CREATED");
        message.setTransactionDateTime(LocalDateTime.of(2026, 7, 12, 10, 0));
        TransactionEventOutboxDO eventDO = new TransactionEventOutboxDO();
        eventDO.setId(1L);
        eventDO.setEventNo("TX202607121000000010001");
        eventDO.setMessageKey("TX202607121000000010001");
        eventDO.setTopic("PAYMENT_EVENT");
        eventDO.setTag("TRANSACTION_CREATED");
        eventDO.setPayloadJson(JsonUtils.toJsonString(message));
        eventDO.setEventTime(message.getCreatedAt());
        eventDO.setEventStatus("INIT");
        eventDO.setVersion(0);
        return eventDO;
    }

    private static class CapturingMqProducer implements MqProducer {

        /**
         * fail 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：布尔值；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final boolean fail;

        /**
         * sent 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：布尔值；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private boolean sent;

        /**
         * send Count 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private int sendCount;

        /**
         * message 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private BaseMqMessage message;

        private CapturingMqProducer(boolean fail) {
            this.fail = fail;
        }

        @Override
        /**
         * 发送 send 对应的外部通知、内部消息或远程请求。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param topic topic 输入值，含义由调用方法名称和所属业务对象限定
         * @param tag tag 输入值，含义由调用方法名称和所属业务对象限定
         * @param message 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
         */
        public void send(String topic, String tag, BaseMqMessage message) {
            if (fail) {
                throw new IllegalStateException("send failed");
            }
            this.message = message;
            this.sent = true;
            this.sendCount++;
        }
    }

    private static class InMemoryEventOutboxService implements TransactionEventOutboxService {

        /**
         * event DO 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final TransactionEventOutboxDO eventDO;

        private InMemoryEventOutboxService(TransactionEventOutboxDO eventDO) {
            this.eventDO = eventDO;
        }

        @Override
        /**
         * 写入或更新 save 相关数据，保持数据库记录与当前业务处理结果一致。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param eventDO event DO 输入值，含义由调用方法名称和所属业务对象限定
         */
        public void save(TransactionEventOutboxDO eventDO) {
        }

        @Override
        /**
         * 完成 list Due Events 分支的校验或转换，返回值供当前调用链继续组装结果。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param eventTime 时间值，使用系统约定时区或调用方传入的业务时区解释
         * @param now now 输入值，含义由调用方法名称和所属业务对象限定
         * @param limit limit 输入值，含义由调用方法名称和所属业务对象限定
         * @return 当前方法计算或转换后的业务结果
         */
        public List<TransactionEventOutboxDO> listDueEvents(LocalDateTime eventTime, LocalDateTime now, int limit) {
            return "SENT".equals(eventDO.getEventStatus()) || "CLOSED".equals(eventDO.getEventStatus())
                    ? List.of()
                    : List.of(eventDO);
        }

        @Override
        /**
         * 推进 mark Sent 对应的状态或处理结果，并保留后续查询所需信息。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @param eventDO event DO 输入值，含义由调用方法名称和所属业务对象限定
         * @param sentTime 时间值，使用系统约定时区或调用方传入的业务时区解释
         * @return 当前方法计算或转换后的业务结果
         */
        public boolean markSent(TransactionEventOutboxDO eventDO, LocalDateTime sentTime) {
            eventDO.setEventStatus("SENT");
            eventDO.setSentTime(sentTime);
            eventDO.setVersion(eventDO.getVersion() + 1);
            return true;
        }

        @Override
/**
 * 推进 mark Failed 对应的状态或处理结果，并保留后续查询所需信息。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param eventDO event DO 输入值，含义由调用方法名称和所属业务对象限定
 * @param nextRetryTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param failReason fail Reason 输入值，含义由调用方法名称和所属业务对象限定
 * @param now now 输入值，含义由调用方法名称和所属业务对象限定
 * @return 当前方法计算或转换后的业务结果
 */
        public boolean markFailed(TransactionEventOutboxDO eventDO,
                                  LocalDateTime nextRetryTime,
                                  String failReason,
                                  LocalDateTime now) {
            eventDO.setEventStatus("FAILED");
            eventDO.setNextRetryTime(nextRetryTime);
            eventDO.setFailReason(failReason);
            eventDO.setVersion(eventDO.getVersion() + 1);
            return true;
        }
    }
}
