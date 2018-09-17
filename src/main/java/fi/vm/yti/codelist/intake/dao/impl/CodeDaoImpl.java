package fi.vm.yti.codelist.intake.dao.impl;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.transaction.Transactional;

import org.apache.commons.lang3.mutable.MutableInt;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import fi.vm.yti.codelist.common.dto.CodeDTO;
import fi.vm.yti.codelist.common.dto.ErrorModel;
import fi.vm.yti.codelist.common.model.Status;
import fi.vm.yti.codelist.intake.api.ApiUtils;
import fi.vm.yti.codelist.intake.dao.CodeDao;
import fi.vm.yti.codelist.intake.dao.ExternalReferenceDao;
import fi.vm.yti.codelist.intake.exception.ExistingCodeException;
import fi.vm.yti.codelist.intake.exception.YtiCodeListException;
import fi.vm.yti.codelist.intake.jpa.CodeRepository;
import fi.vm.yti.codelist.intake.jpa.CodeSchemeRepository;
import fi.vm.yti.codelist.intake.language.LanguageService;
import fi.vm.yti.codelist.intake.log.EntityChangeLogger;
import fi.vm.yti.codelist.intake.model.Code;
import fi.vm.yti.codelist.intake.model.CodeScheme;
import fi.vm.yti.codelist.intake.model.ExternalReference;
import fi.vm.yti.codelist.intake.security.AuthorizationManager;
import static fi.vm.yti.codelist.intake.exception.ErrorConstants.*;
import static fi.vm.yti.codelist.intake.parser.impl.AbstractBaseParser.validateCodeCodeValue;

@Component
public class CodeDaoImpl implements CodeDao {

    private static final int MAX_LEVEL = 10;

    private final EntityChangeLogger entityChangeLogger;
    private final ApiUtils apiUtils;
    private final AuthorizationManager authorizationManager;
    private final CodeRepository codeRepository;
    private final CodeSchemeRepository codeSchemeRepository;
    private final ExternalReferenceDao externalReferenceDao;
    private final LanguageService languageService;

    public CodeDaoImpl(final EntityChangeLogger entityChangeLogger,
                       final ApiUtils apiUtils,
                       final AuthorizationManager authorizationManager,
                       final CodeRepository codeRepository,
                       final CodeSchemeRepository codeSchemeRepository,
                       final ExternalReferenceDao externalReferenceDao,
                       final LanguageService languageService) {
        this.entityChangeLogger = entityChangeLogger;
        this.apiUtils = apiUtils;
        this.authorizationManager = authorizationManager;
        this.codeRepository = codeRepository;
        this.codeSchemeRepository = codeSchemeRepository;
        this.externalReferenceDao = externalReferenceDao;
        this.languageService = languageService;
    }

    public int getCodeCount() {
        return codeRepository.getCodeCount();
    }

    public void save(final Code code) {
        save(code, true);
    }

    public void save(final Code code,
                     final boolean logData) {
        codeRepository.save(code);
        if (logData) {
            entityChangeLogger.logCodeChange(code);
        }
    }

    public void save(final Set<Code> codes) {
        codeRepository.save(codes);
        entityChangeLogger.logCodesChange(codes);
    }

    public void delete(final Code code) {
        entityChangeLogger.logCodeChange(code);
        codeRepository.delete(code);
    }

    public void delete(final Set<Code> codes) {
        entityChangeLogger.logCodesChange(codes);
        codeRepository.delete(codes);
    }

    public Set<Code> findAll(final PageRequest pageRequest) {
        return new HashSet<>(codeRepository.findAll(pageRequest).getContent());
    }

    public Set<Code> findAll() {
        return codeRepository.findAll();
    }

    public Code findByUri(final String uri) {
        return codeRepository.findByUriIgnoreCase(uri);
    }

    public Set<String> findByCodeSchemeAndCodeValue(final UUID codeSchemeId) {
        return codeRepository.getCodeSchemeCodeValues(codeSchemeId);
    }

    public Code findByCodeSchemeAndCodeValue(final CodeScheme codeScheme,
                                             final String codeValue) {
        return codeRepository.findByCodeSchemeAndCodeValueIgnoreCase(codeScheme, codeValue);
    }

