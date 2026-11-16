package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import org.springframework.util.StringUtils;

/**
 * 后续资金动作的原交易渠道身份恢复器。
 *
 * <p>退款、请款、撤销和增额授权必须沿用原交易渠道与 MID。优先使用创建时冻结的完整路由快照；
 * 快照存在但损坏或与动作事实冲突时直接拒绝，禁止重新路由到当前配置造成跨渠道资金动作。</p>
 */
final class OriginalTransactionRouteResolver {

    /**
     * 阻止仅提供静态能力的类型被实例化。
     */
    private OriginalTransactionRouteResolver() {
    }

    /**
     * 从原交易冻结身份恢复渠道路由。
     */
    static PaymentRouteResultDTO restore(PaymentChannelRouteService routeService,
                                         TransactionOrderDO sourceOrder,
                                         TransactionOperationDO sourceOperation) {
        PaymentRouteResultDTO snapshot = snapshot(sourceOrder, sourceOperation);
        if (snapshot == null || !StringUtils.hasText(snapshot.getChannelCode()) || snapshot.getMidConfigId() == null) {
            throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED);
        }
        if (hasFrozenRoute(sourceOperation)) {
            // 完整冻结快照已经包含调用元数据，直接复用，避免读取已轮换的当前 MID 配置。
            return snapshot;
        }
        // 仅兼容没有完整历史快照的旧交易，按已保存的渠道/MID 身份恢复当前调用元数据。
        return routeService.restore(
                snapshot.getChannelCode(),
                snapshot.getChannelId(),
                snapshot.getMidConfigId(),
                snapshot.getMidNo());
    }

    /**
     * 在主单短事务建立前完成原交易路由恢复。
     *
     * <p>完整冻结快照只做本地解析；旧交易需要读取当前渠道与 MID 配置时，也必须在持有原订单
     * 行锁之前完成。原交易不可继续属于可持久化的业务拒绝，因此转换为规划结果而不是直接吞掉。</p>
     *
     * @param routeService 原渠道与 MID 恢复服务
     * @param sourceOrder 原交易主单快照
     * @param sourceOperation 原交易动作快照
     * @return 可执行路由或原交易拒绝规划
     */
    static Resolution plan(PaymentChannelRouteService routeService,
                           TransactionOrderDO sourceOrder,
                           TransactionOperationDO sourceOperation) {
        try {
            return Resolution.routed(restore(routeService, sourceOrder, sourceOperation));
        } catch (ServiceException exception) {
            if (OriginalTransactionRejectionSupport.isOriginalTransactionRejected(exception)) {
                try {
                    return Resolution.rejected(snapshot(sourceOrder, sourceOperation));
                } catch (RuntimeException snapshotFailure) {
                    return Resolution.failed(snapshotFailure);
                }
            }
            return Resolution.failed(exception);
        } catch (RuntimeException exception) {
            return Resolution.failed(exception);
        }
    }

    /**
     * 构建原交易渠道路由快照。
     */
    static PaymentRouteResultDTO snapshot(TransactionOrderDO sourceOrder,
                                          TransactionOperationDO sourceOperation) {
        PaymentRouteResultDTO frozenSnapshot = parseFrozenRoute(sourceOperation);
        if (frozenSnapshot != null) {
            return frozenSnapshot;
        }
        // 旧数据按动作优先、主单兜底恢复最小路由身份，不执行任何新路由选择。
        String channelCode = firstText(
                sourceOperation == null ? null : sourceOperation.getChannelCode(),
                sourceOrder == null ? null : sourceOrder.getChannelCode());
        Long channelId = sourceOperation != null && sourceOperation.getChannelId() != null
                ? sourceOperation.getChannelId()
                : sourceOrder == null ? null : sourceOrder.getChannelId();
        Long midConfigId = sourceOperation != null && sourceOperation.getChannelMidConfigId() != null
                ? sourceOperation.getChannelMidConfigId()
                : sourceOrder == null ? null : sourceOrder.getChannelMidConfigId();
        String fallbackMidNo = firstText(
                sourceOrder == null ? null : sourceOrder.getChannelMerchantId(),
                sourceOperation == null ? null : sourceOperation.getChannelTerminalId());
        if (!StringUtils.hasText(channelCode)) {
            return null;
        }
        PaymentRouteResultDTO snapshot = PaymentRouteResultDTO.routed(channelCode);
        snapshot.setChannelId(channelId);
        snapshot.setMidConfigId(midConfigId);
        snapshot.setMidNo(fallbackMidNo);
        snapshot.setRouteReason("RESTORED_FROM_ORIGINAL_TRANSACTION");
        return snapshot;
    }

    /**
     * 解析交易动作创建时冻结的原始渠道路由；快照损坏时拒绝回退到当前路由，避免误用新 MID。
     */
    private static PaymentRouteResultDTO parseFrozenRoute(TransactionOperationDO sourceOperation) {
        if (sourceOperation == null || !StringUtils.hasText(sourceOperation.getChannelRouteSnapshotJson())) {
            return null;
        }
        PaymentRouteResultDTO snapshot;
        try {
            snapshot = JsonUtils.parseObject(
                    sourceOperation.getChannelRouteSnapshotJson(), PaymentRouteResultDTO.class);
        } catch (RuntimeException exception) {
            throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED);
        }
        if (snapshot == null
                || !StringUtils.hasText(snapshot.getChannelCode())
                || snapshot.getChannelId() == null
                || snapshot.getMidConfigId() == null) {
            throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED);
        }
        if (StringUtils.hasText(sourceOperation.getChannelCode())
                && !sourceOperation.getChannelCode().equalsIgnoreCase(snapshot.getChannelCode())) {
            throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED);
        }
        if (sourceOperation.getChannelMidConfigId() != null
                && !sourceOperation.getChannelMidConfigId().equals(snapshot.getMidConfigId())) {
            throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED);
        }
        if (sourceOperation.getChannelId() != null
                && !sourceOperation.getChannelId().equals(snapshot.getChannelId())) {
            throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED);
        }
        return snapshot;
    }

    /**
     * 判断后续动作是否已冻结原渠道路由。
     */
    private static boolean hasFrozenRoute(TransactionOperationDO sourceOperation) {
        return sourceOperation != null && StringUtils.hasText(sourceOperation.getChannelRouteSnapshotJson());
    }

    /**
     * 返回首个非空文本。
     */
    private static String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 后续资金动作的事务外路由规划结果。
     *
     * @param routeResult 原交易冻结路由；拒绝场景保留最小身份用于审计落库
     * @param rejected 原交易渠道身份是否已确定不可继续
     * @param failure 事务外规划异常；延迟到原业务校验完成后按既有顺序抛出
     */
    record Resolution(PaymentRouteResultDTO routeResult, boolean rejected, RuntimeException failure) {

        /**
         * 构造可继续执行渠道调用的规划结果。
         */
        private static Resolution routed(PaymentRouteResultDTO routeResult) {
            return new Resolution(routeResult, false, null);
        }

        /**
         * 构造需要在本地持久化稳定失败结果的规划结果。
         */
        private static Resolution rejected(PaymentRouteResultDTO routeResult) {
            return new Resolution(routeResult, true, null);
        }

        /**
         * 保存事务外读取异常，避免改变锁内状态机和额度校验的原有错误优先级。
         */
        private static Resolution failed(RuntimeException failure) {
            return new Resolution(null, false, failure);
        }

        /**
         * 在原有路由恢复位置返回规划结果或重新抛出事务外读取异常。
         */
        PaymentRouteResultDTO requireRouteResult() {
            if (failure != null) {
                throw failure;
            }
            return routeResult;
        }
    }
}
