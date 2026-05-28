package com.edificio.admin.service;

import com.edificio.admin.dao.*;
import com.edificio.admin.exception.DatosInvalidosException;
import com.edificio.admin.exception.RegistroNoEncontradoException;
import com.edificio.admin.model.*;
import com.edificio.admin.model.enums.EstadoContrato;
import com.edificio.admin.model.enums.EstadoApartamento;
import com.edificio.admin.model.enums.EstadoCuota;
import com.edificio.admin.model.enums.TipoContrato;
import com.edificio.admin.rest.dto.ContratoDetalleDTO;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class ContratoService {

    private final ContratoDAO            contratoDAO;
    private final ContratoResidenteDAO   contratoResidenteDAO;
    private final ApartamentoDAO         apartamentoDAO;
    private final TutorDAO               tutorDAO;
    private final ParqueaderoDAO         parqueaderoDAO;
    private final ContratoSuggestionService suggestionService;

    public ContratoService() {
        this.contratoDAO          = new ContratoDAO();
        this.contratoResidenteDAO = new ContratoResidenteDAO();
        this.apartamentoDAO       = new ApartamentoDAO();
        this.tutorDAO             = new TutorDAO();
        this.parqueaderoDAO       = new ParqueaderoDAO();
        this.suggestionService    = new ContratoSuggestionService();
    }

    public List<Contrato> listarTodos() throws SQLException {
        contratoDAO.expiracionAutomatica();
        return contratoDAO.findAll();
    }

    public Contrato buscarPorId(Integer id) throws SQLException {
        validarId(id);
        Contrato c = contratoDAO.findById(id);
        if (c == null) throw new RegistroNoEncontradoException("Contrato no encontrado: " + id);
        return c;
    }

    public Contrato buscarActivoPorApartamento(Integer idApartamento) throws SQLException {
        if (idApartamento == null || idApartamento <= 0)
            throw new DatosInvalidosException("ID de apartamento invalido.");
        return contratoDAO.findActivoByApartamento(idApartamento);
    }

    public List<Contrato> buscarPorApartamento(Integer idApartamento) throws SQLException {
        if (idApartamento == null || idApartamento <= 0)
            throw new DatosInvalidosException("ID de apartamento invalido.");
        return contratoDAO.findByApartamento(idApartamento);
    }

    public Integer crearContrato(Contrato contrato, Integer idResidenteArrendatario)
            throws SQLException {
        validar(contrato);
        if (idResidenteArrendatario == null || idResidenteArrendatario <= 0)
            throw new DatosInvalidosException("ID del residente arrendatario es obligatorio.");

        Apartamento apto = apartamentoDAO.findById(contrato.getIdApartamento());
        if (apto == null)
            throw new DatosInvalidosException("El apartamento no existe.");
        if (apto.getEstado() == EstadoApartamento.OCUPADO)
            throw new DatosInvalidosException("El apartamento ya est\u00e1 ocupado. No se puede crear un nuevo contrato.");

        contrato.setEstado(EstadoContrato.PENDIENTE_FIRMA);
        Integer idContrato = contratoDAO.insert(contrato);

        ContratoResidente cr = new ContratoResidente(null, idContrato,
                idResidenteArrendatario, "ARRENDATARIO");
        contratoResidenteDAO.insert(cr);

        return idContrato;
    }

    public void activar(Integer idContrato) throws SQLException {
        Contrato c = buscarPorId(idContrato);
        Integer idApartamento = c.getIdApartamento();
        c.setEstado(EstadoContrato.ACTIVO);
        contratoDAO.update(c);

        Apartamento a = null;
        if (idApartamento != null) {
            a = apartamentoDAO.findById(idApartamento);
            if (a != null) {
                a.setEstado(EstadoApartamento.OCUPADO);
                apartamentoDAO.update(a);
            }
        }

        // Generar cuotas del primer mes (ARRIENDO + ADMINISTRACION)
        try {
            generarCuotasIniciales(c, a);
        } catch (Exception e) {
            // No interrumpir la activación si la cuota ya existe o falla
            System.err.println("[ContratoService] Aviso: no se generaron cuotas iniciales para contrato "
                    + idContrato + ": " + e.getMessage());
        }
    }

    /**
     * Genera las cuotas del primer mes para el contrato recién activado.
     * Crea una fila ARRIENDO y, si el apartamento tiene administración > 0,
     * una fila ADMINISTRACION con el valor del campo APARTAMENTOS.administracion.
     */
    private void generarCuotasIniciales(Contrato c, Apartamento apto) throws SQLException {
        LocalDate inicio = c.getFechaInicio();
        int diaPago = c.getDiaPago() > 0 ? c.getDiaPago() : 5;

        // Calcular año/mes de la primera cuota
        int mes  = inicio.getMonthValue();
        int anio = inicio.getYear();
        java.time.LocalDate fechaLimite = java.time.LocalDate.of(anio, mes,
                Math.min(diaPago, inicio.lengthOfMonth()));

        // Si inicio es posterior al día de pago, la primera cuota es el mes siguiente
        if (inicio.getDayOfMonth() > diaPago) {
            fechaLimite = fechaLimite.plusMonths(1);
            mes  = fechaLimite.getMonthValue();
            anio = fechaLimite.getYear();
        }

        CuotaArriendoDAO cuotaDAO = new CuotaArriendoDAO();

        // Cuota de ARRIENDO
        CuotaArriendo arriendo = new CuotaArriendo();
        arriendo.setIdContrato(c.getIdContrato());
        arriendo.setAnio(anio);
        arriendo.setMes(mes);
        arriendo.setTipoCuota("ARRIENDO");
        arriendo.setFechaLimite(fechaLimite);
        arriendo.setValorBase(c.getValorMensual());
        arriendo.setValorMora(java.math.BigDecimal.ZERO);
        arriendo.setValorTotal(c.getValorMensual());
        arriendo.setEstado(EstadoCuota.PENDIENTE);
        cuotaDAO.insert(arriendo);

        // Cuota de ADMINISTRACION (solo si el apartamento tiene valor de administración)
        if (apto != null && apto.getAdministracion() != null
                && apto.getAdministracion().compareTo(java.math.BigDecimal.ZERO) > 0) {
            CuotaArriendo admin = new CuotaArriendo();
            admin.setIdContrato(c.getIdContrato());
            admin.setAnio(anio);
            admin.setMes(mes);
            admin.setTipoCuota("ADMINISTRACION");
            admin.setFechaLimite(fechaLimite);
            admin.setValorBase(apto.getAdministracion());
            admin.setValorMora(java.math.BigDecimal.ZERO);
            admin.setValorTotal(apto.getAdministracion());
            admin.setEstado(EstadoCuota.PENDIENTE);
            cuotaDAO.insert(admin);
        }
    }

    /**
     * Crea un NUEVO contrato de renovacion a partir de uno vencido.
     * El contrato original queda en estado VENCIDO (no se modifica).
     * El apartamento debe estar DISPONIBLE (lo pone expiracionAutomatica).
     *
     * @param idContratoVencido  ID del contrato VENCIDO que se renueva
     * @param nuevaFechaInicio   Fecha de inicio del nuevo contrato
     * @param nuevaFechaFin      Fecha de fin del nuevo contrato (null = permanente)
     * @param nuevoValor         Nuevo valor mensual (null = copia del contrato anterior)
     * @param notas              Observaciones opcionales
     * @return ID del nuevo contrato creado
     */
    public Integer renovar(Integer idContratoVencido,
                           java.time.LocalDate nuevaFechaInicio,
                           java.time.LocalDate nuevaFechaFin,
                           java.math.BigDecimal nuevoValor,
                           String notas) throws SQLException {
        Contrato viejo = buscarPorId(idContratoVencido);
        if (viejo.getEstado() != EstadoContrato.VENCIDO)
            throw new DatosInvalidosException("Solo se pueden renovar contratos vencidos.");
        if (nuevaFechaInicio == null)
            throw new DatosInvalidosException("La nueva fecha de inicio es obligatoria.");
        if (nuevaFechaFin != null && !nuevaFechaFin.isAfter(nuevaFechaInicio))
            throw new DatosInvalidosException("La fecha de fin debe ser posterior a la fecha de inicio.");

        // idResidente del arrendatario original (ahora devuelto por JOIN)
        Integer idResidente = viejo.getIdResidente();
        if (idResidente == null) {
            // fallback: buscar via tabla de relacion
            List<com.edificio.admin.model.ContratoResidente> rels =
                    contratoResidenteDAO.findByContrato(idContratoVencido);
            if (rels == null || rels.isEmpty())
                throw new DatosInvalidosException("No se encontro residente arrendatario en el contrato original.");
            idResidente = rels.get(0).getIdResidente();
        }

        Contrato nuevo = new Contrato();
        nuevo.setIdApartamento(viejo.getIdApartamento());
        nuevo.setIdTutor(viejo.getIdTutor());
        nuevo.setIdRegistradoPor(viejo.getIdRegistradoPor());
        nuevo.setFechaInicio(nuevaFechaInicio);
        nuevo.setFechaFin(nuevaFechaFin);
        nuevo.setValorMensual(nuevoValor != null ? nuevoValor : viejo.getValorMensual());
        nuevo.setDiaPago(viejo.getDiaPago());
        nuevo.setDiasGracia(viejo.getDiasGracia());
        nuevo.setPorcentajeMora(viejo.getPorcentajeMora());
        nuevo.setTipoContrato(TipoContrato.RENOVACION);
        nuevo.setEstado(EstadoContrato.PENDIENTE_FIRMA);
        nuevo.setNotas(notas);

        return crearContrato(nuevo, idResidente);
    }

    public void cancelar(Integer idContrato) throws SQLException {
        Contrato c = buscarPorId(idContrato);
        if (c.getEstado() == EstadoContrato.CANCELADO)
            throw new DatosInvalidosException("El contrato ya esta cancelado.");

        Integer idApartamento = c.getIdApartamento();
        c.setEstado(EstadoContrato.CANCELADO);
        contratoDAO.update(c);

        if (idApartamento != null) {
            Apartamento a = apartamentoDAO.findById(idApartamento);
            if (a != null) {
                a.setEstado(EstadoApartamento.DISPONIBLE);
                apartamentoDAO.update(a);
            }
        }
    }

    public void actualizar(Contrato contrato) throws SQLException {
        validarId(contrato.getIdContrato());
        validar(contrato);
        contratoDAO.update(contrato);
    }

    public void agregarResidente(Integer idContrato, Integer idResidente,
                                  String rolEnContrato) throws SQLException {
        validarId(idContrato);
        if (idResidente == null || idResidente <= 0)
            throw new DatosInvalidosException("ID de residente invalido.");
        if (rolEnContrato == null || rolEnContrato.isBlank())
            throw new DatosInvalidosException("El rol en contrato es obligatorio.");
        ContratoResidente cr = new ContratoResidente(null, idContrato, idResidente, rolEnContrato);
        contratoResidenteDAO.insert(cr);
    }

    public TipoContrato sugerirTipo(Integer idApartamento) throws SQLException {
        return suggestionService.sugerirTipo(idApartamento);
    }

    public LocalDate calcularFechaFin(LocalDate inicio, TipoContrato tipo) {
        return suggestionService.calcularFechaFin(inicio, tipo);
    }

    public ContratoDetalleDTO obtenerDetalleParaPDF(Integer idContrato) throws SQLException {
        Contrato contrato = buscarPorId(idContrato);
        ContratoDetalleDTO dto = new ContratoDetalleDTO();

        dto.setIdContrato(contrato.getIdContrato());
        dto.setNumeroContrato(String.valueOf(contrato.getIdContrato()));
        dto.setFechaGeneracion(LocalDate.now());
        dto.setFechaInicio(contrato.getFechaInicio());
        dto.setFechaFin(contrato.getFechaFin());
        dto.setPeriodoVigencia(calcularPeriodoTexto(contrato));
        dto.setTipoContrato(contrato.getTipoContrato() != null ? contrato.getTipoContrato() : TipoContrato.INICIAL);
        dto.setEstado(contrato.getEstado().name());

        Apartamento apto = apartamentoDAO.findById(contrato.getIdApartamento());
        if (apto != null) {
            dto.setNumeroApartamento(apto.getNumero());
            dto.setTipoApartamento(EdificioConfigService.formatearTipoApartamento(apto.getTipo()));
            dto.setPiso(apto.getPiso());
            dto.setArea(apto.getAreaM2());
            dto.setCapacidadMaxima(apto.getCapacidadMaxima());
            dto.setValorAdministracion(apto.getAdministracion() != null ? apto.getAdministracion() : BigDecimal.ZERO);
        }

        Residente residente = obtenerArrendatario(idContrato);
        if (residente != null) {
            dto.setNombresResidente(residente.getNombres());
            dto.setApellidosResidente(residente.getApellidos());
            dto.setNombreCompletoResidente(residente.getNombres() + " " + residente.getApellidos());
            dto.setTipoDocumentoResidente(obtenerNombreTipoDoc(residente.getIdTipoDoc()));
            dto.setNumeroDocumentoResidente(residente.getNumeroDocumento());
            dto.setTelefonoResidente(residente.getTelefono());
            dto.setCorreoResidente(residente.getEmail());
        }

        if (contrato.getIdTutor() != null) {
            try {
                Tutor tutor = tutorDAO.findById(contrato.getIdTutor());
                if (tutor != null) {
                    dto.setNombreTutor(tutor.getNombreCompleto());
                    dto.setCedulaTutor(tutor.getNumeroDocumento());
                    dto.setRelacionTutor(tutor.getParentesco() != null ? tutor.getParentesco().name() : "Tutor");
                }
            } catch (Exception e) {
                // No hay tutor
            }
        }

        List<Parqueadero> parqueaderos = parqueaderoDAO.findByApartamento(contrato.getIdApartamento());
        if (parqueaderos != null && !parqueaderos.isEmpty()) {
            Parqueadero p = parqueaderos.get(0);
            dto.setNombreParqueadero(p.getCodigo());
        } else {
            dto.setNombreParqueadero("No asignado");
        }

        dto.setNombreEdificio(EdificioConfigService.getNombreEdificio());
        dto.setDireccionEdificio(EdificioConfigService.getDireccionEdificio());
        dto.setCiudadEdificio(EdificioConfigService.getCiudadEdificio());
        dto.setNitAdministrador(EdificioConfigService.getNitAdministrador());

        dto.setValorCanon(contrato.getValorMensual());
        BigDecimal total = contrato.getValorMensual().add(
            dto.getValorAdministracion() != null ? dto.getValorAdministracion() : BigDecimal.ZERO);
        dto.setValorTotal(total);
        dto.setValorDeposito(total);

        dto.setDiaPago(contrato.getDiaPago());
        dto.setDiasGracia(contrato.getDiasGracia());
        dto.setPorcentajeMora(contrato.getPorcentajeMora());

        dto.setTelefonoAdministracion(EdificioConfigService.getTelefonoAdministracion());
        dto.setCorreoAdministracion(EdificioConfigService.getCorreoAdministracion());

        TipoContrato tipo = contrato.getTipoContrato() != null ? contrato.getTipoContrato() : TipoContrato.INICIAL;
        ContratoSuggestionService.ContratoConfig config = suggestionService.getConfig(tipo);
        dto.setDiasInspeccion(config.diasInspeccion);
        dto.setDiasAvisoPrevio(config.diasAvisoPrevio);
        dto.setPenalizacionSalidaAnticipada(BigDecimal.valueOf(config.penalizacionMeses));
        dto.setTextoRenovacion(config.textoRenovacion);

        if (tipo == TipoContrato.RENOVACION) {
            try {
                List<Contrato> historial = contratoDAO.findByApartamento(contrato.getIdApartamento());
                if (historial.size() > 1) {
                    Contrato anterior = historial.get(1);
                    dto.setNumeroContratoAnterior(String.valueOf(anterior.getIdContrato()));
                }
            } catch (Exception e) {
                dto.setNumeroContratoAnterior("");
            }
        }
        if (tipo == TipoContrato.PERMANENCIA) {
            try {
                List<Contrato> historial = contratoDAO.findByApartamento(contrato.getIdApartamento());
                int previos = historial.size() - 1;
                dto.setHistorialContratos(previos + " contrato(s) previo(s)");
            } catch (Exception e) {
                dto.setHistorialContratos("");
            }
        }

        return dto;
    }

    private String calcularPeriodoTexto(Contrato c) {
        if (c.getTipoContrato() == TipoContrato.PERMANENCIA) {
            return "PERMANENTE (sin fecha de vencimiento)";
        }
        if (c.getFechaFin() == null) return "INDEFINIDO";
        long meses = ChronoUnit.MONTHS.between(c.getFechaInicio(), c.getFechaFin());
        if (meses == 3) return "TRES (3) MESES";
        if (meses == 6) return "SEIS (6) MESES";
        return meses + " MESES";
    }

    private Residente obtenerArrendatario(Integer idContrato) throws SQLException {
        List<ContratoResidente> rels = contratoResidenteDAO.findByContrato(idContrato);
        if (rels != null && !rels.isEmpty()) {
            Integer idResidente = rels.get(0).getIdResidente();
            ResidenteDAO residenteDAO = new ResidenteDAO();
            return residenteDAO.findById(idResidente);
        }
        return null;
    }

    private String obtenerNombreTipoDoc(Integer idTipoDoc) {
        if (idTipoDoc == null) return "N/A";
        return switch (idTipoDoc) {
            case 1 -> "C.C.";
            case 2 -> "C.E.";
            case 3 -> "NIT";
            case 4 -> "Pasaporte";
            case 5 -> "T.I.";
            default -> "Doc. #" + idTipoDoc;
        };
    }

    private void validar(Contrato c) {
        if (c.getIdApartamento() == null || c.getIdApartamento() <= 0)
            throw new DatosInvalidosException("El apartamento es obligatorio.");
        if (c.getFechaInicio() == null)
            throw new DatosInvalidosException("La fecha de inicio es obligatoria.");
        if (c.getFechaInicio().isBefore(java.time.LocalDate.now()))
            throw new DatosInvalidosException("La fecha de inicio no puede ser anterior a hoy.");
        if (c.getValorMensual() == null || c.getValorMensual().compareTo(java.math.BigDecimal.ZERO) <= 0)
            throw new DatosInvalidosException("El valor mensual debe ser mayor que 0.");
        if (c.getFechaFin() != null && !c.getFechaFin().isAfter(c.getFechaInicio()))
            throw new DatosInvalidosException("La fecha de fin debe ser posterior a la fecha de inicio.");
    }

    private void validarId(Integer id) {
        if (id == null || id <= 0)
            throw new DatosInvalidosException("ID de contrato invalido.");
    }
}
