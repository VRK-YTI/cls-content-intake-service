package fi.vm.yti.codelist.intake.dao.impl;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.transaction.Transactional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import fi.vm.yti.codelist.common.dto.CodeSchemeDTO;
import fi.vm.yti.codelist.common.dto.ErrorModel;
import fi.vm.yti.codelist.common.dto.ExtensionDTO;
import fi.vm.yti.codelist.common.model.Status;
import fi.vm.yti.codelist.intake.api.ApiUtils;
import fi.vm.yti.codelist.intake.dao.CodeSchemeDao;
import fi.vm.yti.codelist.intake.dao.ExtensionDao;
import fi.vm.yti.codelist.intake.dao.MemberDao;
import fi.vm.yti.codelist.intake.dao.PropertyTypeDao;
import fi.vm.yti.codelist.intake.exception.YtiCodeListException;
import fi.vm.yti.codelist.intake.jpa.ExtensionRepository;
import fi.vm.yti.codelist.intake.language.LanguageService;
import fi.vm.yti.codelist.intake.log.EntityChangeLogger;
import fi.vm.yti.codelist.intake.model.Code;
import fi.vm.yti.codelist.intake.model.CodeScheme;
import fi.vm.yti.codelist.intake.model.Extension;
import fi.vm.yti.codelist.intake.model.Member;
import fi.vm.yti.codelist.intake.model.PropertyType;
import fi.vm.yti.codelist.intake.security.AuthorizationManager;
import static fi.vm.yti.codelist.common.constants.ApiConstants.CODE_EXTENSION;
import static fi.vm.yti.codelist.intake.exception.ErrorConstants.*;

@Component
public class ExtensionDaoImpl implements ExtensionDao {

    private static final String CONTEXT_EXTENSION = "Extension";

    private final AuthorizationManager authorizationManager;
    private final EntityChangeLogger entityChangeLogger;
    private final ExtensionRepository extensionRepository;
    private final PropertyTypeDao propertyTypeDao;
    private final CodeSchemeDao codeSchemeDao;
    private final LanguageService languageService;
    private final MemberDao memberDao;
    private final ApiUtils apiUtils;

    @Inject
    public ExtensionDaoImpl(final AuthorizationManager authorizationManager,
                            final EntityChangeLogger entityChangeLogger,
                            final ExtensionRepository extensionRepository,
                            final PropertyTypeDao propertyTypeDao,
                            final CodeSchemeDao codeSchemeDao,
                            final LanguageService languageService,
                            final MemberDao memberDao,
                            final ApiUtils apiUtils) {
        this.authorizationManager = authorizationManager;
        this.entityChangeLogger = entityChangeLogger;
        this.extensionRepository = extensionRepository;
        this.propertyTypeDao = propertyTypeDao;
        this.codeSchemeDao = codeSchemeDao;
        this.languageService = languageService;
        this.memberDao = memberDao;
        this.apiUtils = apiUtils;
    }

    public void delete(final Extension extension) {
        entityChangeLogger.logExtensionChange(extension);
        extensionRepository.delete(extension);
    }

    public void delete(final Set<Extension> extensions) {
        extensions.forEach(entityChangeLogger::logExtensionChange);
        extensionRepository.delete(extensions);
    }

    public void save(final Extension extension) {
        extensionRepository.save(extension);
        entityChangeLogger.logExtensionChange(extension);
    }

    public void save(final Set<Extension> extensions,
                     final boolean logChange) {
        extensionRepository.save(extensions);
        if (logChange) {
            extensions.forEach(entityChangeLogger::logExtensionChange);
        }
    }

    public void save(final Set<Extension> extensions) {
        save(extensions, true);
    }

    public Set<Extension> findAll() {
        return extensionRepository.findAll();
    }

    public Extension findById(final UUID id) {
        return extensionRepository.findById(id);
    }

    public Set<Extension> findByParentCodeScheme(final CodeScheme codeScheme) {
        return extensionRepository.findByParentCodeScheme(codeScheme);
    }

    public Set<Extension> findByCodeSchemes(final CodeScheme codeScheme) {
        return extensionRepository.findByCodeSchemes(codeScheme);
    }

    public Set<Extension> findByParentCodeSchemeId(final UUID codeSchemeId) {
        return extensionRepository.findByParentCodeSchemeId(codeSchemeId);
    }

    public Extension findByParentCodeSchemeIdAndCodeValue(final UUID codeSchemeId,
                                                          final String codeValue) {
        return extensionRepository.findByParentCodeSchemeIdAndCodeValueIgnoreCase(codeSchemeId, codeValue);
    }

