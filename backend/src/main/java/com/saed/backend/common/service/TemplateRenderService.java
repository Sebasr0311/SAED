package com.saed.backend.common.service;

import com.saed.backend.finanzas.dto.ContratoDetalleDTO;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@org.springframework.stereotype.Service
public class TemplateRenderService {

    private static final String TEMPLATE_BASE_PATH = "/templates/contratos/";
    private final VariableResolverService variableResolver;

    public TemplateRenderService(VariableResolverService variableResolver) {
        this.variableResolver = variableResolver;
    }

    public String cargarPlantilla(String tipo) throws IOException {
        String filename = switch (tipo.toUpperCase()) {
            case "INICIAL" -> "contrato_inicial.html";
            case "RENOVACION" -> "contrato_renovacion.html";
            case "PERMANENCIA" -> "contrato_permanencia.html";
            default -> "contrato_inicial.html";
        };
        InputStream is = getClass().getResourceAsStream(TEMPLATE_BASE_PATH + filename);
        if (is == null) {
            is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(TEMPLATE_BASE_PATH + filename);
        }
        if (is == null) {
            throw new FileNotFoundException("Plantilla no encontrada: " + TEMPLATE_BASE_PATH + filename);
        }
        byte[] bytes = is.readAllBytes();
        is.close();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public String renderizar(String tipo, ContratoDetalleDTO detalle) throws IOException {
        String template = cargarPlantilla(tipo);
        Map<String, Object> variables = variableResolver.construirMapaVariables(detalle);
        return variableResolver.resolverVariables(template, variables);
    }

    public String validarYLimpiarHtml(String html) {
        Document doc = Jsoup.parse(html);
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        String xhtml = doc.html();
        xhtml = xhtml.replace("<!DOCTYPE html>", "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">");
        return xhtml;
    }

    public String extraerCssInline(String html, String cssContent) {
        int idx = html.indexOf("</style>");
        if (idx > 0) {
            return html.substring(0, idx) + "\n" + cssContent + "\n" + html.substring(idx);
        }
        return html;
    }
}



