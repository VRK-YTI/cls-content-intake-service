package fi.vm.yti.codelist.intake.service.impl;

import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.vm.yti.codelist.common.dto.CodeDTO;
import fi.vm.yti.codelist.common.dto.ErrorModel;
import fi.vm.yti.codelist.common.dto.ExtensionDTO;
import fi.vm.yti.codelist.common.dto.MemberDTO;
import fi.vm.yti.codelist.intake.dao.CodeDao;
import fi.vm.yti.codelist.intake.dao.CodeSchemeDao;
import fi.vm.yti.codelist.intake.dao.ExtensionDao;
import fi.vm.yti.codelist.intake.dao.MemberDao;
import fi.vm.yti.codelist.intake.exception.NotFoundException;
import fi.vm.yti.codelist.intake.exception.UnauthorizedException;
import fi.vm.yti.codelist.intake.exception.YtiCodeListException;
import fi.vm.yti.codelist.intake.model.CodeScheme;
import fi.vm.yti.codelist.intake.model.Extension;
import fi.vm.yti.codelist.intake.model.Member;
import fi.vm.yti.codelist.intake.parser.MemberParser;
import fi.vm.yti.codelist.intake.security.AuthorizationManager;
import fi.vm.yti.codelist.intake.service.ExtensionService;
import fi.vm.yti.codelist.intake.service.MemberService;
import static fi.vm.yti.codelist.common.constants.ApiConstants.*;
import static fi.vm.yti.codelist.intake.exception.ErrorConstants.*;

@Singleton
@Service
public class MemberServiceImpl implements MemberService {

    private final AuthorizationManager authorizationManager;
    private final MemberDao memberDao;
    private final MemberParser memberParser;
    private final ExtensionDao extensionDao;
    private final CodeSchemeDao codeSchemeDao;
    private final DtoMapperService dtoMapperService;
    private final CodeDao codeDao;
    private final ExtensionService extensionService;

    @Inject
    public MemberServiceImpl(final AuthorizationManager authorizationManager,
                             final MemberDao memberDao,
                             final MemberParser memberParser,
                             final ExtensionDao extensionDao,
                             final CodeSchemeDao codeSchemeDao,
                             final DtoMapperService dtoMapperService,
                             final CodeDao codeDao,
                             final ExtensionService extensionService) {
        this.authorizationManager = authorizationManager;
        this.memberDao = memberDao;
        this.memberParser = memberParser;
        this.extensionDao = extensionDao;
        this.codeSchemeDao = codeSchemeDao;
        this.dtoMapperService = dtoMapperService;
        this.codeDao = codeDao;
        this.extensionService = extensionService;
    }

    @Transactional
    public MemberDTO deleteMember(final UUID id,
                                  final Set<MemberDTO> affectedMembers) {
        final Member memberToBeDeleted = memberDao.findById(id);
        if (memberToBeDeleted != null) {
            if (!authorizationManager.canMemberBeDeleted(memberToBeDeleted)) {
                throw new UnauthorizedException(new ErrorModel(HttpStatus.UNAUTHORIZED.value(), ERR_MSG_USER_401));
            }
            final Set<Member> relatedMembers = memberDao.findByRelatedMemberId(memberToBeDeleted.getId());
            relatedMembers.forEach(relatedMember -> {
                relatedMember.setRelatedMember(null);
                memberDao.save(relatedMember);
            });
            affectedMembers.addAll(dtoMapperService.mapDeepMemberDtos(relatedMembers));
            final MemberDTO memberToBeDeletedDto = dtoMapperService.mapDeepMemberDto(memberToBeDeleted);
            memberDao.delete(memberToBeDeleted);
            return memberToBeDeletedDto;
        } else {
            throw new NotFoundException();
        }
    }

    @Transactional
    public int getMemberCount() {
        return memberDao.getMemberCount();
    }

    @Transactional
    public Set<MemberDTO> findAll() {
        return dtoMapperService.mapDeepMemberDtos(memberDao.findAll());
    }

    @Transactional
    public Set<MemberDTO> findAll(final PageRequest pageRequest) {
        final Set<Member> members = memberDao.findAll(pageRequest);
        return dtoMapperService.mapDeepMemberDtos(members);
    }

