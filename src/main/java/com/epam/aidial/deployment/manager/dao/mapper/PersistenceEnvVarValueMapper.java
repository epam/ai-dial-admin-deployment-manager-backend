package com.epam.aidial.deployment.manager.dao.mapper;

import com.epam.aidial.deployment.manager.dao.entity.PersistenceEnvVarValue;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceFileEnvVarValue;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceSimpleEnvVarValue;
import com.epam.aidial.deployment.manager.model.EnvVarValue;
import com.epam.aidial.deployment.manager.model.FileEnvVarValue;
import com.epam.aidial.deployment.manager.model.SimpleEnvVarValue;
import org.mapstruct.Mapper;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;

@Mapper(componentModel = "spring", subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)
public interface PersistenceEnvVarValueMapper {

    @SubclassMapping(target = PersistenceSimpleEnvVarValue.class, source = SimpleEnvVarValue.class)
    @SubclassMapping(target = PersistenceFileEnvVarValue.class, source = FileEnvVarValue.class)
    PersistenceEnvVarValue toEntity(EnvVarValue domain);

    PersistenceSimpleEnvVarValue toPersistenceSimpleEnvVarValue(SimpleEnvVarValue simpleEnvVarValue);

    PersistenceFileEnvVarValue toPersistenceFileEnvVarValue(FileEnvVarValue fileEnvVarValue);

    @SubclassMapping(target = SimpleEnvVarValue.class, source = PersistenceSimpleEnvVarValue.class)
    @SubclassMapping(target = FileEnvVarValue.class, source = PersistenceFileEnvVarValue.class)
    EnvVarValue toDomain(PersistenceEnvVarValue entity);

    SimpleEnvVarValue toSimpleEnvVarValue(PersistenceSimpleEnvVarValue simpleEnvVarValue);

    FileEnvVarValue toFileEnvVarValue(PersistenceFileEnvVarValue fileEnvVarValue);
}
