package fi.vm.yti.codelist.intake.jpa;

import java.util.Set;
import java.util.UUID;

import javax.transaction.Transactional;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import fi.vm.yti.codelist.intake.model.Extension;
import fi.vm.yti.codelist.intake.model.ExtensionScheme;

@Repository
@Transactional
public interface ExtensionRepository extends CrudRepository<Extension, String> {

    Set<Extension> findAll();

    Set<Extension> findByExtensionSchemeId(final UUID id);

    Extension findById(final UUID id);

    Extension findByExtensionSchemeAndId(final ExtensionScheme extensionScheme,
                                         final UUID id);
}
