package fi.vm.yti.codelist.intake.parser.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
import fi.vm.yti.codelist.common.dto.MemberDTO;
import fi.vm.yti.codelist.common.dto.MemberValueDTO;
import fi.vm.yti.codelist.common.dto.ValueTypeDTO;
import fi.vm.yti.codelist.intake.exception.CsvParsingException;
import fi.vm.yti.codelist.intake.exception.ExcelParsingException;
import fi.vm.yti.codelist.intake.exception.JsonParsingException;
import fi.vm.yti.codelist.intake.exception.MissingHeaderCodeValueException;
import fi.vm.yti.codelist.intake.exception.MissingRowValueCodeValueException;
import fi.vm.yti.codelist.intake.exception.YtiCodeListException;
import fi.vm.yti.codelist.intake.model.Extension;
import fi.vm.yti.codelist.intake.model.ValueType;
import fi.vm.yti.codelist.intake.parser.MemberParser;
import static fi.vm.yti.codelist.common.constants.ApiConstants.*;
import static fi.vm.yti.codelist.intake.exception.ErrorConstants.*;

@Component
public class MemberParserImpl extends AbstractBaseParser implements MemberParser {

    private static final Logger LOG = LoggerFactory.getLogger(MemberParserImpl.class);

    public MemberDTO parseMemberFromJson(final String jsonPayload) {
        final ObjectMapper mapper = createObjectMapper();
        final MemberDTO member;
        try {
            member = mapper.readValue(jsonPayload, MemberDTO.class);
            validateStartDateIsBeforeEndDate(member);
        } catch (final IOException e) {
            LOG.error("Member parsing failed from JSON!", e);
            throw new JsonParsingException(ERR_MSG_USER_406);
        }
        return member;
    }

    public Set<MemberDTO> parseMembersFromJson(final String jsonPayload) {
        final ObjectMapper mapper = createObjectMapper();
        final Set<MemberDTO> members;
        try {
            members = mapper.readValue(jsonPayload, new TypeReference<Set<MemberDTO>>() {
            });
        } catch (final IOException e) {
            LOG.error("Member parsing failed from JSON!", e);
            throw new JsonParsingException(ERR_MSG_USER_406);
        }
        members.forEach(this::validateStartDateIsBeforeEndDate);
        return members;
    }

