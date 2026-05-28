package com.edificio.admin.service;

import com.edificio.admin.dao.CuotaArriendoDAO;
import com.edificio.admin.dao.PagoDAO;
import com.edificio.admin.exception.DatosInvalidosException;
import com.edificio.admin.exception.RegistroNoEncontradoException;
import com.edificio.admin.model.CuotaArriendo;
import com.edificio.admin.model.Pago;
import com.edificio.admin.model.enums.EstadoCuota;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * Logica de negocio para CUOTAS_ARRIENDO y PAGOS.
 */
public class PagoService {

    private final CuotaArriendoDAO cuotaDAO;
    private final PagoDAO          pagoDAO;

    public PagoService() {
        this.cuotaDAO = new CuotaArriendoDAO();
        this.pagoDAO  = new PagoDAO();
    }

    // ---- Cuotas ----

    public List<CuotaArriendo> listarCuotasPorContrato(Integer idContrato) throws SQLException {
        if (idContrato == null || idContrato <= 0)
            throw new DatosInvalidosException("ID de contrato invalido.");
        return cuotaDAO.findByContrato(idContrato);
    }

    public List<CuotaArriendo> listarCuotasPendientes() throws SQLException {
        return cuotaDAO.findPendientes();
    }

    public CuotaArriendo buscarCuotaPorId(Integer idCuota) throws SQLException {
        if (idCuota == null || idCuota <= 0)
            throw new DatosInvalidosException("ID de cuota invalido.");
        CuotaArriendo q = cuotaDAO.findById(idCuota);
        if (q == null) throw new RegistroNoEncontradoException("Cuota no encontrada: " + idCuota);
        return q;
    }

    public Integer generarCuota(CuotaArriendo cuota) throws SQLException {
        validarCuota(cuota);
        return cuotaDAO.insert(cuota);
    }

    public void actualizarCuota(CuotaArriendo cuota) throws SQLException {
        if (cuota.getIdCuota() == null || cuota.getIdCuota() <= 0)
            throw new DatosInvalidosException("ID de cuota invalido.");
        validarCuota(cuota);
        cuotaDAO.update(cuota);
    }

    // ---- Pagos ----

    public List<Pago> listarPagosPorCuota(Integer idCuota) throws SQLException {
        if (idCuota == null || idCuota <= 0)
            throw new DatosInvalidosException("ID de cuota invalido.");
        return pagoDAO.findByCuota(idCuota);
    }

    /**
     * Registra un pago y recalcula el estado de la cuota.
     * Si el total pagado cubre el valor_total de la cuota -> estado = PAGADA.
     */
    public Integer registrarPago(Pago pago) throws SQLException {
        validarPago(pago);

        Integer idPago = pagoDAO.insert(pago);

        // Recalcular estado de la cuota
        CuotaArriendo cuota = buscarCuotaPorId(pago.getIdCuota());
        BigDecimal totalPagado = pagoDAO.findByCuota(cuota.getIdCuota())
                .stream()
                .map(Pago::getValorPagado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalPagado.compareTo(cuota.getValorTotal()) >= 0) {
            cuota.setEstado(EstadoCuota.PAGADA);
            cuotaDAO.update(cuota);
        }

        return idPago;
    }

    // ---- validaciones ----

    private void validarCuota(CuotaArriendo q) {
        if (q.getIdContrato() == null || q.getIdContrato() <= 0)
            throw new DatosInvalidosException("El contrato es obligatorio.");
        if (q.getAnio() < 2000 || q.getAnio() > 2100)
            throw new DatosInvalidosException("El anio debe estar entre 2000 y 2100.");
        if (q.getMes() < 1 || q.getMes() > 12)
            throw new DatosInvalidosException("El mes debe estar entre 1 y 12.");
        if (q.getFechaLimite() == null)
            throw new DatosInvalidosException("La fecha limite es obligatoria.");
        if (q.getValorBase() == null || q.getValorBase().compareTo(BigDecimal.ZERO) <= 0)
            throw new DatosInvalidosException("El valor base debe ser mayor que 0.");
    }

    private void validarPago(Pago p) {
        if (p.getIdCuota() == null || p.getIdCuota() <= 0)
            throw new DatosInvalidosException("La cuota es obligatoria.");
        if (p.getFechaPago() == null)
            throw new DatosInvalidosException("La fecha de pago es obligatoria.");
        if (p.getValorPagado() == null || p.getValorPagado().compareTo(BigDecimal.ZERO) <= 0)
            throw new DatosInvalidosException("El valor pagado debe ser mayor que 0.");
        if (p.getMetodoPago() == null)
            throw new DatosInvalidosException("El metodo de pago es obligatorio.");
    }
}