    public Extension findByParentCodeSchemeAndCodeValue(final CodeScheme codeScheme,
                                                        final String codeValue) {
        return extensionRepository.findByParentCodeSchemeAndCodeValueIgnoreCase(codeScheme, codeValue);
    }

    @Transactional
    public Extension updateExtensionEntityFromDto(final CodeScheme codeScheme,
                                                  final ExtensionDTO extensionDto,
                                                  final boolean autoCreateMembers) {
        final Extension extension = createOrUpdateExtension(codeScheme, extensionDto, autoCreateMembers);
        save(extension);
        return extension;
    }

    @Transactional
    public Set<Extension> updateExtensionEntitiesFromDtos(final CodeScheme codeScheme,
                                                          final Set<ExtensionDTO> extensionDtos,
                                                          final boolean autoCreateMembers) {
        final Set<Extension> extensions = new HashSet<>();
        if (extensionDtos != null) {
            extensionDtos.forEach(extensionDto -> {
                final Extension extension = createOrUpdateExtension(codeScheme, extensionDto, autoCreateMembers);
                extensions.add(extension);
            });
        }
        validateCodeExtensionsForDuplicates(extensions.stream().filter(extension -> CODE_EXTENSION.equalsIgnoreCase(extension.getPropertyType().getContext())).collect(Collectors.toSet()));
        save(extensions);
        return extensions;
    }

