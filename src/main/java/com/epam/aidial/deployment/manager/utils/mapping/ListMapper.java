package com.epam.aidial.deployment.manager.utils.mapping;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ListMapper<T> {
    private final List<T> list;
    private final Supplier<T> itemFactory;
    private final BiConsumer<T, String> nameSetter;
    private final Map<String, T> map;

    public ListMapper(List<T> list, NamedItemMapper<T> itemMapper) {
        this.list = list;
        this.itemFactory = itemMapper.factory();
        this.nameSetter = itemMapper.setter();
        this.map = list.stream()
                .collect(Collectors.toMap(itemMapper.getter(), Function.identity()));
    }

    public MappingChain<T> get(String name) {
        return getOrDefault(name, itemFactory);
    }

    public MappingChain<T> getOrDefault(String name, Supplier<T> itemFactory) {
        return new MappingChain<>(map.computeIfAbsent(name, n -> {
            T newItem = itemFactory.get();
            nameSetter.accept(newItem, n);
            list.add(newItem);

            return newItem;
        }));
    }
}
