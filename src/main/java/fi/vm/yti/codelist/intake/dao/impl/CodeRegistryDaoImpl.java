package fi.vm.yti.codelist.intake.dao.impl;

import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;
import javax.transaction.Transactional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import fi.vm.yti.codelist.common.dto.CodeRegistryDTO;
import fi.vm.yti.codelist.common.dto.ErrorModel;
import fi.vm.yti.codelist.common.dto.OrganizationDTO;
import fi.vm.yti.codelist.intake.api.ApiUtils;
import fi.vm.yti.codelist.intake.dao.CodeRegistryDao;
import fi.vm.yti.codelist.intake.exception.YtiCodeListException;
import fi.vm.yti.codelist.intake.jpa.CodeRegistryRepository;
import fi.vm.yti.codelist.intake.jpa.OrganizationRepository;
import fi.vm.yti.codelist.intake.log.EntityChangeLogger;
import fi.vm.yti.codelist.intake.model.CodeRegistry;
import fi.vm.yti.codelist.intake.model.Organization;
import static fi.vm.yti.codelist.intake.exception.ErrorConstants.ERR_MSG_USER_CODEREGISTRY_NO_ORGANIZATION;
import static fi.vm.yti.codelist.intake.parser.impl.AbstractBaseParser.validateCodeValue;

@Component
public class CodeRegistryDaoImpl implements CodeRegistryDao {

    private final EntityChangeLogger entityChangeLogger;
    private final ApiUtils apiUtils;
    private final CodeRegistryRepository codeRegistryRepository;
    private final OrganizationRepository organizationRepository;

    @Inject
    public CodeRegistryDaoImpl(final EntityChangeLogger entityChangeLogger,
                               final ApiUtils apiUtils,
                               final CodeRegistryRepository codeRegistryRepository,
                               final OrganizationRepository organizationRepository) {
        this.entityChangeLogger = entityChangeLogger;
        this.apiUtils = apiUtils;
        this.codeRegistryRepository = codeRegistryRepository;
        this.organizationRepository = organizationRepository;
    }

    @Transactional
    public void delete(final CodeRegistry codeRegistry) {
        entityChangeLogger.logCodeRegistryChange(codeRegistry);
        codeRegistryRepository.delete(codeRegistry);
    }

    @Transactional
    public void save(final Set<CodeRegistry> codeRegistries,
                     final boolean logChange) {
        codeRegistryRepository.save(codeRegistries);
        if (logChange) {
            codeRegistries.forEach(entityChangeLogger::logCodeRegistryChange);
        }
    }

    @Transactional
    public void save(final Set<CodeRegistry> codeRegistries) {
        save(codeRegistries, true);
    }

    @Transactional
    public Set<CodeRegistry> findAll() {
        return codeRegistryRepository.findAll();
    }

    public CodeRegistry findByCodeValue(final String codeValue) {
        return codeRegistryRepository.findByCodeValueIgnoreCase(codeValue);
    }

    @Transactional
    public CodeRegistry updateCodeRegistryFromDto(final CodeRegistryDTO codeRegistryDto) {
        final CodeRegistry codeRegistry = createOrUpdateCodeRegistry(codeRegistryDto);
        codeRegistryRepository.save(codeRegistry);
        entityChangeLogger.logCodeRegistryChange(codeRegistry);
        return codeRegistry;
    }

    @Transactional
    public Set<CodeRegistry> updateCodeRegistriesFromDto(final Set<CodeRegistryDTO> codeRegistryDtos) {
        final Set<CodeRegistry> codeRegistries = new HashSet<>();
        codeRegistryDtos.forEach(codeRegistry -> codeRegistries.add(createOrUpdateCodeRegistry(codeRegistry)));
        if (!codeRegistries.isEmpty()) {
            codeRegistryRepository.save(codeRegistries);
            codeRegistries.forEach(entityChangeLogger::logCodeRegistryChange);
        }
        return codeRegistries;
    }

