package com.scott.payment.admin.support.risk;

import com.scott.payment.admin.dto.risk.TradeBlackWorkflowDTOs;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 系统交易加黑文件解析器，只读取交易定位字段。 */
@Component
public class TradeBlackUploadParser {

    private static final long MAX_FILE_SIZE = 5L * 1024L * 1024L;

    /** 将元数据和 XLSX/XLS/CSV 文件合并成统一预览请求。 */
    public TradeBlackWorkflowDTOs.PreviewRequest parse(String metadataJson, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("请选择需要上传的文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw invalid("上传文件不能超过5MB");
        }
        TradeBlackWorkflowDTOs.PreviewRequest request;
        try {
            request = JsonUtils.parseObject(metadataJson, TradeBlackWorkflowDTOs.PreviewRequest.class);
        } catch (RuntimeException exception) {
            throw invalid("文件上传参数格式不正确");
        }
        if (request == null) {
            throw invalid("文件上传参数不能为空");
        }
        request.setInputMode("FILE");
        request.setOrders(isExcel(file) ? readExcel(file) : readCsv(file));
        if (request.getOrders().isEmpty()) {
            throw invalid("文件中没有可处理的交易记录");
        }
        if (request.getOrders().size() > TradeBlackWorkflowDTOs.MAX_FILE_ORDERS) {
            throw invalid("文件最多包含5000笔交易");
        }
        return request;
    }

    private List<TradeBlackWorkflowDTOs.OrderReference> readCsv(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (!StringUtils.hasText(headerLine)) {
                return List.of();
            }
            List<String> headers = parseCsv(stripBom(headerLine));
            List<TradeBlackWorkflowDTOs.OrderReference> result = new ArrayList<>();
            String line;
            int rowNo = 1;
            while ((line = reader.readLine()) != null) {
                rowNo++;
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                addRow(result, rowNo, headers, parseCsv(line));
            }
            return result;
        } catch (IOException exception) {
            throw invalid("读取CSV文件失败");
        }
    }

    private List<TradeBlackWorkflowDTOs.OrderReference> readExcel(MultipartFile file) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                return List.of();
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            Row headerRow = findHeaderRow(sheet, formatter);
            if (headerRow == null) {
                return List.of();
            }
            List<String> headers = new ArrayList<>();
            for (int column = 0; column < headerRow.getLastCellNum(); column++) {
                headers.add(formatter.formatCellValue(headerRow.getCell(column)).trim());
            }
            List<TradeBlackWorkflowDTOs.OrderReference> result = new ArrayList<>();
            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                List<String> values = new ArrayList<>();
                for (int column = 0; column < headers.size(); column++) {
                    values.add(formatter.formatCellValue(row.getCell(column)).trim());
                }
                addRow(result, rowIndex + 1, headers, values);
            }
            return result;
        } catch (IOException | RuntimeException exception) {
            throw invalid("读取Excel文件失败");
        }
    }

    /** 在标题和导出信息行之后定位真正的交易定位表头。 */
    private Row findHeaderRow(Sheet sheet, DataFormatter formatter) {
        int lastScanRow = Math.min(sheet.getLastRowNum(), sheet.getFirstRowNum() + 9);
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= lastScanRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            for (int column = 0; column < row.getLastCellNum(); column++) {
                String header = normalizeHeader(formatter.formatCellValue(row.getCell(column)));
                if (Set.of("orderno", "ordernumber", "订单号", "交易订单号").contains(header)) {
                    return row;
                }
            }
        }
        return null;
    }

    private void addRow(List<TradeBlackWorkflowDTOs.OrderReference> target,
                        int rowNo,
                        List<String> headers,
                        List<String> values) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            row.put(normalizeHeader(headers.get(index)), index < values.size() ? values.get(index).trim() : "");
        }
        String orderNo = first(row, "orderno", "ordernumber", "订单号", "交易订单号");
        if (!StringUtils.hasText(orderNo)) {
            return;
        }
        TradeBlackWorkflowDTOs.OrderReference reference = new TradeBlackWorkflowDTOs.OrderReference();
        reference.setRowNo(rowNo);
        reference.setLookupMerchantId(first(
                row,
                "lookupmerchantid",
                "Source Merchant ID (Required for Merchant Order)",
                "来源商户号",
                "查询商户号",
                "查询来源商户号（商户订单号时必填）"
        ));
        reference.setIdentifierType(identifierType(first(row, "identifiertype", "订单号类型", "标识类型")));
        reference.setOrderNo(orderNo);
        target.add(reference);
    }

    private String identifierType(String value) {
        if (!StringUtils.hasText(value)) {
            return "SYSTEM_ORDER_NO";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("商户订单号".equals(value.trim()) || "MERCHANT ORDER NUMBER".equals(normalized)
                || "MERCHANT_ORDER_NO".equals(normalized)) {
            return "MERCHANT_ORDER_NO";
        }
        if ("系统订单号".equals(value.trim()) || "平台订单号".equals(value.trim())
                || "SYSTEM ORDER NUMBER".equals(normalized)
                || "SYSTEM_ORDER_NO".equals(normalized)) {
            return "SYSTEM_ORDER_NO";
        }
        return normalized;
    }

    private String normalizeHeader(String value) {
        return stripBom(value == null ? "" : value).trim().replace("_", "").replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private String first(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String value = row.get(normalizeHeader(key));
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isExcel(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        String normalized = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".xlsx") || normalized.endsWith(".xls")) {
            return true;
        }
        if (normalized.endsWith(".csv")) {
            return false;
        }
        throw invalid("仅支持XLSX、XLS、CSV文件");
    }

    private List<String> parseCsv(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char ch = line.charAt(index);
            if (ch == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private String stripBom(String value) {
        return value != null && value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private ServiceException invalid(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }
}
