package com.epam.aidial.deployment.manager.web.controller;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageDefinitionView;
import com.epam.aidial.deployment.manager.model.ImageType;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.web.dto.BaseImageDetailsDto;
import com.epam.aidial.deployment.manager.web.dto.ImageDefinitionDto;
import com.epam.aidial.deployment.manager.web.dto.ImageDefinitionRequestDto;
import com.epam.aidial.deployment.manager.web.dto.ImageDefinitionViewDto;
import com.epam.aidial.deployment.manager.web.dto.ImageTypeDto;
import com.epam.aidial.deployment.manager.web.mapper.ImageDefinitionDtoMapper;
import com.epam.aidial.deployment.manager.web.mapper.ImageDefinitionViewDtoMapper;
import com.epam.aidial.deployment.manager.web.security.FullAdminOnly;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@LogExecution
@RestController
@RequestMapping("/api/v1/images/definitions")
@RequiredArgsConstructor
public class ImageDefinitionController {

    private final ImageDefinitionService imageDefinitionService;
    private final ImageDefinitionDtoMapper dtoMapper;
    private final ImageDefinitionViewDtoMapper viewDtoMapper;

    @GetMapping(produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public List<ImageDefinitionDto> getAllImageDefinitions(
            @RequestParam(required = false) ImageTypeDto type
    ) {
        Collection<ImageDefinition> imageDefinitions = type != null
                ? imageDefinitionService.getAllImageDefinitionsByType(toImageType(type))
                : imageDefinitionService.getAllImageDefinitions();
        return imageDefinitions.stream()
                .map(dtoMapper::toImageDefinitionDto)
                .collect(Collectors.toList());
    }

    @GetMapping(path = "/grouped",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public List<ImageDefinitionViewDto> getGroupedImageDefinitionViews(
            @RequestParam(required = false) ImageTypeDto type
    ) {
        Collection<ImageDefinitionView> imageDefinitionViews = type != null
                ? imageDefinitionService.getImageDefinitionViewsByType(toImageType(type))
                : imageDefinitionService.getImageDefinitionViews();
        return imageDefinitionViews.stream()
                .map(viewDtoMapper::toDto)
                .collect(Collectors.toList());
    }

    @GetMapping(path = "/{id}",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public ImageDefinitionDto getImageDefinitionById(@PathVariable UUID id) {
        return imageDefinitionService.getImageDefinition(id)
                .map(dtoMapper::toImageDefinitionDto)
                .orElseThrow(() -> new EntityNotFoundException("Image definition not found by id: %s".formatted(id)));
    }

    @GetMapping(path = "/{id}/revision/{revision}",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public ImageDefinitionDto getImageDefinitionSnapshot(@PathVariable UUID id, @PathVariable Integer revision) {
        return dtoMapper.toImageDefinitionDto(imageDefinitionService.getImageDefinitionSnapshot(id, revision));
    }

    @GetMapping(path = "/revision/{revision}",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public List<ImageDefinitionDto> getAllImageDefinitionsAtRevision(@PathVariable Integer revision) {
        return imageDefinitionService.getAllImageDefinitionsAtRevision(revision).stream()
                .map(dtoMapper::toImageDefinitionDto)
                .collect(Collectors.toList());
    }

    @GetMapping(path = "/{name}/versions",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public List<BaseImageDetailsDto> getImageVersionsWithStatusesByName(
            @PathVariable String name,
            @RequestParam(required = false) ImageTypeDto type
    ) {
        Collection<ImageDefinition> imageDefinitions = type != null
                ? imageDefinitionService.getAllImageDefinitionsByNameAndType(name, toImageType(type))
                : imageDefinitionService.getAllImageDefinitionsByName(name);
        return imageDefinitions.stream()
                .map(dtoMapper::toBaseImageDetailsDto)
                .collect(Collectors.toList());
    }

    @FullAdminOnly
    @PostMapping(consumes = MimeTypeUtils.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImageDefinitionDto createImageDefinition(@RequestBody @Valid ImageDefinitionRequestDto requestDto) {
        var imageDefinition = dtoMapper.toImageDefinition(requestDto);
        var created = imageDefinitionService.createImageDefinition(imageDefinition);
        return dtoMapper.toImageDefinitionDto(created);
    }

    @FullAdminOnly
    @PutMapping(path = "/{id}",
            consumes = MimeTypeUtils.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public ImageDefinitionDto updateImageDefinition(@PathVariable UUID id,
                                                    @RequestBody @Valid ImageDefinitionRequestDto requestDto) {
        var imageDefinition = dtoMapper.toImageDefinition(requestDto);
        var updated = imageDefinitionService.updateImageDefinition(id, imageDefinition);
        return dtoMapper.toImageDefinitionDto(updated);
    }

    @FullAdminOnly
    @DeleteMapping(path = "/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteImageDefinition(@PathVariable UUID id) {
        imageDefinitionService.deleteImageDefinitionSync(id);
    }

    private static ImageType toImageType(ImageTypeDto dto) {
        return dto == null ? null : ImageType.valueOf(dto.name());
    }
}