    public Code findByCodeSchemeAndCodeValueAndBroaderCodeId(final CodeScheme codeScheme,
                                                             final String codeValue,
                                                             final UUID broaderCodeId) {
        return codeRepository.findByCodeSchemeAndCodeValueIgnoreCaseAndBroaderCodeId(codeScheme, codeValue, broaderCodeId);
    }

    public Set<String> getCodeSchemeCodeValues(final UUID codeSchemeId) {
        return codeRepository.getCodeSchemeCodeValues(codeSchemeId);
    }

    public Code findById(UUID id) {
        return codeRepository.findById(id);
    }

    public Set<Code> findByCodeSchemeId(final UUID codeSchemeId) {
        return codeRepository.findByCodeSchemeId(codeSchemeId);
    }

    public Set<Code> findByCodeSchemeIdAndBroaderCodeIdIsNull(final UUID codeSchemeId) {
        return codeRepository.findByCodeSchemeIdAndBroaderCodeIdIsNull(codeSchemeId);
    }

    public Set<Code> findByBroaderCodeId(final UUID broaderCodeId) {
        return codeRepository.findByBroaderCodeId(broaderCodeId);
    }

    @Transactional
    public Code updateCodeFromDto(final CodeScheme codeScheme,
                                  final CodeDTO codeDto) {
        final Code code = createOrUpdateCode(codeScheme, codeDto, null, null, null);
        updateExternalReferences(codeScheme, code, codeDto);
        checkCodeHierarchyLevels(code);
        save(code);
        codeSchemeRepository.save(codeScheme);
        return code;
    }

    @Transactional
    public Set<Code> updateCodesFromDtos(final CodeScheme codeScheme,
                                         final Set<CodeDTO> codeDtos,
                                         final Map<String, String> broaderCodeMapping,
                                         final boolean updateExternalReferences) {
        final Set<Code> codes = new HashSet<>();
        MutableInt nextOrder = new MutableInt(getNextOrderInSequence(codeScheme));
        final Set<Code> existingCodes = codeRepository.findByCodeSchemeId(codeScheme.getId());
        for (final CodeDTO codeDto : codeDtos) {
            final Code code = createOrUpdateCode(codeScheme, codeDto, existingCodes, codes, nextOrder);
            save(code, false);
            if (updateExternalReferences) {
                updateExternalReferences(codeScheme, code, codeDto);
            }
            if (code != null) {
                codes.add(code);
                save(code, false);
            }
        }
        setBroaderCodesAndEvaluateHierarchyLevels(broaderCodeMapping, codes, codeScheme);
        if (!codes.isEmpty()) {
            codes.forEach(this::checkCodeHierarchyLevels);
            save(codes);
            codeSchemeRepository.save(codeScheme);
        }
        return codes;
    }

    private void updateExternalReferences(final CodeScheme codeScheme,
                                          final Code code,
                                          final CodeDTO codeDto) {
        final Set<ExternalReference> externalReferences = externalReferenceDao.updateExternalReferenceEntitiesFromDtos(codeDto.getExternalReferences(), codeScheme);
        code.setExternalReferences(externalReferences);
    }

    private Code findExistingCodeFromSet(final Set<Code> existingCodes,
                                         final String codeValue) {
        for (final Code code : existingCodes) {
            if (code.getCodeValue().equalsIgnoreCase(codeValue)) {
                return code;
            }
        }
        return null;
    }

    @Transactional
    public Code createOrUpdateCode(final CodeScheme codeScheme,
                                   final CodeDTO codeDto,
                                   final Set<Code> existingCodes,
                                   final Set<Code> codes,
                                   final MutableInt nextOrder) {
        validateCodeForCodeScheme(codeDto);
        final Code existingCode;
        if (codeDto.getId() != null) {
            existingCode = codeRepository.findById(codeDto.getId());
            if (existingCode == null) {
                checkForExistingCodeInCodeScheme(codeScheme, codeDto);
            }
            validateCodeScheme(existingCode, codeScheme);
        } else if (existingCodes != null) {
            existingCode = findExistingCodeFromSet(existingCodes, codeDto.getCodeValue());
        } else {
            existingCode = codeRepository.findByCodeSchemeAndCodeValueIgnoreCase(codeScheme, codeDto.getCodeValue());
        }
        final Code code;
        if (existingCode != null) {
            code = updateCode(codeScheme, existingCode, codeDto, codes, nextOrder);
        } else {
            code = createCode(codeScheme, codeDto, codes, nextOrder);
        }
        return code;
    }

