package fi.vm.yti.codelist.intake.jpa;

import java.util.Set;
import java.util.UUID;

import javax.transaction.Transactional;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import fi.vm.yti.codelist.intake.model.CodeScheme;
import fi.vm.yti.codelist.intake.model.Extension;

@Repository
@Transactional
public interface ExtensionRepository extends CrudRepository<Extension, String> {

    Set<Extension> findAll();

    Extension findById(final UUID id);

    Set<Extension> findByParentCodeScheme(final CodeScheme codeScheme);

    Set<Extension> findByParentCodeSchemeId(final UUID codeSchemeId);

    Extension findByParentCodeSchemeAndCodeValueIgnoreCase(final CodeScheme codeScheme,
                                                           final String codeValue);

    Extension findByParentCodeSchemeIdAndCodeValueIgnoreCase(final UUID codeSchemeId,
                                                             final String codeValue);
}
