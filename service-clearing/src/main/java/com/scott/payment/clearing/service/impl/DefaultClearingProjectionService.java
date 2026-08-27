package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.domain.model.ClearingCompletionModels.LocatorFacts;
import com.scott.payment.clearing.domain.model.ClearingOperationFacts;
import com.scott.payment.clearing.domain.state.ClearingFailureCodeEnum;
import com.scott.payment.clearing.domain.state.ClearingProjectionStatusEnum;
import com.scott.payment.clearing.domain.state.ClearingStateEnum;
import com.scott.payment.clearing.entity.ClearingTransactionLocatorDO;
import com.scott.payment.clearing.entity.ClearingTransactionOrderDO;
import com.scott.payment.clearing.exception.ClearingProcessingException;
import com.scott.payment.clearing.mapper.ClearingTransactionContextMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionOperationMapper;
import com.scott.payment.clearing.service.ClearingProjectionService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultClearingProjectionService
 * @date : 2026-08-26 18:30
 * @email : scott_x@163.com
 * @description : 统一清分查询投影实现，以动作版本 CAS、生命周期真实分片定位和根主单行锁保证成功与失败路径口径一致。
 * @status : create
 */
@Service
public class DefaultClearingProjectionService implements ClearingProjectionService {

    private final ClearingTransactionOperationMapper operationMapper;
    private final ClearingTransactionContextMapper contextMapper;

    /**
     * 创建清分查询投影服务。
     *
     * @param operationMapper 动作查询投影 Mapper
     * @param contextMapper 生命周期定位和根主单投影 Mapper
     */
    public DefaultClearingProjectionService(ClearingTransactionOperationMapper operationMapper,
                                            ClearingTransactionContextMapper contextMapper) {
        this.operationMapper = operationMapper;
        this.contextMapper = contextMapper;
    }

    /** {@inheritDoc} */
    @Override
    public void updateWithLocator(ClearingOperationFacts operation,
                                  LocatorFacts currentLocator,
                                  ClearingStateEnum authoritativeStatus,
                                  String failureCode,
                                  LocalDateTime now) {
        requireArguments(operation, authoritativeStatus, now);
        validateCurrentLocator(currentLocator, operation);
        updateProjection(operation, currentLocator, authoritativeStatus, failureCode, now);
    }

    /** {@inheritDoc} */
    @Override
    public void updateResolvingLocator(ClearingOperationFacts operation,
                                       ClearingStateEnum authoritativeStatus,
                                       String failureCode,
                                       LocalDateTime now) {
        requireArguments(operation, authoritativeStatus, now);
        ClearingTransactionLocatorDO locator = contextMapper.selectLocator(
                operation.merchantId(), operation.transactionId());
        updateProjection(operation, toValidatedLocator(locator, operation), authoritativeStatus, failureCode, now);
    }

    private void updateProjection(ClearingOperationFacts operation,
                                  LocatorFacts currentLocator,
                                  ClearingStateEnum authoritativeStatus,
                                  String failureCode,
                                  LocalDateTime now) {
        ClearingProjectionStatusEnum operationStatus =
                ClearingProjectionStatusEnum.fromAuthoritative(authoritativeStatus);
        LocalDateTime completeTime = operationStatus.isCompleted() ? now : null;
        requireOne(operationMapper.updateClearingProjection(
                operation.transactionId(), operation.transactionDateTime(), requiredVersion(operation.operationVersion()),
                operationStatus.name(), completeTime, operationStatus.isCompleted() ? null : failureCode, now),
                "operation clearing projection CAS");

        List<ClearingTransactionLocatorDO> locatorRows = contextMapper.selectOperationLocators(
                operation.merchantId(), operation.operationId());
        List<LocatorFacts> locators = validateLifecycleLocators(locatorRows, currentLocator, operation);
        List<String> statuses = contextMapper.selectOperationClearingStatuses(locators);
        if (statuses == null || statuses.size() != locators.size()) {
            throw failure(ClearingFailureCodeEnum.TRANSACTION_VERSION_CONFLICT,
                    "transaction lifecycle clearing projections are incomplete");
        }
        String orderStatus;
        try {
            orderStatus = ClearingProjectionStatusEnum.aggregate(statuses).name();
        } catch (IllegalArgumentException exception) {
            throw failure(ClearingFailureCodeEnum.TRANSACTION_VERSION_CONFLICT,
                    "transaction lifecycle clearing projection is unsupported");
        }

        ClearingTransactionOrderDO order = contextMapper.selectOrderForUpdate(
                operation.operationId(), currentLocator.rootTransactionDateTime());
        if (order == null || !Objects.equals(order.getMerchantId(), operation.merchantId())
                || !Objects.equals(order.getOperationId(), operation.operationId())
                || !Objects.equals(order.getTransactionDateTime(), currentLocator.rootTransactionDateTime())
                || order.getVersion() == null) {
            throw failure(ClearingFailureCodeEnum.CLEARING_CAS_CONFLICT,
                    "root transaction order is unavailable or inconsistent");
        }
        requireOne(contextMapper.updateOrderClearingProjection(
                operation.operationId(), currentLocator.rootTransactionDateTime(),
                order.getVersion(), orderStatus, now), "order clearing projection CAS");
    }

