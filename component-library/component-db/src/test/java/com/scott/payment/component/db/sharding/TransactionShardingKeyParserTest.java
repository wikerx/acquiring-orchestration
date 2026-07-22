package com.scott.payment.component.db.sharding;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionShardingKeyParserTest
 * @date : 2026-07-21 00:00
 * @email : scott_x@163.com
 * @description : 交易分表键时间片解析测试，覆盖无前缀交易号、历史 TX 交易号和 OP 生命周期号。
 * @status : create
 */
class TransactionShardingKeyParserTest {

    /**
     * 验证无前缀平台交易号可以解析交易业务时间。
     */
    @Test
    void shouldParsePrefixlessTransactionId() {
        TransactionShardingKeyParser parser = new TransactionShardingKeyParser();

        LocalDateTime parsed = parser.parseTransactionDateTime("202607151512134560001");

        assertThat(parsed).isEqualTo(LocalDateTime.of(2026, 7, 15, 15, 12, 13, 456_000_000));
    }

    /**
     * 验证历史 TX 交易号可以解析交易业务时间。
     */
    @Test
    void shouldParseLegacyTxTransactionId() {
        TransactionShardingKeyParser parser = new TransactionShardingKeyParser();

        LocalDateTime parsed = parser.parseTransactionDateTime("TX202607151512134560001");

        assertThat(parsed).isEqualTo(LocalDateTime.of(2026, 7, 15, 15, 12, 13, 456_000_000));
    }

    /**
     * 验证生命周期操作号可以解析原始交易业务时间。
     */
    @Test
    void shouldParseOperationId() {
        TransactionShardingKeyParser parser = new TransactionShardingKeyParser();

        LocalDateTime parsed = parser.parseOperationDateTime("OP202607151512134560001");

        assertThat(parsed).isEqualTo(LocalDateTime.of(2026, 7, 15, 15, 12, 13, 456_000_000));
    }

    /**
     * 验证非法交易号返回 null，避免误路由到错误物理表。
     */
    @Test
    void shouldReturnNullForInvalidKey() {
        TransactionShardingKeyParser parser = new TransactionShardingKeyParser();

        assertThat(parser.parseTransactionDateTime("INVALID")).isNull();
        assertThat(parser.parseOperationDateTime("202607151512134560001")).isNull();
    }
}
