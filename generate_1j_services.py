import os

base_dir = "backend/src/main/java/com/saed/backend/convivencia"

services = {
    "MultaService.java": """package com.saed.backend.convivencia.service;
import com.saed.backend.convivencia.dto.MultaDTO;
import java.util.List;

public interface MultaService {
    List<MultaDTO> findAll();
    MultaDTO findById(Long id);
    void pagar(Long id, String metodo);
    void anular(Long id);
}
""",
    "QuejaService.java": """package com.saed.backend.convivencia.service;
import com.saed.backend.convivencia.dto.QuejaDTO;
import com.saed.backend.convivencia.dto.QuejaRequestDTO;
import java.util.List;

public interface QuejaService {
    List<QuejaDTO> findAll();
    List<QuejaDTO> findMyQuejas();
    void createQueja(QuejaRequestDTO dto);
    void responder(Long id, String respuesta);
    void actualizarEstado(Long id, String estado);
    void actualizarPrioridad(Long id, String prioridad);
}
""",
    "NotificacionService.java": """package com.saed.backend.convivencia.service;
import com.saed.backend.convivencia.dto.NotificacionDTO;
import java.util.List;

public interface NotificacionService {
    List<NotificacionDTO> getMyNotificaciones();
    void marcarLeido(Long id);
    void vaciarBuzon();
    void eliminarMensajes(List<Long> ids);
}
"""
}

for name, content in services.items():
    with open(os.path.join(base_dir, "service", name), "w", encoding="utf-8") as f:
        f.write(content)

services_impl = {
    "MultaServiceImpl.java": """package com.saed.backend.convivencia.service.impl;
import com.saed.backend.convivencia.dto.MultaDTO;
import com.saed.backend.convivencia.repository.MultaRepository;
import com.saed.backend.convivencia.service.MultaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MultaServiceImpl implements MultaService {
    private final MultaRepository repo;
    
    @Override
    public List<MultaDTO> findAll() {
        return repo.findAll();
    }
    
    @Override
    public MultaDTO findById(Long id) {
        return repo.findById(id);
    }
    
    @Override
    @Transactional
    public void pagar(Long id, String metodo) {
        // En una implementacion real se crearia un pago
        repo.updateEstado(id, "PAGADA");
    }
    
    @Override
    @Transactional
    public void anular(Long id) {
        repo.updateEstado(id, "ANULADA");
    }
}
""",
    "QuejaServiceImpl.java": """package com.saed.backend.convivencia.service.impl;
import com.saed.backend.convivencia.dto.QuejaDTO;
import com.saed.backend.convivencia.dto.QuejaRequestDTO;
import com.saed.backend.convivencia.repository.QuejaRepository;
import com.saed.backend.convivencia.service.QuejaService;
import com.saed.backend.context.SaedContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QuejaServiceImpl implements QuejaService {
    private final QuejaRepository repo;

    @Override
    public List<QuejaDTO> findAll() {
        return repo.findAll();
    }

    @Override
    public List<QuejaDTO> findMyQuejas() {
        Long idUsuario = SaedContextHolder.getContext().getUserId();
        return repo.findByUserId(idUsuario);
    }

    @Override
    @Transactional
    public void createQueja(QuejaRequestDTO dto) {
        Long idUsuario = SaedContextHolder.getContext().getUserId();
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        repo.create(dto, idUsuario, idPropiedad);
    }

    @Override
    @Transactional
    public void responder(Long id, String respuesta) {
        repo.updateRespuesta(id, respuesta);
    }

    @Override
    @Transactional
    public void actualizarEstado(Long id, String estado) {
        repo.updateEstado(id, estado);
    }

    @Override
    @Transactional
    public void actualizarPrioridad(Long id, String prioridad) {
        repo.updatePrioridad(id, prioridad);
    }
}
""",
    "NotificacionServiceImpl.java": """package com.saed.backend.convivencia.service.impl;
import com.saed.backend.convivencia.dto.NotificacionDTO;
import com.saed.backend.convivencia.repository.NotificacionRepository;
import com.saed.backend.convivencia.service.NotificacionService;
import com.saed.backend.context.SaedContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificacionServiceImpl implements NotificacionService {
    private final NotificacionRepository repo;

    @Override
    public List<NotificacionDTO> getMyNotificaciones() {
        Long idUsuario = SaedContextHolder.getContext().getUserId();
        return repo.findByUsuarioDestinatario(idUsuario);
    }

    @Override
    @Transactional
    public void marcarLeido(Long id) {
        Long idUsuario = SaedContextHolder.getContext().getUserId();
        repo.marcarLeido(id, idUsuario);
    }

    @Override
    @Transactional
    public void vaciarBuzon() {
        Long idUsuario = SaedContextHolder.getContext().getUserId();
        repo.vaciarBuzon(idUsuario);
    }

    @Override
    @Transactional
    public void eliminarMensajes(List<Long> ids) {
        Long idUsuario = SaedContextHolder.getContext().getUserId();
        repo.eliminarMensajes(ids, idUsuario);
    }
}
"""
}

for name, content in services_impl.items():
    with open(os.path.join(base_dir, "service/impl", name), "w", encoding="utf-8") as f:
        f.write(content)

print("Phase 1J Services Created")