    @Transactional
    public MemberDTO findById(final UUID id) {
        return dtoMapperService.mapDeepMemberDto(memberDao.findById(id));
    }

    @Transactional
    public Set<MemberDTO> findByCodeId(final UUID id) {
        return dtoMapperService.mapDeepMemberDtos(memberDao.findByCodeId(id));
    }

    @Transactional
    public Set<MemberDTO> findByRelatedMemberCode(final CodeDTO code) {
        return dtoMapperService.mapDeepMemberDtos(memberDao.findByRelatedMemberCode(codeDao.findById(code.getId())));
    }

    @Transactional
    public Set<MemberDTO> findByExtensionId(final UUID id) {
        return dtoMapperService.mapDeepMemberDtos(memberDao.findByExtensionId(id));
    }

    @Transactional
    public Set<MemberDTO> parseAndPersistMemberFromJson(final String jsonPayload) {
        Set<Member> members;
        if (jsonPayload != null && !jsonPayload.isEmpty()) {
            final MemberDTO memberDto = memberParser.parseMemberFromJson(jsonPayload);
            if (memberDto.getExtension() != null) {
                final Extension extension = extensionDao.findById(memberDto.getExtension().getId());
                if (!authorizationManager.canBeModifiedByUserInOrganization(extension.getParentCodeScheme().getOrganizations())) {
                    throw new UnauthorizedException(new ErrorModel(HttpStatus.UNAUTHORIZED.value(), ERR_MSG_USER_401));
                }
                members = memberDao.updateMemberEntityFromDto(extension, memberDto);
            } else {
                throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_EXTENSION_NOT_FOUND));
            }
        } else {
            throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_JSON_PAYLOAD_EMPTY));
        }
        if (members == null) {
            throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_MEMBERS_ARE_EMPTY));
        }
        return dtoMapperService.mapDeepMemberDtos(members);
    }

    @Transactional
    public Set<MemberDTO> parseAndPersistMembersFromSourceData(final String codeRegistryCodeValue,
                                                               final String codeSchemeCodeValue,
                                                               final String extensionCodeValue,
                                                               final String format,
                                                               final InputStream inputStream,
                                                               final String jsonPayload,
                                                               final String sheetName,
                                                               final Map<String, LinkedHashSet<MemberDTO>> memberDTOsToBeDeletedPerExtension) {
        final CodeScheme codeScheme = codeSchemeDao.findByCodeRegistryCodeValueAndCodeValue(codeRegistryCodeValue, codeSchemeCodeValue);
        if (codeScheme != null) {
            if (!authorizationManager.canBeModifiedByUserInOrganization(codeScheme.getOrganizations())) {
                throw new UnauthorizedException(new ErrorModel(HttpStatus.UNAUTHORIZED.value(), ERR_MSG_USER_401));
            }
            final Set<Member> members;
            final Extension extension = extensionDao.findByParentCodeSchemeIdAndCodeValue(codeScheme.getId(), extensionCodeValue);
            if (extension != null) {
                switch (format.toLowerCase()) {
                    case FORMAT_JSON:
                        if (jsonPayload != null && !jsonPayload.isEmpty()) {
                            members = memberDao.updateMemberEntitiesFromDtos(extension, memberParser.parseMembersFromJson(jsonPayload));
                        } else {
                            throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_JSON_PAYLOAD_EMPTY));
                        }
                        break;
                    case FORMAT_EXCEL:
                        final Set<MemberDTO> memberDtos = memberParser.parseMembersFromExcelInputStream(extension, inputStream, sheetName, memberDTOsToBeDeletedPerExtension, codeScheme.getStatus());
                        memberDtos.forEach(memberDto -> {
                            Member correspondingMemberFromDb = memberDao.findByExtensionAndSequenceId(extension, memberDto.getSequenceId());
                            memberDto.setId(correspondingMemberFromDb == null ? UUID.randomUUID() : correspondingMemberFromDb.getId());
                        });
                        members = memberDao.updateMemberEntitiesFromDtos(extension, memberDtos);
                        LinkedHashSet<MemberDTO> memberDTOsToBeDeleted = memberDTOsToBeDeletedPerExtension.get(extension.getUri());
                        if (memberDTOsToBeDeleted.size() > 0) {
                            Set<Member> memberEntitiesToDelete = new LinkedHashSet<>();
                            memberDTOsToBeDeleted.forEach(memberDTO -> {
                                Member correspondingMemberFromDb = memberDao.findByExtensionAndSequenceId(extension, memberDTO.getSequenceId());
                                memberEntitiesToDelete.add(correspondingMemberFromDb);
                                memberDTO.setId(correspondingMemberFromDb.getId()); // need this later for indexing purposes
                            });
                            memberDao.delete(memberEntitiesToDelete);
                        }
                        break;
                    case FORMAT_CSV:
                        final Set<MemberDTO> memberDtossFromCsv = memberParser.parseMembersFromCsvInputStream(extension, inputStream);
                        memberDtossFromCsv.forEach(memberDto -> {
                            Member correspondingMemberFromDb = memberDao.findByExtensionAndSequenceId(extension, memberDto.getSequenceId());
                            memberDto.setId(correspondingMemberFromDb == null ? UUID.randomUUID() : correspondingMemberFromDb.getId());
                        });
                        members = memberDao.updateMemberEntitiesFromDtos(extension, memberDtossFromCsv);
                        break;
                    default:
                        throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_INVALID_FORMAT));
                }
                return dtoMapperService.mapDeepMemberDtos(members);
            } else {
                throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_EXTENSION_NOT_FOUND));
            }
        } else {
            throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_CODESCHEME_NOT_FOUND));
        }
    }

    @Transactional
    public Set<MemberDTO> parseAndPersistMembersFromExcelWorkbook(final Extension extension,
                                                                  final Workbook workbook,
                                                                  final String sheetName,
                                                                  final Map<String, LinkedHashSet<MemberDTO>> memberDTOsToBeDeletedPerExtension,
                                                                  final CodeScheme codeScheme) {
        if (!authorizationManager.canBeModifiedByUserInOrganization(extension.getParentCodeScheme().getOrganizations())) {
            throw new UnauthorizedException(new ErrorModel(HttpStatus.UNAUTHORIZED.value(), ERR_MSG_USER_401));
        }
        Set<Member> members = null;

        final Set<MemberDTO> memberDtos = memberParser.parseMembersFromExcelWorkbook(extension, workbook, sheetName, memberDTOsToBeDeletedPerExtension, codeScheme.getStatus());

        if (memberDtos == null || memberDtos.isEmpty()) {
            return null;
        }

        members = memberDao.updateMemberEntitiesFromDtos(extension, memberDtos);


        LinkedHashSet<MemberDTO> memberDTOsToBeDeleted = memberDTOsToBeDeletedPerExtension.get(extension.getUri());
        if (memberDTOsToBeDeleted != null && memberDTOsToBeDeleted.size() > 0) {
            Set<Member> memberEntitiesToDelete = new LinkedHashSet<>();

            memberDTOsToBeDeleted.forEach(memberDTO -> {
                Member correspondingMemberFromDb = memberDao.findByExtensionAndSequenceId(extension, memberDTO.getSequenceId());
                memberEntitiesToDelete.add(correspondingMemberFromDb);
                memberDTO.setId(correspondingMemberFromDb.getId()); // need this later for indexing purposes
                extension.getMembers().removeIf(member -> member.getId().compareTo(correspondingMemberFromDb.getId()) == 0);
            });

/*            memberDao.delete(memberEntitiesToDelete);
            extensionDao.save(extension);
            codeScheme.getExtensions().removeIf(extension1 -> extension1.getId().compareTo(extension.getId()) == 0 );
            codeScheme.getExtensions().add(extension);
            codeSchemeDao.save(codeScheme);*/

        }

        return dtoMapperService.mapDeepMemberDtos(members);
    }

    @Transactional
    public Set<MemberDTO> createMissingMembersForAllCodesOfAllCodelistsOfAnExtension(final ExtensionDTO extension) {
        final Set<Member> createdMembers = memberDao.createMissingMembersForAllCodesOfAllCodelistsOfAnExtension(extension);
        return dtoMapperService.mapDeepMemberDtos(createdMembers);
    }
}
