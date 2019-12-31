package fi.vm.yti.codelist.intake.log;

import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import brave.Span;
import brave.Tracer;
import fi.vm.yti.codelist.intake.jpa.CodeRepository;
import fi.vm.yti.codelist.intake.jpa.CommitRepository;
import fi.vm.yti.codelist.intake.jpa.EditedEntityRepository;
import fi.vm.yti.codelist.intake.model.Code;
import fi.vm.yti.codelist.intake.model.CodeRegistry;
import fi.vm.yti.codelist.intake.model.CodeScheme;
import fi.vm.yti.codelist.intake.model.Commit;
import fi.vm.yti.codelist.intake.model.EditedEntity;
import fi.vm.yti.codelist.intake.model.Extension;
import fi.vm.yti.codelist.intake.model.ExternalReference;
import fi.vm.yti.codelist.intake.model.Member;
import fi.vm.yti.codelist.intake.model.PropertyType;
import fi.vm.yti.codelist.intake.model.ValueType;
import fi.vm.yti.codelist.intake.security.AuthorizationManager;

@Service
public class EntityChangeLoggerImpl implements EntityChangeLogger {

    private static final Logger LOG = LoggerFactory.getLogger(EntityChangeLoggerImpl.class);
    private final AuthorizationManager authorizationManager;
    private final Tracer tracer;
    private final CommitRepository commitRepository;
    private final EditedEntityRepository editedEntityRepository;
    private final EntityPayloadLogger entityPayloadLogger;

    @Inject
    public EntityChangeLoggerImpl(final AuthorizationManager authorizationManager,
                                  final Tracer tracer,
                                  final CommitRepository commitRepository,
                                  final EditedEntityRepository editedEntityRepository,
                                  final EntityPayloadLogger entityPayloadLogger) {
        this.authorizationManager = authorizationManager;
        this.tracer = tracer;
        this.commitRepository = commitRepository;
        this.editedEntityRepository = editedEntityRepository;
        this.entityPayloadLogger = entityPayloadLogger;
    }

    @Transactional
    public void logCodeRegistryChange(final CodeRegistry codeRegistry) {
        entityPayloadLogger.logCodeRegistry(codeRegistry);
        final EditedEntity editedEntity = new EditedEntity(createCommit());
        editedEntity.setCodeRegistry(codeRegistry);
        editedEntityRepository.save(editedEntity);
    }

    @Transactional
    public void logCodeSchemeChange(final CodeScheme codeScheme) {
        entityPayloadLogger.logCodeScheme(codeScheme);
        final EditedEntity editedEntity = new EditedEntity(createCommit());
        editedEntity.setCodeScheme(codeScheme);
        editedEntityRepository.save(editedEntity);
    }

    @Transactional
    public void logCodesChange(final Set<Code> codes) {
        final Commit commit = createCommit();
        codes.forEach(code -> {
            entityPayloadLogger.logCode(code);
            final EditedEntity editedEntity = new EditedEntity(commit);
            editedEntity.setCode(code);
            editedEntityRepository.save(editedEntity);
        });
    }

    @Transactional
    public void logCodeChange(final Code code) {
        entityPayloadLogger.logCode(code);
        final EditedEntity editedEntity = new EditedEntity(createCommit());
        editedEntity.setCode(code);
        editedEntityRepository.save(editedEntity);
    }

    @Transactional
    public void logExternalReferenceChange(final ExternalReference externalReference) {
        entityPayloadLogger.logExternalReference(externalReference);
        final EditedEntity editedEntity = new EditedEntity(createCommit());
        editedEntity.setExternalReference(externalReference);
        editedEntityRepository.save(editedEntity);
    }

    @Transactional
    public void logPropertyTypeChange(final PropertyType propertyType) {
        entityPayloadLogger.logPropertyType(propertyType);
        final EditedEntity editedEntity = new EditedEntity(createCommit());
        editedEntity.setPropertyType(propertyType);
        editedEntityRepository.save(editedEntity);
    }

    @Transactional
    public void logExtensionChange(final Extension extension) {
        entityPayloadLogger.logExtension(extension);
        final EditedEntity editedEntity = new EditedEntity(createCommit());
        editedEntity.setExtension(extension);
        editedEntityRepository.save(editedEntity);
    }

    @Transactional
    public void logMemberChange(final Member member) {
        entityPayloadLogger.logMember(member);
        final EditedEntity editedEntity = new EditedEntity(createCommit());
        editedEntity.setMember(member);
        editedEntityRepository.save(editedEntity);
    }

    @Transactional
    public void logMemberChanges(final Set<Member> members) {
        final Set<EditedEntity> editedEntities = new HashSet<>();
        final Commit commit = createCommit();
        entityPayloadLogger.logMembers(members);
        members.forEach(member -> {
            final EditedEntity editedEntity = new EditedEntity(commit);
            editedEntity.setMember(member);
            editedEntities.add(editedEntity);
        });
        editedEntityRepository.saveAll(editedEntities);
    }

    @Transactional
    public void logValueTypeChange(final ValueType valueType) {
        entityPayloadLogger.logValueType(valueType);
        final EditedEntity editedEntity = new EditedEntity(createCommit());
        editedEntity.setValueType(valueType);
        editedEntityRepository.save(editedEntity);
    }

    private Commit createCommit() {
        final String traceId = getTraceId();
        Commit commit = null;
        if (traceId != null && !traceId.isEmpty()) {
            try {
                commit = commitRepository.findByTraceId(traceId);
            } catch (final Exception e) {
                LOG.error("Issue with trying to find commit with traceId: " + traceId, e);
            }
        }
        if (commit == null) {
            commit = new Commit(traceId, authorizationManager.getUserId());
            commitRepository.save(commit);
        }
        return commit;
    }

    private String getTraceId() {
        final Span span = tracer.currentSpan();
        if (span != null) {
            return span.context().traceIdString();
        }
        return null;
    }
}
