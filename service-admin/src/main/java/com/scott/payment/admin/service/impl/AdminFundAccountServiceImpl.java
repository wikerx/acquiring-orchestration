package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.CurrencyBalanceResponse;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundAccountQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundAccountResponse;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDetailQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundLedgerResponse;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundRechargeCreateRequest;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundRechargeQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundRechargeResponse;
import com.scott.payment.admin.entity.fund.FundAccountEntities.MerchantFundAccountDO;
import com.scott.payment.admin.entity.fund.FundAccountEntities.MerchantFundLedgerDO;
import com.scott.payment.admin.entity.fund.FundAccountEntities.MerchantFundRechargeDO;
import com.scott.payment.admin.entity.fund.FundAccountEntities.PendingBalanceAggregate;
import com.scott.payment.admin.mapper.MerchantFundAccountMapper;
import com.scott.payment.admin.mapper.MerchantFundLedgerMapper;
import com.scott.payment.admin.mapper.MerchantFundRechargeMapper;
import com.scott.payment.admin.mapper.MerchantReserveItemMapper;
import com.scott.payment.admin.service.AdminFundAccountService;
import com.scott.payment.admin.service.AdminTransactionFundQueryService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminFundAccountServiceImpl
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 资金账户与充值审批实现；从交易副本实时聚合在途资金，并以账户锁串行化充值入账。
 * @status : create
 */
@Service
public class AdminFundAccountServiceImpl implements AdminFundAccountService {

    private static final String BUILTIN_ADMIN_ACCOUNT = "admin";
    private static final String PENDING_AUDIT = "PENDING_AUDIT";
    private static final String PENDING_RECHECK = "PENDING_RECHECK";
    private static final String POSTED = "POSTED";
    private static final String REJECTED = "REJECTED";
    private static final String NORMAL = "NORMAL";
    private static final String FROZEN = "FROZEN";
    private static final String CLOSED = "CLOSED";

    private final MerchantFundAccountMapper accountMapper;
    private final MerchantFundLedgerMapper ledgerMapper;
    private final MerchantFundRechargeMapper rechargeMapper;
    private final AdminTransactionFundQueryService transactionFundQueryService;
    private final MerchantReserveItemMapper reserveMapper;
    private final BaseMerchantInfoMapper merchantInfoMapper;

