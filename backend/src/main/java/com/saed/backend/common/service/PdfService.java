package com.saed.backend.common.service;

public interface PdfService {
    byte[] generarPdf(String htmlContent) throws Exception;
}
