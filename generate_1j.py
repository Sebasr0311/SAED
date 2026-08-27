import os

base_dir = "backend/src/main/java/com/saed/backend/convivencia"
packages = ["controller", "dto", "service", "service/impl", "repository", "repository/impl"]

for p in packages:
    os.makedirs(os.path.join(base_dir, p), exist_ok=True)

# DTOs
dtos = {
    "MultaDTO.java": """package com.saed.backend.convivencia.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MultaDTO {
    private Long idMulta;
    private String numeroApartamento;
    private String nombreResidente;
    private String tipo;
    private BigDecimal monto;
    private String estado;
    private LocalDateTime fechaCreacion;
}
""",
    "QuejaDTO.java": """package com.saed.backend.convivencia.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class QuejaDTO {
    private Long idQueja;
    private String radicado;
    private String tipo;
    private String categoria;
    private String prioridad;
    private String titulo;
    private String descripcion;
    private String estado;
    private String respuesta;
    private String autor;
    private String apartamento;
    private LocalDateTime fecha;
}
""",
    "QuejaRequestDTO.java": """package com.saed.backend.convivencia.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class QuejaRequestDTO {
    @NotBlank private String tipo;
    @NotBlank private String categoria;
    @NotBlank private String titulo;
    @NotBlank private String descripcion;
    private Long idMulta;
}
""",
    "QuejaResponseDTO.java": """package com.saed.backend.convivencia.dto;
import lombok.Data;
@Data
public class QuejaResponseDTO {
    private String respuesta;
    private String estado;
    private String prioridad;
}
""",
    "NotificacionDTO.java": """package com.saed.backend.convivencia.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NotificacionDTO {
    private Long idMensaje;
    private String titulo;
    private String cuerpo;
    private LocalDateTime fecha;
    private Boolean leido;
}
"""
}

for name, content in dtos.items():
    with open(os.path.join(base_dir, "dto", name), "w") as f:
        f.write(content)

# Repository Interfaces
repos = {
    "MultaRepository.java": """package com.saed.backend.convivencia.repository;
import com.saed.backend.convivencia.dto.MultaDTO;
import java.util.List;

public interface MultaRepository {
    List<MultaDTO> findAll();
    MultaDTO findById(Long id);
    void updateEstado(Long id, String estado);
}
""",
    "QuejaRepository.java": """package com.saed.backend.convivencia.repository;
import com.saed.backend.convivencia.dto.QuejaDTO;
import com.saed.backend.convivencia.dto.QuejaRequestDTO;
import java.util.List;

public interface QuejaRepository {
    List<QuejaDTO> findAll();
    List<QuejaDTO> findByUserId(Long idUsuario);
    void create(QuejaRequestDTO dto, Long idUsuario, Long idPropiedad);
    void updateRespuesta(Long id, String respuesta);
    void updateEstado(Long id, String estado);
    void updatePrioridad(Long id, String prioridad);
}
""",
    "NotificacionRepository.java": """package com.saed.backend.convivencia.repository;
import com.saed.backend.convivencia.dto.NotificacionDTO;
import java.util.List;

public interface NotificacionRepository {
    List<NotificacionDTO> findByUsuarioDestinatario(Long idUsuario);
    void marcarLeido(Long idNotificacion, Long idUsuario);
    void vaciarBuzon(Long idUsuario);
    void eliminarMensajes(List<Long> ids, Long idUsuario);
}
"""
}

for name, content in repos.items():
    with open(os.path.join(base_dir, "repository", name), "w") as f:
        f.write(content)

print("Phase 1J Base Created")
