package com.edificio.admin.service;

import com.edificio.admin.rest.dto.ContratoDetalleDTO;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class VariableResolverService {

    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", new Locale("es", "CO"));
    private static final NumberFormat CURRENCY_FORMAT =
        NumberFormat.getCurrencyInstance(new Locale("es", "CO"));

    public String resolverVariables(String html, Map<String, Object> variables) {
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String valor = formatearValor(entry.getValue());
            html = html.replace(placeholder, valor);
        }
        return html;
    }

    private String formatearValor(Object valor) {
        if (valor == null) return "";
        if (valor instanceof LocalDate) return ((LocalDate) valor).format(DATE_FORMAT);
        if (valor instanceof BigDecimal) return CURRENCY_FORMAT.format(valor);
        if (valor instanceof Integer) return String.valueOf(valor);
        return valor.toString();
    }

    public Map<String, Object> construirMapaVariables(ContratoDetalleDTO dto) {
        Map<String, Object> vars = new HashMap<>();

        vars.put("numeroContrato", dto.getNumeroContrato());
        vars.put("fechaGeneracion", dto.getFechaGeneracion());
        vars.put("fechaInicio", dto.getFechaInicio());
        vars.put("fechaFin", dto.getFechaFin() != null ? dto.getFechaFin() : "SIN FECHA DE VENCIMIENTO");
        vars.put("periodoVigencia", dto.getPeriodoVigencia());

        vars.put("numeroApartamento", dto.getNumeroApartamento());
        vars.put("tipoApartamento", dto.getTipoApartamento());
        vars.put("piso", dto.getPiso());
        vars.put("area", dto.getArea());
        vars.put("capacidadMaxima", dto.getCapacidadMaxima());

        vars.put("nombreCompletoResidente", dto.getNombreCompletoResidente());
        vars.put("nombresResidente", dto.getNombresResidente());
        vars.put("apellidosResidente", dto.getApellidosResidente());
        vars.put("tipoDocumento", dto.getTipoDocumentoResidente());
        vars.put("numeroDocumento", dto.getNumeroDocumentoResidente());
        vars.put("telefonoResidente", dto.getTelefonoResidente());
        vars.put("correoResidente", dto.getCorreoResidente());

        vars.put("valorCanon", dto.getValorCanon());
        vars.put("valorAdministracion", dto.getValorAdministracion());
        vars.put("valorTotal", dto.getValorTotal());
        vars.put("valorDeposito", dto.getValorDeposito());
        vars.put("diaPago", dto.getDiaPago());
        vars.put("diasGracia", dto.getDiasGracia());
        vars.put("porcentajeMora", dto.getPorcentajeMora());

        if (dto.getNombreTutor() != null) {
            vars.put("nombreTutor", dto.getNombreTutor());
            vars.put("cedulaTutor", dto.getCedulaTutor());
            vars.put("relacionTutor", dto.getRelacionTutor());
        } else {
            vars.put("nombreTutor", "");
            vars.put("cedulaTutor", "");
            vars.put("relacionTutor", "");
        }

        vars.put("nombreParqueadero", dto.getNombreParqueadero() != null
            ? dto.getNombreParqueadero() : "No asignado");

        vars.put("nombreEdificio", dto.getNombreEdificio());
        vars.put("direccionEdificio", dto.getDireccionEdificio());
        vars.put("ciudadEdificio", dto.getCiudadEdificio());
        vars.put("nitAdministrador", dto.getNitAdministrador());

        vars.put("diasInspeccion", dto.getDiasInspeccion());
        vars.put("diasAvisoPrevio", dto.getDiasAvisoPrevio());
        vars.put("penalizacionMeses", dto.getPenalizacionSalidaAnticipada());
        vars.put("textoRenovacion", dto.getTextoRenovacion());

        vars.put("telefonoAdministracion", dto.getTelefonoAdministracion() != null
            ? dto.getTelefonoAdministracion() : "");
        vars.put("correoAdministracion", dto.getCorreoAdministracion() != null
            ? dto.getCorreoAdministracion() : "");

        vars.put("numeroContratoAnterior", dto.getNumeroContratoAnterior() != null
            ? dto.getNumeroContratoAnterior() : "");
        vars.put("historialContratos", dto.getHistorialContratos() != null
            ? dto.getHistorialContratos() : "");

        LocalDate hoy = LocalDate.now();
        vars.put("diaFirma", String.valueOf(hoy.getDayOfMonth()));
        vars.put("mesFirma", hoy.format(DateTimeFormatter.ofPattern("MMMM", new Locale("es", "CO"))));
        vars.put("anioFirma", String.valueOf(hoy.getYear()));

        return vars;
    }
}
