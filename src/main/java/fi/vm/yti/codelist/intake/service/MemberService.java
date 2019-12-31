package fi.vm.yti.codelist.intake.service;

import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.data.domain.PageRequest;

import fi.vm.yti.codelist.common.dto.CodeDTO;
import fi.vm.yti.codelist.common.dto.ExtensionDTO;
import fi.vm.yti.codelist.common.dto.MemberDTO;
import fi.vm.yti.codelist.intake.model.CodeScheme;
import fi.vm.yti.codelist.intake.model.Extension;

public interface MemberService {

    int getMemberCount();

    MemberDTO deleteMember(final UUID id,
                           final Set<MemberDTO> affectedMembers);

    Set<MemberDTO> findAll();

    Set<MemberDTO> findAll(final PageRequest pageRequest);

    MemberDTO findById(final UUID id);

    Set<MemberDTO> findByCodeId(final UUID id);

    Set<MemberDTO> findByExtensionId(final UUID id);

    Set<MemberDTO> findByRelatedMemberCode(final CodeDTO code);

    Set<MemberDTO> parseAndPersistMemberFromJson(final String jsonPayload);

    Set<MemberDTO> parseAndPersistMembersFromSourceData(final String codeRegistryCodeValue,
                                                        final String codeSchemeCodeValue,
                                                        final String extensionCodeValue,
                                                        final String format,
                                                        final InputStream inputStream,
                                                        final String jsonPayload,
                                                        final String sheetname,
                                                        final Map<String, LinkedHashSet<MemberDTO>> memberDTOsToBeDeletedPerExtension);

    Set<MemberDTO> parseAndPersistMembersFromExcelWorkbook(final Extension extension,
                                                           final Workbook workbook,
                                                           final String sheetName,
                                                           final Map<String, LinkedHashSet<MemberDTO>> memberDTOsToBeDeletedPerExtension,
                                                           final CodeScheme codeScheme);

    Set<MemberDTO> createMissingMembersForAllCodesOfAllCodelistsOfAnExtension(final ExtensionDTO extension);
}
