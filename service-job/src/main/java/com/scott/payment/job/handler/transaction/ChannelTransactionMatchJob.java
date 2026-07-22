package com.scott.payment.job.handler.transaction;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.job.executor.JobHandler;
import com.scott.payment.component.job.executor.JobHandlerDescriptor;
import com.scott.payment.component.job.model.JobExecuteResult;
import com.scott.payment.job.client.payment.PaymentInternalClient;
import com.scott.payment.job.client.payment.dto.PaymentChannelMatchClientRequestDTO;
import com.scott.payment.job.client.payment.dto.PaymentChannelMatchClientResultDTO;
import com.scott.payment.job.dto.transaction.ChannelTransactionMatchRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelTransactionMatchJob
 * @date : 2026-07-19 22:40
 * @email : scott_x@163.com
 * @description : 渠道交易查询勾兑任务，位于 service-job 任务处理层，按 transaction_date_time 定位分表并触发 service-payment 查询渠道终态；任务本身不决定资金状态。
 * @status : create
 */
@Component
public class ChannelTransactionMatchJob implements JobHandler {

    /**
     * 任务编码，和 sys_job_task.job_code 保持一致。
     */
    public static final String JOB_CODE = "CHANNEL_TRANSACTION_MATCH";

    /**
     * 处理器编码。
     */
    public static final String HANDLER_CODE = "channelTransactionMatch";

    private static final int DEFAULT_LIMIT = 100;

    private static final int MAX_LIMIT = 500;

    private final PaymentInternalClient paymentInternalClient;

    /**
     * 创建渠道交易查询勾兑任务处理器。
     *
     * @param paymentInternalClient service-payment 内部客户端
     */
    public ChannelTransactionMatchJob(PaymentInternalClient paymentInternalClient) {
        this.paymentInternalClient = paymentInternalClient;
    }

    /**
     * 返回处理器注册描述。
     *
     * @return 处理器描述
     */
    @Override
    public JobHandlerDescriptor descriptor() {
        return JobHandlerDescriptor.sync(
                HANDLER_CODE,
                "渠道交易查询勾兑",
                "transaction",
                "扫描待渠道确认交易并调用支付核心执行查询勾兑"
        );
    }

    /**
     * 执行渠道交易查询勾兑。
     * <p>
     * 该任务只负责把分表时间和渠道过滤条件传给 payment 服务；例如 WPGXML/WPGJSON 能否真实查询，
     * 取决于各自渠道客户端是否已经实现 Inquiry 请求，不能仅凭任务存在判断渠道已生产接通。
     *
     * @param context 任务执行上下文
     * @return 执行结果，包含每个分表时间点的处理数量
     */
    @Override
    public JobExecuteResult execute(JobExecuteContext context) {
        ChannelTransactionMatchRequest request = context == null ? null : context.parseParams(ChannelTransactionMatchRequest.class);
        if (request == null) {
            request = new ChannelTransactionMatchRequest();
        }
        int limit = normalizeLimit(request.getLimit());
        List<LocalDateTime> transactionDateTimes = resolveTransactionDateTimes(request);
        Map<String, PaymentChannelMatchClientResultDTO> result = new LinkedHashMap<>();
        int matchedCount = 0;
        for (LocalDateTime transactionDateTime : transactionDateTimes) {
            PaymentChannelMatchClientRequestDTO clientRequestDTO = new PaymentChannelMatchClientRequestDTO();
            clientRequestDTO.setTransactionDateTime(transactionDateTime);
            clientRequestDTO.setChannelCode(request.getChannelCode());
            clientRequestDTO.setLimit(limit);
            PaymentChannelMatchClientResultDTO matchResult = paymentInternalClient.matchDueChannelTransactions(clientRequestDTO);
            if (matchResult != null) {
                matchedCount += matchResult.getMatchedCount();
            }
            result.put(transactionDateTime.toString(), matchResult);
        }
        return JobExecuteResult.success("channel transaction match finished, matchedCount=" + matchedCount, result);
    }

    private List<LocalDateTime> resolveTransactionDateTimes(ChannelTransactionMatchRequest request) {
        if (request.getTransactionDateTimes() != null && !request.getTransactionDateTimes().isEmpty()) {
            return request.getTransactionDateTimes();
        }
        if (request.getTransactionDateTime() != null) {
            return List.of(request.getTransactionDateTime());
        }
        return List.of(LocalDateTime.now());
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "limit must be greater than zero");
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