    /**
     * 构造资金账户领域服务，统一编排账户、不可变流水、充值审批和派生余额查询。
     *
     * @param accountMapper 资金账户查询、开户和行锁数据访问
     * @param ledgerMapper 不可变余额流水数据访问
     * @param rechargeMapper 充值申请及审批行锁数据访问
     * @param transactionFundQueryService 交易副本在途资金实时汇总服务
     * @param reserveMapper 保证金留存净额汇总数据访问
     * @param merchantInfoMapper 商户名称和账户归属查询数据访问
     */
    public AdminFundAccountServiceImpl(MerchantFundAccountMapper accountMapper,
                                       MerchantFundLedgerMapper ledgerMapper,
                                       MerchantFundRechargeMapper rechargeMapper,
                                       AdminTransactionFundQueryService transactionFundQueryService,
                                       MerchantReserveItemMapper reserveMapper,
                                       BaseMerchantInfoMapper merchantInfoMapper) {
        this.accountMapper = accountMapper;
        this.ledgerMapper = ledgerMapper;
        this.rechargeMapper = rechargeMapper;
        this.transactionFundQueryService = transactionFundQueryService;
        this.reserveMapper = reserveMapper;
        this.merchantInfoMapper = merchantInfoMapper;
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<FundAccountResponse> pageAccounts(FundAccountQuery request) {
        FundAccountQuery query = request == null ? new FundAccountQuery() : request;
        LambdaQueryWrapper<MerchantFundAccountDO> wrapper = Wrappers.<MerchantFundAccountDO>lambdaQuery()
                .eq(MerchantFundAccountDO::getDeleted, 0L)
                .eq(StringUtils.hasText(query.getAccountStatus()), MerchantFundAccountDO::getAccountStatus,
                        upper(query.getAccountStatus()))
                .eq(StringUtils.hasText(query.getSettlementCurrency()), MerchantFundAccountDO::getSettlementCurrency,
                        upper(query.getSettlementCurrency()))
                .orderByDesc(MerchantFundAccountDO::getUpdateTime);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            Set<String> merchantIds = merchantInfoMapper.selectList(Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                            .select(BaseMerchantInfoDO::getMerchantId)
                            .eq(BaseMerchantInfoDO::getDeleted, 0)
                            .like(BaseMerchantInfoDO::getMerchantName, keyword)).stream()
                    .map(BaseMerchantInfoDO::getMerchantId).collect(Collectors.toSet());
            wrapper.and(value -> {
                value.like(MerchantFundAccountDO::getAccountNo, keyword)
                        .or().like(MerchantFundAccountDO::getMerchantId, keyword);
                if (!merchantIds.isEmpty()) {
                    value.or().in(MerchantFundAccountDO::getMerchantId, merchantIds);
                }
            });
        }
        Page<MerchantFundAccountDO> page = accountMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()), wrapper);
        Map<String, String> names = merchantNames(page.getRecords().stream()
                .map(MerchantFundAccountDO::getMerchantId).collect(Collectors.toSet()));
        List<FundAccountResponse> records = page.getRecords().stream()
                .map(account -> toAccount(account, names.get(account.getMerchantId()), List.of(), null)).toList();
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.SLAVE)
    public FundAccountResponse getAccount(Long id) {
        MerchantFundAccountDO account = requireAccount(id);
        return toAccount(account, merchantNames(Set.of(account.getMerchantId())).get(account.getMerchantId()),
                transactionFundQueryService.sumPendingBalances(account.getMerchantId()).stream()
                        .map(this::toCurrencyBalance).toList(),
                reserveMapper.sumHeldBalance(account.getId(), account.getMerchantId()));
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<FundLedgerResponse> pageLedgers(Long accountId, FundDetailQuery request) {
        MerchantFundAccountDO account = requireAccount(accountId);
        FundDetailQuery query = request == null ? new FundDetailQuery() : request;
        LambdaQueryWrapper<MerchantFundLedgerDO> wrapper = Wrappers.<MerchantFundLedgerDO>lambdaQuery()
                .eq(MerchantFundLedgerDO::getAccountId, account.getId())
                .eq(MerchantFundLedgerDO::getMerchantId, account.getMerchantId())
                .eq(StringUtils.hasText(query.getBalanceType()), MerchantFundLedgerDO::getBalanceType,
                        upper(query.getBalanceType()))
                .eq(StringUtils.hasText(query.getBusinessType()), MerchantFundLedgerDO::getBusinessType,
                        upper(query.getBusinessType()))
                .eq(StringUtils.hasText(query.getDirection()), MerchantFundLedgerDO::getDirection,
                        upper(query.getDirection()))
                .ge(query.getPostedStartTime() != null, MerchantFundLedgerDO::getPostedTime,
                        query.getPostedStartTime())
                .le(query.getPostedEndTime() != null, MerchantFundLedgerDO::getPostedTime,
                        query.getPostedEndTime())
                .orderByDesc(MerchantFundLedgerDO::getPostedTime)
                .orderByDesc(MerchantFundLedgerDO::getId);
        validatePostedRange(query);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(value -> value.like(MerchantFundLedgerDO::getLedgerNo, keyword)
                    .or().like(MerchantFundLedgerDO::getBusinessNo, keyword)
                    .or().like(MerchantFundLedgerDO::getTransactionId, keyword)
                    .or().like(MerchantFundLedgerDO::getSummary, keyword));
        }
        Page<MerchantFundLedgerDO> page = ledgerMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()), wrapper);
        Map<String, MerchantFundRechargeDO> recharges = rechargeMap(page.getRecords());
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                page.getRecords().stream().map(row -> toLedger(row, account, null,
                        recharges.get(row.getBusinessNo()))).toList());
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<FundLedgerResponse> pageAllLedgers(FundDetailQuery request) {
        FundDetailQuery query = request == null ? new FundDetailQuery() : request;
        validatePostedRange(query);
        LambdaQueryWrapper<MerchantFundLedgerDO> wrapper = Wrappers.<MerchantFundLedgerDO>lambdaQuery()
                .eq(StringUtils.hasText(query.getMerchantId()), MerchantFundLedgerDO::getMerchantId,
                        trim(query.getMerchantId()))
                .eq(StringUtils.hasText(query.getCurrency()), MerchantFundLedgerDO::getCurrency,
                        upper(query.getCurrency()))
                .eq(StringUtils.hasText(query.getBalanceType()), MerchantFundLedgerDO::getBalanceType,
                        upper(query.getBalanceType()))
                .eq(StringUtils.hasText(query.getBusinessType()), MerchantFundLedgerDO::getBusinessType,
                        upper(query.getBusinessType()))
                .eq(StringUtils.hasText(query.getDirection()), MerchantFundLedgerDO::getDirection,
                        upper(query.getDirection()))
                .ge(query.getPostedStartTime() != null, MerchantFundLedgerDO::getPostedTime,
                        query.getPostedStartTime())
                .le(query.getPostedEndTime() != null, MerchantFundLedgerDO::getPostedTime,
                        query.getPostedEndTime())
                .orderByDesc(MerchantFundLedgerDO::getPostedTime)
                .orderByDesc(MerchantFundLedgerDO::getId);
        if (StringUtils.hasText(query.getAccountNo())) {
            List<Long> accountIds = accountMapper.selectList(Wrappers.<MerchantFundAccountDO>lambdaQuery()
                            .select(MerchantFundAccountDO::getId)
                            .eq(MerchantFundAccountDO::getAccountNo, query.getAccountNo().trim())
                            .eq(MerchantFundAccountDO::getDeleted, 0L))
                    .stream().map(MerchantFundAccountDO::getId).toList();
            if (accountIds.isEmpty()) {
                return PageResult.of(0L, query.safePageNo(), query.safePageSize(), List.of());
            }
            wrapper.in(MerchantFundLedgerDO::getAccountId, accountIds);
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(value -> value.like(MerchantFundLedgerDO::getLedgerNo, keyword)
                    .or().like(MerchantFundLedgerDO::getBusinessNo, keyword)
                    .or().like(MerchantFundLedgerDO::getTransactionId, keyword)
                    .or().like(MerchantFundLedgerDO::getSummary, keyword));
        }
        Page<MerchantFundLedgerDO> page = ledgerMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()), wrapper);
        Map<Long, MerchantFundAccountDO> accounts = accountMap(page.getRecords().stream()
                .map(MerchantFundLedgerDO::getAccountId).collect(Collectors.toSet()));
        Map<String, String> names = merchantNames(page.getRecords().stream()
                .map(MerchantFundLedgerDO::getMerchantId).collect(Collectors.toSet()));
        Map<String, MerchantFundRechargeDO> recharges = rechargeMap(page.getRecords());
        List<FundLedgerResponse> records = page.getRecords().stream().map(row -> {
            MerchantFundAccountDO account = accounts.get(row.getAccountId());
            return toLedger(row, account, names.get(row.getMerchantId()),
                    recharges.get(row.getBusinessNo()));
        }).toList();
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<FundRechargeResponse> pageRecharges(FundRechargeQuery request) {
        FundRechargeQuery query = request == null ? new FundRechargeQuery() : request;
        LambdaQueryWrapper<MerchantFundRechargeDO> wrapper = Wrappers.<MerchantFundRechargeDO>lambdaQuery()
                .eq(MerchantFundRechargeDO::getDeleted, 0L)
                .eq(StringUtils.hasText(query.getMerchantId()), MerchantFundRechargeDO::getMerchantId,
                        query.getMerchantId() == null ? null : query.getMerchantId().trim())
                .eq(StringUtils.hasText(query.getRechargeStatus()), MerchantFundRechargeDO::getRechargeStatus,
                        upper(query.getRechargeStatus()))
                .orderByDesc(MerchantFundRechargeDO::getCreateTime)
                .orderByDesc(MerchantFundRechargeDO::getId);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(value -> value.like(MerchantFundRechargeDO::getRechargeNo, keyword)
                    .or().like(MerchantFundRechargeDO::getRequestId, keyword)
                    .or().like(MerchantFundRechargeDO::getMerchantId, keyword));
        }
        Page<MerchantFundRechargeDO> page = rechargeMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()), wrapper);
        Map<Long, MerchantFundAccountDO> accounts = accountMap(page.getRecords().stream()
                .map(MerchantFundRechargeDO::getAccountId).collect(Collectors.toSet()));
        Map<String, String> names = merchantNames(page.getRecords().stream()
                .map(MerchantFundRechargeDO::getMerchantId).collect(Collectors.toSet()));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords().stream()
                .map(row -> toRecharge(row, accounts.get(row.getAccountId()), names.get(row.getMerchantId())))
                .toList());
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public FundRechargeResponse createRecharge(FundRechargeCreateRequest request,
                                               Long operatorId,
                                               String operatorName,
                                               String loginAccount) {
        MerchantFundAccountDO account = requireAccount(request.getAccountId());
        String requestId = request.getRequestId().trim();
        MerchantFundRechargeDO existing = findRechargeByRequestId(requestId);
        if (existing != null) {
            return resolveIdempotentRecharge(existing, account, request.getAmount());
        }
        LocalDateTime now = LocalDateTime.now();
        MerchantFundRechargeDO recharge = new MerchantFundRechargeDO();
        recharge.setRechargeNo(generateCode("RC"));
        recharge.setAccountId(account.getId());
        recharge.setMerchantId(account.getMerchantId());
        recharge.setCurrency(account.getSettlementCurrency());
        recharge.setAmount(request.getAmount());
        recharge.setRechargeStatus(PENDING_AUDIT);
        recharge.setRemark(request.getRemark().trim());
        recharge.setSubmitById(operatorId);
        recharge.setSubmitByName(operatorName);
        recharge.setSubmitLoginAccount(loginAccount);
        recharge.setSubmitTime(now);
        recharge.setRequestId(requestId);
        recharge.setCreateTime(now);
        recharge.setUpdateTime(now);
        recharge.setDeleted(0L);
        try {
            rechargeMapper.insert(recharge);
        } catch (DuplicateKeyException exception) {
            MerchantFundRechargeDO concurrentRecharge = rechargeMapper.selectByRequestIdForUpdate(requestId);
            if (concurrentRecharge == null) {
                throw exception;
            }
            return resolveIdempotentRecharge(concurrentRecharge, account, request.getAmount());
        }
        return toRecharge(recharge, account, merchantName(account.getMerchantId()));
    }

    /**
     * 解析相同请求号的充值申请；只有账户和金额完全一致时才视为幂等重试。
     *
     * @param existing 数据库中已占用请求号的充值申请
     * @param account 本次请求指定的资金账户
     * @param amount 本次请求金额，单位为账户结算币种
     * @return 可安全复用的已有充值申请
     * @throws ServiceException 请求号对应的账户或金额与本次请求不一致时抛出
     */
    private FundRechargeResponse resolveIdempotentRecharge(MerchantFundRechargeDO existing,
                                                            MerchantFundAccountDO account,
                                                            BigDecimal amount) {
        if (!existing.getAccountId().equals(account.getId()) || existing.getAmount().compareTo(amount) != 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请求号已被其他充值申请使用");
        }
        return rechargeResponse(existing);
    }

    /**
     * 按客户端请求号读取未删除的充值申请，数据库唯一键提供最终并发幂等保障。
     *
     * @param requestId 客户端生成的充值唯一请求号
     * @return 已存在的充值申请；首次请求时返回 null
     */
    private MerchantFundRechargeDO findRechargeByRequestId(String requestId) {
        return rechargeMapper.selectOne(Wrappers.<MerchantFundRechargeDO>lambdaQuery()
                .eq(MerchantFundRechargeDO::getRequestId, requestId)
                .eq(MerchantFundRechargeDO::getDeleted, 0L)
                .last("LIMIT 1"));
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public FundRechargeResponse auditRecharge(Long id,
                                              String comment,
                                              Long operatorId,
                                              String operatorName,
                                              String loginAccount) {
        MerchantFundRechargeDO recharge = requireLockedRecharge(id);
        requireStatus(recharge, PENDING_AUDIT);
        if (!isBuiltinAdmin(loginAccount) && sameOperator(recharge.getSubmitById(), operatorId)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "提交人不能审核自己提交的充值申请");
        }
        LocalDateTime now = LocalDateTime.now();
        recharge.setAuditById(operatorId);
        recharge.setAuditByName(operatorName);
        recharge.setAuditComment(trimToNull(comment));
        recharge.setAuditTime(now);
        recharge.setRechargeStatus(PENDING_RECHECK);
        recharge.setUpdateTime(now);
        rechargeMapper.updateById(recharge);
        return rechargeResponse(recharge);
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public FundRechargeResponse recheckRecharge(Long id,
                                                String comment,
                                                Long operatorId,
                                                String operatorName,
                                                String loginAccount) {
        MerchantFundRechargeDO recharge = requireLockedRecharge(id);
        requireStatus(recharge, PENDING_RECHECK);
        if (!isBuiltinAdmin(loginAccount) && sameOperator(recharge.getSubmitById(), operatorId)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "提交人不能复核自己提交的充值申请");
        }
        if (!isBuiltinAdmin(loginAccount) && sameOperator(recharge.getAuditById(), operatorId)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "普通账号的提交、审核和复核必须由三个人完成");
        }
        MerchantFundAccountDO account = accountMapper.selectByIdForUpdate(recharge.getAccountId());
        if (account == null || !account.getMerchantId().equals(recharge.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "充值资金账户不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        BigDecimal before = account.getAvailableBalance();
        BigDecimal after = before.add(recharge.getAmount());
        account.setAvailableBalance(after);
        account.setAccountVersion(account.getAccountVersion() + 1);
        account.setReverseRestricted(after.signum() < 0 ? 1 : 0);
        account.setAccountStatus(normalizeManualStatus(account.getAccountStatus()));
        account.setUpdateBy(operatorName);
        account.setUpdateTime(now);
        accountMapper.updateById(account);

        MerchantFundLedgerDO ledger = buildRechargeLedger(recharge, account, before, after,
                comment, operatorId, operatorName, now);
        ledgerMapper.insert(ledger);
        recharge.setRecheckById(operatorId);
        recharge.setRecheckByName(operatorName);
        recharge.setRecheckComment(trimToNull(comment));
        recharge.setRecheckTime(now);
        recharge.setRechargeStatus(POSTED);
        recharge.setLedgerNo(ledger.getLedgerNo());
        recharge.setPostedTime(now);
        recharge.setUpdateTime(now);
        rechargeMapper.updateById(recharge);
        return toRecharge(recharge, account, merchantName(account.getMerchantId()));
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public FundRechargeResponse rejectRecharge(Long id,
                                               String comment,
                                               Long operatorId,
                                               String operatorName,
                                               String loginAccount) {
        MerchantFundRechargeDO recharge = requireLockedRecharge(id);
        if (!Set.of(PENDING_AUDIT, PENDING_RECHECK).contains(recharge.getRechargeStatus())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "当前充值状态不允许驳回");
        }
        if (sameOperator(recharge.getSubmitById(), operatorId)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "提交人不能驳回自己提交的充值申请");
        }
        LocalDateTime now = LocalDateTime.now();
        recharge.setRechargeStatus(REJECTED);
        recharge.setRejectById(operatorId);
        recharge.setRejectByName(operatorName);
        recharge.setRejectComment(comment.trim());
        recharge.setRejectTime(now);
        recharge.setUpdateTime(now);
        rechargeMapper.updateById(recharge);
        return rechargeResponse(recharge);
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public FundAccountResponse changeAccountStatus(Long id,
                                                   Long expectedVersion,
                                                   String targetStatus,
                                                   String reason,
                                                   Long operatorId,
                                                   String operatorName) {
        MerchantFundAccountDO account = accountMapper.selectByIdForUpdate(id);
        if (account == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "资金账户不存在");
        }
        if (!account.getAccountVersion().equals(expectedVersion)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "账户状态已发生变化，请刷新后重试");
        }
        String currentStatus = normalizeManualStatus(account.getAccountStatus());
        String normalizedTarget = upper(targetStatus);
        validateStatusTransition(currentStatus, normalizedTarget);
        account.setAccountStatus(normalizedTarget);
        account.setReverseRestricted(account.getAvailableBalance().signum() < 0 ? 1 : 0);
        account.setAccountVersion(account.getAccountVersion() + 1);
        account.setUpdateBy(operatorName + "：" + reason.trim());
        account.setUpdateTime(LocalDateTime.now());
        accountMapper.updateById(account);
        return toAccount(account, merchantName(account.getMerchantId()), List.of(),
                reserveMapper.sumHeldBalance(account.getId(), account.getMerchantId()));
    }

    private FundAccountResponse toAccount(MerchantFundAccountDO account,
                                          String merchantName,
                                          List<CurrencyBalanceResponse> pendingBalances,
                                          BigDecimal reserveBalance) {
        FundAccountResponse response = new FundAccountResponse();
        response.setId(account.getId());
        response.setAccountNo(account.getAccountNo());
        response.setMerchantId(account.getMerchantId());
        response.setMerchantName(merchantName);
        response.setSettlementCurrency(account.getSettlementCurrency());
        response.setAvailableBalance(account.getAvailableBalance());
        response.setReserveBalance(reserveBalance);
        response.setAccountStatus(normalizeManualStatus(account.getAccountStatus()));
        response.setReverseRestricted(account.getReverseRestricted());
        applyAccountCapabilities(response);
        response.setAccountVersion(account.getAccountVersion());
        response.setCreateTime(account.getCreateTime());
        response.setUpdateTime(account.getUpdateTime());
        response.setPendingBalances(pendingBalances);
        return response;
    }

    /** 将标签币种在途聚合投影转换为账户详情响应。 */
    private CurrencyBalanceResponse toCurrencyBalance(PendingBalanceAggregate aggregate) {
        CurrencyBalanceResponse response = new CurrencyBalanceResponse();
        response.setCurrency(aggregate.getCurrency());
        response.setAmount(aggregate.getAmount());
        return response;
    }

    /**
     * 批量读取当前页充值流水对应的审批快照，避免详情展示产生逐行查询。
     *
     * @param ledgers 当前页不可变余额流水
     * @return 以充值单号为键的有效充值申请；没有充值流水时返回空映射
     */
    private Map<String, MerchantFundRechargeDO> rechargeMap(List<MerchantFundLedgerDO> ledgers) {
        Set<String> rechargeNos = ledgers.stream()
                .filter(row -> "RECHARGE".equals(row.getBusinessType()))
                .map(MerchantFundLedgerDO::getBusinessNo)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (rechargeNos.isEmpty()) {
            return Map.of();
        }
        List<MerchantFundRechargeDO> recharges = rechargeMapper.selectList(
                Wrappers.<MerchantFundRechargeDO>lambdaQuery()
                        .in(MerchantFundRechargeDO::getRechargeNo, rechargeNos)
                        .eq(MerchantFundRechargeDO::getDeleted, 0L));
        if (recharges == null || recharges.isEmpty()) {
            return Map.of();
        }
        return recharges.stream().collect(Collectors.toMap(MerchantFundRechargeDO::getRechargeNo,
                Function.identity(), (left, right) -> left));
    }

    /**
     * 将不可变余额流水及其可选充值审批快照转换为管理端响应。
     *
     * @param row 不可变余额流水
     * @param account 流水所属资金账户，数据异常时允许为空
     * @param merchantName 商户名称快照，账户内查询时允许为空
     * @param recharge 充值审批快照，非充值流水时为空
     * @return 管理端余额流水响应
     */
    private FundLedgerResponse toLedger(MerchantFundLedgerDO row,
                                        MerchantFundAccountDO account,
                                        String merchantName,
                                        MerchantFundRechargeDO recharge) {
        FundLedgerResponse response = new FundLedgerResponse();
        response.setId(row.getId());
        response.setLedgerNo(row.getLedgerNo());
        response.setLedgerGroupNo(row.getLedgerGroupNo());
        response.setAccountNo(account == null ? null : account.getAccountNo());
        response.setMerchantId(row.getMerchantId());
        response.setMerchantName(merchantName);
        response.setBalanceType(row.getBalanceType());
        response.setBusinessType(row.getBusinessType());
        response.setSummary(row.getSummary());
        response.setBusinessNo(row.getBusinessNo());
        response.setTransactionId(row.getTransactionId());
        response.setSettlementBatchNo(row.getSettlementBatchNo());
        response.setFeeDetailNo(row.getFeeDetailNo());
        response.setCurrency(row.getCurrency());
        response.setDirection(row.getDirection());
        response.setAmount(row.getAmount());
        response.setBalanceBefore(row.getBalanceBefore());
        response.setBalanceAfter(row.getBalanceAfter());
        response.setAccountSequence(row.getAccountSequence());
        response.setFeeVersionId(row.getFeeVersionId());
        response.setRateSnapshotId(row.getRateSnapshotId());
        response.setOperationMode(row.getOperationMode());
        response.setOperatorId(row.getOperatorId());
        response.setOperatorName(row.getOperatorName());
        response.setReviewerId(row.getReviewerId());
        response.setReviewerName(row.getReviewerName());
        response.setOperationReason(row.getOperationReason());
        response.setReviewComment(row.getReviewComment());
        response.setBusinessTime(row.getBusinessTime());
        response.setSubmitTime(row.getSubmitTime());
        response.setReviewTime(row.getReviewTime());
        response.setPostedTime(row.getPostedTime());
        response.setRequestId(row.getRequestId());
        response.setIdempotencyKey(row.getIdempotencyKey());
        response.setTraceId(row.getTraceId());
        response.setReversalOfLedgerId(row.getReversalOfLedgerId());
        if (recharge != null) {
            response.setRechargeDetail(toRecharge(recharge, account, merchantName));
        }
        return response;
    }

    /**
     * 在账户行锁保护下构造充值入账流水；账户序号和幂等键均由数据库唯一约束兜底。
     *
     * @param recharge 已完成审核、当前正在复核的充值申请
     * @param account 已持有排他锁的资金账户
     * @param before 充值前可用余额，单位为账户结算币种
     * @param after 充值后可用余额，单位为账户结算币种
     * @param recheckComment 复核意见，允许为空
     * @param recheckById 复核人账号主键
     * @param recheckByName 复核人名称快照
     * @param now 本次原子入账使用的统一系统时间
     * @return 待插入的不可变充值余额流水
     */
    private MerchantFundLedgerDO buildRechargeLedger(MerchantFundRechargeDO recharge,
                                                      MerchantFundAccountDO account,
                                                      BigDecimal before,
                                                      BigDecimal after,
                                                      String recheckComment,
                                                      Long recheckById,
                                                      String recheckByName,
                                                      LocalDateTime now) {
        MerchantFundLedgerDO ledger = new MerchantFundLedgerDO();
        ledger.setLedgerNo(generateCode("FL"));
        ledger.setLedgerGroupNo(recharge.getRechargeNo());
        ledger.setAccountId(account.getId());
        ledger.setMerchantId(account.getMerchantId());
        ledger.setBalanceType("AVAILABLE");
        ledger.setBusinessType("RECHARGE");
        ledger.setSummary("管理端充值入账");
        ledger.setBusinessNo(recharge.getRechargeNo());
        ledger.setCurrency(account.getSettlementCurrency());
        ledger.setDirection("CREDIT");
        ledger.setAmount(recharge.getAmount());
        ledger.setBalanceBefore(before);
        ledger.setBalanceAfter(after);
        ledger.setAccountSequence(ledgerMapper.selectMaxAccountSequence(account.getId()) + 1);
        ledger.setOperationMode("MANUAL");
        ledger.setOperatorId(recharge.getSubmitById());
        ledger.setOperatorName(recharge.getSubmitByName());
        ledger.setReviewerId(recheckById);
        ledger.setReviewerName(recheckByName);
        ledger.setOperationReason(recharge.getRemark());
        ledger.setReviewComment(joinReviewComments(recharge.getAuditComment(), recheckComment));
        ledger.setBusinessTime(recharge.getSubmitTime());
        ledger.setSubmitTime(recharge.getSubmitTime());
        ledger.setReviewTime(now);
        ledger.setPostedTime(now);
        ledger.setRequestId(recharge.getRequestId());
        ledger.setIdempotencyKey("FUND_RECHARGE:" + recharge.getRechargeNo());
        ledger.setCreateTime(now);
        return ledger;
    }

    private FundRechargeResponse rechargeResponse(MerchantFundRechargeDO recharge) {
        MerchantFundAccountDO account = requireAccount(recharge.getAccountId());
        return toRecharge(recharge, account, merchantName(recharge.getMerchantId()));
    }

    private FundRechargeResponse toRecharge(MerchantFundRechargeDO row,
                                            MerchantFundAccountDO account,
                                            String merchantName) {
        FundRechargeResponse response = new FundRechargeResponse();
        response.setId(row.getId());
        response.setRechargeNo(row.getRechargeNo());
        response.setAccountId(row.getAccountId());
        response.setAccountNo(account == null ? null : account.getAccountNo());
        response.setMerchantId(row.getMerchantId());
        response.setMerchantName(merchantName);
        response.setCurrency(row.getCurrency());
        response.setAmount(row.getAmount());
        response.setRechargeStatus(row.getRechargeStatus());
        response.setRemark(row.getRemark());
        response.setSubmitById(row.getSubmitById());
        response.setSubmitByName(row.getSubmitByName());
        response.setSubmitTime(row.getSubmitTime());
        response.setAuditById(row.getAuditById());
        response.setAuditByName(row.getAuditByName());
        response.setAuditComment(row.getAuditComment());
        response.setAuditTime(row.getAuditTime());
        response.setRecheckById(row.getRecheckById());
        response.setRecheckByName(row.getRecheckByName());
        response.setRecheckComment(row.getRecheckComment());
        response.setRecheckTime(row.getRecheckTime());
        response.setRejectById(row.getRejectById());
        response.setRejectByName(row.getRejectByName());
        response.setRejectComment(row.getRejectComment());
        response.setRejectTime(row.getRejectTime());
        response.setRequestId(row.getRequestId());
        response.setLedgerNo(row.getLedgerNo());
        response.setPostedTime(row.getPostedTime());
        response.setCreateTime(row.getCreateTime());
        response.setUpdateTime(row.getUpdateTime());
        return response;
    }

    /**
     * 通过主库排他锁读取充值申请，串行化审核、复核、驳回和最终入账。
     *
     * @param id 充值申请主键
     * @return 已持有数据库行锁的充值申请
     * @throws ServiceException 充值申请不存在或已逻辑删除时抛出
     */
    private MerchantFundRechargeDO requireLockedRecharge(Long id) {
        MerchantFundRechargeDO recharge = rechargeMapper.selectByIdForUpdate(id);
        if (recharge == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "充值申请不存在");
        }
        return recharge;
    }

    private void requireStatus(MerchantFundRechargeDO recharge, String expectedStatus) {
        if (!expectedStatus.equals(recharge.getRechargeStatus())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "充值申请状态已变化，请刷新后重试");
        }
    }

    private boolean sameOperator(Long left, Long right) {
        return left != null && left.equals(right);
    }

    private boolean isBuiltinAdmin(String loginAccount) {
        return BUILTIN_ADMIN_ACCOUNT.equals(loginAccount);
    }

    private String joinReviewComments(String auditComment, String recheckComment) {
        String audit = trimToNull(auditComment);
        String recheck = trimToNull(recheckComment);
        if (audit == null) return recheck;
        if (recheck == null) return audit;
        return "审核：" + audit + "；复核：" + recheck;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String generateCode(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase(Locale.ROOT);
    }

    private Map<Long, MerchantFundAccountDO> accountMap(Set<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return accountMapper.selectBatchIds(accountIds).stream()
                .collect(Collectors.toMap(MerchantFundAccountDO::getId, Function.identity(), (left, right) -> left));
    }

    private String merchantName(String merchantId) {
        return merchantNames(Set.of(merchantId)).get(merchantId);
    }

    private MerchantFundAccountDO requireAccount(Long id) {
        MerchantFundAccountDO account = accountMapper.selectOne(Wrappers.<MerchantFundAccountDO>lambdaQuery()
                .eq(MerchantFundAccountDO::getId, id)
                .eq(MerchantFundAccountDO::getDeleted, 0L)
                .last("LIMIT 1"));
        if (account == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "资金账户不存在");
        }
        return account;
    }

    private Map<String, String> merchantNames(Set<String> merchantIds) {
        if (merchantIds.isEmpty()) {
            return Map.of();
        }
        return merchantInfoMapper.selectList(Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                        .in(BaseMerchantInfoDO::getMerchantId, merchantIds)
                        .eq(BaseMerchantInfoDO::getDeleted, 0)).stream()
                .collect(Collectors.toMap(BaseMerchantInfoDO::getMerchantId,
                        BaseMerchantInfoDO::getMerchantName, (left, right) -> left));
    }

    /** 将历史负余额状态归一为正常人工状态，负余额限制仅由独立标识表达。 */
    private String normalizeManualStatus(String status) {
        return "NEGATIVE_BALANCE".equals(status) ? NORMAL : status;
    }

    /**
     * 校验人工状态机，关闭账户仅允许通过恢复操作回到正常状态。
     */
    private void validateStatusTransition(String currentStatus, String targetStatus) {
        if (!Set.of(NORMAL, FROZEN, CLOSED).contains(currentStatus)
                || !Set.of(NORMAL, FROZEN, CLOSED).contains(targetStatus)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "资金账户状态不合法");
        }
        if (currentStatus.equals(targetStatus)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "资金账户已经处于目标状态");
        }
        boolean allowed = NORMAL.equals(currentStatus) && Set.of(FROZEN, CLOSED).contains(targetStatus)
                || FROZEN.equals(currentStatus) && Set.of(NORMAL, CLOSED).contains(targetStatus)
                || CLOSED.equals(currentStatus) && NORMAL.equals(targetStatus);
        if (!allowed) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "当前资金账户状态不允许该操作");
        }
    }

    /**
     * 按人工状态和负余额限制生成统一账户能力，供管理端和后续资金链路复用。
     *
     * <p>关闭账户仍允许人工充值；冻结账户允许结算入账但禁止提现、转出和主动逆向交易。</p>
     *
     * @param response 已包含账户状态和负余额限制标识的响应对象
     */
    private void applyAccountCapabilities(FundAccountResponse response) {
        boolean normal = NORMAL.equals(response.getAccountStatus());
        boolean frozen = FROZEN.equals(response.getAccountStatus());
        response.setCreditAllowed(true);
        response.setDebitAllowed(normal);
        response.setWithdrawalAllowed(normal);
        response.setSettlementAllowed(normal || frozen);
        response.setReverseTransactionAllowed(normal && response.getReverseRestricted() != null
                && response.getReverseRestricted() == 0);
    }

    /**
     * 拒绝结束时间早于开始时间的入账范围，避免产生不透明的空结果。
     *
     * @param query 余额流水筛选条件
     * @throws ServiceException 入账结束时间早于开始时间时抛出
     */
    private void validatePostedRange(FundDetailQuery query) {
        if (query.getPostedStartTime() != null && query.getPostedEndTime() != null
                && query.getPostedEndTime().isBefore(query.getPostedStartTime())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "入账结束时间不能早于开始时间");
        }
    }

    /** 去除查询条件首尾空白，空值保持为空。 */
    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
