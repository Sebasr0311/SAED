# Certificación Técnica E2E — Control de Acceso y Visitas Multi-Rol (SAED 2.0)

**Entorno:** Producción (`https://www.saed.app`)  
**Fecha de Ejecución:** 13 de Septiembre de 2026  
**Roles Evaluados:** Residente Titular (`camartinez`), Residente Conviviente (`jjuan123`), Portero (`portero01`)  
**Estado General:** 🟢 **100% OPERATIVO & CERTIFICADO**

---

## 1. Resumen Ejecutivo

Se ejecutó una verificación integral end-to-end de los flujos de creación, validación y control de acceso vehicular, peatonal y alternativo en el portal web de SAED, cubriendo los requerimientos de diferenciación de perfiles (Titular, Conviviente y Portero en garita), validaciones en caliente ("moods" peatonal, vehicular, bicicleta y pase rápido) y procesamiento bidireccional en tiempo real.

```mermaid
flowchart TD
    subgraph Emisores["Emisores de Visitas"]
        T["Residente Titular\n(camartinez)"] -->|Peatonal / Pase Rápido| V["Validación en Caliente\n(Campos, Regex)"]
        C["Residente Conviviente\n(jjuan123)"] -->|Vehicular (Carro / Moto)| V
        P["Portería / Garita\n(portero01)"] -->|Registro Directo Walk-in| V
    end

    V --> QR["Generación Token QR Dinámico\n(Oracle ATP)"]

    subgraph Garita["Control Operativo en Garita"]
        QR --> VAL["Lector / Escáner QR (/escanner-qr)"]
        VAL -->|Valida Identidad & Vigencia| AUT["Credencial AUTORIZADO"]
        AUT --> IN["Check-In (Ingreso)\nEstado: EN_CURSO"]
        IN --> OUT["Check-Out (Salida)\nEstado: FINALIZADA"]
        OUT --> AUD["Auditoría Histórica de Accesos"]
    end
```

---

## 2. Matriz de Pruebas y Validación por Modo ("Moods")

| Caso de Prueba | Rol Emisor | Modo de Acceso | Validaciones Probadas | Token QR / Resultado | Portería (Check-In / Out) | Evidencia Visual |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **CP-VIS-01** | Residente Titular (`camartinez`) | **Peatonal (`A_PIE`)** | • Tipo doc obligatorio.<br>• Teléfono válido.<br>• Vigencia: 30 min.<br>• Guardar frecuente. | Token generado:<br>`#15f65cea-bcd4-4504-8b1f-3057433f66d3` (Santiago Morales Gómez) | 🟢 **AUTORIZADO**<br>Destino: Apto 101.<br>Check-in: Pase #81 `EN_CURSO`.<br>Salida marcada con éxito. | [QR Generado](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/residente_titular_visita_qr_generado.png)<br>[Portero Validación](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/portero_validacion_qr_residente_titular.png)<br>[Portero Visitas Dentro](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/portero_visitas_dentro_santiago_morales.png) |
| **CP-VIS-02** | Residente Conviviente (`jjuan123`) | **Vehicular (`CARRO`)** | • Regex placa carro: rechazo inmediato de formato inválido `12345`.<br>• Aceptación de placa legal `XYZ-789`.<br>• Cupo: 2 personas.<br>• Vigencia: 45 min. | Token generado:<br>`#3b326f4a-1c06-4324-82d3-a5fa3c7b2a7e` (Valeria Gómez Restrepo) | 🟢 **AUTORIZADO**<br>Anfitrión reconocido: *Juan sebastian rincon farelo*.<br>Check-in: Pase #82 `EN_CURSO`.<br>Salida completada. | [QR Conviviente](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/residente_conviviente_visita_vehicular_qr.png)<br>[Portero Validación](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/portero_validacion_qr_conviviente_vehicular.png)<br>[Portero Visitas Dentro](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/portero_visita_conviviente_dentro.png) |
| **CP-VIS-03** | Residente Titular (`camartinez`) | **Pase Rápido (1-Clic)** | • Detección automática en lista de frecuentes.<br>• Precarga de placa `XYZ-789` y documento.<br>• Emisión instantánea sin reingresar datos. | Token generado:<br>`#400eac40-3658-48a2-9f24-6f370e972edf` (Valeria Gómez Restrepo) | 🟢 **EMISIÓN EXITOSA**<br>Listado en pestaña *Pases QR Activos* con vigencia dinámica. | [Pase Rápido QR](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/residente_pase_rapido_qr_generado.png)<br>[Pases QR Activos](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/residente_pases_activos_pase_rapido.png) |
| **CP-VIS-04** | Portero en Garita (`portero01`) | **Directo Walk-in (`BICICLETA`)** | • Formulario vacío: validación de Apto, Tipo Doc, Doc, Nombres, Apellidos.<br>• Formato vehicular: rechazo de placa errónea `12345`.<br>• Modo bicicleta: descripción de móvil obligatoria.<br>• Registro asignando anfitrión residente o portería. | Token generado:<br>`#c1cbdf8d-ef70-4f5c-8849-a5438465d98f` (Andres Felipe Herrera Castro) | 🟢 **AUTORIZADO**<br>Check-in: Pase #84 `EN_CURSO` en garita.<br>Check-out: Salida marcada.<br>Auditoría: Registrado como `FINALIZADA` en Historial. | [Validaciones Vacío](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/portero_nueva_visita_validaciones_vacio.png)<br>[Validación Bicicleta](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/portero_nueva_visita_validacion_bicicleta.png)<br>[QR Garita](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/portero_nueva_visita_qr_generado.png)<br>[Validación Token](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/portero_validacion_qr_visita_creada_por_portero.png)<br>[Visitas Dentro](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/portero_visitas_dentro_pase84.png)<br>[Historial Finalizada](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/portero_historial_accesos_finalizada_pase84.png) |

