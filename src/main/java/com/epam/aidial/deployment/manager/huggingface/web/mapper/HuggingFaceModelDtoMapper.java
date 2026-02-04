package com.epam.aidial.deployment.manager.huggingface.web.mapper;

import com.epam.aidial.deployment.manager.huggingface.model.Model;
import com.epam.aidial.deployment.manager.huggingface.model.TagInfo;
import com.epam.aidial.deployment.manager.huggingface.web.dto.ModelDto;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.ArrayList;
import java.util.Map;

@Slf4j
@Mapper(componentModel = "spring")
public abstract class HuggingFaceModelDtoMapper {

    @Mapping(target = "libraries", ignore = true)
    @Mapping(target = "languages", ignore = true)
    @Mapping(target = "licenses", ignore = true)
    @Mapping(target = "datasets", ignore = true)
    @Mapping(target = "parameters", source = "safetensors.total")
    public abstract ModelDto toDto(Model model, @Context Map<String, TagInfo> tagDictionary);

    @AfterMapping
    void mapTags(Model model,
                 @MappingTarget ModelDto dto,
                 @Context Map<String, TagInfo> tagDictionary) {
        if (model.getTags() == null || tagDictionary == null) {
            return;
        }

        var libraries = new ArrayList<String>();
        var languages = new ArrayList<String>();
        var licenses = new ArrayList<String>();
        var datasets = new ArrayList<String>();

        for (var tag : model.getTags()) {
            var tagInfo = tagDictionary.get(tag);
            if (tagInfo == null) {
                continue;
            }

            switch (tagInfo.type()) {
                case LIBRARY:
                    libraries.add(tagInfo.label());
                    break;
                case LANGUAGE:
                    languages.add(tagInfo.label());
                    break;
                case LICENSE:
                    licenses.add(tagInfo.label());
                    break;
                case DATASET:
                    datasets.add(tagInfo.label());
                    break;
                case null, default:
                    log.debug("Received unsupported tag type: {}. tag: {}. model: {}", tagInfo.type(), tag, model.getId());
                    break;
            }
        }

        dto.setLibraries(libraries);
        dto.setLanguages(languages);
        dto.setLicenses(licenses);
        dto.setDatasets(datasets);
    }
}
