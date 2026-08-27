package com.saed.backend.common.service.impl;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.saed.backend.common.service.PdfService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public class PdfServiceImpl implements PdfService {

    @Override
    public byte[] generarPdf(String htmlContent) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        // Replace &nbsp; to prevent XML parsing issues from Jsoup/Flying Saucer
        String safeHtml = htmlContent.replace("&nbsp;", "&#160;");
        if (!safeHtml.contains("<!DOCTYPE")) {
            safeHtml = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n" + safeHtml;
        }
        builder.withHtmlContent(safeHtml, "file:///");
        builder.toStream(outputStream);
        builder.run();
        return outputStream.toByteArray();
    }
}
