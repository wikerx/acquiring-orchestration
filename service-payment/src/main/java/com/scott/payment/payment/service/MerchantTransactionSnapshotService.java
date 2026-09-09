package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.service.dto.MerchantTransactionSnapshotDTO;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantTransactionSnapshotService
 * @date : 2026-08-14 12:45
 * @email : scott_x@163.com
 * @description : 商户交易快照服务，负责在首次交易事务中冻结请求资料，并按根交易分片键为响应和查询恢复快照。
 * @status : create
 */
public interface MerchantTransactionSnapshotService {

    /**
     * 保存首次交易的商户可见请求快照。
     *
     * @param commandDTO 首次交易命令
     * @param resultDTO 已生成平台交易标识的受理结果
     * @param now 本地事务持久化时间
     */
    void recordInitialSnapshots(PaymentCreateCommandDTO commandDTO,
                                PaymentCreateResultDTO resultDTO,
                                LocalDateTime now);

    /**
     * 保存 Capture、Refund、Void 和增额授权等后续动作自身的商户配置快照。
     *
     * @param commandDTO 当前交易动作命令
     * @param resultDTO 已生成当前动作交易号的受理结果
     * @param now 当前动作本地事务持久化时间
     */
    void recordActionSnapshot(PaymentCreateCommandDTO commandDTO,
                              PaymentCreateResultDTO resultDTO,
                              LocalDateTime now);

    /**
     * 读取生命周期根交易的商户可见请求快照。
     *
     * @param merchantId 平台商户号，用作付款人密文 AAD
     * @param rootTransactionId 生命周期首笔平台交易 ID
     * @param rootTransactionDateTime 生命周期根交易分片时间
     * @return 快照聚合；某个可选对象未上送时对应字段为空
     */
    MerchantTransactionSnapshotDTO loadSnapshots(String merchantId,
                                                  String rootTransactionId,
                                                  LocalDateTime rootTransactionDateTime);
}
