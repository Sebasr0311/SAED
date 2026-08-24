package com.edificio.admin.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;

public class PdfGeneratorService {

    public byte[] generarPdf(String htmlContent) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(htmlContent, "file:///");
        builder.toStream(outputStream);
        builder.run();
        return outputStream.toByteArray();
    }
}
