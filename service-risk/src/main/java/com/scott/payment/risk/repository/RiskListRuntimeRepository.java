package com.scott.payment.risk.repository;

import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateRequestDTO;
import com.scott.payment.risk.domain.MerchantLimitEvaluation;
import com.scott.payment.risk.domain.RiskListFunction;
import com.scott.payment.risk.domain.RiskListMatch;
import com.scott.payment.risk.domain.RiskRuntimeLookupValue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 风控运行时名单和规则查询仓储。
 */
public interface RiskListRuntimeRepository {

    /**
     * 查询指定名单功能中与运行时值匹配的首条启用规则。
     *
     * @param function 名单功能及其受控表、匹配方式
     * @param merchantId 当前商户号；全局规则查询时实现可按约定降级到公共范围
     * @param lookupValue 已完成脱敏、哈希或区间归一化的查询值
     * @return 命中明细；没有启用规则命中时返回空
     */
    Optional<RiskListMatch> findListMatch(RiskListFunction function,
                                          String merchantId,
                                          RiskRuntimeLookupValue lookupValue);

    /**
     * 判断指定名单功能是否存在当前商户可用的启用规则。
     *
     * @param function 名单功能
     * @param merchantId 当前商户号
     * @return 存在商户级或适用的公共规则时返回 {@code true}
     */
    boolean hasActiveListRule(RiskListFunction function, String merchantId);

    /**
     * 查询与当前来源主机匹配的启用来源网址规则。
     *
     * @param merchantId 当前商户号
     * @param lookupValue 已提取规范化主机名的来源网址查询值
     * @return 命中的来源网址规则；未命中时返回空
     */
    Optional<RiskListMatch> findSourceUrlRule(String merchantId, RiskRuntimeLookupValue lookupValue);

    /**
     * 检查来源网址限制是否已配置但当前主机未命中。
     *
     * @param merchantId 当前商户号
     * @param lookupValue 已提取规范化主机名的来源网址查询值
     * @return 限制生效且当前来源不在允许范围内时返回拒绝明细
     */
    Optional<RiskListMatch> findSourceUrlRestrictionMiss(String merchantId, RiskRuntimeLookupValue lookupValue);

    /**
     * 查询当前请求 IP 是否命中商户 IP 白名单。
     *
     * @param merchantId 当前商户号
     * @param lookupValue 已解析为 IP 版本和数值的查询值
     * @return 白名单命中明细；未命中或未配置时返回空
     */
    Optional<RiskListMatch> findMerchantIpWhitelistHit(String merchantId, RiskRuntimeLookupValue lookupValue);

    /**
     * 检查商户 IP 白名单是否启用但当前请求 IP 未命中。
     *
     * @param merchantId 当前商户号
     * @param lookupValue 已解析为 IP 版本和数值的查询值
     * @return 白名单已启用且未命中时返回拒绝明细
     */
    Optional<RiskListMatch> findMerchantIpWhitelistMiss(String merchantId, RiskRuntimeLookupValue lookupValue);

    /**
     * 查询交易金额超过当前商户单笔限额的启用规则。
     *
     * @param merchantId 当前商户号
     * @param amount 交易主币种金额，不得使用浮点数
     * @param currency ISO 4217 币种代码
     * @return 首条超限规则；没有超限时返回空
     */
    Optional<RiskListMatch> findMerchantLimitRule(String merchantId,
                                                  BigDecimal amount,
                                                  String currency);

    /**
     * 使用 Redis 原子预留执行日、周、月累计限额。
     *
     * @param requestDTO 风控请求
     * @return 累计限额明细和可回滚预留
     */
    default MerchantLimitEvaluation reserveCumulativeMerchantLimits(RiskPaymentEvaluateRequestDTO requestDTO) {
        return MerchantLimitEvaluation.empty();
    }

    /**
     * 使用本次稳定风控流水号创建可追踪的累计限额预占。
     *
     * @param requestDTO 风控请求
     * @param riskRecordNo 本次风控评估流水号
     * @return 累计限额明细和可回滚预留
     */
    default MerchantLimitEvaluation reserveCumulativeMerchantLimits(RiskPaymentEvaluateRequestDTO requestDTO,
                                                                    String riskRecordNo) {
        return reserveCumulativeMerchantLimits(requestDTO);
    }

    /**
     * 当后续风控节点拒绝时回滚本笔累计限额预留。
     *
     * @param evaluation 累计限额评估结果
     */
    default void rollbackMerchantLimitReservations(MerchantLimitEvaluation evaluation) {
    }

