package fi.vm.yti.codelist.intake.service.impl;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.transaction.Transactional;

import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import fi.vm.yti.codelist.common.dto.CodeDTO;
import fi.vm.yti.codelist.intake.dao.CodeDao;
import fi.vm.yti.codelist.intake.dao.CodeRegistryDao;
import fi.vm.yti.codelist.intake.dao.CodeSchemeDao;
import fi.vm.yti.codelist.intake.dao.ExternalReferenceDao;
import fi.vm.yti.codelist.intake.exception.UnauthorizedException;
import fi.vm.yti.codelist.intake.exception.YtiCodeListException;
import fi.vm.yti.codelist.intake.model.Code;
import fi.vm.yti.codelist.intake.model.CodeRegistry;
import fi.vm.yti.codelist.intake.model.CodeScheme;
import fi.vm.yti.codelist.intake.model.ErrorModel;
import fi.vm.yti.codelist.intake.model.ExternalReference;
import fi.vm.yti.codelist.intake.parser.CodeParser;
import fi.vm.yti.codelist.intake.security.AuthorizationManager;
import fi.vm.yti.codelist.intake.service.CodeService;
import static fi.vm.yti.codelist.common.constants.ApiConstants.*;
import static fi.vm.yti.codelist.intake.exception.ErrorConstants.*;

@Singleton
@Service
public class CodeServiceImpl extends BaseService implements CodeService {

    private static final Logger LOG = LoggerFactory.getLogger(CodeServiceImpl.class);
    private final AuthorizationManager authorizationManager;
    private final CodeRegistryDao codeRegistryDao;
    private final CodeSchemeDao codeSchemeDao;
    private final CodeDao codeDao;
    private final ExternalReferenceDao externalReferenceDao;
    private final CodeParser codeParser;

    @Inject
    public CodeServiceImpl(final AuthorizationManager authorizationManager,
                           final CodeRegistryDao codeRegistryDao,
                           final CodeSchemeDao codeSchemeDao,
                           final CodeParser codeParser,
                           final CodeDao codeDao,
                           final ExternalReferenceDao externalReferenceDao) {
        this.authorizationManager = authorizationManager;
        this.codeRegistryDao = codeRegistryDao;
        this.codeSchemeDao = codeSchemeDao;
        this.codeParser = codeParser;
        this.codeDao = codeDao;
        this.externalReferenceDao = externalReferenceDao;
    }

    @Transactional
    public Set<CodeDTO> findAll() {
        return mapDeepCodeDtos(codeDao.findAll());
    }

    @Transactional
    public CodeDTO findByCodeSchemeAndCodeValueAndBroaderCodeId(final CodeScheme codeScheme,
                                                                final String codeValue,
                                                                final UUID broaderCodeId) {
        return mapDeepCodeDto(codeDao.findByCodeSchemeAndCodeValueAndBroaderCodeId(codeScheme, codeValue, broaderCodeId));
    }

    @Transactional
    public Set<CodeDTO> findByCodeSchemeId(final UUID codeSchemeId) {
        return mapDeepCodeDtos(codeDao.findByCodeSchemeId(codeSchemeId));
    }

