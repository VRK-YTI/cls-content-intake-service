package fi.vm.yti.codelist.intake.dao.impl;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.transaction.Transactional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import fi.vm.yti.codelist.common.dto.ErrorModel;
import fi.vm.yti.codelist.common.dto.ExternalReferenceDTO;
import fi.vm.yti.codelist.intake.dao.ExternalReferenceDao;
import fi.vm.yti.codelist.intake.exception.YtiCodeListException;
import fi.vm.yti.codelist.intake.jpa.ExternalReferenceRepository;
import fi.vm.yti.codelist.intake.jpa.PropertyTypeRepository;
import fi.vm.yti.codelist.intake.log.EntityChangeLogger;
import fi.vm.yti.codelist.intake.model.CodeScheme;
import fi.vm.yti.codelist.intake.model.ExternalReference;
import fi.vm.yti.codelist.intake.model.PropertyType;
import static fi.vm.yti.codelist.intake.exception.ErrorConstants.ERR_MSG_USER_500;

@Component
public class ExternalReferenceDaoImpl implements ExternalReferenceDao {

    private final EntityChangeLogger entityChangeLogger;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final PropertyTypeRepository propertyTypeRepository;

    public ExternalReferenceDaoImpl(final EntityChangeLogger entityChangeLogger,
                                    final ExternalReferenceRepository externalReferenceRepository,
                                    final PropertyTypeRepository propertyTypeRepository) {
        this.entityChangeLogger = entityChangeLogger;
        this.externalReferenceRepository = externalReferenceRepository;
        this.propertyTypeRepository = propertyTypeRepository;
    }

    @Transactional
    public void delete(final ExternalReference externalReference) {
        entityChangeLogger.logExternalReferenceChange(externalReference);
        externalReferenceRepository.delete(externalReference);
    }

    @Transactional
    public void delete(final Set<ExternalReference> externalReferences) {
        externalReferences.forEach(entityChangeLogger::logExternalReferenceChange);
        externalReferenceRepository.delete(externalReferences);
    }

    @Transactional
    public void save(final Set<ExternalReference> externalReferences) {
        externalReferenceRepository.save(externalReferences);
        externalReferences.forEach(entityChangeLogger::logExternalReferenceChange);
    }

    @Transactional
    public void save(final ExternalReference externalReference) {
        externalReferenceRepository.save(externalReference);
        entityChangeLogger.logExternalReferenceChange(externalReference);
    }

    @Transactional
    public ExternalReference updateExternalReferenceFromDto(final ExternalReferenceDTO externalReferenceDto,
                                                            final CodeScheme codeScheme) {
        ExternalReference externalReference = createOrUpdateExternalReference(false, externalReferenceDto, codeScheme);
        save(externalReference);
        return externalReference;
    }

    @Transactional
    public Set<ExternalReference> updateExternalReferenceEntitiesFromDtos(final Set<ExternalReferenceDTO> externalReferenceDtos,
                                                                          final CodeScheme codeScheme) {
        return updateExternalReferenceEntitiesFromDtos(false, externalReferenceDtos, codeScheme);
    }

    @Transactional
    public Set<ExternalReference> updateExternalReferenceEntitiesFromDtos(final boolean internal,
                                                                          final Set<ExternalReferenceDTO> externalReferenceDtos,
                                                                          final CodeScheme codeScheme) {
        final Set<ExternalReference> externalReferences = new HashSet<>();
        if (externalReferenceDtos != null) {
            for (final ExternalReferenceDTO externalReferenceDto : externalReferenceDtos) {
                final ExternalReference externalReference = createOrUpdateExternalReference(internal, externalReferenceDto, codeScheme);
                if (externalReference != null) {
                    externalReferences.add(externalReference);
                }
            }
            if (!externalReferences.isEmpty()) {
                save(externalReferences);
            }
        }
        return externalReferences;
    }

    @Override
    public ExternalReference findById(UUID id) {
        return externalReferenceRepository.findById(id);
    }

    @Override
    public Set<ExternalReference> findAll() {
        return externalReferenceRepository.findAll();
    }

    @Override
    public Set<ExternalReference> findByParentCodeSchemeId(UUID parentCodeSchemeId) {
        return externalReferenceRepository.findByParentCodeSchemeId(parentCodeSchemeId);
    }

