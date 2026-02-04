package com.epam.aidial.deployment.manager.huggingface.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public final class TagsInfo {
    @Builder.Default
    private List<TagInfo> language = List.of();
    @Builder.Default
    private List<TagInfo> library = List.of();
    @Builder.Default
    private List<TagInfo> license = List.of();
    @Builder.Default
    private List<TagInfo> dataset = List.of();

    public List<TagInfo> getAllTags() {
        var list = new ArrayList<TagInfo>(language.size() + library.size() + license.size() + dataset.size());
        list.addAll(language);
        list.addAll(library);
        list.addAll(license);
        list.addAll(dataset);
        return list;
    }
}
