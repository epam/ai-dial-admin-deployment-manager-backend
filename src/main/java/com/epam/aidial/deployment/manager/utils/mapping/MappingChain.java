package com.epam.aidial.deployment.manager.utils.mapping;

import java.util.List;

public record MappingChain<T>(T data) {
    public <Y> MappingChain<Y> get(FieldMapper<T, Y> fieldMapper) {
        return new MappingChain<>(fieldMapper.getOrSet(data));
    }

    public <Y> MappingChain<Y> getNullable(FieldMapper<T, Y> fieldMapper) {
        return new MappingChain<>(fieldMapper.getter().apply(data));
    }

    public <Y> ListMapper<Y> getList(FieldMapper<T, List<Y>> fieldMapper, NamedItemMapper<Y> itemMapper) {
        return new ListMapper<>(fieldMapper.getOrSet(data), itemMapper);
    }
}
