package com.epam.aidial.deployment.manager.huggingface.web.controller;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.huggingface.client.HuggingFaceClientException;
import com.epam.aidial.deployment.manager.huggingface.model.HuggingFaceFileRequest;
import com.epam.aidial.deployment.manager.huggingface.model.HuggingFaceModelsRequest;
import com.epam.aidial.deployment.manager.huggingface.service.HuggingFaceService;
import com.epam.aidial.deployment.manager.huggingface.web.dto.HuggingFaceModelsPageResponseDto;
import com.epam.aidial.deployment.manager.huggingface.web.mapper.HuggingFaceModelDtoMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
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
        var request = HuggingFaceModelsRequest.builder()
                .search(search)
                .author(author)
                .filter(filter)
                .sort(sort)
                .direction(direction)
                .limit(limit)
                .full(full)
                .config(config)
                .build();

        var pageResponse = huggingFaceService.getModelsPage(request, pageUrl);

        var tagDictionary = huggingFaceService.getTagDictionary();

        var modelDtos = pageResponse.getModels().stream()
                .map(model -> dtoMapper.toDto(model, tagDictionary))
                .collect(Collectors.toList());

        return HuggingFaceModelsPageResponseDto.builder()
                .models(modelDtos)
                .nextPageUrl(pageResponse.getNextPageUrl())
                .prevPageUrl(pageResponse.getPrevPageUrl())
                .build();
    }

    @GetMapping(value = "/{namespace}/{repoName}/resolve/{revision}/**")
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @PathVariable String namespace,
            @PathVariable String repoName,
            @PathVariable String revision,
            HttpServletRequest request
    ) {
        var fullPath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        var bestMatchPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        var rawFilePath = new AntPathMatcher().extractPathWithinPattern(bestMatchPattern, fullPath);

        var filePath = URLDecoder.decode(rawFilePath, StandardCharsets.UTF_8);
        var modelName = namespace + "/" + repoName;

        var fileRequest = HuggingFaceFileRequest.builder()
                .modelName(modelName)
                .revision(revision)
                .filePath(filePath)
                .build();

        try {
            var responseBody = huggingFaceService.downloadFile(fileRequest);
            var inputStream = responseBody.byteStream();

            // Safe filename extraction
            var fileName = Paths.get(filePath).getFileName().toString();

            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            if (responseBody.contentType() != null) {
                try {
                    mediaType = MediaType.parseMediaType(responseBody.contentType().toString());
                } catch (InvalidMediaTypeException ignored) {
                    log.debug("Invalid media type: {}", responseBody.contentType());
                }
            }

            StreamingResponseBody streamingResponse = outputStream -> {
                try (inputStream) {
                    inputStream.transferTo(outputStream);
                } catch (Exception e) {
                    log.warn("Stream error for file: {}", filePath, e);
                }
            };

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(fileName)
                            .build()
                            .toString())
                    .body(streamingResponse);

        } catch (HuggingFaceClientException e) {
            log.warn("Upstream error: {}", e.getMessage());
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (Exception e) {
            log.warn("Download failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

}
