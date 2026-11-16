package com.scott.payment.admin.application.base;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.application.base.cache.CardBinCacheInvalidationCoordinator;
import com.scott.payment.admin.dto.base.CardBinDTOs;
import com.scott.payment.admin.entity.base.CardBinEntities;
import com.scott.payment.admin.mapper.BaseCardBinImportBatchMapper;
import com.scott.payment.admin.mapper.BaseCardBinRangeMapper;
import com.scott.payment.admin.service.AdminDictService;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.iso.mapper.IsoCountryMapper;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.model.ExcelExportRequest;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.Locale;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 旧同步卡 BIN 导出必须在数据库查询处受限，超量时提示使用异步下载中心。
 */
class AdminCardBinLegacyExportLimitTests {

    @Test
    void overLimitShouldRejectWithoutBuildingAnExcelFile() {
        BaseCardBinRangeMapper mapper = mock(BaseCardBinRangeMapper.class);
        ExcelExportService excelExportService = mock(ExcelExportService.class);
        ExcelLocaleResolver localeResolver = mock(ExcelLocaleResolver.class);
        when(localeResolver.resolveCurrentLocale()).thenReturn(Locale.SIMPLIFIED_CHINESE);
        Page<CardBinEntities.BaseCardBinRangeDO> page = new Page<>(1, 5_001, false);
        page.setRecords(Collections.nCopies(5_001, new CardBinEntities.BaseCardBinRangeDO()));
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        AdminBaseCardBinApplicationService service = new AdminBaseCardBinApplicationService(
                mapper,
                mock(BaseCardBinImportBatchMapper.class),
                mock(AdminDictService.class),
                mock(IsoCountryMapper.class),
                excelExportService,
                mock(ExcelI18nMessageResolver.class),
                localeResolver,
                mock(CardBinCacheInvalidationCoordinator.class));

        assertThatThrownBy(() -> service.export(new CardBinDTOs.CardBinQueryRequest(),
                mock(HttpServletResponse.class)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("异步导出");

        ArgumentCaptor<Page<CardBinEntities.BaseCardBinRangeDO>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(mapper).selectPage(pageCaptor.capture(), any(Wrapper.class));
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(5_001);
        verifyNoInteractions(excelExportService);
    }

    @Test
    void smallLegacyExportShouldStillCreateExcel() {
        BaseCardBinRangeMapper mapper = mock(BaseCardBinRangeMapper.class);
        AdminDictService dictService = mock(AdminDictService.class);
        ExcelExportService excelExportService = mock(ExcelExportService.class);
        ExcelLocaleResolver localeResolver = mock(ExcelLocaleResolver.class);
        when(localeResolver.resolveCurrentLocale()).thenReturn(Locale.SIMPLIFIED_CHINESE);
        when(dictService.listDictData(any())).thenReturn(List.of());
        Page<CardBinEntities.BaseCardBinRangeDO> page = new Page<>(1, 5_001, false);
        page.setRecords(List.of(new CardBinEntities.BaseCardBinRangeDO()));
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        AdminBaseCardBinApplicationService service = new AdminBaseCardBinApplicationService(
                mapper, mock(BaseCardBinImportBatchMapper.class), dictService,
                mock(IsoCountryMapper.class), excelExportService,
                mock(ExcelI18nMessageResolver.class), localeResolver,
                mock(CardBinCacheInvalidationCoordinator.class));
        HttpServletResponse response = mock(HttpServletResponse.class);

        service.export(new CardBinDTOs.CardBinQueryRequest(), response);

        verify(excelExportService).export(any(ExcelExportRequest.class), org.mockito.ArgumentMatchers.eq(response));
    }
}
