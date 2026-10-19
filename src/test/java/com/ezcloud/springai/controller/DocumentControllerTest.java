package com.ezcloud.springai.controller;

import com.ezcloud.springai.ingestion.DocumentIngestionService;
import com.ezcloud.springai.ingestion.UnsupportedDocumentTypeException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentIngestionService ingestionService;

    @Test
    void uploadReturnsChunkCount() throws Exception {
        when(ingestionService.ingest(any(), eq("notes.txt"))).thenReturn(3);

        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("notes.txt"))
                .andExpect(jsonPath("$.chunks").value(3));
    }

    @Test
    void uploadOfUnsupportedTypeReturns415() throws Exception {
        when(ingestionService.ingest(any(), eq("data.xlsx")))
                .thenThrow(new UnsupportedDocumentTypeException("data.xlsx"));

        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "data.xlsx", "application/octet-stream", new byte[1])))
                .andExpect(status().isUnsupportedMediaType());
    }
}