    public ExternalReference createOrUpdateExternalReference(final boolean internal,
                                                             final ExternalReferenceDTO fromExternalReference,
                                                             final CodeScheme codeScheme) {
        final boolean isGlobal = fromExternalReference.getGlobal() != null ? fromExternalReference.getGlobal() : true;
        final ExternalReference existingExternalReference;
        if (fromExternalReference.getId() != null && codeScheme != null && !isGlobal) {
            existingExternalReference = externalReferenceRepository.findByIdAndParentCodeScheme(fromExternalReference.getId(), codeScheme);
        } else if (fromExternalReference.getId() != null && isGlobal) {
            existingExternalReference = externalReferenceRepository.findById(fromExternalReference.getId());
        } else {
            existingExternalReference = null;
        }
        final ExternalReference externalReference;
        if (!internal && existingExternalReference != null && isGlobal) {
            externalReference = existingExternalReference;
        } else if (existingExternalReference != null) {
            externalReference = updateExternalReference(existingExternalReference, fromExternalReference, codeScheme);
        } else if (!isGlobal) {
            externalReference = createExternalReference(fromExternalReference, codeScheme);
        } else if (codeScheme == null) {
            externalReference = createExternalReference(fromExternalReference, null);
        } else {
            throw new YtiCodeListException(new ErrorModel(HttpStatus.INTERNAL_SERVER_ERROR.value(), ERR_MSG_USER_500));
        }
        return externalReference;
    }

    private ExternalReference updateExternalReference(final ExternalReference existingExternalReference,
                                                      final ExternalReferenceDTO fromExternalReference,
                                                      final CodeScheme parentCodeScheme) {
        if (!Objects.equals(existingExternalReference.getParentCodeScheme(), parentCodeScheme)) {
            existingExternalReference.setParentCodeScheme(parentCodeScheme);
            existingExternalReference.setGlobal(parentCodeScheme == null);
        }
        final PropertyType propertyType = propertyTypeRepository.findByLocalName(fromExternalReference.getPropertyType().getLocalName());
        if (!Objects.equals(existingExternalReference.getPropertyType(), propertyType)) {
            existingExternalReference.setPropertyType(propertyType);
        }
        if (!Objects.equals(existingExternalReference.getHref(), fromExternalReference.getHref())) {
            existingExternalReference.setHref(fromExternalReference.getHref());
        }
        for (final Map.Entry<String, String> entry : fromExternalReference.getTitle().entrySet()) {
            final String language = entry.getKey();
            final String value = entry.getValue();
            if (!Objects.equals(existingExternalReference.getTitle(language), value)) {
                existingExternalReference.setTitle(language, value);
            }
        }
        for (final Map.Entry<String, String> entry : fromExternalReference.getDescription().entrySet()) {
            final String language = entry.getKey();
            final String value = entry.getValue();
            if (!Objects.equals(existingExternalReference.getDescription(language), value)) {
                existingExternalReference.setDescription(language, value);
            }
        }
        return existingExternalReference;
    }

    private ExternalReference createExternalReference(final ExternalReferenceDTO fromExternalReference,
                                                      final CodeScheme parentCodeScheme) {
        final ExternalReference externalReference = new ExternalReference();
        if (fromExternalReference.getId() != null) {
            externalReference.setId(fromExternalReference.getId());
        } else {
            final UUID uuid = UUID.randomUUID();
            externalReference.setId(uuid);
        }
        externalReference.setParentCodeScheme(parentCodeScheme);
        externalReference.setHref(fromExternalReference.getHref());
        externalReference.setGlobal(parentCodeScheme == null);
        externalReference.setPropertyType(propertyTypeRepository.findByLocalName(fromExternalReference.getPropertyType().getLocalName()));
        for (final Map.Entry<String, String> entry : fromExternalReference.getTitle().entrySet()) {
            externalReference.setTitle(entry.getKey(), entry.getValue());
        }
        for (final Map.Entry<String, String> entry : fromExternalReference.getDescription().entrySet()) {
            externalReference.setDescription(entry.getKey(), entry.getValue());
        }
        return externalReference;
    }
}