    private void validateCodeScheme(final Code code,
                                    final CodeScheme codeScheme) {
        if (code != null && code.getCodeScheme() != codeScheme) {
            throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_406));
        }
    }

    private void checkOrderAndShiftExistingCodeOrderIfInUse(final CodeScheme codeScheme,
                                                            final CodeDTO fromCode,
                                                            final Set<Code> codes) {
        final Code code = codeRepository.findByCodeSchemeAndOrder(codeScheme, fromCode.getOrder());
        if (code != null && !code.getCodeValue().equalsIgnoreCase(fromCode.getCodeValue())) {
            code.setOrder(getNextOrderInSequence(codeScheme));
            save(code);
            codes.add(code);
        }
    }

    private Code updateCode(final CodeScheme codeScheme,
                            final Code existingCode,
                            final CodeDTO fromCode,
                            final Set<Code> codes,
                            final MutableInt nextOrder) {
        final String uri = apiUtils.createCodeUri(codeScheme.getCodeRegistry(), codeScheme, existingCode);
        if (!Objects.equals(existingCode.getStatus(), fromCode.getStatus())) {
            if (!authorizationManager.isSuperUser() && Status.valueOf(existingCode.getStatus()).ordinal() >= Status.VALID.ordinal() && Status.valueOf(fromCode.getStatus()).ordinal() < Status.VALID.ordinal()) {
                throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_STATUS_CHANGE_NOT_ALLOWED));
            }
            existingCode.setStatus(fromCode.getStatus());
        }
        if (!Objects.equals(existingCode.getCodeScheme(), codeScheme)) {
            existingCode.setCodeScheme(codeScheme);
        }
        if (!Objects.equals(existingCode.getUri(), uri)) {
            existingCode.setUri(uri);
        }
        if (!Objects.equals(existingCode.getShortName(), fromCode.getShortName())) {
            existingCode.setShortName(fromCode.getShortName());
        }
        if (!Objects.equals(existingCode.getHierarchyLevel(), fromCode.getHierarchyLevel())) {
            existingCode.setHierarchyLevel(fromCode.getHierarchyLevel());
        }
        if (!Objects.equals(existingCode.getOrder(), fromCode.getOrder())) {
            if (fromCode.getOrder() != null) {
                checkOrderAndShiftExistingCodeOrderIfInUse(codeScheme, fromCode, codes);
                existingCode.setOrder(fromCode.getOrder());
                if (fromCode.getOrder() > nextOrder.getValue()) {
                    nextOrder.setValue(fromCode.getOrder() + 1);
                }
            } else if (fromCode.getOrder() == null && existingCode.getOrder() == null) {
                final Integer next = getNextOrderInSequence(codeScheme);
                existingCode.setOrder(next);
                nextOrder.setValue(next + 1);
            } else {
                existingCode.setOrder(nextOrder.getValue());
                nextOrder.setValue(nextOrder.getValue() + 1);
            }
        }
        existingCode.setBroaderCode(resolveBroaderCode(fromCode, codeScheme));
        for (final Map.Entry<String, String> entry : fromCode.getPrefLabel().entrySet()) {
            final String language = entry.getKey();
            languageService.validateInputLanguage(codeScheme, language);
            final String value = entry.getValue();
            if (!Objects.equals(existingCode.getPrefLabel(language), value)) {
                existingCode.setPrefLabel(language, value);
            }
        }
        for (final Map.Entry<String, String> entry : fromCode.getDescription().entrySet()) {
            final String language = entry.getKey();
            languageService.validateInputLanguage(codeScheme, language);
            final String value = entry.getValue();
            if (!Objects.equals(existingCode.getDescription(language), value)) {
                existingCode.setDescription(language, value);
            }
        }
        for (final Map.Entry<String, String> entry : fromCode.getDefinition().entrySet()) {
            final String language = entry.getKey();
            languageService.validateInputLanguage(codeScheme, language);
            final String value = entry.getValue();
            if (!Objects.equals(existingCode.getDefinition(language), value)) {
                existingCode.setDefinition(language, value);
            }
        }
        if (!Objects.equals(existingCode.getStartDate(), fromCode.getStartDate())) {
            existingCode.setStartDate(fromCode.getStartDate());
        }
        if (!Objects.equals(existingCode.getEndDate(), fromCode.getEndDate())) {
            existingCode.setEndDate(fromCode.getEndDate());
        }
        if (!Objects.equals(existingCode.getConceptUriInVocabularies(), fromCode.getConceptUriInVocabularies())) {
            existingCode.setConceptUriInVocabularies(fromCode.getConceptUriInVocabularies());
        }
        existingCode.setModified(new Date(System.currentTimeMillis()));
        return existingCode;
    }

    private Code createCode(final CodeScheme codeScheme,
                            final CodeDTO fromCode,
                            final Set<Code> codes,
                            final MutableInt nextOrder) {
        final Code code = new Code();
        if (fromCode.getId() != null) {
            code.setId(fromCode.getId());
        } else {
            final UUID uuid = UUID.randomUUID();
            code.setId(uuid);
        }
        code.setStatus(fromCode.getStatus());
        code.setCodeScheme(codeScheme);
        final String codeValue = fromCode.getCodeValue();
        validateCodeCodeValue(codeValue);
        code.setCodeValue(codeValue);
        code.setShortName(fromCode.getShortName());
        code.setHierarchyLevel(fromCode.getHierarchyLevel());
        code.setBroaderCode(resolveBroaderCode(fromCode, codeScheme));
        if (fromCode.getOrder() != null) {
            checkOrderAndShiftExistingCodeOrderIfInUse(codeScheme, fromCode, codes);
            code.setOrder(fromCode.getOrder());
            if (fromCode.getOrder() > nextOrder.getValue()) {
                nextOrder.setValue(fromCode.getOrder() + 1);
            }
        } else if (nextOrder == null) {
            final int order = getNextOrderInSequence(codeScheme);
            code.setOrder(order);
        } else {
            code.setOrder(nextOrder.getValue());
            nextOrder.setValue(nextOrder.getValue() + 1);
        }
        for (Map.Entry<String, String> entry : fromCode.getPrefLabel().entrySet()) {
            final String language = entry.getKey();
            languageService.validateInputLanguage(codeScheme, language);
            code.setPrefLabel(language, entry.getValue());
        }
        for (Map.Entry<String, String> entry : fromCode.getDescription().entrySet()) {
            final String language = entry.getKey();
            languageService.validateInputLanguage(codeScheme, language);
            code.setDescription(language, entry.getValue());
        }
        for (Map.Entry<String, String> entry : fromCode.getDefinition().entrySet()) {
            final String language = entry.getKey();
            languageService.validateInputLanguage(codeScheme, language);
            code.setDefinition(language, entry.getValue());
        }
        code.setStartDate(fromCode.getStartDate());
        code.setEndDate(fromCode.getEndDate());
        code.setUri(apiUtils.createCodeUri(codeScheme.getCodeRegistry(), codeScheme, code));
        code.setConceptUriInVocabularies(fromCode.getConceptUriInVocabularies());
        final Date timeStamp = new Date(System.currentTimeMillis());
        code.setCreated(timeStamp);
        code.setModified(timeStamp);
        return code;
    }

    private Code resolveBroaderCode(final CodeDTO fromCode,
                                    final CodeScheme codeScheme) {
        if (fromCode != null && fromCode.getBroaderCode() != null) {
            final Code broaderCode = findById(fromCode.getBroaderCode().getId());
            if (broaderCode != null && broaderCode.getCodeScheme() != codeScheme) {
                throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_EXISTING_CODE_MISMATCH));
            }
            return broaderCode;
        }
        return null;
    }

    private void validateCodeForCodeScheme(final CodeDTO code) {
        if (code.getId() != null) {
            final Code existingCode = codeRepository.findById(code.getId());
            if (existingCode != null && !existingCode.getCodeValue().equalsIgnoreCase(code.getCodeValue())) {
                throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_EXISTING_CODE_MISMATCH));
            }
        }
    }

    private Integer getNextOrderInSequence(final CodeScheme codeScheme) {
        final Integer maxOrder = codeRepository.getCodeMaxOrder(codeScheme.getId());
        if (maxOrder == null) {
            return 1;
        } else {
            return maxOrder + 1;
        }
    }

    private void checkForExistingCodeInCodeScheme(final CodeScheme codeScheme,
                                                  final CodeDTO fromCode) {
        final Code code = codeRepository.findByCodeSchemeAndCodeValueIgnoreCase(codeScheme, fromCode.getCodeValue());
        if (code != null) {
            throw new ExistingCodeException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(),
                ERR_MSG_USER_ALREADY_EXISTING_CODE, code.getCodeValue()));
        }
    }

    private void setBroaderCodesAndEvaluateHierarchyLevels(final Map<String, String> broaderCodeMapping,
                                                           final Set<Code> codes,
                                                           final CodeScheme codeScheme) {
        final Map<String, Code> codeMap = new HashMap<>();
        codes.forEach(code -> codeMap.put(code.getCodeValue().toLowerCase(), code));
        setBroaderCodes(broaderCodeMapping, codeMap);
        evaluateAndSetHierarchyLevels(codeScheme.getCodes());
    }

    private void setBroaderCodes(final Map<String, String> broaderCodeMapping,
                                 final Map<String, Code> codes) {
        broaderCodeMapping.forEach((codeCodeValue, broaderCodeCodeValue) -> {
            final Code code = codes.get(codeCodeValue);
            final Code broaderCode = codes.get(broaderCodeCodeValue);
            if (broaderCode == null && broaderCodeCodeValue != null) {
                throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_BROADER_CODE_DOES_NOT_EXIST));
            } else if (broaderCode != null && broaderCode.getCodeValue().equalsIgnoreCase(code.getCodeValue())) {
                throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_BROADER_CODE_SELF_REFERENCE));
            } else if (code == null) {
                throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_406));
            } else {
                code.setBroaderCode(broaderCode);
            }
        });
    }

    public void evaluateAndSetHierarchyLevels(final Set<Code> codes) {
        final Set<Code> codesToEvaluate = new HashSet<>(codes);
        final Map<Integer, Set<UUID>> hierarchyMapping = new HashMap<>();
        int hierarchyLevel = 0;
        while (!codesToEvaluate.isEmpty()) {
            ++hierarchyLevel;
            resolveAndSetCodeHierarchyLevels(codesToEvaluate, hierarchyMapping, hierarchyLevel);
        }
    }

    private void resolveAndSetCodeHierarchyLevels(final Set<Code> codesToEvaluate,
                                                  final Map<Integer, Set<UUID>> hierarchyMapping,
                                                  final Integer hierarchyLevel) {
        final Set<Code> toRemove = new HashSet<>();
        codesToEvaluate.forEach(code -> {
            if ((hierarchyLevel == 1 && code.getBroaderCode() == null) ||
                (hierarchyLevel > 1 && code.getBroaderCode() != null && code.getBroaderCode().getId() != null && hierarchyMapping.get(hierarchyLevel - 1) != null && hierarchyMapping.get(hierarchyLevel - 1).contains(code.getBroaderCode().getId()))) {
                code.setHierarchyLevel(hierarchyLevel);
                if (hierarchyMapping.get(hierarchyLevel) != null) {
                    hierarchyMapping.get(hierarchyLevel).add(code.getId());
                } else {
                    final Set<UUID> uuids = new HashSet<>();
                    uuids.add(code.getId());
                    hierarchyMapping.put(hierarchyLevel, uuids);
                }
                toRemove.add(code);
            }
        });
        codesToEvaluate.removeAll(toRemove);
    }

    private void checkCodeHierarchyLevels(final Code code) {
        final Set<Code> chainedCodes = new HashSet<>();
        chainedCodes.add(code);
        checkCodeHierarchyLevels(chainedCodes, code, 1);
    }

    private void checkCodeHierarchyLevels(final Set<Code> chainedCodes,
                                          final Code code,
                                          final int level) {
        if (level > MAX_LEVEL) {
            throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_CODE_HIERARCHY_MAXLEVEL_REACHED));
        }
        final Code broaderCode = code.getBroaderCode();
        if (broaderCode != null) {
            if (chainedCodes.contains(broaderCode)) {
                throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_CODE_CYCLIC_DEPENDENCY_ISSUE));
            }
            chainedCodes.add(broaderCode);
            checkCodeHierarchyLevels(chainedCodes, broaderCode, level + 1);
        }
    }

}
