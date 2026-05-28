package com.edificio.admin.service;

import com.edificio.admin.dao.*;
import com.edificio.admin.exception.DatosInvalidosException;
import com.edificio.admin.exception.RegistroNoEncontradoException;
import com.edificio.admin.model.*;
import com.edificio.admin.model.enums.EstadoVisita;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Logica de negocio para el modulo de VISITAS.
 * Cubre el Flujo C (visita normal) y parte del Flujo D (visita frecuente).
 * La validacion atomica del QR al escanear se delega a SP_VALIDAR_QR
 * (llamado desde el portero via EscannerQRController).
 */
public class VisitaService {

    private final VisitaDAO          visitaDAO;
    private final PersonaVisitaDAO   personaVisitaDAO;
    private final VehiculoVisitaDAO  vehiculoVisitaDAO;
    private final QRAccesoDAO        qrAccesoDAO;
    private final ContratoResidenteDAO contratoResidenteDAO;

    public VisitaService() {
        this.visitaDAO             = new VisitaDAO();
        this.personaVisitaDAO      = new PersonaVisitaDAO();
        this.vehiculoVisitaDAO     = new VehiculoVisitaDAO();
        this.qrAccesoDAO           = new QRAccesoDAO();
        this.contratoResidenteDAO  = new ContratoResidenteDAO();
    }

    // ---- Consultas ----

    public List<Visita> listarTodas() throws SQLException {
        return visitaDAO.findAll();
    }

    public List<Visita> listarPorResidente(Integer idResidente) throws SQLException {
        if (idResidente == null || idResidente <= 0)
            throw new DatosInvalidosException("ID de residente invalido.");
        return visitaDAO.findByResidente(idResidente);
    }

    public List<Visita> listarActivas() throws SQLException {
        return visitaDAO.findByEstado(EstadoVisita.ACTIVA);
    }

    public List<Visita> listarPendientesYActivas() throws SQLException {
        return visitaDAO.findPendientesYActivas();
    }

    public Visita buscarPorId(Integer id) throws SQLException {
        validarId(id);
        Visita v = visitaDAO.findById(id);
        if (v == null) throw new RegistroNoEncontradoException("Visita no encontrada: " + id);
        return v;
    }

    // ---- Creacion de visita ----

    /**
     * Registra una visita nueva, agrega el visitante titular y genera el QR.
     * Flujo:
     *   1. Validar datos
     *   2. INSERT VISITAS -> id_visita
     *   3. INSERT REGISTRO_VISITA (es_titular=1) -> TRG_AUTO_FRECUENTE
     *   4. INSERT VEHICULOS_VISITA (opcional)
     *   5. INSERT QR_ACCESOS (codigo = UUID)
     *
     * @param visita           datos de la visita
     * @param idVisitante      visitante titular (ya registrado en VISITANTES)
     * @param vehiculo         vehiculo del grupo (null si vienen a pie)
     * @return codigo QR generado (hex 32 chars) para mostrar/imprimir
     */
    public String crearVisita(Visita visita, Integer idVisitante, VehiculoVisita vehiculo)
            throws SQLException {

        validarVisita(visita);
        if (idVisitante == null || idVisitante <= 0)
            throw new DatosInvalidosException("ID del visitante titular es obligatorio.");

        Connection conn = ConexionBD.getInstancia().getConexion();
        // Sincronizar sobre la conexion compartida para evitar interferencia entre hilos
        synchronized (conn) {
            conn.setAutoCommit(false);
            try {
                // 1. INSERT VISITAS
                Integer idVisita = visitaDAO.insert(visita);
                visita.setIdVisita(idVisita);

                // 2. INSERT REGISTRO_VISITA (titular)
                PersonaVisita pv = new PersonaVisita();
                pv.setIdVisita(idVisita);
                pv.setIdVisitante(idVisitante);
                pv.setEsTitular(true);
                personaVisitaDAO.insert(pv);

                // 3. INSERT VEHICULOS_VISITA (opcional)
                if (vehiculo != null) {
                    vehiculo.setIdVisita(idVisita);
                    Integer idVeh = vehiculoVisitaDAO.insert(vehiculo);
                    vehiculo.setIdVehiculoVisita(idVeh);
                }

                // 4. INSERT QR_ACCESOS
                String codigoQr = UUID.randomUUID().toString().replace("-", "");
                LocalDateTime expiracion = LocalDateTime.now()
                        .plusMinutes(visita.getTiempoValidezMin());

                QRAcceso qr = new QRAcceso();
                qr.setIdVisita(idVisita);
                qr.setCodigoQr(codigoQr);
                qr.setFechaExpiracion(expiracion);
                qrAccesoDAO.insert(qr);

                conn.commit();
                return codigoQr;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /** Cancela una visita pendiente. */
    public void cancelar(Integer idVisita) throws SQLException {
        validarId(idVisita);
        visitaDAO.delete(idVisita); // soft-delete -> CANCELADA
    }

    // ---- validaciones ----

    private void validarVisita(Visita v) {
        if (v.getIdContratoRes() == null || v.getIdContratoRes() <= 0)
            throw new DatosInvalidosException("El contrato-residente es obligatorio.");
        if (v.getIdResidente() == null || v.getIdResidente() <= 0)
            throw new DatosInvalidosException("El residente autorizante es obligatorio.");
        if (v.getTiempoValidezMin() < 5 || v.getTiempoValidezMin() > 60)
            throw new DatosInvalidosException("El tiempo de validez debe estar entre 5 y 60 minutos.");
        if (v.getCantidadPersonas() < 1 || v.getCantidadPersonas() > 99)
            throw new DatosInvalidosException("La cantidad de personas debe estar entre 1 y 99.");
    }

    private void validarId(Integer id) {
        if (id == null || id <= 0)
            throw new DatosInvalidosException("ID de visita invalido.");
    }
}
