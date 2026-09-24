package com.saed.backend.common.service;

import com.saed.backend.finanzas.dto.ContratoDetalleDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class VariableResolverService {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", new Locale("es", "CO"));
    private static final NumberFormat CURRENCY_FORMAT =
            NumberFormat.getCurrencyInstance(new Locale("es", "CO"));

    private static final Set<String> CRITICAL_CONTRACT_VARIABLES = Set.of(
            "residente.nombreCompleto",
            "inquilino.nombre_completo",
            "nombreCompletoResidente",
            "unidad.identificador",
            "apartamento.numero",
            "numeroApartamento",
            "propiedad.nombre",
            "nombreEdificio",
            "contrato.valorMensual",
            "contrato.canon_mensual",
            "valorCanon",
            "contrato.fechaInicio",
            "fechaInicio"
    );

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("(\\$\\{([a-zA-Z0-9_.]+)\\}|\\{\\{([a-zA-Z0-9_.]+)\\}\\})");

    public String resolverVariables(String html, Map<String, Object> variables) {
        if (html == null || html.isBlank()) {
            return "";
        }

        Map<String, Object> vars = variables != null ? variables : Collections.emptyMap();
        Matcher matcher = VARIABLE_PATTERN.matcher(html);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            Object rawValue = vars.get(key);

            // Validation: if this is a critical contractual variable and it's missing or null
            if (CRITICAL_CONTRACT_VARIABLES.contains(key) && (rawValue == null || (rawValue instanceof String str && str.trim().isEmpty()))) {
                throw new IllegalStateException("Variable contractual esencial requerida y no resuelta: " + key);
            }

            String valor = formatearValor(rawValue);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(valor));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public String formatearValor(Object valor) {
        if (valor == null) return "";
        if (valor instanceof LocalDate d) return d.format(DATE_FORMAT);
        if (valor instanceof BigDecimal bd) return CURRENCY_FORMAT.format(bd);
        if (valor instanceof Integer i) return String.valueOf(i);
        if (valor instanceof Long l) return String.valueOf(l);
        return valor.toString();
    }

    public Map<String, Object> construirMapaVariables(ContratoDetalleDTO dto) {
        Map<String, Object> vars = new HashMap<>();
        if (dto == null) {
            return vars;
        }

        // 1. Identificación y Organizacion
        vars.put("organizacion.nombre", dto.getNombreOrganizacion() != null ? dto.getNombreOrganizacion() : "Administración");
        vars.put("organizacion.nit", dto.getNitAdministrador() != null ? dto.getNitAdministrador() : "");
        vars.put("nitAdministrador", dto.getNitAdministrador() != null ? dto.getNitAdministrador() : "");

        // 2. Propiedad / Edificio
        String propNombre = dto.getNombreEdificio() != null ? dto.getNombreEdificio() : "Propiedad";
        vars.put("propiedad.nombre", propNombre);
        vars.put("nombreEdificio", propNombre);
        vars.put("propiedad.direccion", dto.getDireccionEdificio() != null ? dto.getDireccionEdificio() : "");
        vars.put("direccionEdificio", dto.getDireccionEdificio() != null ? dto.getDireccionEdificio() : "");
        vars.put("propiedad.ciudad", dto.getCiudadEdificio() != null ? dto.getCiudadEdificio() : "");
        vars.put("ciudadEdificio", dto.getCiudadEdificio() != null ? dto.getCiudadEdificio() : "");

        // 3. Unidad / Apartamento
        String aptoNum = dto.getNumeroApartamento() != null ? dto.getNumeroApartamento() : "";
        vars.put("unidad.identificador", aptoNum);
        vars.put("apartamento.numero", aptoNum);
        vars.put("numeroApartamento", aptoNum);
        vars.put("tipoApartamento", dto.getTipoApartamento() != null ? dto.getTipoApartamento() : "Apartamento");
        vars.put("piso", dto.getPiso() != null ? dto.getPiso() : 1);
        vars.put("area", dto.getArea() != null ? dto.getArea() : BigDecimal.ZERO);
        vars.put("capacidadMaxima", dto.getCapacidadMaxima() != null ? dto.getCapacidadMaxima() : 4);

        // 4. Arrendatario / Residente
        String nombreRes = dto.getNombreCompletoResidente() != null ? dto.getNombreCompletoResidente() : "";
        vars.put("residente.nombreCompleto", nombreRes);
        vars.put("inquilino.nombre_completo", nombreRes);
        vars.put("nombreCompletoResidente", nombreRes);
        vars.put("residente.nombres", dto.getNombresResidente() != null ? dto.getNombresResidente() : "");
        vars.put("nombresResidente", dto.getNombresResidente() != null ? dto.getNombresResidente() : "");
        vars.put("residente.apellidos", dto.getApellidosResidente() != null ? dto.getApellidosResidente() : "");
        vars.put("apellidosResidente", dto.getApellidosResidente() != null ? dto.getApellidosResidente() : "");
        vars.put("residente.tipoDocumento", dto.getTipoDocumentoResidente() != null ? dto.getTipoDocumentoResidente() : "CC");
        vars.put("tipoDocumento", dto.getTipoDocumentoResidente() != null ? dto.getTipoDocumentoResidente() : "CC");
        vars.put("residente.numeroDocumento", dto.getNumeroDocumentoResidente() != null ? dto.getNumeroDocumentoResidente() : "");
        vars.put("inquilino.documento", dto.getNumeroDocumentoResidente() != null ? dto.getNumeroDocumentoResidente() : "");
        vars.put("numeroDocumento", dto.getNumeroDocumentoResidente() != null ? dto.getNumeroDocumentoResidente() : "");
        vars.put("residente.telefono", dto.getTelefonoResidente() != null ? dto.getTelefonoResidente() : "");
        vars.put("telefonoResidente", dto.getTelefonoResidente() != null ? dto.getTelefonoResidente() : "");
        vars.put("residente.email", dto.getCorreoResidente() != null ? dto.getCorreoResidente() : "");
        vars.put("correoResidente", dto.getCorreoResidente() != null ? dto.getCorreoResidente() : "");

        // 5. Contrato
        String numContrato = dto.getNumeroContrato() != null ? dto.getNumeroContrato() : "";
        vars.put("contrato.numero", numContrato);
        vars.put("contrato.numeroContrato", numContrato);
        vars.put("numeroContrato", numContrato);
        vars.put("contrato.fechaInicio", dto.getFechaInicio());
        vars.put("fechaInicio", dto.getFechaInicio());
        vars.put("contrato.fechaFin", dto.getFechaFin() != null ? dto.getFechaFin() : "Indefinido");
        vars.put("fechaFin", dto.getFechaFin() != null ? dto.getFechaFin() : "SIN FECHA DE VENCIMIENTO");
        vars.put("periodoVigencia", dto.getPeriodoVigencia() != null ? dto.getPeriodoVigencia() : "12 meses");
        vars.put("contrato.tipoContrato", dto.getTipoContrato() != null ? dto.getTipoContrato() : "INICIAL");
        vars.put("tipoContrato", dto.getTipoContrato() != null ? dto.getTipoContrato() : "INICIAL");

        BigDecimal canon = dto.getValorCanon() != null ? dto.getValorCanon() : BigDecimal.ZERO;
        vars.put("contrato.valorMensual", canon);
        vars.put("contrato.canon_mensual", canon);
        vars.put("valorCanon", canon);
        vars.put("valorAdministracion", dto.getValorAdministracion() != null ? dto.getValorAdministracion() : BigDecimal.ZERO);
        vars.put("valorTotal", dto.getValorTotal() != null ? dto.getValorTotal() : canon);
        vars.put("valorDeposito", dto.getValorDeposito() != null ? dto.getValorDeposito() : BigDecimal.ZERO);
        vars.put("diaPago", dto.getDiaPago() > 0 ? dto.getDiaPago() : 5);
        vars.put("diasGracia", dto.getDiasGracia());
        vars.put("porcentajeMora", dto.getPorcentajeMora());

        // 6. Tutor / Representante Legal (opcional)
        String nomTut = dto.getNombreTutor() != null ? dto.getNombreTutor() : "";
        String cedTut = dto.getCedulaTutor() != null ? dto.getCedulaTutor() : "";
        String relTut = dto.getRelacionTutor() != null ? dto.getRelacionTutor() : (dto.getParentescoTutor() != null ? dto.getParentescoTutor() : "");
        String telTut = dto.getTelefonoTutor() != null ? dto.getTelefonoTutor() : "";
        String mailTut = dto.getEmailTutor() != null ? dto.getEmailTutor() : "";

        vars.put("nombreTutor", nomTut);
        vars.put("tutor.nombre", nomTut);
        vars.put("cedulaTutor", cedTut);
        vars.put("tutor.cedula", cedTut);
        vars.put("tutor.documento", cedTut);
        vars.put("relacionTutor", relTut);
        vars.put("tutor.parentesco", relTut);
        vars.put("tutor.relacion", relTut);
        vars.put("telefonoTutor", telTut);
        vars.put("tutor.telefono", telTut);
        vars.put("correoTutor", mailTut);
        vars.put("emailTutor", mailTut);
        vars.put("tutor.email", mailTut);

        // 6b. Coarrendatarios (0..N)
        List<com.saed.backend.finanzas.dto.CoarrendatarioDTO> coarrendatarios = dto.getCoarrendatarios() != null
                ? dto.getCoarrendatarios()
                : Collections.emptyList();

        vars.put("coarrendatarios.total", String.valueOf(coarrendatarios.size()));

        if (coarrendatarios.isEmpty()) {
            vars.put("coarrendatarios.lista", "Ninguno");
            vars.put("coarrendatarios.nombres", "");
            vars.put("coarrendatarios.firmas_html", "");
        } else {
            StringBuilder sbLista = new StringBuilder();
            StringBuilder sbNombres = new StringBuilder();
            StringBuilder sbFirmas = new StringBuilder();

            sbFirmas.append("<div class=\"firmas-coarrendatarios\" style=\"margin-top: 25px; page-break-inside: avoid;\">");
            sbFirmas.append("<table style=\"width: 100%; border: none; border-collapse: collapse;\"><tr>");

            int idx = 0;
            for (com.saed.backend.finanzas.dto.CoarrendatarioDTO c : coarrendatarios) {
                String cNombre = c.nombrePersona() != null ? c.nombrePersona() : ("Persona #" + c.idPersona());
                String cDoc = c.numeroDocumento() != null ? c.numeroDocumento() : "Sin documento";
                String cTel = c.telefono() != null ? c.telefono() : "N/A";
                String esResp = "S".equalsIgnoreCase(c.esResponsablePago()) ? "Sí" : "No";

                if (idx > 0) {
                    sbLista.append("\n");
                    sbNombres.append(", ");
                }
                sbLista.append((idx + 1)).append(". ").append(cNombre)
                       .append(" (Doc: ").append(cDoc).append(") - ").append(c.tipoVinculo())
                       .append(" - Responsable de pago: ").append(esResp);
                sbNombres.append(cNombre);

                if (idx > 0 && idx % 2 == 0) {
                    sbFirmas.append("</tr><tr>");
                }

                sbFirmas.append("<td style=\"width: 50%; vertical-align: top; padding: 15px;\">");
                sbFirmas.append("<p style=\"margin-bottom: 45px;\">_________________________________________</p>");
                sbFirmas.append("<p style=\"margin: 2px 0;\"><strong>COARRENDATARIO:</strong> ").append(escapeXml(cNombre)).append("</p>");
                sbFirmas.append("<p style=\"margin: 2px 0;\">C.C. / Doc: ").append(escapeXml(cDoc)).append("</p>");
                sbFirmas.append("<p style=\"margin: 2px 0;\">Tel: ").append(escapeXml(cTel)).append("</p>");
                sbFirmas.append("<p style=\"margin: 2px 0;\">Vínculo: ").append(escapeXml(c.tipoVinculo())).append("</p>");
                sbFirmas.append("</td>");

                idx++;
            }

            if (idx % 2 != 0) {
                sbFirmas.append("<td style=\"width: 50%; padding: 15px;\"></td>");
            }

            sbFirmas.append("</tr></table></div>");

            vars.put("coarrendatarios.lista", sbLista.toString());
            vars.put("coarrendatarios.nombres", sbNombres.toString());
            vars.put("coarrendatarios.firmas_html", sbFirmas.toString());
        }

        // 7. Parqueadero y Administrativo
        vars.put("nombreParqueadero", dto.getNombreParqueadero() != null ? dto.getNombreParqueadero() : "No asignado");
        vars.put("diasInspeccion", dto.getDiasInspeccion() != null ? dto.getDiasInspeccion() : 30);
        vars.put("diasAvisoPrevio", dto.getDiasAvisoPrevio() != null ? dto.getDiasAvisoPrevio() : 30);
        vars.put("penalizacionMeses", dto.getPenalizacionSalidaAnticipada() != null ? dto.getPenalizacionSalidaAnticipada() : BigDecimal.ONE);
        vars.put("textoRenovacion", dto.getTextoRenovacion() != null ? dto.getTextoRenovacion() : "Renovación automática");
        vars.put("telefonoAdministracion", dto.getTelefonoAdministracion() != null ? dto.getTelefonoAdministracion() : "");
        vars.put("correoAdministracion", dto.getCorreoAdministracion() != null ? dto.getCorreoAdministracion() : "");
        vars.put("numeroContratoAnterior", dto.getNumeroContratoAnterior() != null ? dto.getNumeroContratoAnterior() : "");
        vars.put("historialContratos", dto.getHistorialContratos() != null ? dto.getHistorialContratos() : "");

        LocalDate hoy = LocalDate.now();
        vars.put("fechaActual", hoy);
        vars.put("fechaGeneracion", dto.getFechaGeneracion() != null ? dto.getFechaGeneracion() : hoy);
        vars.put("diaFirma", String.valueOf(hoy.getDayOfMonth()));
        vars.put("mesFirma", hoy.format(DateTimeFormatter.ofPattern("MMMM", new Locale("es", "CO"))));
        vars.put("anioFirma", String.valueOf(hoy.getYear()));

        return vars;
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}
