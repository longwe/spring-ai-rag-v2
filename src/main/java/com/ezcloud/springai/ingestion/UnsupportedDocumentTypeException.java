package com.ezcloud.springai.ingestion;

public class UnsupportedDocumentTypeException extends RuntimeException {

    public UnsupportedDocumentTypeException(String source) {
        super("Unsupported document type: '" + source + "'. Supported types are .pdf, .txt and .md");
    }
}