    @Transactional
    public CodeRegistry createOrUpdateCodeRegistry(final CodeRegistryDTO fromCodeRegistry) {
        final CodeRegistry codeRegistry;
        final CodeRegistry existingCodeRegistry = codeRegistryRepository.findByCodeValueIgnoreCase(fromCodeRegistry.getCodeValue());
        if (existingCodeRegistry != null) {
            codeRegistry = updateCodeRegistry(existingCodeRegistry, fromCodeRegistry);
        } else {
            codeRegistry = createCodeRegistry(fromCodeRegistry);
        }
        return codeRegistry;
    }

    private CodeRegistry updateCodeRegistry(final CodeRegistry codeRegistry,
                                            final CodeRegistryDTO fromCodeRegistry) {
        final String uri = apiUtils.createCodeRegistryUri(codeRegistry);
        if (!Objects.equals(codeRegistry.getUri(), uri)) {
            codeRegistry.setUri(uri);
        }
        codeRegistry.setOrganizations(resolveOrganizationsFromDtos(fromCodeRegistry.getOrganizations()));
        mapPrefLabel(fromCodeRegistry, codeRegistry);
        mapDescription(fromCodeRegistry, codeRegistry);
        for (final Map.Entry<String, String> entry : fromCodeRegistry.getDescription().entrySet()) {
            final String language = entry.getKey();
            final String value = entry.getValue();
            if (!Objects.equals(codeRegistry.getDescription(language), value)) {
                codeRegistry.setDescription(language, value);
            }
        }
        codeRegistry.setModified(new Date(System.currentTimeMillis()));
        return codeRegistry;
    }

    private CodeRegistry createCodeRegistry(final CodeRegistryDTO fromCodeRegistry) {
        final CodeRegistry codeRegistry = new CodeRegistry();
        codeRegistry.setId(UUID.randomUUID());
        final String codeValue = fromCodeRegistry.getCodeValue();
        validateCodeValue(codeValue);
        codeRegistry.setCodeValue(codeValue);
        codeRegistry.setOrganizations(resolveOrganizationsFromDtos(fromCodeRegistry.getOrganizations()));
        mapPrefLabel(fromCodeRegistry, codeRegistry);
        mapDescription(fromCodeRegistry, codeRegistry);
        codeRegistry.setUri(apiUtils.createCodeRegistryUri(codeRegistry));
        final Date timeStamp = new Date(System.currentTimeMillis());
        codeRegistry.setCreated(timeStamp);
        codeRegistry.setModified(timeStamp);
        return codeRegistry;
    }

    private Set<Organization> resolveOrganizationsFromDtos(final Set<OrganizationDTO> organizationDtos) {
        final Set<Organization> organizations = new HashSet<>();
        organizationDtos.forEach(organizationDto -> {
            final Organization organization = organizationRepository.findById(organizationDto.getId());
            if (organization != null) {
                organizations.add(organization);
            }
        });
        if (organizations.isEmpty()) {
            throw new YtiCodeListException(new ErrorModel(HttpStatus.NOT_ACCEPTABLE.value(), ERR_MSG_USER_CODEREGISTRY_NO_ORGANIZATION));
        }
        return organizations;
    }

    private void mapPrefLabel(final CodeRegistryDTO fromCodeRegistry,
                              final CodeRegistry codeRegistry) {
        final Map<String, String> prefLabel = fromCodeRegistry.getPrefLabel();
        if (prefLabel != null && !prefLabel.isEmpty()) {
            for (final Map.Entry<String, String> entry : prefLabel.entrySet()) {
                final String language = entry.getKey();
                final String value = entry.getValue();
                if (!Objects.equals(codeRegistry.getPrefLabel(language), value)) {
                    codeRegistry.setPrefLabel(language, value);
                }
            }
        } else {
            codeRegistry.setPrefLabel(null);
        }
    }

    private void mapDescription(final CodeRegistryDTO fromCodeRegistry,
                                final CodeRegistry codeRegistry) {
        final Map<String, String> description = fromCodeRegistry.getDescription();
        if (description != null && !description.isEmpty()) {
            for (final Map.Entry<String, String> entry : description.entrySet()) {
                final String language = entry.getKey();
                final String value = entry.getValue();
                if (!Objects.equals(codeRegistry.getDescription(language), value)) {
                    codeRegistry.setDescription(language, value);
                }
            }
        } else {
            codeRegistry.setDescription(null);
        }
    }
}
