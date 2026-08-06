package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.sharding.TransactionShardingKeyParser;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.mapper.TransactionAmountChangeLogMapper;
import com.scott.payment.payment.mapper.TransactionChannelCallbackLogMapper;
import com.scott.payment.payment.mapper.TransactionChannelCallbackMapper;
import com.scott.payment.payment.mapper.TransactionChannelInteractionLogMapper;
import com.scott.payment.payment.mapper.TransactionChannelRequestMapper;
import com.scott.payment.payment.mapper.TransactionFlowEventMapper;
import com.scott.payment.payment.mapper.TransactionMerchantApiInteractionLogMapper;
import com.scott.payment.payment.mapper.TransactionMerchantNotificationLogMapper;
import com.scott.payment.payment.mapper.TransactionMerchantNotificationMapper;
import com.scott.payment.payment.mapper.TransactionOperationMapper;
import com.scott.payment.payment.mapper.TransactionOrderMapper;
import com.scott.payment.payment.mapper.TransactionPaymentMethodInfoMapper;
import com.scott.payment.payment.mapper.TransactionStatusHistoryMapper;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionOperationSearchResponse;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionOrderResponse;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionPageQuery;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionQueryServiceTests
 * @date : 2026-08-02 03:10
 * @email : scott_x@163.com
 * @description : 验证交易查询服务在 ShardingSphere 模式下只调用固定逻辑表 Mapper，并由数据库完成全局分页与按币种汇总。
 * @status : create
 */
class DefaultTransactionQueryServiceTests {

    @Test
    void shouldUseLogicalOrderPagination() {
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        when(orderMapper.countPageLogical(any(), any(), any(), any(), any(), any())).thenReturn(1L);
        when(orderMapper.selectPageLogical(any(), any(), any(), any(), any(), any(), anyLong(), anyLong()))
                .thenReturn(List.of(new TransactionOrderDO()));
        DefaultTransactionQueryService service = queryService(orderMapper, operationMapper);
        TransactionPageQuery query = query();

        PageResult<TransactionOrderResponse> result = service.pageOrders(query);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(invokedMethodNames(orderMapper))
                .contains("countPageLogical", "selectPageLogical")
                .noneMatch(name -> name.endsWith("Physical"));
    }

    @Test
    void shouldUseLogicalOperationPaginationAndCurrencySummaries() {
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        DefaultTransactionQueryService service = queryService(orderMapper, operationMapper);

        TransactionOperationSearchResponse result = service.searchOperations(query());

        assertThat(result.getPage().getRecords()).isEmpty();
        assertThat(invokedMethodNames(operationMapper))
                .contains("countPageLogical", "selectPageLogical",
                        "selectAmountSummaryLogical", "selectPaymentMethodSummaryLogical")
                .noneMatch(name -> name.endsWith("Physical"));
    }

    /**
     * 详情必须使用列表返回的根主单时间精确查询主单，不能从操作号推导时间。
     */
    @Test
    void detailShouldUseProvidedRootTransactionDateTime() {
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        LocalDateTime transactionDateTime =
                LocalDateTime.of(2026, 8, 2, 12, 8, 38, 176_000_000);
        LocalDateTime rootTransactionDateTime =
                LocalDateTime.of(2026, 8, 2, 12, 8, 36, 255_000_000);
        TransactionOperationDO operation = new TransactionOperationDO();
        operation.setOperationId("opaque-operation-id");
        operation.setTransactionId("transaction-id");
        operation.setTransactionDateTime(transactionDateTime);
        operation.setOperationSequence(1);
        TransactionOrderDO order = new TransactionOrderDO();
        order.setOperationId(operation.getOperationId());
        order.setTransactionDateTime(rootTransactionDateTime);
        when(operationMapper.selectByTransactionId(
                operation.getTransactionId(), transactionDateTime)).thenReturn(operation);
        when(orderMapper.selectByOperationId(
                operation.getOperationId(), rootTransactionDateTime)).thenReturn(order);
        when(operationMapper.selectByOperationId(
                org.mockito.ArgumentMatchers.eq(operation.getOperationId()),
                org.mockito.ArgumentMatchers.eq(rootTransactionDateTime),
                any(LocalDateTime.class))).thenReturn(new ArrayList<>(List.of(operation)));
        TransactionShardingKeyParser shardingKeyParser = mock(TransactionShardingKeyParser.class);
        DefaultTransactionQueryService service = queryService(orderMapper, operationMapper, shardingKeyParser);

        service.detail(
                operation.getTransactionId(), transactionDateTime, rootTransactionDateTime);

        verify(orderMapper).selectByOperationId(
                operation.getOperationId(), rootTransactionDateTime);
        verifyNoInteractions(shardingKeyParser);
    }

    private DefaultTransactionQueryService queryService(TransactionOrderMapper orderMapper,
                                                         TransactionOperationMapper operationMapper) {
        return queryService(orderMapper, operationMapper, new TransactionShardingKeyParser());
    }

    private DefaultTransactionQueryService queryService(TransactionOrderMapper orderMapper,
                                                         TransactionOperationMapper operationMapper,
                                                         TransactionShardingKeyParser shardingKeyParser) {
        return new DefaultTransactionQueryService(
                orderMapper,
                operationMapper,
                mock(TransactionStatusHistoryMapper.class),
                mock(TransactionChannelRequestMapper.class),
                mock(TransactionChannelInteractionLogMapper.class),
                mock(TransactionChannelCallbackLogMapper.class),
                mock(TransactionChannelCallbackMapper.class),
                mock(TransactionFlowEventMapper.class),
                mock(TransactionAmountChangeLogMapper.class),
                mock(TransactionMerchantNotificationMapper.class),
                mock(TransactionMerchantNotificationLogMapper.class),
                mock(TransactionMerchantApiInteractionLogMapper.class),
                mock(TransactionPaymentMethodInfoMapper.class),
                shardingKeyParser);
    }

    private TransactionPageQuery query() {
        TransactionPageQuery query = new TransactionPageQuery();
        query.setMerchantId("200001");
        query.setBeginTime(LocalDateTime.of(2026, 4, 1, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 7, 31, 23, 59, 59, 999_000_000));
        query.setPageNo(1);
        query.setPageSize(20);
        return query;
    }

    private Set<String> invokedMethodNames(Object mock) {
        return mockingDetails(mock).getInvocations().stream()
                .map(Invocation::getMethod)
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());
    }
}