    /**
     * 判断当前商户和币种是否配置了启用的金额限额规则。
     *
     * @param merchantId 当前商户号
     * @param currency ISO 4217 币种代码
     * @return 存在单笔或累计限额规则时返回 {@code true}
     */
    boolean hasActiveMerchantLimitRule(String merchantId, String currency);

    /**
     * 执行启用的交易频率规则并返回首条触发明细。
     *
     * @param merchantId 当前商户号
     * @param requestDTO 本次交易及其风控上下文
     * @param cardNoLookup 卡号哈希查询值
     * @param cardFingerprintLookup 卡指纹查询值
     * @param ipLookup IP 数值查询值
     * @param emailLookup 邮箱哈希查询值
     * @param phoneLookup 手机号哈希查询值
     * @param customerIdLookup 商户客户标识哈希查询值
     * @param deviceFingerprintLookup 设备指纹哈希查询值
     * @return 首条达到阈值的规则；没有触发时返回空
     */
    Optional<RiskListMatch> findFrequencyRuleHit(String merchantId,
                                                 RiskPaymentEvaluateRequestDTO requestDTO,
                                                 RiskRuntimeLookupValue cardNoLookup,
                                                 RiskRuntimeLookupValue cardFingerprintLookup,
                                                 RiskRuntimeLookupValue ipLookup,
                                                 RiskRuntimeLookupValue emailLookup,
                                                 RiskRuntimeLookupValue phoneLookup,
                                                 RiskRuntimeLookupValue customerIdLookup,
                                                 RiskRuntimeLookupValue deviceFingerprintLookup);

    /**
     * 执行全部启用的交易频率规则并返回逐条审计明细。
     *
     * <p>默认实现兼容旧仓储，只返回首条命中；运行时仓储必须覆盖本方法，
     * 为每条已执行规则返回 PASS、HIT 或 ERROR 明细。</p>
     *
     * @param merchantId             商户号
     * @param requestDTO             风控请求
     * @param cardNoLookup           卡号查询值
     * @param cardFingerprintLookup  卡指纹查询值
     * @param ipLookup               IP 查询值
     * @param emailLookup            邮箱查询值
     * @param phoneLookup            手机号查询值
     * @param customerIdLookup       客户号查询值
     * @param deviceFingerprintLookup 设备指纹查询值
     * @return 每条启用频率规则的执行明细
     */
    default List<RiskListMatch> evaluateFrequencyRules(String merchantId,
                                                       RiskPaymentEvaluateRequestDTO requestDTO,
                                                       RiskRuntimeLookupValue cardNoLookup,
                                                       RiskRuntimeLookupValue cardFingerprintLookup,
                                                       RiskRuntimeLookupValue ipLookup,
                                                       RiskRuntimeLookupValue emailLookup,
                                                       RiskRuntimeLookupValue phoneLookup,
                                                       RiskRuntimeLookupValue customerIdLookup,
                                                       RiskRuntimeLookupValue deviceFingerprintLookup) {
        return findFrequencyRuleHit(merchantId, requestDTO, cardNoLookup, cardFingerprintLookup,
                ipLookup, emailLookup, phoneLookup, customerIdLookup, deviceFingerprintLookup)
                .map(List::of)
                .orElseGet(List::of);
    }

    /**
     * 后续风控节点阻断当前交易时释放频控成功名额预占。
     *
     * @param merchantId 当前交易商户号
     * @param transactionId 平台交易号
     */
    default void releaseFrequencySuccessReservations(String merchantId, String transactionId) {
    }

    /**
     * 判断当前商户是否存在启用的交易频率规则。
     *
     * @param merchantId 当前商户号
     * @return 存在可执行频率规则时返回 {@code true}
     */
    boolean hasActiveFrequencyRule(String merchantId);

    /**
     * 根据卡 BIN 查询发卡行国家或地区。
     *
     * @param cardBinLookup 已规范化为 BIN 区间数值的查询值
     * @return ISO 国家或地区代码；无有效 BIN 记录时返回空
     */
    Optional<String> findIssuerCountryByCardBin(RiskRuntimeLookupValue cardBinLookup);

    /**
     * 查询适用于本次交易的 3DS 风控规则。
     *
     * @param merchantId 当前商户号
     * @param paymentMethod 支付方式编码
     * @param cardBrand 卡品牌编码
     * @param amount 交易金额
     * @param currency ISO 4217 币种代码
     * @param currentRiskLevel 当前已计算的风险等级
     * @return 优先级最高的适用 3DS 规则；无规则时返回空
     */
    Optional<RiskListMatch> findThreeDsRule(String merchantId,
                                            String paymentMethod,
                                            String cardBrand,
                                            BigDecimal amount,
                                            String currency,
                                            String currentRiskLevel);
}
