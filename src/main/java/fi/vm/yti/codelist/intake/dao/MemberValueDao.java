package fi.vm.yti.codelist.intake.dao;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import fi.vm.yti.codelist.common.dto.MemberValueDTO;
import fi.vm.yti.codelist.intake.model.Member;
import fi.vm.yti.codelist.intake.model.MemberValue;
import fi.vm.yti.codelist.intake.model.ValueType;

public interface MemberValueDao {

    MemberValue findById(final UUID id);

    void save(final MemberValue membeValue);

    void save(final Set<MemberValue> memberValues);

    Set<MemberValue> updateMemberValueEntitiesFromDtos(final Member member,
                                                       final Set<MemberValueDTO> memberValueDtos,
                                                       final Map<String, ValueType> valueTypeMap);
}
