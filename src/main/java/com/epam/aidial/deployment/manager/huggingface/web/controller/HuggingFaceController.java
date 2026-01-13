package com.epam.aidial.deployment.manager.huggingface.web.controller;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.huggingface.model.HuggingFaceModelsPageResponse;
import com.epam.aidial.deployment.manager.huggingface.model.HuggingFaceModelsRequest;
import com.epam.aidial.deployment.manager.huggingface.service.HuggingFaceService;
import com.epam.aidial.deployment.manager.huggingface.web.dto.HuggingFaceModelDto;
import com.epam.aidial.deployment.manager.huggingface.web.dto.HuggingFaceModelsPageResponseDto;
import com.epam.aidial.deployment.manager.huggingface.web.mapper.HuggingFaceModelDtoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@LogExecution
@RestController
@RequestMapping("/api/v1/huggingface/models")
@RequiredArgsConstructor
public class HuggingFaceController {

    private final HuggingFaceService huggingFaceService;
    private final HuggingFaceModelDtoMapper dtoMapper;

    @GetMapping(produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public HuggingFaceModelsPageResponseDto getModelsPage(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Boolean full,
            @RequestParam(required = false) Boolean config,
            @RequestParam(required = false) String pageUrl
    ) {
        HuggingFaceModelsRequest request = HuggingFaceModelsRequest.builder()
                .search(search)
                .author(author)
                .filter(filter)
                .sort(sort)
                .direction(direction)
                .limit(limit)
                .full(full)
                .config(config)
                .build();

        HuggingFaceModelsPageResponse pageResponse = huggingFaceService.getModelsPage(request, pageUrl);
        
        List<HuggingFaceModelDto> modelDtos = pageResponse.getModels().stream()
                .map(dtoMapper::toDto)
                .collect(Collectors.toList());

        return HuggingFaceModelsPageResponseDto.builder()
                .models(modelDtos)
                .nextPageUrl(pageResponse.getNextPageUrl())
                .hasNextPage(pageResponse.getHasNextPage())
                .prevPageUrl(pageResponse.getPrevPageUrl())
                .hasPrevPage(pageResponse.getHasPrevPage())
                .build();
    }
}
