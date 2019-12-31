package fi.vm.yti.codelist.intake.service;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.data.domain.PageRequest;

import fi.vm.yti.codelist.common.dto.CodeDTO;
import fi.vm.yti.codelist.intake.model.Code;
import fi.vm.yti.codelist.intake.model.CodeScheme;

public interface CodeService {

    int getCodeCount();

    Set<CodeDTO> findAll();

    Set<CodeDTO> findAll(final PageRequest pageRequest);

    CodeDTO findById(final UUID codeId);

    Set<CodeDTO> findByCodeSchemeId(final UUID codeSchemeId);

    Set<CodeDTO> parseAndPersistCodesFromExcelWorkbook(final Workbook workbook,
                                                       final String sheetName,
                                                       final CodeScheme codeScheme,
                                                       final Set<CodeDTO> codeDTOsToBeDeleted,
                                                       final Set<CodeDTO> codeDTOsThatCouldNotBeDeletedDueToRestrictions);

    Set<CodeDTO> parseAndPersistCodesFromSourceData(final boolean isAuthorized,
                                                    final String codeRegistryCodeValue,
                                                    final String codeSchemeCodeValue,
                                                    final String format,
                                                    final InputStream inputStream,
                                                    final String jsonPayload,
                                                    final Set<CodeDTO> codeDTOsToBeDeleted,
                                                    final Set<CodeDTO> codeDTOsThatCouldNotBeDeletedDueToRestrictions);

    Set<CodeDTO> parseAndPersistCodesFromSourceData(final String codeRegistryCodeValue,
                                                    final String codeSchemeCodeValue,
                                                    final String format,
                                                    final InputStream inputStream,
                                                    final String jsonPayload,
                                                    final Set<CodeDTO> codeDTOsToBeDeleted,
                                                    final Set<CodeDTO> codeDTOsThatCouldNotBeDeletedDueToRestrictions);

    Set<CodeDTO> massChangeCodeStatuses(final String codeRegistryCodeValue,
                                        final String codeSchemeCodeValue,
                                        final String initialCodeStatus,
                                        final String endCodeStatus,
                                        final boolean skipValidation);

    Set<CodeDTO> parseAndPersistCodeFromJson(final String codeRegistryCodeValue,
                                             final String codeSchemeCodeValue,
                                             final String codeCodeValue,
                                             final String jsonPayload);

    CodeDTO deleteCode(final String codeRegistryCodeValue,
                       final String codeSchemeCodeValue,
                       final String codeCodeValue,
                       final Set<CodeDTO> affectedCodes);

    CodeDTO findByCodeRegistryCodeValueAndCodeSchemeCodeValueAndCodeValue(final String codeRegistryCodeValue,
                                                                          final String codeSchemeCodeValue,
                                                                          final String codeCodeValue);

    boolean canThisCodeBeDeleted (final Code codeToBeDeleted,
                                  final CodeScheme codeScheme,
                                  final String codeCodeValue,
                                  final boolean useExceptions);
}
