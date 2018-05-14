package fi.vm.yti.codelist.intake.parser.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.QuoteMode;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.poi.POIXMLException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import fi.vm.yti.codelist.common.dto.CodeDTO;
import fi.vm.yti.codelist.common.dto.ErrorModel;
import fi.vm.yti.codelist.common.dto.ExtensionDTO;
import fi.vm.yti.codelist.common.dto.ExtensionSchemeDTO;
import fi.vm.yti.codelist.intake.exception.CsvParsingException;
import fi.vm.yti.codelist.intake.exception.ExcelParsingException;
import fi.vm.yti.codelist.intake.exception.JsonParsingException;
import fi.vm.yti.codelist.intake.exception.MissingHeaderClassificationException;
import fi.vm.yti.codelist.intake.exception.MissingHeaderCodeValueException;
import fi.vm.yti.codelist.intake.exception.MissingRowValueCodeValueException;
import fi.vm.yti.codelist.intake.exception.MissingRowValueStatusException;
import fi.vm.yti.codelist.intake.exception.YtiCodeListException;
import fi.vm.yti.codelist.intake.parser.ExtensionParser;
import static fi.vm.yti.codelist.common.constants.ApiConstants.*;
import static fi.vm.yti.codelist.intake.exception.ErrorConstants.*;

@Component
public class ExtensionParserImpl extends AbstractBaseParser implements ExtensionParser {

    private static final Logger LOG = LoggerFactory.getLogger(ExtensionParserImpl.class);

    public ExtensionDTO parseExtensionFromJson(final String jsonPayload) {
        final ObjectMapper mapper = createObjectMapper();
        final ExtensionDTO extension;
        try {
            extension = mapper.readValue(jsonPayload, ExtensionDTO.class);
        } catch (final IOException e) {
            LOG.error("Extension parsing failed from JSON!", e);
            throw new JsonParsingException(ERR_MSG_USER_406);
        }
        return extension;
    }

    public Set<ExtensionDTO> parseExtensionsFromJson(final String jsonPayload) {
        final ObjectMapper mapper = createObjectMapper();
        final Set<ExtensionDTO> extensions;
        try {
            extensions = mapper.readValue(jsonPayload, new TypeReference<List<ExtensionDTO>>() {
            });
        } catch (final IOException e) {
            LOG.error("Extension parsing failed from JSON!", e);
            throw new JsonParsingException(ERR_MSG_USER_406);
        }
        return extensions;
    }

    @SuppressFBWarnings("UC_USELESS_OBJECT")
    public Set<ExtensionDTO> parseExtensionsFromCsvInputStream(final InputStream inputStream) {
        final Set<ExtensionDTO> extensionSchemes = new HashSet<>();
        try (final InputStreamReader inputStreamReader = new InputStreamReader(new BOMInputStream(inputStream), StandardCharsets.UTF_8);
             final BufferedReader in = new BufferedReader(inputStreamReader);
             final CSVParser csvParser = new CSVParser(in, CSVFormat.newFormat(',').withQuote('"').withQuoteMode(QuoteMode.MINIMAL).withHeader())) {
            final Map<String, Integer> headerMap = csvParser.getHeaderMap();
            validateRequiredSchemeHeaders(headerMap);
            final List<CSVRecord> records = csvParser.getRecords();
            for (final CSVRecord record : records) {
                validateRequiredDataOnRecord(record);
                final ExtensionDTO extension = new ExtensionDTO();
                extension.setId(parseIdFromRecord(record));
                extension.setExtensionOrder(parseExtensionOrderCsvRecord(record));
                extension.setExtensionValue(parseExtensionValueFromCsvRecord(record));
                final CodeDTO code = new CodeDTO();
                code.setCodeValue(parseCodeFromCsvRecord(record));
                extension.setCode(code);
                final ExtensionDTO refExtension = new ExtensionDTO();
                final UUID id = UUID.fromString(parseExtensionIdFromCsvRecord(record));
                refExtension.setId(id);
                extension.setExtension(refExtension);
                extensionSchemes.add(extension);
            }
        } catch (final IllegalArgumentException e) {
            LOG.error("Duplicate header value found in CSV!", e);
            throw new CsvParsingException(ERR_MSG_USER_DUPLICATE_HEADER_VALUE);
        } catch (final IOException e) {
            LOG.error("Error parsing CSV file!", e);
            throw new CsvParsingException(ERR_MSG_USER_ERROR_PARSING_CSV_FILE);
        }
        return extensionSchemes;
    }

    public Set<ExtensionDTO> parseExtensionsFromExcelInputStream(final InputStream inputStream,
                                                                 final String sheetName) {
        try (final Workbook workbook = WorkbookFactory.create(inputStream)) {
            return parseExtensionsFromExcelWorkbook(workbook, sheetName);
        } catch (final InvalidFormatException | IOException | POIXMLException e) {
            LOG.error("Error parsing Excel file!", e);
            throw new ExcelParsingException(ERR_MSG_USER_ERROR_PARSING_EXCEL_FILE);
        }
    }