---

## 3. Comportamiento y Aislamiento por Roles

1. **Residente Titular (`camartinez` - Rol `RESIDENTE`):**
   - Acceso total a la administración de visitas de la unidad residencial (Apto 101).
   - Visualización y gestión de pestañas: *Visitantes Frecuentes*, *Pases QR Activos* e *Historial Completo*.
   - Capacidad de revocar pases activos y eliminar visitantes frecuentes.

2. **Residente Conviviente (`jjuan123` - Rol `RESIDENTE_CONVIVENCIA`):**
   - Permiso activo para emitir pases de visita (peatonales y vehiculares) vinculados a la unidad 101.
   - **Aislamiento defensivo verificado:** La pestaña *Historial* se oculta dinámicamente en el frontend para este rol, protegiendo la auditoría histórica global del titular sin degradar la operatividad del conviviente.
   - Al ser escaneado en portería, el sistema informa con exactitud el nombre del conviviente emisor, garantizando trazabilidad de autorizaciones.

3. **Portería (`portero01` - Rol `PORTERO`):**
   - Creación directa de visitas no programadas (walk-in) con selección de unidad destino y residente autorizante.
   - Validación unificada mediante endpoint seguro de escaneo (`/escanner-qr`).
   - Detección inmediata de inconsistencias, estados expirados o unidades no autorizadas.
   - En visitas vehiculares y medios alternativos (bicicleta), exige validación y descripción obligatoria.
   - Transición atómica de estados: `PROGRAMADA` ➔ `EN_CURSO` (ingreso) ➔ `FINALIZADA` (salida).
   - Auditoría en tiempo real en la pestaña *Historial de Accesos Recientes en Garita*.

---

## 4. Galería Completa de Evidencia Fotográfica

### Flujo Titular
- [Pase Peatonal Titular - QR Generado](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/residente_titular_visita_qr_generado.png)
- [Pases QR Activos Titular](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/residente_titular_pases_activos.png)
- [Portero - Validación QR Titular](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/portero_validacion_qr_residente_titular.png)
- [Portero - Visitas Dentro Titular](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/portero_visitas_dentro_santiago_morales.png)

### Flujo Conviviente
- [Pase Vehicular Conviviente - QR Generado](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/residente_conviviente_visita_vehicular_qr.png)
- [Portero - Validación QR Conviviente](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/portero_validacion_qr_conviviente_vehicular.png)
- [Portero - Visitas Dentro Conviviente](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/portero_visita_conviviente_dentro.png)

### Flujo Pase Rápido
- [Pase Rápido 1-Clic - QR Generado](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/residente_pase_rapido_qr_generado.png)
- [Pases QR Activos con Pase Rápido](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/residente_pases_activos_pase_rapido.png)

### Flujo Portero en Garita
- [Portero - Tabla General de Visitas](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/portero_visitas_tabla_general.png)
- [Portero - Validaciones en Vacío](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/portero_nueva_visita_validaciones_vacio.png)
- [Portero - Validación Descripción Bicicleta](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/portero_nueva_visita_validacion_bicicleta.png)
- [Portero - Formulario Completo Diligenciado](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/portero_nueva_visita_formulario_lleno.png)
- [Portero - QR Generado en Garita](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/portero_nueva_visita_qr_generado.png)
- [Portero - Fila Creada en Tabla (#84)](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/portero_visita_registrada_en_tabla.png)
- [Portero - Validación Credencial en Garita](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/portero_validacion_qr_visita_creada_por_portero.png)
- [Portero - Visitas Dentro Pase #84](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/portero_visitas_dentro_pase84.png)
- [Portero - Historial Accesos FINALIZADA](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/portero_historial_accesos_finalizada_pase84.png)
