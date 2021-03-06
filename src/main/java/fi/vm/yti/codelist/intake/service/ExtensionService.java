package fi.vm.yti.codelist.intake.service;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.data.domain.PageRequest;

import fi.vm.yti.codelist.common.dto.ExtensionDTO;
import fi.vm.yti.codelist.intake.model.CodeScheme;

public interface ExtensionService {

    int getExtensionCount();

    Set<ExtensionDTO> findAll();

    Set<ExtensionDTO> findAll(final PageRequest pageRequest);

    ExtensionDTO findById(final UUID id);

    Set<ExtensionDTO> findByCodeSchemeId(final UUID codeSchemeId);

    Set<ExtensionDTO> findByParentCodeSchemeId(final UUID codeSchemeId);

    ExtensionDTO findByCodeSchemeIdAndCodeValue(final UUID codeSchemeId,
                                                final String codeValue);

    Set<ExtensionDTO> parseAndPersistExtensionsFromSourceData(final String codeRegistryCodeValue,
                                                              final String codeSchemeCodeValue,
                                                              final String format,
                                                              final InputStream inputStream,
                                                              final String jsonPayload,
                                                              final String sheetName,
                                                              final boolean autoCreateMembers);

    Set<ExtensionDTO> parseAndPersistExtensionsFromExcelWorkbook(final CodeScheme codeScheme,
                                                                 final Workbook workbook,
                                                                 final String sheetName,
                                                                 final Map<ExtensionDTO, String> membersSheetNames,
                                                                 final boolean autoCreateMembers);

    ExtensionDTO parseAndPersistExtensionFromJson(final String codeRegistryCodeValue,
                                                  final String codeSchemeCodeValue,
                                                  final String extensionCodeValue,
                                                  final String jsonPayload,
                                                  final boolean autoCreateMembers);

    ExtensionDTO parseAndPersistExtensionFromJson(final UUID extensionId,
                                                  final String jsonPayload,
                                                  final boolean autoCreateMembers);

    ExtensionDTO deleteExtension(final UUID extensionId);
}
