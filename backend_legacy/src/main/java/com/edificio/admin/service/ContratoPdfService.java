package com.edificio.admin.service;

import com.edificio.admin.model.enums.TipoContrato;
import com.edificio.admin.rest.dto.ContratoDetalleDTO;

public class ContratoPdfService {

    private final TemplateRenderService templateService;
    private final PdfGeneratorService pdfGenerator;

    public ContratoPdfService() {
        this.templateService = new TemplateRenderService();
        this.pdfGenerator = new PdfGeneratorService();
    }

    public byte[] generarPdf(ContratoDetalleDTO detalle) throws Exception {
        String html = templateService.renderizar(detalle.getTipoContrato(), detalle);
        // Replace &nbsp; with numeric entity to avoid DTD dependency
        html = html.replace("&nbsp;", "&#160;");
        // Use XHTML DOCTYPE required by OpenHTMLtoPDF (Flying Saucer)
        html = html.replace("<!DOCTYPE html>",
            "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">");
        return pdfGenerator.generarPdf(html);
    }

    public byte[] generarPdfConTipo(ContratoDetalleDTO detalle, TipoContrato tipo) throws Exception {
        detalle.setTipoContrato(tipo);
        return generarPdf(detalle);
    }

}
