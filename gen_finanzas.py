import os

files = {
    'backend/src/main/java/com/saed/backend/finanzas/dto/ContratoDTO.java': '''package com.saed.backend.finanzas.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
public record ContratoDTO(Long id, Long idUnidad, String numeroApartamento, Long idArrendatario, String nombreArrendatario, String numeroContrato, BigDecimal canonMensual, Integer diaCortePago, LocalDate fechaInicio, LocalDate fechaFin, LocalDate fechaTerminacion, String estado, String tipoContrato) {}
''',
    'backend/src/main/java/com/saed/backend/finanzas/dto/ContratoRequestDTO.java': '''package com.saed.backend.finanzas.dto;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
public record ContratoRequestDTO(@NotNull Long idApartamento, @NotNull Long idResidente, @NotNull LocalDate fechaInicio, LocalDate fechaFin, @NotNull String tipoContrato, @NotNull BigDecimal canonMensual) {}
''',
    'backend/src/main/java/com/saed/backend/finanzas/dto/CuotaDTO.java': '''package com.saed.backend.finanzas.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
public record CuotaDTO(Long id, Long idUnidad, String numeroApartamento, String nombreResidente, Long idContrato, String concepto, String periodo, BigDecimal valorBase, BigDecimal valorTotal, BigDecimal saldoPendiente, LocalDate fechaLimite, String estado) {}
''',
    'backend/src/main/java/com/saed/backend/finanzas/dto/PagoRequestDTO.java': '''package com.saed.backend.finanzas.dto;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
public record PagoRequestDTO(@NotNull Long idCuota, LocalDate fechaPago, @NotNull BigDecimal valorPagado, @NotNull String metodoPago, String referencia) {}
''',
    'backend/src/main/java/com/saed/backend/finanzas/dto/ResidenteDashboardDTO.java': '''package com.saed.backend.finanzas.dto;
import java.util.List;
public record ResidenteDashboardDTO(Long idResidente, List<CuotaDTO> cuotas) {}
''',
    'backend/src/main/java/com/saed/backend/finanzas/repository/FinanzasRepository.java': '''package com.saed.backend.finanzas.repository;
import com.saed.backend.finanzas.dto.*;
import java.util.List;
import java.math.BigDecimal;
public interface FinanzasRepository {
    List<ContratoDTO> getContratos();
    Long createContrato(ContratoRequestDTO req, String numContrato);
    void updateEstadoContrato(Long id, String estado);
    List<CuotaDTO> getCuotasPendientes();
    List<CuotaDTO> getCuotasByResidente(Long idResidente);
    Long registrarPago(PagoRequestDTO req, Long idUnidad);
    void actualizarSaldoCuota(Long idCuota, BigDecimal montoAplicado);
}
''',
    'backend/src/main/java/com/saed/backend/finanzas/repository/impl/FinanzasRepositoryImpl.java': '''package com.saed.backend.finanzas.repository.impl;
import com.saed.backend.finanzas.dto.*;
import com.saed.backend.finanzas.repository.FinanzasRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.math.BigDecimal;

@Repository
public class FinanzasRepositoryImpl implements FinanzasRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    public FinanzasRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public List<ContratoDTO> getContratos() {
        String sql = "SELECT c.ID_CONTRATO, c.ID_UNIDAD, u.NUMERO as numeroApartamento, c.ID_ARRENDATARIO_PRINCIPAL, " +
                     "p.NOMBRES || ' ' || p.APELLIDOS as nombreArrendatario, c.NUMERO_CONTRATO, c.CANON_MENSUAL, c.DIA_CORTE_PAGO, " +
                     "c.FECHA_INICIO, c.FECHA_FIN, c.FECHA_TERMINACION_ANTICIPADA, c.ESTADO, c.TIPO_CONTRATO " +
                     "FROM CONTRATOS c " +
                     "JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD " +
                     "JOIN PERSONAS p ON c.ID_ARRENDATARIO_PRINCIPAL = p.ID_PERSONA " +
                     "ORDER BY c.ID_CONTRATO DESC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ContratoDTO(
            rs.getLong("ID_CONTRATO"), rs.getLong("ID_UNIDAD"), rs.getString("numeroApartamento"),
            rs.getLong("ID_ARRENDATARIO_PRINCIPAL"), rs.getString("nombreArrendatario"), rs.getString("NUMERO_CONTRATO"),
            rs.getBigDecimal("CANON_MENSUAL"), rs.getInt("DIA_CORTE_PAGO"),
            rs.getDate("FECHA_INICIO") != null ? rs.getDate("FECHA_INICIO").toLocalDate() : null,
            rs.getDate("FECHA_FIN") != null ? rs.getDate("FECHA_FIN").toLocalDate() : null,
            rs.getDate("FECHA_TERMINACION_ANTICIPADA") != null ? rs.getDate("FECHA_TERMINACION_ANTICIPADA").toLocalDate() : null,
            rs.getString("ESTADO"), rs.getString("TIPO_CONTRATO")
        ));
    }

    @Override
    public Long createContrato(ContratoRequestDTO req, String numContrato) {
        String sql = "INSERT INTO CONTRATOS (ID_UNIDAD, ID_ARRENDATARIO_PRINCIPAL, NUMERO_CONTRATO, CANON_MENSUAL, FECHA_INICIO, FECHA_FIN, TIPO_CONTRATO, ESTADO) " +
                     "VALUES (:idUnidad, :idArrendatario, :numContrato, :canon, :fInicio, :fFin, :tipo, 'ACTIVO')";
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("idUnidad", req.idApartamento())
            .addValue("idArrendatario", req.idResidente())
            .addValue("numContrato", numContrato)
            .addValue("canon", req.canonMensual())
            .addValue("fInicio", req.fechaInicio())
            .addValue("fFin", req.fechaFin())
            .addValue("tipo", req.tipoContrato());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_CONTRATO"});
        return keyHolder.getKey().longValue();
    }

    @Override
    public void updateEstadoContrato(Long id, String estado) {
        jdbcTemplate.update("UPDATE CONTRATOS SET ESTADO = :est WHERE ID_CONTRATO = :id",
            new MapSqlParameterSource().addValue("est", estado).addValue("id", id));
    }

    @Override
    public List<CuotaDTO> getCuotasPendientes() {
        String sql = "SELECT c.ID_CUOTA, c.ID_UNIDAD, u.NUMERO as numeroApartamento, p.NOMBRES || ' ' || p.APELLIDOS as nombreResidente, c.ID_CONTRATO, " +
                     "co.NOMBRE as concepto, c.PERIODO, c.VALOR_BASE, c.VALOR_TOTAL, c.SALDO_PENDIENTE, c.FECHA_LIMITE, c.ESTADO " +
                     "FROM CUOTAS c " +
                     "JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD " +
                     "JOIN CONCEPTOS_COBRO co ON c.ID_CONCEPTO = co.ID_CONCEPTO " +
                     "LEFT JOIN CONTRATOS con ON c.ID_CONTRATO = con.ID_CONTRATO " +
                     "LEFT JOIN PERSONAS p ON con.ID_ARRENDATARIO_PRINCIPAL = p.ID_PERSONA " +
                     "WHERE c.ESTADO = 'PENDIENTE'";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new CuotaDTO(
            rs.getLong("ID_CUOTA"), rs.getLong("ID_UNIDAD"), rs.getString("numeroApartamento"), rs.getString("nombreResidente"),
            rs.getLong("ID_CONTRATO"), rs.getString("concepto"), rs.getString("PERIODO"),
            rs.getBigDecimal("VALOR_BASE"), rs.getBigDecimal("VALOR_TOTAL"), rs.getBigDecimal("SALDO_PENDIENTE"),
            rs.getDate("FECHA_LIMITE") != null ? rs.getDate("FECHA_LIMITE").toLocalDate() : null, rs.getString("ESTADO")
        ));
    }

    @Override
    public List<CuotaDTO> getCuotasByResidente(Long idResidente) {
        String sql = "SELECT c.ID_CUOTA, c.ID_UNIDAD, u.NUMERO as numeroApartamento, p.NOMBRES || ' ' || p.APELLIDOS as nombreResidente, c.ID_CONTRATO, " +
                     "co.NOMBRE as concepto, c.PERIODO, c.VALOR_BASE, c.VALOR_TOTAL, c.SALDO_PENDIENTE, c.FECHA_LIMITE, c.ESTADO " +
                     "FROM CUOTAS c " +
                     "JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD " +
                     "JOIN CONCEPTOS_COBRO co ON c.ID_CONCEPTO = co.ID_CONCEPTO " +
                     "JOIN CONTRATOS con ON c.ID_CONTRATO = con.ID_CONTRATO " +
                     "JOIN PERSONAS p ON con.ID_ARRENDATARIO_PRINCIPAL = p.ID_PERSONA " +
                     "WHERE con.ID_ARRENDATARIO_PRINCIPAL = :idRes " +
                     "ORDER BY c.FECHA_LIMITE DESC";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("idRes", idResidente), (rs, rowNum) -> new CuotaDTO(
            rs.getLong("ID_CUOTA"), rs.getLong("ID_UNIDAD"), rs.getString("numeroApartamento"), rs.getString("nombreResidente"),
            rs.getLong("ID_CONTRATO"), rs.getString("concepto"), rs.getString("PERIODO"),
            rs.getBigDecimal("VALOR_BASE"), rs.getBigDecimal("VALOR_TOTAL"), rs.getBigDecimal("SALDO_PENDIENTE"),
            rs.getDate("FECHA_LIMITE") != null ? rs.getDate("FECHA_LIMITE").toLocalDate() : null, rs.getString("ESTADO")
        ));
    }

    @Override
    public Long registrarPago(PagoRequestDTO req, Long idUnidad) {
        String sql = "INSERT INTO PAGOS (ID_UNIDAD, MONTO_TOTAL, METODO_PAGO, REFERENCIA_COMPROBANTE, ESTADO) " +
                     "VALUES (:unidad, :monto, :metodo, :ref, 'APROBADO')";
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("unidad", idUnidad)
            .addValue("monto", req.valorPagado())
            .addValue("metodo", req.metodoPago())
            .addValue("ref", req.referencia());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_PAGO"});
        Long idPago = keyHolder.getKey().longValue();

        String sqlDetalle = "INSERT INTO PAGO_DETALLE (ID_PAGO, ID_CUOTA, MONTO_APLICADO) VALUES (:pago, :cuota, :monto)";
        jdbcTemplate.update(sqlDetalle, new MapSqlParameterSource()
            .addValue("pago", idPago).addValue("cuota", req.idCuota()).addValue("monto", req.valorPagado()));
            
        return idPago;
    }

    @Override
    public void actualizarSaldoCuota(Long idCuota, BigDecimal montoAplicado) {
        String sql = "UPDATE CUOTAS SET SALDO_PENDIENTE = GREATEST(SALDO_PENDIENTE - :monto, 0), " +
                     "ESTADO = CASE WHEN SALDO_PENDIENTE - :monto <= 0 THEN 'PAGADA' ELSE ESTADO END " +
                     "WHERE ID_CUOTA = :idCuota";
        jdbcTemplate.update(sql, new MapSqlParameterSource().addValue("monto", montoAplicado).addValue("idCuota", idCuota));
    }
}
''',
    'backend/src/main/java/com/saed/backend/finanzas/service/FinanzasService.java': '''package com.saed.backend.finanzas.service;
import com.saed.backend.finanzas.dto.*;
import java.util.List;
public interface FinanzasService {
    List<ContratoDTO> getContratos();
    Long createContrato(ContratoRequestDTO request);
    void actualizarEstadoContrato(Long id, String estado);
    List<CuotaDTO> getCuotasPendientes();
    void registrarPago(PagoRequestDTO request);
    ResidenteDashboardDTO getDashboardResidente(Long idResidente);
}
''',
    'backend/src/main/java/com/saed/backend/finanzas/service/impl/FinanzasServiceImpl.java': '''package com.saed.backend.finanzas.service.impl;
import com.saed.backend.finanzas.dto.*;
import com.saed.backend.finanzas.repository.FinanzasRepository;
import com.saed.backend.finanzas.service.FinanzasService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class FinanzasServiceImpl implements FinanzasService {
    private final FinanzasRepository finanzasRepository;
    public FinanzasServiceImpl(FinanzasRepository finanzasRepository) { this.finanzasRepository = finanzasRepository; }

    @Override
    public List<ContratoDTO> getContratos() {
        return finanzasRepository.getContratos();
    }

    @Override
    @Transactional
    public Long createContrato(ContratoRequestDTO request) {
        String numContrato = "C-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return finanzasRepository.createContrato(request, numContrato);
    }

    @Override
    @Transactional
    public void actualizarEstadoContrato(Long id, String estado) {
        finanzasRepository.updateEstadoContrato(id, estado);
    }

    @Override
    public List<CuotaDTO> getCuotasPendientes() {
        return finanzasRepository.getCuotasPendientes();
    }

    @Override
    @Transactional
    public void registrarPago(PagoRequestDTO request) {
        List<CuotaDTO> pendientes = finanzasRepository.getCuotasPendientes();
        CuotaDTO cuota = pendientes.stream().filter(c -> c.id().equals(request.idCuota())).findFirst()
                .orElseThrow(() -> new RuntimeException("Cuota no encontrada o ya pagada"));
                
        finanzasRepository.registrarPago(request, cuota.idUnidad());
        finanzasRepository.actualizarSaldoCuota(request.idCuota(), request.valorPagado());
    }

    @Override
    public ResidenteDashboardDTO getDashboardResidente(Long idResidente) {
        List<CuotaDTO> cuotas = finanzasRepository.getCuotasByResidente(idResidente);
        return new ResidenteDashboardDTO(idResidente, cuotas);
    }
}
''',
    'backend/src/main/java/com/saed/backend/finanzas/controller/ContratosController.java': '''package com.saed.backend.finanzas.controller;
import com.saed.backend.finanzas.dto.ContratoDTO;
import com.saed.backend.finanzas.dto.ContratoRequestDTO;
import com.saed.backend.finanzas.service.FinanzasService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/contratos")
public class ContratosController {
    private final FinanzasService finanzasService;
    public ContratosController(FinanzasService finanzasService) { this.finanzasService = finanzasService; }

    @GetMapping
    public ResponseEntity<List<ContratoDTO>> getContratos() {
        return ResponseEntity.ok(finanzasService.getContratos());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createContrato(@Valid @RequestBody ContratoRequestDTO request) {
        Long id = finanzasService.createContrato(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "id", id));
    }

    @PostMapping("/{id}/activar")
    public ResponseEntity<Map<String, Object>> activarContrato(@PathVariable Long id) {
        finanzasService.actualizarEstadoContrato(id, "ACTIVO");
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<Map<String, Object>> cancelarContrato(@PathVariable Long id) {
        finanzasService.actualizarEstadoContrato(id, "CANCELADO");
        return ResponseEntity.ok(Map.of("success", true));
    }
}
''',
    'backend/src/main/java/com/saed/backend/finanzas/controller/PagosController.java': '''package com.saed.backend.finanzas.controller;
import com.saed.backend.finanzas.dto.CuotaDTO;
import com.saed.backend.finanzas.dto.PagoRequestDTO;
import com.saed.backend.finanzas.service.FinanzasService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class PagosController {
    private final FinanzasService finanzasService;
    public PagosController(FinanzasService finanzasService) { this.finanzasService = finanzasService; }

    @GetMapping("/cuotas")
    public ResponseEntity<List<CuotaDTO>> getCuotasPendientes(@RequestParam(required = false) Boolean pendientes) {
        return ResponseEntity.ok(finanzasService.getCuotasPendientes());
    }

    @PostMapping("/pagos")
    public ResponseEntity<Map<String, Object>> registrarPago(@Valid @RequestBody PagoRequestDTO request) {
        finanzasService.registrarPago(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true));
    }
    
    @GetMapping("/multas/todas")
    public ResponseEntity<List<Object>> getMultas() {
        return ResponseEntity.ok(List.of()); 
    }
}
''',
    'backend/src/main/java/com/saed/backend/finanzas/controller/ResidentesFinanzasController.java': '''package com.saed.backend.finanzas.controller;
import com.saed.backend.finanzas.dto.ResidenteDashboardDTO;
import com.saed.backend.finanzas.service.FinanzasService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/residentes")
public class ResidentesFinanzasController {
    private final FinanzasService finanzasService;
    public ResidentesFinanzasController(FinanzasService finanzasService) { this.finanzasService = finanzasService; }

    @GetMapping("/{id}/dashboard")
    public ResponseEntity<ResidenteDashboardDTO> getDashboard(@PathVariable Long id) {
        return ResponseEntity.ok(finanzasService.getDashboardResidente(id));
    }
}
'''
}

for path, content in files.items():
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)

print("Backend files generated.")
