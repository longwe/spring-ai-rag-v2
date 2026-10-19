package com.ezcloud.springai.controller;

import com.ezcloud.springai.dto.IngestionResponse;
import com.ezcloud.springai.ingestion.DocumentIngestionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentIngestionService ingestionService;

    public DocumentController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public IngestionResponse upload(@RequestParam("file") MultipartFile file) {
        String source = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        int chunks = ingestionService.ingest(file.getResource(), source);
        return new IngestionResponse(source, chunks);
    }
}