    private LocatorFacts toValidatedLocator(ClearingTransactionLocatorDO row,
                                            ClearingOperationFacts operation) {
        if (row == null) {
            throw failure(ClearingFailureCodeEnum.TRANSACTION_VERSION_CONFLICT,
                    "current transaction locator is unavailable");
        }
        LocatorFacts locator;
        try {
            locator = new LocatorFacts(row.getTransactionId(), row.getOperationId(), row.getRootTransactionId(),
                    row.getMerchantId(), row.getMerchantOrderNo(), row.getTransactionType(),
                    row.getTransactionDateTime(), row.getRootTransactionDateTime());
        } catch (RuntimeException exception) {
            throw failure(ClearingFailureCodeEnum.TRANSACTION_VERSION_CONFLICT,
                    "current transaction locator is invalid");
        }
        validateCurrentLocator(locator, operation);
        return locator;
    }

    private List<LocatorFacts> validateLifecycleLocators(List<ClearingTransactionLocatorDO> rows,
                                                        LocatorFacts current,
                                                        ClearingOperationFacts operation) {
        if (rows == null || rows.isEmpty()) {
            throw failure(ClearingFailureCodeEnum.TRANSACTION_VERSION_CONFLICT,
                    "transaction lifecycle locators are unavailable");
        }
        List<LocatorFacts> result = new ArrayList<>();
        boolean currentFound = false;
        for (ClearingTransactionLocatorDO row : rows) {
            if (row == null || !Objects.equals(row.getMerchantId(), operation.merchantId())
                    || !Objects.equals(row.getOperationId(), operation.operationId())
                    || !Objects.equals(row.getRootTransactionId(), current.rootTransactionId())
                    || !Objects.equals(row.getRootTransactionDateTime(), current.rootTransactionDateTime())
                    || row.getTransactionDateTime() == null) {
                throw failure(ClearingFailureCodeEnum.TRANSACTION_VERSION_CONFLICT,
                        "transaction lifecycle locator is inconsistent");
            }
            currentFound |= Objects.equals(row.getTransactionId(), operation.transactionId())
                    && Objects.equals(row.getTransactionDateTime(), operation.transactionDateTime());
            result.add(new LocatorFacts(row.getTransactionId(), row.getOperationId(), row.getRootTransactionId(),
                    row.getMerchantId(), row.getMerchantOrderNo(), row.getTransactionType(),
                    row.getTransactionDateTime(), row.getRootTransactionDateTime()));
        }
        if (!currentFound) {
            throw failure(ClearingFailureCodeEnum.TRANSACTION_VERSION_CONFLICT,
                    "current transaction locator is absent from lifecycle");
        }
        return List.copyOf(result);
    }

    private void validateCurrentLocator(LocatorFacts locator, ClearingOperationFacts operation) {
        if (locator == null || !Objects.equals(locator.transactionId(), operation.transactionId())
                || !Objects.equals(locator.operationId(), operation.operationId())
                || !Objects.equals(locator.merchantId(), operation.merchantId())
                || !Objects.equals(locator.transactionDateTime(), operation.transactionDateTime())) {
            throw failure(ClearingFailureCodeEnum.TRANSACTION_VERSION_CONFLICT,
                    "current transaction locator is inconsistent");
        }
    }

    private void requireArguments(ClearingOperationFacts operation,
                                  ClearingStateEnum authoritativeStatus,
                                  LocalDateTime now) {
        if (operation == null || operation.transactionDateTime() == null
                || operation.operationVersion() == null || authoritativeStatus == null || now == null) {
            throw new IllegalArgumentException("clearing projection operation, status, version and time are required");
        }
    }

    private int requiredVersion(Integer version) {
        if (version == null || version < 0) {
            throw new IllegalArgumentException("transaction operation version is invalid");
        }
        return version;
    }

    private void requireOne(int affected, String action) {
        if (affected != 1) {
            throw failure(ClearingFailureCodeEnum.CLEARING_CAS_CONFLICT, action + " did not affect one row");
        }
    }

    private ClearingProcessingException failure(ClearingFailureCodeEnum code, String message) {
        return new ClearingProcessingException(code, message);
    }
}
