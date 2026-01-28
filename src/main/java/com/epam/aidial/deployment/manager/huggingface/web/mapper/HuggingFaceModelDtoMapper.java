package com.epam.aidial.deployment.manager.huggingface.web.mapper;

import com.epam.aidial.deployment.manager.huggingface.model.HuggingFaceModel;
import com.epam.aidial.deployment.manager.huggingface.model.HuggingFaceTagInfo;
import com.epam.aidial.deployment.manager.huggingface.web.dto.HuggingFaceModelDto;
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
    public abstract HuggingFaceModelDto toDto(HuggingFaceModel model, @Context Map<String, HuggingFaceTagInfo> tagDictionary);

    @AfterMapping
    void mapTags(HuggingFaceModel model,
                 @MappingTarget HuggingFaceModelDto dto,
                 @Context Map<String, HuggingFaceTagInfo> tagDictionary) {
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
                case "library":
                    libraries.add(tagInfo.label());
                    break;
                case "language":
                    languages.add(tagInfo.label());
                    break;
                case "license":
                    licenses.add(tagInfo.label());
                    break;
                case "dataset":
                    datasets.add(tagInfo.label());
                    break;
                default:
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
