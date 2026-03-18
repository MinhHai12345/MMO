package com.store.huyen.importer.util;

public class ExcelUtils {
    public static <T> List<T> mapRowsToModel(Sheet sheet, Class<T> modelClass) {
        List<T> results = new ArrayList<>();

        if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
            return results;
        }

        // === 1️⃣ Đọc header row (dòng đầu tiên) ===
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return results;
        }

        List<String> headers = new ArrayList<>();
        for (Cell cell : headerRow) {
            headers.add(getCellStringValue(cell));
        }

        // === 2️⃣ Map các dòng dữ liệu ===
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            try {
                T instance = modelClass.getDeclaredConstructor().newInstance();

                for (int col = 0; col < headers.size(); col++) {
                    String header = headers.get(col);
                    Field field = ReflectionUtils.findField(modelClass, header);
                    if (field == null) continue;

                    field.setAccessible(true);
                    String cellValue = getCellStringValue(row.getCell(col));
                    Object converted = convertValue(cellValue, field.getType());
                    ReflectionUtils.setField(field, instance, converted);
                }

                results.add(instance);
            } catch (Exception e) {
                System.err.println("Error mapping row " + i + ": " + e.getMessage());
            }
        }

        return results;
    }

    public static Workbook loadWorkbook(InputStream inputStream) {
        try {
            return WorkbookFactory.create(inputStream);
        } catch (Exception e) {
            throw new RuntimeException("Error reading Excel file", e);
        }
    }

    private static String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                } else {
                    yield BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private static Object convertValue(String value, Class<?> targetType) {
        if (value == null || value.isBlank()) return null;
        if (targetType.equals(String.class)) return value;
        if (targetType.equals(Integer.class) || targetType.equals(int.class)) return Integer.valueOf(value);
        if (targetType.equals(Long.class) || targetType.equals(long.class)) return Long.valueOf(value);
        if (targetType.equals(Double.class) || targetType.equals(double.class)) return Double.valueOf(value);
        if (targetType.equals(BigDecimal.class)) return new BigDecimal(value);
        if (targetType.equals(Boolean.class) || targetType.equals(boolean.class)) return Boolean.valueOf(value);
        return value;
    }
}
