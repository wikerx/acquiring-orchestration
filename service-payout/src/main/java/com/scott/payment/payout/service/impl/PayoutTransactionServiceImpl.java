package com.scott.payment.payout.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payout.api.internal.dto.PayoutCreateCommandDTO;
import com.scott.payment.payout.api.internal.dto.PayoutCreateResultDTO;
import com.scott.payment.payout.service.PayoutTransactionService;
import org.springframework.stereotype.Service;


/**
 * 在代付核心交易闭环尚未建设完成时关闭创建入口。
 *
 * <p>当前模块尚无代付事实表、幂等状态机、审核记录、渠道执行记录和可靠事件。任何临时生成的单号都无法支持查询、回调、勾兑或重复请求保护，因此创建请求必须明确失败。</p>
 */
@Service
public class PayoutTransactionServiceImpl implements PayoutTransactionService {

    /**
     * 拒绝尚未具备可靠落库和渠道执行能力的代付创建请求。
     *
     * @param commandDTO 创建代付命令
     * @return 当前实现不会返回代付创建结果
     * @throws ServiceException 代付核心闭环尚未启用
     */
    @Override
    public PayoutCreateResultDTO createPayout(PayoutCreateCommandDTO commandDTO) {
        throw new ServiceException(ApiResultEnum.PAYOUT_CORE_UNAVAILABLE);
    }
}