    public Set<ExtensionDTO> parseExtensionsFromExcelWorkbook(final Workbook workbook,
                                                              final String sheetName) {
        final Set<ExtensionDTO> extensions = new HashSet<>();
        final DataFormatter formatter = new DataFormatter();
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            sheet = workbook.getSheetAt(0);
        }
        final Iterator<Row> rowIterator = sheet.rowIterator();
        Map<String, Integer> headerMap = null;
        boolean firstRow = true;
        while (rowIterator.hasNext()) {
            final Row row = rowIterator.next();
            if (firstRow) {
                firstRow = false;
                headerMap = resolveHeaderMap(row);
                validateRequiredSchemeHeaders(headerMap);
            } else {
                validateRequiredDataOnRow(row, headerMap, formatter);
                final ExtensionDTO extension = new ExtensionDTO();
                if (headerMap.containsKey(CONTENT_HEADER_ID)) {
                    extension.setId(parseUUIDFromString(formatter.formatCellValue(row.getCell(headerMap.get(CONTENT_HEADER_ID)))));
                }
                extension.setExtensionOrder(Integer.parseInt(formatter.formatCellValue(row.getCell(headerMap.get(CONTENT_HEADER_EXTENSIONORDER)))));
                extension.setExtensionValue(formatter.formatCellValue(row.getCell(headerMap.get(CONTENT_HEADER_EXTENSIONVALUE))));
                final String codeCodeValue = formatter.formatCellValue(row.getCell(headerMap.get(CONTENT_HEADER_CODE)));
                if (codeCodeValue != null && !codeCodeValue.isEmpty()) {
                    final CodeDTO code = new CodeDTO();
                    code.setCodeValue(codeCodeValue);
                    extension.setCode(code);
                }
                if (headerMap.containsKey(CONTENT_HEADER_EXTENSIONID)) {
                    final UUID refExtensionId = parseUUIDFromString(formatter.formatCellValue(row.getCell(headerMap.get(CONTENT_HEADER_EXTENSIONID))));
                    if (refExtensionId != null) {
                        final ExtensionDTO refExtension = new ExtensionDTO();
                        refExtension.setId(refExtensionId);
                        extension.setExtension(refExtension);
                    }
                }
                extensions.add(extension);
            }
        }
        return extensions;
    }

    private void validateRequiredDataOnRow(final Row row,
                                           final Map<String, Integer> headerMap,
                                           final DataFormatter formatter) {
        if (formatter.formatCellValue(row.getCell(headerMap.get(CONTENT_HEADER_EXTENSIONVALUE))) == null ||
            formatter.formatCellValue(row.getCell(headerMap.get(CONTENT_HEADER_EXTENSIONVALUE))).isEmpty()) {
            throw new MissingRowValueCodeValueException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(),
                ERR_MSG_USER_ROW_MISSING_CODEVALUE, String.valueOf(row.getRowNum() + 1)));
        }
    }

    private void validateRequiredDataOnRecord(final CSVRecord record) {
        if (record.get(CONTENT_HEADER_EXTENSIONVALUE) == null || record.get(CONTENT_HEADER_EXTENSIONVALUE).isEmpty()) {
            throw new MissingRowValueCodeValueException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(),
                ERR_MSG_USER_ROW_MISSING_EXTENSIONVALUE, String.valueOf(record.getRecordNumber() + 1)));
        }
        if (record.get(CONTENT_HEADER_EXTENSIONORDER) == null || record.get(CONTENT_HEADER_EXTENSIONORDER).isEmpty()) {
            throw new MissingRowValueCodeValueException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(),
                CONTENT_HEADER_EXTENSIONORDER, String.valueOf(record.getRecordNumber() + 1)));
        }
        if (record.get(CONTENT_HEADER_CODE) == null || record.get(CONTENT_HEADER_CODE).isEmpty()) {
            throw new MissingRowValueCodeValueException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(),
                ERR_MSG_USER_ROW_MISSING_CODE, String.valueOf(record.getRecordNumber() + 1)));
        }
    }

    private void validateRequiredSchemeHeaders(final Map<String, Integer> headerMap) {
        if (!headerMap.containsKey(CONTENT_HEADER_EXTENSIONVALUE)) {
            throw new MissingHeaderCodeValueException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(),
                CONTENT_HEADER_EXTENSIONVALUE));
        }
        if (!headerMap.containsKey(CONTENT_HEADER_EXTENSIONORDER)) {
            throw new MissingHeaderCodeValueException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(),
                CONTENT_HEADER_EXTENSIONORDER));
        }
        if (!headerMap.containsKey(CONTENT_HEADER_CODE)) {
            throw new MissingHeaderCodeValueException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(),
                CONTENT_HEADER_CODE));
        }
    }

    private Integer parseExtensionOrderCsvRecord(final CSVRecord record) {
        final String value = parseStringFromCsvRecord(record, CONTENT_HEADER_EXTENSIONORDER);
        if (value != null) {
            return Integer.parseInt(value);
        } else {
            return null;
        }
    }

    private String parseCodeFromCsvRecord(final CSVRecord record) {
        return parseStringFromCsvRecord(record, CONTENT_HEADER_CODE);
    }

    private String parseExtensionValueFromCsvRecord(final CSVRecord record) {
        return parseStringFromCsvRecord(record, CONTENT_HEADER_EXTENSIONVALUE);
    }

    private String parseExtensionIdFromCsvRecord(final CSVRecord record) {
        return parseStringFromCsvRecord(record, CONTENT_HEADER_EXTENSIONID);
    }
}
