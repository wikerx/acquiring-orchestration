package com.scott.payment.payment.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionPersistenceMapperContractTests {

    @Test
    void operationInsertShouldPersistMerchantOperationNoAndSourceOperationId() throws NoSuchMethodException {
        Method method = TransactionOperationMapper.class.getMethod(
                "insertPhysical",
                String.class,
                com.scott.payment.payment.entity.TransactionOperationDO.class);

        String sql = annotationValue(method, Insert.class);

        assertThat(sql).contains("source_operation_id");
        assertThat(sql).contains("merchant_operation_no");
        assertThat(sql).contains("#{operationDO.sourceOperationId}");
        assertThat(sql).contains("#{operationDO.merchantOperationNo}");
        assertThat(sql).doesNotContain("merchant_order_id");
    }

    @Test
    void channelRequestMapperShouldExposeStableLookupAndCasUpdateSql() throws NoSuchMethodException {
        String requestIdSql = annotationValue(TransactionChannelRequestMapper.class.getMethod(
                "selectByRequestIdPhysical",
                String.class,
                String.class), Select.class);
        String channelTransactionSql = annotationValue(TransactionChannelRequestMapper.class.getMethod(
                "selectByChannelTransactionPhysical",
                String.class,
                String.class,
                String.class,
                String.class), Select.class);
        String updateStatusSql = annotationValue(TransactionChannelRequestMapper.class.getMethod(
                "updateStatusPhysical",
                String.class,
                String.class,
                Integer.class,
                java.util.List.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                Integer.class,
                String.class,
                String.class,
                java.time.LocalDateTime.class,
                Integer.class), Update.class);

        assertThat(requestIdSql).contains("request_id = #{requestId}");
        assertThat(channelTransactionSql).contains("channel_code = #{channelCode}");
        assertThat(channelTransactionSql).contains("channel_order_no = #{channelOrderNo}");
        assertThat(channelTransactionSql).contains("channel_transaction_id = #{channelTransactionId}");
        assertThat(updateStatusSql).contains("version = #{expectedVersion}");
        assertThat(updateStatusSql).contains("request_status IN");
        assertThat(updateStatusSql).contains("version = version + 1");
    }

    @Test
    void operationMapperShouldCasUpdateNonTerminalChannelResultForRecovery() throws NoSuchMethodException {
        String sql = annotationValue(TransactionOperationMapper.class.getMethod(
                "updateNonTerminalChannelResultPhysical",
                String.class,
                Long.class,
                Integer.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                java.time.LocalDateTime.class), Update.class);

        assertThat(sql).contains("version = #{expectedVersion}");
        assertThat(sql).contains("transaction_status NOT IN ('SUCCESS', 'FAILED')");
        assertThat(sql).contains("channel_match_status = 'PENDING'");
        assertThat(sql).contains("last_channel_match_request_id = #{requestId}");
        assertThat(sql).contains("next_channel_match_time = COALESCE(next_channel_match_time, #{matchTime})");
        assertThat(sql).doesNotContain("complete_time");
    }

    private static <A extends java.lang.annotation.Annotation> String annotationValue(Method method, Class<A> annotationType) {
        java.lang.annotation.Annotation annotation = method.getAnnotation(annotationType);
        assertThat(annotation).isNotNull();
        if (annotation instanceof Insert insert) {
            return String.join("\n", insert.value());
        }
        if (annotation instanceof Select select) {
            return String.join("\n", select.value());
        }
        if (annotation instanceof Update update) {
            return String.join("\n", update.value());
        }
        return Arrays.toString(method.getAnnotations());
    }
}
