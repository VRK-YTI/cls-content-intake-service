package fi.vm.yti.codelist.intake.dao;

import java.util.Set;

import fi.vm.yti.codelist.common.dto.CodeRegistryDTO;
import fi.vm.yti.codelist.intake.model.CodeRegistry;

public interface CodeRegistryDao {

    void save(final Set<CodeRegistry> codeRegistries);

    void save(final Set<CodeRegistry> codeRegistries,
              final boolean logChange);

    void delete(final CodeRegistry codeRegistry);

    Set<CodeRegistry> findAll();

    CodeRegistry findByCodeValue(final String codeValue);

    CodeRegistry updateCodeRegistryFromDto(final CodeRegistryDTO codeRegistryDto);

    Set<CodeRegistry> updateCodeRegistriesFromDto(final Set<CodeRegistryDTO> codeRegistryDtos);
}
