package com.epam.aidial.deployment.manager.web.controller;

import com.epam.aidial.deployment.manager.service.TopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/topics")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;

    @GetMapping(produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public List<String> getAllTopics() {
        return topicService.getAllTopics();
    }

}