    @SuppressFBWarnings("UC_USELESS_OBJECT")
    public Set<MemberDTO> parseMembersFromCsvInputStream(final Extension extension,
                                                         final InputStream inputStream) {
        final Set<ValueType> valueTypes = extension.getPropertyType().getValueTypes();
        final Set<MemberDTO> extensions = new LinkedHashSet<>();
        try (final InputStreamReader inputStreamReader = new InputStreamReader(new BOMInputStream(inputStream), StandardCharsets.UTF_8);
             final BufferedReader in = new BufferedReader(inputStreamReader);
             final CSVParser csvParser = new CSVParser(in, CSVFormat.newFormat(',').withQuote('"').withQuoteMode(QuoteMode.MINIMAL).withHeader())) {
            final Map<String, Integer> headerMap = csvParser.getHeaderMap();
            final Map<String, Integer> prefLabelHeaders = parseHeadersWithPrefix(headerMap, CONTENT_HEADER_PREFLABEL_PREFIX);
            validateRequiredHeaders(filterRequiredValueTypes(valueTypes), headerMap);
            final List<CSVRecord> records = csvParser.getRecords();
            for (final CSVRecord record : records) {
                validateRequiredDataOnRecord(filterRequiredValueTypes(valueTypes), record);
                final MemberDTO member = new MemberDTO();
                member.setId(parseIdFromRecord(record));
                member.setOrder(resolveOrderFromCsvRecord(record));
                member.setPrefLabel(parseLocalizedValueFromCsvRecord(prefLabelHeaders, record));
                if (!valueTypes.isEmpty()) {
                    final HashSet<MemberValueDTO> memberValues = new HashSet<>();
                    for (final ValueType valueType : valueTypes) {
                        final String headerName = valueType.getLocalName().toUpperCase();
                        if (headerMap.containsKey(headerName)) {
                            final String value = parseStringFromCsvRecord(record, headerName).trim();
                            if (!value.isEmpty()) {
                                memberValues.add(createMemberValueWithValue(value, valueType.getLocalName()));
                            } else if (valueType.getRequired()) {
                                throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_ROW_MISSING_MEMBERVALUE));
                            }
                        }
                    }
                    member.setMemberValues(memberValues);
                }
                member.setCode(createCodeUsingIdentifier(parseCodeIdentifierFromCsvRecord(record), String.valueOf(record.getRecordNumber() + 1)));
                final String relationCodeValue = parseMemberRelationFromCsvRecord(record);
                if (relationCodeValue != null) {
                    member.setRelatedMember(createMemberWithCodeAndCodeValue(relationCodeValue));
                }
                if (record.isMapped(CONTENT_HEADER_STARTDATE)) {
                    member.setStartDate(parseStartDateFromString(parseStartDateStringFromCsvRecord(record), String.valueOf(record.getRecordNumber() + 1)));
                }
                if (record.isMapped(CONTENT_HEADER_ENDDATE)) {
                    member.setEndDate(parseEndDateFromString(parseEndDateStringFromCsvRecord(record), String.valueOf(record.getRecordNumber() + 1)));
                }
                validateStartDateIsBeforeEndDate(member);
                extensions.add(member);
            }
        } catch (final IllegalArgumentException e) {
            LOG.error("Duplicate header value found in CSV!", e);
            throw new CsvParsingException(ERR_MSG_USER_DUPLICATE_HEADER_VALUE);
        } catch (final IOException e) {
            LOG.error("Error parsing CSV file!", e);
            throw new CsvParsingException(ERR_MSG_USER_ERROR_PARSING_CSV_FILE);
        }
        return extensions;
    }

    public Set<MemberDTO> parseMembersFromExcelWorkbook(final Extension extension,
                                                        final Workbook workbook,
                                                        final String sheetName) {
        final Set<ValueType> valueTypes = extension.getPropertyType().getValueTypes();
        final Set<MemberDTO> members = new LinkedHashSet<>();
        final DataFormatter formatter = new DataFormatter();
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            throw new ExcelParsingException(ERR_MSG_USER_ERROR_PARSING_EXCEL_FILE_SHEET_NOT_FOUND);
        }
        final Iterator<Row> rowIterator = sheet.rowIterator();
        Map<String, Integer> headerMap = null;
        Map<String, Integer> prefLabelHeaders = null;
        boolean firstRow = true;
        while (rowIterator.hasNext()) {
            final Row row = rowIterator.next();
            if (row == null) {
                continue;
            }
            if (firstRow) {
                firstRow = false;
                headerMap = resolveHeaderMap(row);
                prefLabelHeaders = parseHeadersWithPrefix(headerMap, CONTENT_HEADER_PREFLABEL_PREFIX);
                validateRequiredHeaders(valueTypes, headerMap);
            } else {
                final MemberDTO member = new MemberDTO();
                final String codeIdentifier = formatter.formatCellValue(row.getCell(headerMap.get(CONTENT_HEADER_CODE)));
                member.setCode(createCodeUsingIdentifier(codeIdentifier, String.valueOf(row.getRowNum())));
                validateRequiredDataOnRow(valueTypes, row, headerMap, formatter);
                if (headerMap.containsKey(CONTENT_HEADER_ID)) {
                    member.setId(parseUUIDFromString(formatter.formatCellValue(row.getCell(headerMap.get(CONTENT_HEADER_ID)))));
                }
                member.setPrefLabel(parseLocalizedValueFromExcelRow(prefLabelHeaders, row, formatter));
                member.setOrder(resolveOrderFromExcelRow(headerMap, row, formatter));
                if (valueTypes != null && !valueTypes.isEmpty()) {
                    final HashSet<MemberValueDTO> memberValues = new HashSet<>();
                    for (final ValueType valueType : valueTypes) {
                        if (headerMap.containsKey(valueType.getLocalName().toUpperCase())) {
                            final String headerName = valueType.getLocalName().toUpperCase();
                            final String value = formatter.formatCellValue(row.getCell(headerMap.get(headerName))).trim();
                            if (!value.isEmpty()) {
                                memberValues.add(createMemberValueWithValue(value, valueType.getLocalName()));
                            } else if (valueType.getRequired()) {
                                throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_ROW_MISSING_MEMBERVALUE));
                            }
                        }
                    }
                    member.setMemberValues(memberValues);
                }
                if (headerMap.containsKey(CONTENT_HEADER_RELATION)) {
                    final String relationCodeValue = formatter.formatCellValue(row.getCell(headerMap.get(CONTENT_HEADER_RELATION)));
                    if (relationCodeValue != null && !relationCodeValue.isEmpty()) {
                        member.setRelatedMember(createMemberWithCodeAndCodeValue(relationCodeValue));
                    }
                }
                if (headerMap.containsKey(CONTENT_HEADER_STARTDATE)) {
                    member.setStartDate(parseStartDateFromString(formatter.formatCellValue(row.getCell(headerMap.get(CONTENT_HEADER_STARTDATE))), String.valueOf(row.getRowNum())));
                }
                if (headerMap.containsKey(CONTENT_HEADER_ENDDATE)) {
                    member.setEndDate(parseEndDateFromString(formatter.formatCellValue(row.getCell(headerMap.get(CONTENT_HEADER_ENDDATE))), String.valueOf(row.getRowNum())));
                }
                validateStartDateIsBeforeEndDate(member);
                members.add(member);
            }
        }
        return members;
    }

    private MemberValueDTO createMemberValueWithValue(final String value,
                                                      final String valueTypeLocalName) {
        final MemberValueDTO memberValue = new MemberValueDTO();
        memberValue.setValue(value);
        final ValueTypeDTO valueType = new ValueTypeDTO();
        valueType.setLocalName(valueTypeLocalName);
        memberValue.setValueType(valueType);
        return memberValue;
    }

    private Set<ValueType> filterRequiredValueTypes(final Set<ValueType> valueTypes) {
        return valueTypes.stream().filter(ValueType::getRequired).collect(Collectors.toSet());
    }

    private void validateRequiredDataOnRow(final Set<ValueType> valueTypes,
                                           final Row row,
                                           final Map<String, Integer> headerMap,
                                           final DataFormatter formatter) {
        if (valueTypes != null) {
            final Set<ValueType> requiredValueTypes = filterRequiredValueTypes(valueTypes);
            if (!requiredValueTypes.isEmpty()) {
                requiredValueTypes.forEach(valueType -> {
                    if ((formatter.formatCellValue(row.getCell(headerMap.get(valueType.getLocalName().toUpperCase()))) == null ||
                        formatter.formatCellValue(row.getCell(headerMap.get(valueType.getLocalName().toUpperCase()))).isEmpty())) {
                        throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(),
                            ERR_MSG_USER_ROW_MISSING_MEMBERVALUE, String.valueOf(row.getRowNum() + 1)));
                    }
                });
            }
        }
        if (formatter.formatCellValue(row.getCell(headerMap.get(CONTENT_HEADER_CODE))) == null ||
            formatter.formatCellValue(row.getCell(headerMap.get(CONTENT_HEADER_CODE))).isEmpty()) {
            throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(),
                ERR_MSG_USER_ROW_MISSING_CODE, String.valueOf(row.getRowNum() + 1)));
        }
    }

    private void validateRequiredDataOnRecord(final Set<ValueType> requiredValueTypes,
                                              final CSVRecord record) {
        if (requiredValueTypes != null && !requiredValueTypes.isEmpty()) {
            requiredValueTypes.forEach(valueType -> {
                final String headerName = valueType.getLocalName().toUpperCase();
                if ((record.get(headerName) == null || record.get(headerName).isEmpty())) {
                    throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(),
                        ERR_MSG_USER_ROW_MISSING_MEMBERVALUE, String.valueOf(record.getRecordNumber() + 1)));
                }
            });
        }
        if (record.get(CONTENT_HEADER_CODE) == null || record.get(CONTENT_HEADER_CODE).isEmpty()) {
            throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(),
                ERR_MSG_USER_ROW_MISSING_CODE, String.valueOf(record.getRecordNumber() + 1)));
        }
    }

    private void validateRequiredHeaders(final Set<ValueType> requiredValueTypes,
                                         final Map<String, Integer> headerMap) {
        if (requiredValueTypes != null && !requiredValueTypes.isEmpty()) {
            requiredValueTypes.forEach(valueType -> {
                if (!headerMap.containsKey(valueType.getLocalName().toUpperCase())) {
                    throw new MissingHeaderCodeValueException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(),
                        ERR_MSG_USER_MISSING_HEADER_MEMBERVALUE));
                }
            });
        }
        if (!headerMap.containsKey(CONTENT_HEADER_ORDER)) {
            throw new MissingHeaderCodeValueException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(),
                ERR_MSG_USER_MISSING_HEADER_ORDER));
        }
        if (!headerMap.containsKey(CONTENT_HEADER_CODE)) {
            throw new MissingHeaderCodeValueException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(),
                ERR_MSG_USER_MISSING_HEADER_CODE));
        }
    }

    private String parseCodeIdentifierFromCsvRecord(final CSVRecord record) {
        return parseStringFromCsvRecord(record, CONTENT_HEADER_CODE);
    }

    private CodeDTO createCodeUsingIdentifier(final String identifier,
                                              final String rowIdentifier) {
        final CodeDTO code = new CodeDTO();
        if (identifier == null || identifier.isEmpty()) {
            throw new MissingRowValueCodeValueException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_ROW_MISSING_CODE, rowIdentifier));
        } else if (identifier.startsWith("http://uri.suomi.fi/codelist/")) {
            code.setUri(identifier);
        } else {
            code.setCodeValue(identifier);
        }
        return code;
    }

    private String parseMemberRelationFromCsvRecord(final CSVRecord record) {
        return parseStringFromCsvRecord(record, CONTENT_HEADER_RELATION);
    }

    private void validateStartDateIsBeforeEndDate(final MemberDTO member) {
        if (!startDateIsBeforeEndDateSanityCheck(member.getStartDate(), member.getEndDate())) {
            throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_END_BEFORE_START_DATE));
        }
    }

    private MemberDTO createMemberWithCodeAndCodeValue(final String codeValue) {
        final MemberDTO member = new MemberDTO();
        final CodeDTO refCode = new CodeDTO();
        refCode.setCodeValue(codeValue);
        member.setCode(refCode);
        return member;
    }

    public Set<MemberDTO> parseMembersFromExcelInputStream(final Extension extension,
                                                           final InputStream inputStream,
                                                           final String sheetName) {
        try (final Workbook workbook = WorkbookFactory.create(inputStream)) {
            return parseMembersFromExcelWorkbook(extension, workbook, sheetName);
        } catch (final InvalidFormatException | IOException | POIXMLException e) {
            LOG.error("Error parsing Excel file!", e);
            throw new ExcelParsingException(ERR_MSG_USER_ERROR_PARSING_EXCEL_FILE);
        }
    }
}