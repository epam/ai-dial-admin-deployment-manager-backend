package com.epam.aidial.deployment.manager.dao.mapper;

import org.mapstruct.Mapper;

import java.time.Instant;

@Mapper(componentModel = "spring")
public interface PersistenceTimestampMapper {

    default Instant longToInstant(Long timestamp) {
        return timestamp != null ? Instant.ofEpochMilli(timestamp) : null;
    }

    default Long instantToEpochMillis(Instant instant) {
        return instant != null ? instant.toEpochMilli() : null;
    }
}
