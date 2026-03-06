package com.epam.aidial.deployment.manager.web.utils;

import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@UtilityClass
public final class MapExtractionUtils {

    public static <K, V> Optional<V> extractFirstNonNullValue(Map<K, V> source, Collection<K> keys) {
        if (MapUtils.isEmpty(source) || CollectionUtils.isEmpty(keys)) {
            return Optional.empty();
        }

        return keys.stream()
                .map(source::get)
                .filter(Objects::nonNull)
                .findFirst();
    }
}