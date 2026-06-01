package com.edificio.admin.service;

import com.edificio.admin.dao.CuotaArriendoDAO;
import com.edificio.admin.dao.MultaDAO;
import com.edificio.admin.dao.PagoDAO;
import com.edificio.admin.exception.DatosInvalidosException;
import com.edificio.admin.exception.RegistroNoEncontradoException;
import com.edificio.admin.model.CuotaArriendo;
import com.edificio.admin.model.Pago;
import com.edificio.admin.model.enums.EstadoCuota;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Logica de negocio para CUOTAS_ARRIENDO y PAGOS.
 */
public class PagoService {

    private final CuotaArriendoDAO cuotaDAO;
    private final PagoDAO          pagoDAO;
    private final MultaDAO         multaDAO;

    public PagoService() {
        this.cuotaDAO = new CuotaArriendoDAO();
        this.pagoDAO  = new PagoDAO();
        this.multaDAO = new MultaDAO();
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

    public List<Map<String, Object>> listarPagosRegistrados() throws SQLException {
        return pagoDAO.findAllRegistrados();
    }

    // ---- Resumen de ganancias ----

    public Map<String, Object> obtenerResumenGanancias() throws SQLException {
        BigDecimal totalCuotas = pagoDAO.sumAll();
        BigDecimal totalMultas = multaDAO.sumPaid();
        List<Map<String, Object>> porTipo = pagoDAO.sumByTipoCuota();
        List<Map<String, Object>> cuotasPorMes = pagoDAO.monthlyBreakdown();
        List<Map<String, Object>> multasPorMes = multaDAO.monthlyPaidMultas();

        List<Map<String, Object>> porMes = combinarMensual(cuotasPorMes, multasPorMes);

        Map<String, Object> resumen = new HashMap<>();
        resumen.put("totalIngresos", totalCuotas.add(totalMultas));
        resumen.put("totalCuotas", totalCuotas);
        resumen.put("totalMultas", totalMultas);
        resumen.put("porTipo", porTipo);
        resumen.put("porMes", porMes);
        return resumen;
    }

    private List<Map<String, Object>> combinarMensual(List<Map<String, Object>> cuotas,
                                                       List<Map<String, Object>> multas) {
        Map<String, Map<String, Object>> mapa = new HashMap<>();

        for (Map<String, Object> c : cuotas) {
            String key = c.get("anio") + "-" + c.get("mes");
            Map<String, Object> m = new HashMap<>();
            m.put("anio", c.get("anio"));
            m.put("mes", c.get("mes"));
            m.put("totalCuotas", c.get("total"));
            m.put("totalMultas", BigDecimal.ZERO);
            m.put("total", c.get("total"));
            mapa.put(key, m);
        }

        for (Map<String, Object> m : multas) {
            String key = m.get("anio") + "-" + m.get("mes");
            Map<String, Object> existing = mapa.get(key);
            if (existing != null) {
                existing.put("totalMultas", m.get("total"));
                BigDecimal totalCuotas = (BigDecimal) existing.get("totalCuotas");
                BigDecimal totalMultas = (BigDecimal) m.get("total");
                existing.put("total", totalCuotas.add(totalMultas));
            } else {
                Map<String, Object> n = new HashMap<>();
                n.put("anio", m.get("anio"));
                n.put("mes", m.get("mes"));
                n.put("totalCuotas", BigDecimal.ZERO);
                n.put("totalMultas", m.get("total"));
                n.put("total", m.get("total"));
                mapa.put(key, n);
            }
        }

        List<Map<String, Object>> resultado = new ArrayList<>(mapa.values());
        resultado.sort((a, b) -> {
            int cmp = ((Integer) b.get("anio")).compareTo((Integer) a.get("anio"));
            if (cmp != 0) return cmp;
            return ((Integer) b.get("mes")).compareTo((Integer) a.get("mes"));
        });
        return resultado;
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