    private void validateCodeExtensionsForDuplicates(final Set<Extension> extensions) {
        if (extensions != null && !extensions.isEmpty()) {
            final Set<String> propertyTypes = new HashSet<>();
            extensions.forEach(extension -> {
                final String propertyTypeLocalName = extension.getPropertyType().getLocalName();
                if (!propertyTypes.contains(propertyTypeLocalName)) {
                    propertyTypes.add(propertyTypeLocalName);
                } else {
                    throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_MULTIPLE_CODEEXTENSIONS_FOUND_WITH_SAME_TYPE));
                }
            });
        }
    }

    @Transactional
    public Extension createOrUpdateExtension(final CodeScheme codeScheme,
                                             final ExtensionDTO fromExtension,
                                             final boolean autoCreateMembers) {
        Extension existingExtension;
        if (fromExtension.getId() != null) {
            existingExtension = extensionRepository.findById(fromExtension.getId());
            if (existingExtension != null) {
                validateParentCodeScheme(existingExtension, codeScheme);
            }
        } else {
            existingExtension = extensionRepository.findByParentCodeSchemeAndCodeValueIgnoreCase(codeScheme, fromExtension.getCodeValue());
        }
        final Extension extension;
        if (existingExtension != null) {
            extension = updateExtension(existingExtension, fromExtension);
        } else {
            checkForExistingCodeExtensions(codeScheme, fromExtension);
            extension = createExtension(fromExtension, codeScheme, autoCreateMembers);
        }
        return extension;
    }

    private void checkForExistingCodeExtensions(final CodeScheme codeScheme,
                                                final ExtensionDTO fromExtension) {
        final PropertyType propertyType = propertyTypeDao.findByLocalName(fromExtension.getPropertyType().getLocalName());
        if (propertyType != null && CODE_EXTENSION.equalsIgnoreCase(propertyType.getContext())) {
            final Set<Extension> existingExtensions = extensionRepository.findByParentCodeSchemeIdAndPropertyTypeId(codeScheme.getId(), propertyType.getId());
            if (existingExtensions != null && !existingExtensions.isEmpty()) {
                throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_MULTIPLE_CODEEXTENSIONS_FOUND_WITH_SAME_TYPE));
            }
        }
    }

    private void validateParentCodeScheme(final Extension extension,
                                          final CodeScheme codeScheme) {
        if (extension != null && extension.getParentCodeScheme() != codeScheme) {
            throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_EXTENSION_PARENTCODESCHEME_DOES_NOT_MATCH));
        }
    }

    private Extension updateExtension(final Extension existingExtension,
                                      final ExtensionDTO fromExtension) {
        if (!Objects.equals(existingExtension.getStatus(), fromExtension.getStatus())) {
            if (!authorizationManager.canBeModifiedByUserInOrganization(existingExtension.getParentCodeScheme().getCodeRegistry().getOrganizations()) &&
                Status.valueOf(existingExtension.getStatus()).ordinal() >= Status.VALID.ordinal() &&
                Status.valueOf(fromExtension.getStatus()).ordinal() < Status.VALID.ordinal()) {
                throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_STATUS_CHANGE_NOT_ALLOWED));
            }
            existingExtension.setStatus(fromExtension.getStatus());
        }
        final PropertyType propertyType = propertyTypeDao.findByContextAndLocalName(CONTEXT_EXTENSION, fromExtension.getPropertyType().getLocalName());
        if (propertyType == null) {
            throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_EXTENSION_PROPERTYTYPE_NOT_FOUND));
        }
        if (!Objects.equals(existingExtension.getPropertyType(), propertyType)) {
            throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_EXTENSION_PROPERTYTYPE_CHANGE_NOT_ALLOWED));
        }
        final Set<CodeScheme> codeSchemes = new HashSet<>();
        if (fromExtension.getCodeSchemes() != null && !fromExtension.getCodeSchemes().isEmpty()) {
            for (final CodeSchemeDTO codeSchemeDto : fromExtension.getCodeSchemes()) {
                if (codeSchemeDto.getUri() != null && !codeSchemeDto.getUri().isEmpty()) {
                    final CodeScheme relatedCodeScheme = codeSchemeDao.findByUri(codeSchemeDto.getUri());
                    if (relatedCodeScheme != null) {
                        codeSchemes.add(relatedCodeScheme);
                    } else {
                        throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_EXTENSION_CODESCHEME_NOT_FOUND));
                    }
                }
            }
        }
        existingExtension.setCodeSchemes(codeSchemes);
        for (final Map.Entry<String, String> entry : fromExtension.getPrefLabel().entrySet()) {
            final String language = entry.getKey();
            languageService.validateInputLanguageForCodeScheme(existingExtension.getParentCodeScheme(), language);
            final String value = entry.getValue();
            if (!Objects.equals(existingExtension.getPrefLabel(language), value)) {
                existingExtension.setPrefLabel(language, value);
            }
        }
        if (!Objects.equals(existingExtension.getStartDate(), fromExtension.getStartDate())) {
            existingExtension.setStartDate(fromExtension.getStartDate());
        }
        if (!Objects.equals(existingExtension.getEndDate(), fromExtension.getEndDate())) {
            existingExtension.setEndDate(fromExtension.getEndDate());
        }
        existingExtension.setModified(new Date(System.currentTimeMillis()));
        return existingExtension;
    }

    private Extension createExtension(final ExtensionDTO fromExtension,
                                      final CodeScheme codeScheme,
                                      final boolean autoCreateMembers) {
        final Extension extension = new Extension();
        if (fromExtension.getId() != null) {
            extension.setId(fromExtension.getId());
        } else {
            final UUID uuid = UUID.randomUUID();
            extension.setId(uuid);
        }
        extension.setCodeValue(fromExtension.getCodeValue());
        extension.setStartDate(fromExtension.getStartDate());
        extension.setEndDate(fromExtension.getEndDate());
        extension.setStatus(fromExtension.getStatus());
        final PropertyType propertyType = propertyTypeDao.findByContextAndLocalName(CONTEXT_EXTENSION, fromExtension.getPropertyType().getLocalName());
        if (propertyType == null) {
            throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_EXTENSION_PROPERTYTYPE_NOT_FOUND));
        }
        extension.setPropertyType(propertyType);
        for (final Map.Entry<String, String> entry : fromExtension.getPrefLabel().entrySet()) {
            final String language = entry.getKey();
            languageService.validateInputLanguageForCodeScheme(codeScheme, language);
            extension.setPrefLabel(language, entry.getValue());
        }
        final Set<CodeScheme> codeSchemes = new HashSet<>();
        LinkedHashSet<CodeScheme> codeSchemesAlphabeticallyOrdered = null;
        if (fromExtension.getCodeSchemes() != null && !fromExtension.getCodeSchemes().isEmpty()) {
            fromExtension.getCodeSchemes().forEach(codeSchemeDto -> {
                final CodeScheme relatedCodeScheme = codeSchemeDao.findByUri(codeSchemeDto.getUri());
                if (relatedCodeScheme != null && relatedCodeScheme.getId().equals(codeScheme.getId())) {
                    throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_EXTENSION_CODESCHEME_MAPPED_TO_PARENT));
                } else if (relatedCodeScheme != null) {
                    codeSchemes.add(relatedCodeScheme);
                } else {
                    throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_EXTENSION_CODESCHEME_NOT_FOUND));
                }
            });
            codeSchemesAlphabeticallyOrdered = orderExtensionsCodeSchemesAlphabetically(codeSchemes);
            extension.setCodeSchemes(codeSchemes);
        }
        extension.setParentCodeScheme(codeScheme);
        addExtensionToParentCodeScheme(codeScheme, extension);
        extension.setUri(apiUtils.createExtensionUri(extension));
        final Date timeStamp = new Date(System.currentTimeMillis());
        extension.setCreated(timeStamp);
        extension.setModified(timeStamp);

        if (autoCreateMembers) {

            final LinkedHashSet<CodeScheme> codeSchemesToGenerateAutoMembersFor = new LinkedHashSet<>();
            final LinkedHashMap<CodeScheme, LinkedHashSet<Code>> codeSchemesWithCodesOrdered = new LinkedHashMap<>();

            codeSchemesToGenerateAutoMembersFor.add(codeScheme);
            codeSchemesToGenerateAutoMembersFor.addAll(codeSchemesAlphabeticallyOrdered);

            codeSchemesToGenerateAutoMembersFor.forEach(cs -> populateMapWhereCodesAreOrderedBasedOnFlatOrderAscending(cs, codeSchemesWithCodesOrdered));

            codeSchemesWithCodesOrdered.keySet().forEach(cs -> {
                LinkedHashSet<Code> codesInCorrectOrder = codeSchemesWithCodesOrdered.get(cs);
                codesInCorrectOrder.forEach(code -> {
                    Member m = new Member();
                    m.setId(UUID.randomUUID());
                    m.setOrder(memberDao.getNextOrderInSequence(extension));
                    m.setCode(code);
                    m.setRelatedMember(null);
                    m.setEndDate(code.getEndDate());
                    m.setStartDate(code.getStartDate());
                    m.setExtension(extension);
                    m.setMemberValues(null);
                    m.setPrefLabel(null);
                    m.setUri(apiUtils.createMemberUri(m));
                    memberDao.save(m);
                });
            });
        }

        return extension;
    }

    /**
     * The why and how of this ordering-trick is the following - when the user creates an extension, and chooses to autocreate the members, a member is created for every code of
     * every codescheme involved (that is , the extensions parent codescheme, and all the attached codeschemes (0-n pieces).
     * <p>
     * For arguments sake lets assume there is the parent codescheme with codes a,b,c and 2 others with codes d,e,f and g,h,i.
     * <p>
     * If we do nothing explicit about the ordering of the members during creation, the members listing could look this this: c a b f d e h i g , which looks chaotic.
     * <p>
     * So we order, and guarantee that each codescheme retains the order of the codes (abc, def, ghi) and also guarantee that the parent codeschemes codes are always first,
     * and the other codeschemes are ordered alphabetically according to their codeValue.
     */
    private void populateMapWhereCodesAreOrderedBasedOnFlatOrderAscending(CodeScheme cs,
                                                                          HashMap<CodeScheme, LinkedHashSet<Code>> codeSchemesWithCodesOrdered) {

        Set<Code> items = cs.getCodes();
        List<Code> codesSorted = items.stream().collect(Collectors.toList());
        Collections.sort(codesSorted, (o1, o2) -> o1.getOrder().compareTo(o2.getOrder()));

        LinkedHashSet<Code> codesOrdered = new LinkedHashSet<>();
        for (Code code : codesSorted) {
            codesOrdered.add(code);
        }
        codeSchemesWithCodesOrdered.put(cs, codesOrdered);
    }

    /**
     * @param codeSchemes these are all the other codeschemes, but not the extensions parent codescheme
     */
    private LinkedHashSet<CodeScheme> orderExtensionsCodeSchemesAlphabetically(Set<CodeScheme> codeSchemes) {

        List<CodeScheme> codeSchemesSorted = codeSchemes.stream().collect(Collectors.toList());
        Collections.sort(codeSchemesSorted, (o1, o2) -> o1.getCodeValue().compareTo(o2.getCodeValue()));

        LinkedHashSet<CodeScheme> result = new LinkedHashSet<>();

        for (CodeScheme codeScheme : codeSchemesSorted) {
            result.add(codeScheme);
        }
        return result;
    }

    private void addExtensionToParentCodeScheme(final CodeScheme codeScheme,
                                                final Extension extension) {
        final Set<Extension> parentCodeSchemeExtensions = codeScheme.getExtensions();
        if (parentCodeSchemeExtensions != null) {
            parentCodeSchemeExtensions.add(extension);
        } else {
            final Set<Extension> extensions = new HashSet<>();
            extensions.add(extension);
            codeScheme.setExtensions(extensions);
        }
        codeSchemeDao.save(codeScheme);
    }
}
