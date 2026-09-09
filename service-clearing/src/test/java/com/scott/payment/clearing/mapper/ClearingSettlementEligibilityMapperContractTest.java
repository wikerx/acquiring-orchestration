package com.scott.payment.clearing.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** 锁定交易结算周期查询与管理端年度日历状态的统一契约。 */
class ClearingSettlementEligibilityMapperContractTest {

    @Test
    void workdayQueriesShouldReadActiveCalendarYears() throws Exception {
        assertActiveCalendarQuery("selectNthConfirmedWorkday", LocalDate.class, int.class);
        assertActiveCalendarQuery("countConfirmedCalendarDays", LocalDate.class, LocalDate.class);
    }

    private void assertActiveCalendarQuery(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = ClearingSettlementEligibilityMapper.class.getMethod(methodName, parameterTypes);
        String sql = String.join("\n", method.getAnnotation(Select.class).value());

        assertThat(sql)
                .contains("calendar_year.year_status = 'ACTIVE'")
                .doesNotContain("calendar_year.year_status = 'CONFIRMED'");
    }
}