    @Transactional
    public Set<CodeDTO> parseAndPersistCodesFromExcelWorkbook(final String codeRegistryCodeValue,
                                                              final String codeSchemeCodeValue,
                                                              final Workbook workbook) {
        Set<Code> codes;
        final CodeRegistry codeRegistry = codeRegistryDao.findByCodeValue(codeRegistryCodeValue);
        if (codeRegistry != null) {
            if (!authorizationManager.canBeModifiedByUserInOrganization(codeRegistry.getOrganizations())) {
                throw new UnauthorizedException(new ErrorModel(HttpStatus.UNAUTHORIZED.value(), ERR_MSG_USER_401));
            }
            final CodeScheme codeScheme = codeSchemeDao.findByCodeRegistryAndCodeValue(codeRegistry, codeSchemeCodeValue);
            final HashMap<String, String> broaderCodeMapping = new HashMap<>();
            if (codeScheme != null) {
                final Set<CodeDTO> codeDtos = codeParser.parseCodesFromExcelWorkbook(workbook, broaderCodeMapping);
                codes = codeDao.updateCodesFromDtos(codeScheme, codeDtos, broaderCodeMapping);
            } else {
                throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_406));
            }
        } else {
            throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_406));
        }
        return mapDeepCodeDtos(codes);
    }

    @Transactional
    public Set<CodeDTO> parseAndPersistCodesFromSourceData(final String codeRegistryCodeValue,
                                                           final String codeSchemeCodeValue,
                                                           final String format,
                                                           final InputStream inputStream,
                                                           final String jsonPayload) {
        Set<Code> codes;
        final CodeRegistry codeRegistry = codeRegistryDao.findByCodeValue(codeRegistryCodeValue);
        if (codeRegistry != null) {
            if (!authorizationManager.canBeModifiedByUserInOrganization(codeRegistry.getOrganizations())) {
                throw new UnauthorizedException(new ErrorModel(HttpStatus.UNAUTHORIZED.value(), ERR_MSG_USER_401));
            }
            final CodeScheme codeScheme = codeSchemeDao.findByCodeRegistryAndCodeValue(codeRegistry, codeSchemeCodeValue);
            final HashMap<String, String> broaderCodeMapping = new HashMap<>();
            if (codeScheme != null) {
                switch (format.toLowerCase()) {
                    case FORMAT_JSON:
                        if (jsonPayload != null && !jsonPayload.isEmpty()) {
                            final Set<CodeDTO> codeDtos = codeParser.parseCodesFromJsonData(jsonPayload);
                            codes = codeDao.updateCodesFromDtos(codeScheme, codeDtos, broaderCodeMapping);
                            final Map<String, Code> codeMap = new HashMap<>();
                            codes.forEach(code -> codeMap.put(code.getCodeValue(), code));
                            codeDtos.forEach(codeDto -> {
                                final Set<ExternalReference> externalReferences = externalReferenceDao.updateExternalReferenceEntitiesFromDtos(codeDto.getExternalReferences(), codeScheme);
                                final Code code = codeMap.get(codeDto.getCodeValue());
                                code.setExternalReferences(externalReferences);
                            });
                            codeDao.save(codes);
                        } else {
                            throw new YtiCodeListException(new ErrorModel(HttpStatus.INTERNAL_SERVER_ERROR.value(), ERR_MSG_USER_500));
                        }
                        break;
                    case FORMAT_EXCEL:
                        codes = codeDao.updateCodesFromDtos(codeScheme, codeParser.parseCodesFromExcelInputStream(inputStream, broaderCodeMapping), broaderCodeMapping);
                        break;
                    case FORMAT_CSV:
                        codes = codeDao.updateCodesFromDtos(codeScheme, codeParser.parseCodesFromCsvInputStream(inputStream, broaderCodeMapping), broaderCodeMapping);
                        break;
                    default:
                        throw new YtiCodeListException(new ErrorModel(HttpStatus.INTERNAL_SERVER_ERROR.value(), ERR_MSG_USER_500));
                }
            } else {
                throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_406));
            }
        } else {
            throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_406));
        }
        return mapDeepCodeDtos(codes);
    }

    @Transactional
    public CodeDTO parseAndPersistCodeFromJson(final String codeRegistryCodeValue,
                                               final String codeSchemeCodeValue,
                                               final String codeCodeValue,
                                               final String jsonPayload) {
        Code code = null;
        final CodeRegistry codeRegistry = codeRegistryDao.findByCodeValue(codeRegistryCodeValue);
        if (codeRegistry != null) {
            if (!authorizationManager.canBeModifiedByUserInOrganization(codeRegistry.getOrganizations())) {
                throw new UnauthorizedException(new ErrorModel(HttpStatus.UNAUTHORIZED.value(), ERR_MSG_USER_401));
            }
            final CodeScheme codeScheme = codeSchemeDao.findByCodeRegistryAndCodeValue(codeRegistry, codeSchemeCodeValue);
            if (codeScheme != null) {
                try {
                    if (jsonPayload != null && !jsonPayload.isEmpty()) {
                        final CodeDTO codeDto = codeParser.parseCodeFromJsonData(jsonPayload);
                        if (!codeDto.getCodeValue().equalsIgnoreCase(codeCodeValue)) {
                            throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_PATH_CODE_MISMATCH));
                        }
                        code = codeDao.updateCodeFromDto(codeScheme, codeDto);
                        final Set<ExternalReference> externalReferences = externalReferenceDao.updateExternalReferenceEntitiesFromDtos(codeDto.getExternalReferences(), codeScheme);
                        code.setExternalReferences(externalReferences);
                        codeDao.save(code);
                    } else {
                        throw new YtiCodeListException(new ErrorModel(HttpStatus.INTERNAL_SERVER_ERROR.value(), ERR_MSG_USER_500));
                    }
                } catch (final YtiCodeListException e) {
                    throw e;
                } catch (final Exception e) {
                    LOG.error("Caught exception in parseAndPersistCodeFromJson.", e);
                    throw new YtiCodeListException(new ErrorModel(HttpStatus.INTERNAL_SERVER_ERROR.value(), ERR_MSG_USER_500));
                }
            } else {
                throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_406));
            }
        } else {
            throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_406));
        }
        return mapDeepCodeDto(code);
    }

    private Set<CodeDTO> decreaseChildHierarchyLevel(final UUID broaderCodeId) {
        final Set<Code> childCodes = codeDao.findByBroaderCodeId(broaderCodeId);
        childCodes.forEach(code -> {
            code.setHierarchyLevel(code.getHierarchyLevel() - 1);
            if (code.getBroaderCodeId() != null) {
                decreaseChildHierarchyLevel(code.getBroaderCodeId());
            }
        });
        codeDao.save(childCodes);
        return mapDeepCodeDtos(childCodes);
    }

    @Transactional
    public Set<CodeDTO> removeBroaderCodeId(final UUID broaderCodeId) {
        final Set<CodeDTO> updateCodes = new HashSet<>();
        final Set<Code> childCodes = codeDao.findByBroaderCodeId(broaderCodeId);
        if (childCodes != null && !childCodes.isEmpty()) {
            childCodes.forEach(code -> {
                code.setBroaderCodeId(null);
                code.setHierarchyLevel(1);
                updateCodes.addAll(decreaseChildHierarchyLevel(code.getId()));
            });
            updateCodes.addAll(mapDeepCodeDtos(childCodes));
            codeDao.save(childCodes);
        }
        return updateCodes;
    }

    @Transactional
    public CodeDTO deleteCode(final String codeRegistryCodeValue,
                              final String codeSchemeCodeValue,
                              final String codeCodeValue) {
        if (authorizationManager.isSuperUser()) {
            final CodeScheme codeScheme = codeSchemeDao.findByCodeRegistryCodeValueAndCodeValue(codeRegistryCodeValue, codeSchemeCodeValue);
            final Code code = codeDao.findByCodeSchemeAndCodeValue(codeScheme, codeCodeValue);
            final CodeDTO codeDto = mapCodeDto(code, false);
            codeDao.delete(code);
            return codeDto;
        } else {
            throw new UnauthorizedException(new ErrorModel(HttpStatus.UNAUTHORIZED.value(), ERR_MSG_USER_401));
        }
    }

    @Transactional
    @Nullable
    public CodeDTO findByCodeRegistryCodeValueAndCodeSchemeCodeValueAndCodeValue(final String codeRegistryCodeValue,
                                                                                 final String codeSchemeCodeValue,
                                                                                 final String codeCodeValue) {
        CodeRegistry registry = codeRegistryDao.findByCodeValue(codeRegistryCodeValue);
        CodeScheme scheme = codeSchemeDao.findByCodeRegistryAndCodeValue(registry, codeSchemeCodeValue);
        Code code = codeDao.findByCodeSchemeAndCodeValue(scheme, codeCodeValue);
        if (code == null)
            return null;
        return mapDeepCodeDto(code);
    }
}