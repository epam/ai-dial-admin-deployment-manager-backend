package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.model.EnvVarValue;
import com.epam.aidial.deployment.manager.model.FileEnvVarValue;
import com.epam.aidial.deployment.manager.model.SimpleEnvVarValue;
import com.epam.aidial.deployment.manager.web.dto.value.EnvVarValueDto;
import com.epam.aidial.deployment.manager.web.dto.value.FileEnvVarValueDto;
import com.epam.aidial.deployment.manager.web.dto.value.SimpleEnvVarValueDto;
import org.mapstruct.Mapper;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;

@Mapper(componentModel = "spring", subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)
public interface EnvVarValueDtoMapper {

    @SubclassMapping(target = SimpleEnvVarValue.class, source = SimpleEnvVarValueDto.class)
    @SubclassMapping(target = FileEnvVarValue.class, source = FileEnvVarValueDto.class)
    EnvVarValue toDomain(EnvVarValueDto dto);

    SimpleEnvVarValue toSimpleEnvVarValue(SimpleEnvVarValueDto simpleEnvVarValueDto);

    FileEnvVarValue toFileEnvVarValue(FileEnvVarValueDto fileEnvVarValueDto);

    @SubclassMapping(target = SimpleEnvVarValueDto.class, source = SimpleEnvVarValue.class)
    @SubclassMapping(target = FileEnvVarValueDto.class, source = FileEnvVarValue.class)
    EnvVarValueDto toDto(EnvVarValue domain);

    SimpleEnvVarValueDto toSimpleEnvVarValueDto(SimpleEnvVarValue simpleEnvVarValue);

    FileEnvVarValueDto toFileEnvVarValueDto(FileEnvVarValue fileEnvVarValue);
}
