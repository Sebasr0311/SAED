
ALTER SESSION SET CURRENT_SCHEMA = RESIDENCIAL;

-- ????????????????????????????????????????????????????????????????????
-- ?   SISTEMA DE ADMINISTRACI?N RESIDENCIAL ? EDIFICIO               ?
-- ?   Modelo Relacional v5.4 ? Script DDL Oracle SQL                 ?
-- ????????????????????????????????????????????????????????????????????
-- ?  Asignatura : Programaci?n de Computadores III (SS462)           ?
-- ?  Docente    : Ing. Esp. Alfredo Bautista                         ?
-- ?  Fecha      : Mayo 2026                                          ?
-- ?  Oracle     : 18c o superior                                     ?
-- ?  Charset    : AL32UTF8  ?  VARCHAR2 con sem?ntica CHAR           ?
-- ????????????????????????????????????????????????????????????????????
--
-- ??????????????????????????????????????????????????????????????????????
-- ?  ?NDICE DE CONTENIDO                                               ?
-- ??????????????????????????????????????????????????????????????????????
-- ?  ?0   Eliminaci?n segura de objetos previos                        ?
-- ?  ?1   Cat?logos          TIPOS_DOCUMENTO                          ?
-- ?  ?2   Infraestructura    APARTAMENTOS ? PARQUEADEROS               ?
-- ?  ?3   Personas           RESIDENTES ? TUTORES ? USUARIOS          ?
-- ?  ?4   Contratos/Pagos    CONTRATOS ? CONTRATO_RESIDENTE           ?
-- ?                          CUOTAS_ARRIENDO ? PAGOS ? ALERTAS_PAGO   ?
-- ?  ?4.5 Multas             MULTAS                                   ?
-- ?  ?5   M?dulo Visitas     VISITAS ? QR_ACCESOS ? VISITANTES        ?
-- ?                          VEHICULOS_VISITA ? REGISTRO_VISITA        ?
-- ?                          FRECUENTES_RESIDENTE ? REGISTROS_ACCESO  ?
-- ?  ?6   ?ndices de rendimiento                                       ?
-- ?  ?7   Triggers (17 en total)                                       ?
-- ?  ?8   Procedimientos SP_VALIDAR_QR ? SP_LIBERAR_VISITA_FRECUENTE  ?
-- ?  ?8.3 Funciones aut?nomas FN_SALDO_CUOTA ? FN_CALCULAR_MORA      ?
-- ?         FN_MINUTOS_RESTANTES_QR                                   ?
-- ?  ?8.4 Paquetes PKG_PAGOS ? PKG_VISITAS ? PKG_RESIDENTES          ?
-- ?  ?9   Vistas operativas (5 en total)                               ?
-- ?  ?10  Datos semilla                                                ?
-- ??????????????????????????????????????????????????????????????????????
--
-- ??????????????????????????????????????????????????????????????????????
-- ?  MAPA DE RELACIONES (20 tablas)                                    ?
-- ??????????????????????????????????????????????????????????????????????
-- ?  TIPOS_DOCUMENTO    ?  RESIDENTES.id_tipo_doc          (RESTRICT) ?
-- ?  TIPOS_DOCUMENTO    ?  TUTORES.id_tipo_doc             (RESTRICT) ?
-- ?  TIPOS_DOCUMENTO    ?  VISITANTES.id_tipo_doc          (RESTRICT) ?
-- ?  APARTAMENTOS       ?  PARQUEADEROS.id_apartamento     (SET NULL) ?
-- ?  APARTAMENTOS       ?  CONTRATOS.id_apartamento        (RESTRICT) ?
-- ?  RESIDENTES         ?  TUTORES.id_residente_menor      (CASCADE)  ?
-- ?  RESIDENTES         ?  USUARIOS.id_residente           (SET NULL) ?
-- ?  RESIDENTES         ?  CONTRATO_RESIDENTE.id_residente (RESTRICT) ?
-- ?  RESIDENTES         ?  VISITAS.id_residente            (RESTRICT) ?
-- ?  TUTORES            ?  CONTRATOS.id_tutor              (SET NULL) ?
-- ?  USUARIOS           ?  CONTRATOS.id_registrado_por     (RESTRICT) ?
-- ?  CONTRATOS          ?  CONTRATO_RESIDENTE.id_contrato  (CASCADE)  ?
-- ?  CONTRATOS          ?  CUOTAS_ARRIENDO.id_contrato     (CASCADE)  ?
-- ?  CUOTAS_ARRIENDO    ?  PAGOS.id_cuota                  (RESTRICT) ?
-- ?  CUOTAS_ARRIENDO    ?  ALERTAS_PAGO.id_cuota           (CASCADE)  ?
-- ?  CONTRATO_RESIDENTE ?  VISITAS.id_contrato_res         (RESTRICT) ?
-- ?  VISITAS            ?  QR_ACCESOS.id_visita            (CASCADE)  ?
-- ?  VISITAS            ?  VEHICULOS_VISITA.id_visita      (CASCADE)  ?
-- ?  VISITAS            ?  REGISTRO_VISITA.id_visita       (CASCADE)  ?
-- ?  VISITAS            ?  REGISTROS_ACCESO.id_visita      (RESTRICT) ?
-- ?  PARQUEADEROS       ?  VEHICULOS_VISITA.id_parqueadero (SET NULL) ?
-- ?  VISITANTES         ?  REGISTRO_VISITA.id_visitante    (RESTRICT) ?
-- ?  VEHICULOS_VISITA   ?  REGISTRO_VISITA.id_vehiculo_vis (SET NULL) ?
-- ?  RESIDENTES         ?  FRECUENTES_RESIDENTE.id_residente (CASCADE) ?
-- ?  VISITANTES         ?  FRECUENTES_RESIDENTE.id_visitante (CASCADE) ?
-- ?  USUARIOS           ?  QR_ACCESOS.id_vigilante_uso     (SET NULL) ?
-- ?  USUARIOS           ?  REGISTROS_ACCESO.id_vigilante   (RESTRICT) ?
-- ?  USUARIOS           ?  PAGOS.id_registrado_por         (RESTRICT) ?
-- ?  USUARIOS           ?  MULTAS.creado_por               (RESTRICT) ?
-- ?  APARTAMENTOS       ?  MULTAS.id_apartamento           (RESTRICT) ?
-- ?  BUZON              ?  MULTAS.id_mensaje               (SET NULL) ?
-- ?  APARTAMENTOS       ?  QUEJAS_SUGERENCIAS.id_apartamento (CASCADE) ?
-- ?  MULTAS             ?  QUEJAS_SUGERENCIAS.id_multa      (CASCADE)  ?
-- ?  USUARIOS           ?  QUEJAS_SUGERENCIAS.creado_por    (RESTRICT) ?
-- ?  USUARIOS           ?  QUEJAS_SUGERENCIAS.respondido_por (RESTRICT)?
-- ??????????????????????????????????????????????????????????????????????
--
-- ??????????????????????????????????????????????????????????????????????
-- ?  JUSTIFICACI?N DE RELACIONES (FKs)                                 ?
-- ?  Cada FK refleja una regla de negocio del sistema residencial.     ?
-- ??????????????????????????????????????????????????????????????????????
-- ?  TIPOS_DOCUMENTO ? RESIDENTES.id_tipo_doc (RESTRICT)              ?
-- ?    El residente debe tener tipo de documento del cat?logo.         ?
-- ?  TIPOS_DOCUMENTO ? TUTORES.id_tipo_doc (RESTRICT)                 ?
-- ?    El tutor legal tambi?n requiere tipo de documento v?lido.       ?
-- ?  TIPOS_DOCUMENTO ? VISITANTES.id_tipo_doc (RESTRICT)              ?
-- ?    El visitante se identifica con un tipo de documento v?lido.     ?
-- ??????????????????????????????????????????????????????????????????????
-- ?  APARTAMENTOS ? PARQUEADEROS.id_apartamento (SET NULL)            ?
-- ?    Si se elimina un apt., el puesto fijo queda sin asignar.        ?
-- ?  APARTAMENTOS ? CONTRATOS.id_apartamento (RESTRICT)               ?
-- ?    Un apartamento con contrato vigente no puede eliminarse.        ?
-- ??????????????????????????????????????????????????????????????????????
-- ?  RESIDENTES ? TUTORES.id_residente_menor (CASCADE)                ?
-- ?    El tutor es exclusivo del menor; si el menor se borra, la       ?
-- ?    tutela pierde sentido y se elimina en cascada.                  ?
-- ?  RESIDENTES ? USUARIOS.id_residente (SET NULL)                    ?
-- ?    La cuenta app es opcional; si el residente se elimina, el       ?
-- ?    usuario puede conservarse como admin (SET NULL).                ?
-- ?  RESIDENTES ? CONTRATO_RESIDENTE.id_residente (RESTRICT)          ?
-- ?    No se puede eliminar un residente vinculado a contratos.        ?
-- ?  RESIDENTES ? VISITAS.id_residente (RESTRICT)                     ?
-- ?    Desnormalizaci?n controlada de CONTRATO_RESIDENTE.id_residente; ?
-- ?    coherencia garantizada por TRG_VIS_VALIDAR_RESIDENTE.           ?
-- ?  RESIDENTES ? FRECUENTES_RESIDENTE.id_residente (CASCADE)         ?
-- ?    La lista de frecuentes es del residente; si el residente se     ?
-- ?    borra, su lista desaparece en cascada.                          ?
-- ??????????????????????????????????????????????????????????????????????
-- ?  TUTORES ? CONTRATOS.id_tutor (SET NULL)                          ?
-- ?    El tutor firma contratos de menores; si se elimina el tutor,    ?
-- ?    el contrato queda sin tutor asignado (SET NULL).                ?
-- ??????????????????????????????????????????????????????????????????????
-- ?  USUARIOS ? CONTRATOS.id_registrado_por (RESTRICT)                ?
-- ?    Auditor?a: el admin que cre? el contrato no puede borrarse      ?
-- ?    mientras el contrato exista.                                    ?
-- ?  USUARIOS ? QR_ACCESOS.id_vigilante_uso (SET NULL)                ?
-- ?    Vigilante que escane? el QR. Si se borra el usuario, el QR      ?
-- ?    conserva su trazabilidad con vigilante NULL.                    ?
-- ?  USUARIOS ? REGISTROS_ACCESO.id_vigilante (RESTRICT)              ?
-- ?    El portero que autoriz? el acceso no puede borrarse mientras    ?
-- ?    tenga registros de entrada/salida asociados.                    ?
-- ?  USUARIOS ? PAGOS.id_registrado_por (RESTRICT)                    ?
-- ?    Auditor?a financiera: el usuario que registr? el pago no        ?
-- ?    puede borrarse (protege integridad del historial de pagos).     ?
-- ??????????????????????????????????????????????????????????????????????
-- ?  CONTRATOS ? CONTRATO_RESIDENTE.id_contrato (CASCADE)             ?
-- ?    Al eliminar un contrato, sus v?nculos con residentes se borran; ?
-- ?    los residentes mismos no se eliminan.                           ?
-- ?  CONTRATOS ? CUOTAS_ARRIENDO.id_contrato (CASCADE)                ?
-- ?    Las cuotas pertenecen al contrato; sin contrato no tienen       ?
-- ?    sentido y se eliminan en cascada (junto con sus alertas).       ?
-- ??????????????????????????????????????????????????????????????????????
-- ?  CUOTAS_ARRIENDO ? PAGOS.id_cuota (RESTRICT)                      ?
-- ?    No se puede borrar una cuota con pagos registrados              ?
-- ?    (protege el historial financiero).                              ?
-- ?  CUOTAS_ARRIENDO ? ALERTAS_PAGO.id_cuota (CASCADE)                ?
-- ?    Las alertas son notificaciones de la cuota; sin cuota la        ?
-- ?    alerta pierde referencia y se elimina en cascada.               ?
-- ??????????????????????????????????????????????????????????????????????
-- ?  CONTRATO_RESIDENTE ? VISITAS.id_contrato_res (RESTRICT)          ?
-- ?    La visita acredita que el residente tiene contrato activo;      ?
-- ?    no se puede borrar el v?nculo si hay visitas asociadas.         ?
-- ??????????????????????????????????????????????????????????????????????
-- ?  VISITAS ? QR_ACCESOS.id_visita (CASCADE)                         ?
-- ?    El QR es el token de acceso de la visita; sin visita no         ?
-- ?    existe el QR.                                                   ?
-- ?  VISITAS ? VEHICULOS_VISITA.id_visita (CASCADE)                   ?
-- ?    Los veh?culos son datos de la visita; se borran con ella.       ?
-- ?  VISITAS ? REGISTRO_VISITA.id_visita (CASCADE)                    ?
-- ?    Los visitantes del grupo son parte de la visita.                ?
-- ?  VISITAS ? REGISTROS_ACCESO.id_visita (RESTRICT)                  ?
-- ?    El acceso f?sico confirma que la visita ocurri?; RESTRICT       ?
-- ?    protege el log de entrada/salida.                               ?
-- ??????????????????????????????????????????????????????????????????????
-- ?  PARQUEADEROS ? VEHICULOS_VISITA.id_parqueadero (SET NULL)        ?
-- ?    El puesto puede desvincularse si el veh?culo se reasigna;       ?
-- ?    el registro del veh?culo permanece (sin puesto asignado).       ?
-- ??????????????????????????????????????????????????????????????????????
-- ?  VISITANTES ? REGISTRO_VISITA.id_visitante (RESTRICT)             ?
-- ?    El visitante es reutilizable; solo se borra si no tiene         ?
-- ?    visitas registradas (preserva historial de accesos).            ?
-- ?  VISITANTES ? FRECUENTES_RESIDENTE.id_visitante (CASCADE)         ?
-- ?    Si un visitante se elimina del sistema, se purga de todas       ?
-- ?    las listas de frecuentes autom?ticamente.                       ?
-- ??????????????????????????????????????????????????????????????????????
-- ?  VEHICULOS_VISITA ? REGISTRO_VISITA.id_vehiculo_vis (SET NULL)    ?
-- ?    Un visitante puede llegar a pie (NULL) o en veh?culo; si el     ?
-- ?    veh?culo se borra, el registro permanece (lleg? a pie).         ?
-- ??????????????????????????????????????????????????????????????????????
--
-- ??????????????????????????????????????????????????????????????????????
-- ?  CONVENCIONES DE NOMBRES                                           ?
-- ??????????????????????????????????????????????????????????????????????
-- ?  PK_*  Primary Key    UQ_*  Unique constraint                      ?
-- ?  FK_*  Foreign Key    CHK_* Check constraint                       ?
-- ?  IDX_* ?ndice com?n   UIX_* ?ndice ?nico / funcional               ?
-- ?  TRG_* Trigger        VW_*  Vista   SP_* Procedimiento             ?
-- ??????????????????????????????????????????????????????????????????????
--
-- ??????????????????????????????????????????????????????????????????????
-- ?  HISTORIAL DE VERSIONES                                            ?
-- ??????????????????????????????????????????????????????????????????????
-- ?  v1.0  Dise?o inicial ? 10 tablas, PKs, FKs, ?ndices base         ?
-- ?  v2.0  22 correcciones: 3FN, ?ndices funcionales, triggers,        ?
-- ?        vistas, CHECKs de estado, VARCHAR2(n CHAR)                  ?
-- ?  v2.1  nombres/apellidos ? VARCHAR2(50 CHAR) en todas las tablas  ?
-- ?  v3.0  Fusi?n v2.1 + mejoras: TIPOS_DOCUMENTO normalizado,        ?
-- ?        CUOTAS_ARRIENDO separada, ALERTAS_PAGO, QR trazable,       ?
-- ?        PERSONAS_VEHICULO N:M, correcciones Oracle                  ?
-- ?  v4.0  Evoluci?n estructural ? 17 tablas:                         ?
-- ?    CAMBIOS INCORPORADOS:                                            ?
-- ?    ? TUTORES tabla independiente (antes autorreferencia en         ?
-- ?      RESIDENTES.id_tutor_legal ? m?s sem?ntico y extensible)      ?
-- ?    ? CONTRATO_RESIDENTE N:M (un contrato puede tener varios        ?
-- ?      residentes con roles; residente puede aparecer en contratos   ?
-- ?      hist?ricos)                                                   ?
-- ?    ? VISITANTES tabla reutilizable (reemplaza PERSONAS_VISITA ?   ?
-- ?      el mismo visitante no se duplica entre visitas)               ?
-- ?    ? REGISTRO_VISITA puente N:M (reemplaza PERSONAS_VEHICULO ?    ?
-- ?      id_vehiculo_visita nullable cubre llegada a pie)              ?
-- ?    ? VISITAS.id_contrato_res ? FK a CONTRATO_RESIDENTE (no a      ?
-- ?      CONTRATOS directamente ? garantiza relaci?n activa)           ?
-- ?    ? CONTRATOS.id_residente eliminado (ahora en CONTRATO_RES)     ?
-- ?    ? CONTRATOS.id_tutor FK ? TUTORES (firma del tutor)            ?
-- ?    ? APARTAMENTOS.numero (renombrado de numero_apartamento)        ?
-- ?    ? APARTAMENTOS tipos: 1_HAB, 2_HAB, 3_HAB (simplificado)      ?
-- ?    ? PARQUEADEROS UQ(id_apartamento) ? cardinalidad 1:1 expl?cita ?
-- ?    ? nombres/apellidos ? VARCHAR2(100 CHAR) en RESIDENTES,         ?
-- ?      TUTORES, VISITANTES                                            ?
-- ?    ? VISITAS.notas (renombrado de motivo)                          ?
-- ?    ? VISITAS.tiempo_validez_min CHECK 5-60 min                     ?
-- ?    ? REGISTROS_ACCESO.id_acceso (renombrado de id_registro)        ?
-- ?    ? Triggers reducidos a 8 (se retiran los dos de validaci?n      ?
-- ?      de edad ya que TUTORES gestiona esa l?gica externamente)      ?
-- ?    ? SP_VALIDAR_QR actualizado para VISITANTES/REGISTRO_VISITA    ?
-- ?    ? Vistas actualizadas para nuevo esquema                        ?
-- ?    ? Todas las constraints con nomenclatura PK_/FK_/UQ_/CHK_      ?
-- ?  v4.3  MULTAS + ajustes de columnas ? 19 tablas:                  ?
-- ?    ? MULTAS (nueva) ? tabla de sanciones (ruido/parqueadero)      ?
-- ?      con FK a APARTAMENTOS, BUZON y USUARIOS                      ?
-- ?    ? CONTRATOS.tipo_contrato (nueva columna) ? INICIAL,           ?
-- ?      RENOVACION, PERMANENCIA                                       ?
-- ?    ? APARTAMENTOS.administracion (nueva columna) ? cuota de       ?
-- ?      administraci?n mensual del apartamento                       ?
-- ?  v5.0  QUEJAS_SUGERENCIAS + correcciones de script ? 20 tablas:  ?
-- ?    ? QUEJAS_SUGERENCIAS (nueva) ? sistema de quejas, sugerencias  ?
-- ?      y apelaciones de multas; FK a APARTAMENTOS, MULTAS, USUARIOS ?
-- ?    ? SEQ_QUEJAS_SUGERENCIAS + SEC_MULTAS consolidados en ?0.5     ?
-- ?    ? FK_MULTA_MENSAJE separada como ALTER TABLE (MULTAS depend?a  ?
-- ?      de BUZON que se crea en ?5 ? reordenado con ALTER TABLE)     ?
-- ?    ? COMMENT ON COLUMN inv?lidos de BUZON eliminados              ?
-- ?      (empresa_mensajeria, numero_guia no existen en la tabla)     ?
-- ?  v5.1  Correcciones de constraints y columnas de pago:            ?
-- ?    ? CHK_CUOTA_ESTADO ampliado con 'ANULADA'                      ?
-- ?    ? MULTAS + registrado_pago_por / metodo_pago / comprobante_url ?
-- ?      + FK_MULTA_REGISTRADO_PAGO + CHK_MULTA_METODO_PAGO          ?
-- ?    ? CUOTAS_ARRIENDO + tipo_cuota DEFAULT 'ARRIENDO'; UQ_CUOTA_   ?
-- ?      PERIODO ampliada a (id_contrato,anio,mes,tipo_cuota);        ?
-- ?      CHK_CUOTA_TIPO a?adido; APARTAMENTOS.administracion (NUMBER) ?
-- ?  v5.2  Auditor?a de ?ndices FK e integridad de datos:             ?
-- ?    ? 8 ?ndices FK faltantes creados: IDX_BUZON_CREADO_POR,        ?
-- ?      IDX_CONTRATOS_REGISTRADO_POR, IDX_MULTAS_APARTAMENTO,        ?
-- ?      IDX_MULTAS_CREADO_POR, IDX_MULTAS_MENSAJE, IDX_MULTAS_PAGO_  ?
-- ?      POR, IDX_QR_VIGILANTE, IDX_QUEJAS_RESPONDIDO_POR             ?
-- ?    ? RECYCLEBIN purgado (9 objetos BIN$ de BUZON DROP previo)     ?
-- ?    ? MULTAS.id_mensaje = NULL donde apuntaba a PAQUETE (multa #6) ?
-- ?    ? APARTAMENTOS 203 y 303 ? DISPONIBLE (sin contrato ACTIVO)   ?
-- ?  v5.3  Simplificaci?n tabla MULTAS:                               ?
-- ?    ? DROP COLUMN MULTAS.comprobante_url (VARCHAR2 ? URL soporte)  ?
-- ?      Flujo de pago solo requiere metodo_pago y registrado_pago_por?
-- ?      foto_evidencia (CLOB) se conserva para evidencia PARQUEADERO ?
-- ?  v5.4  Correcci?n y normalizaci?n de columnas:                    ?
-- ?    ? ADD COLUMN MULTAS.foto_evidencia CLOB (restaurada de v5.3)   ?
-- ?    ? RESIDENTES/TUTORES/VISITANTES: nombres/apellidos 100?25      ?
-- ?    ? RESIDENTES/TUTORES/VISITANTES: email 254?40                  ?
-- ?    ? VEHICULOS_VISITA: marca 100?15, color 60?15                  ?

-- ??????????????????????????????????????????????????????????????????????

-- Configuraci?n de sesi?n sqlplus
-- SQLBLANKLINES ON : permite l?neas en blanco dentro de sentencias SQL
--                   (por defecto sqlplus las trata como terminador).
-- DEFINE OFF       : deshabilita sustituci?n de variables (&var)
--                   para que literales con '&' no interrumpan la ejecuci?n.

-- ??????????????????????????????????????????????????????????????????
-- ?0  ELIMINACI?N SEGURA (orden inverso de dependencias)
-- ??????????????????????????????????????????????????????????????????

-- Vistas
BEGIN EXECUTE IMMEDIATE 'DROP VIEW VW_VISITANTES_FRECUENTES';   EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP VIEW VW_PARQUEADEROS_VISITANTES'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP VIEW VW_CARTERA_PENDIENTE';       EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP VIEW VW_VISITAS_ACTIVAS';         EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP VIEW VW_RESIDENTE_APARTAMENTO';   EXCEPTION WHEN OTHERS THEN NULL; END;

-- Procedimiento almacenado
BEGIN EXECUTE IMMEDIATE 'DROP PROCEDURE SP_LIBERAR_VISITA_FRECUENTE'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP PROCEDURE SP_VALIDAR_QR'; EXCEPTION WHEN OTHERS THEN NULL; END;

-- Paquetes PL/SQL (body antes que spec para evitar dependencias)
BEGIN EXECUTE IMMEDIATE 'DROP PACKAGE BODY PKG_RESIDENTES'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP PACKAGE      PKG_RESIDENTES'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP PACKAGE BODY PKG_VISITAS';    EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP PACKAGE      PKG_VISITAS';    EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP PACKAGE BODY PKG_PAGOS';      EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP PACKAGE      PKG_PAGOS';      EXCEPTION WHEN OTHERS THEN NULL; END;

-- Funciones aut?nomas
BEGIN EXECUTE IMMEDIATE 'DROP FUNCTION FN_MINUTOS_RESTANTES_QR'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP FUNCTION FN_CALCULAR_MORA';        EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP FUNCTION FN_SALDO_CUOTA';          EXCEPTION WHEN OTHERS THEN NULL; END;

-- Tablas (m?s dependientes primero)
BEGIN EXECUTE IMMEDIATE 'DROP TABLE REGISTROS_ACCESO      CASCADE CONSTRAINTS PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE QUEJAS_SUGERENCIAS    CASCADE CONSTRAINTS PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE MULTAS                CASCADE CONSTRAINTS PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE BUZON                 CASCADE CONSTRAINTS PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE REGISTRO_VISITA       CASCADE CONSTRAINTS PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE FRECUENTES_RESIDENTE  CASCADE CONSTRAINTS PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE VEHICULOS_VISITA      CASCADE CONSTRAINTS PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE VISITANTES            CASCADE CONSTRAINTS PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE QR_ACCESOS            CASCADE CONSTRAINTS PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE VISITAS               CASCADE CONSTRAINTS PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE ALERTAS_PAGO          CASCADE CONSTRAINTS PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE PAGOS                 CASCADE CONSTRAINTS PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE CUOTAS_ARRIENDO       CASCADE CONSTRAINTS PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE CONTRATO_RESIDENTE    CASCADE CONSTRAINTS PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE CONTRATOS             CASCADE CONSTRAINTS PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE USUARIOS              CASCADE CONSTRAINTS PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE TUTORES               CASCADE CONSTRAINTS PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE RESIDENTES            CASCADE CONSTRAINTS PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE PARQUEADEROS          CASCADE CONSTRAINTS PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE APARTAMENTOS          CASCADE CONSTRAINTS PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP TABLE TIPOS_DOCUMENTO       CASCADE CONSTRAINTS PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;

-- Secuencias
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEQ_QUEJAS_SUGERENCIAS';   EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEC_MULTAS';                EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEC_BUZON';                 EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEC_REGISTROS_ACCESO';      EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEC_FRECUENTES_RESIDENTE';  EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEC_REGISTRO_VISITA';       EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEC_VEHICULOS_VISITA';      EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEC_VISITANTES';            EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEC_QR_ACCESOS';            EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEC_VISITAS';               EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEC_ALERTAS_PAGO';          EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEC_PAGOS';                 EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEC_CUOTAS_ARRIENDO';       EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEC_CONTRATO_RESIDENTE';    EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEC_CONTRATOS';             EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEC_USUARIOS';              EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEC_TUTORES';               EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEC_RESIDENTES';            EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEC_PARQUEADEROS';          EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEC_APARTAMENTOS';          EXCEPTION WHEN OTHERS THEN NULL; END;
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEC_TIPOS_DOCUMENTO';       EXCEPTION WHEN OTHERS THEN NULL; END;

-- ??????????????????????????????????????????????????????????????????
-- ?0.5  SECUENCIAS ? todas las tablas usan DEFAULT SEC_<tabla>.NEXTVAL
-- ??????????????????????????????????????????????????????????????????
CREATE SEQUENCE SEC_TIPOS_DOCUMENTO      START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEC_APARTAMENTOS         START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEC_PARQUEADEROS         START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEC_RESIDENTES           START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEC_TUTORES              START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEC_USUARIOS             START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEC_CONTRATOS            START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEC_CONTRATO_RESIDENTE   START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEC_CUOTAS_ARRIENDO      START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEC_PAGOS                START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEC_ALERTAS_PAGO         START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEC_MULTAS               START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEC_VISITAS              START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEC_QR_ACCESOS           START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEC_VISITANTES           START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEC_VEHICULOS_VISITA     START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEC_REGISTRO_VISITA      START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEC_FRECUENTES_RESIDENTE START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEC_REGISTROS_ACCESO     START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEC_BUZON                START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_QUEJAS_SUGERENCIAS   START WITH 1 INCREMENT BY 1 NOCACHE;

-- ??????????????????????????????????????????????????????????????????
-- ?1  CAT?LOGOS
-- ??????????????????????????????????????????????????????????????????

-- -------------------------------------------------------------------
-- TABLA 1: TIPOS_DOCUMENTO
-- Cat?logo normalizado de tipos de documento v?lidos en Colombia.
-- Evita valores inconsistentes como 'cc', 'C.C.', 'cedula'.
-- Compartido por RESIDENTES, TUTORES y VISITANTES.
-- -------------------------------------------------------------------
CREATE TABLE TIPOS_DOCUMENTO (
    id_tipo_doc     NUMBER              DEFAULT SEC_TIPOS_DOCUMENTO.NEXTVAL NOT NULL
                                        CONSTRAINT PK_TIPODOC PRIMARY KEY,
    codigo          VARCHAR2(10  CHAR)  NOT NULL,
    descripcion     VARCHAR2(80  CHAR)  NOT NULL,
    activo          NUMBER(1)           DEFAULT 1 NOT NULL,

    CONSTRAINT UQ_TIPODOC_CODIGO  UNIQUE (codigo),
    CONSTRAINT CHK_TIPODOC_ACTIVO CHECK  (activo IN (0, 1))
);

COMMENT ON TABLE  TIPOS_DOCUMENTO            IS 'Cat?logo normalizado de tipos de documento de identidad v?lidos en Colombia.';
COMMENT ON COLUMN TIPOS_DOCUMENTO.codigo     IS 'C?digo corto ?nico: CC, TI, CE, PP, PEP, RC, NIT. Case-sensitive.';
COMMENT ON COLUMN TIPOS_DOCUMENTO.activo     IS '0 = tipo descontinuado (no usar en nuevos registros).';

-- Seed del cat?logo
INSERT INTO TIPOS_DOCUMENTO (codigo, descripcion) VALUES ('CC',  'C?dula de Ciudadan?a');
INSERT INTO TIPOS_DOCUMENTO (codigo, descripcion) VALUES ('TI',  'Tarjeta de Identidad');
INSERT INTO TIPOS_DOCUMENTO (codigo, descripcion) VALUES ('CE',  'C?dula de Extranjer?a');
INSERT INTO TIPOS_DOCUMENTO (codigo, descripcion) VALUES ('PP',  'Pasaporte');
INSERT INTO TIPOS_DOCUMENTO (codigo, descripcion) VALUES ('PEP', 'Permiso Especial de Permanencia');
INSERT INTO TIPOS_DOCUMENTO (codigo, descripcion) VALUES ('RC',  'Registro Civil');
INSERT INTO TIPOS_DOCUMENTO (codigo, descripcion) VALUES ('NIT', 'NIT');
COMMIT;

-- ??????????????????????????????????????????????????????????????????
-- ?2  INFRAESTRUCTURA  (sin dependencias entre s?)
-- ??????????????????????????????????????????????????????????????????

-- -------------------------------------------------------------------
-- TABLA 2: APARTAMENTOS
-- Unidades habitacionales del edificio. Solo se arriendan, nunca
-- se venden. La columna se llama 'numero' (identificador visible).
-- Tipos simplificados: 1_HAB, 2_HAB, 3_HAB en lugar de los nombres
-- largos de v3 para agilizar queries y reducir valores de CHECK.
-- -------------------------------------------------------------------
CREATE TABLE APARTAMENTOS (
    id_apartamento  NUMBER              DEFAULT SEC_APARTAMENTOS.NEXTVAL NOT NULL
                                        CONSTRAINT PK_APT PRIMARY KEY,
    numero          VARCHAR2(20  CHAR)  NOT NULL,
    piso            NUMBER(3)           NOT NULL,
    tipo            VARCHAR2(20  CHAR)  NOT NULL,
    area_m2         NUMBER(6,2),
    capacidad_maxima NUMBER(3)          DEFAULT 4 NOT NULL,
    administracion  NUMBER(12,2),                        -- Valor de la cuota de administraci?n
    estado          VARCHAR2(20  CHAR)  DEFAULT 'DISPONIBLE' NOT NULL,
    activo          NUMBER(1)           DEFAULT 1 NOT NULL,
    fecha_registro  TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    actualizado_en  TIMESTAMP,

    CONSTRAINT UQ_APT_NUMERO
        UNIQUE (numero),

    CONSTRAINT CHK_APT_TIPO
        CHECK (tipo IN (
            'ESTUDIO','1_HAB','2_HAB','3_HAB','PENTHOUSE','OTRO'
        )),

    CONSTRAINT CHK_APT_ESTADO
        CHECK (estado IN ('DISPONIBLE','OCUPADO','EN_MANTENIMIENTO')),

    CONSTRAINT CHK_APT_ACTIVO
        CHECK (activo IN (0, 1)),

    CONSTRAINT CHK_APT_PISO
        CHECK (piso >= 1),

    CONSTRAINT CHK_APT_AREA
        CHECK (area_m2 IS NULL OR area_m2 > 0),

    CONSTRAINT CHK_APT_CAPACIDAD
        CHECK (capacidad_maxima > 0)
);

COMMENT ON TABLE  APARTAMENTOS                IS 'Unidades habitacionales del edificio. Solo se arriendan, no se venden.';
COMMENT ON COLUMN APARTAMENTOS.numero         IS 'Identificador visible: 101, 202B, PH-1. ?nico por edificio.';
COMMENT ON COLUMN APARTAMENTOS.tipo           IS 'ESTUDIO | 1_HAB | 2_HAB | 3_HAB | PENTHOUSE | OTRO';
COMMENT ON COLUMN APARTAMENTOS.estado         IS 'DISPONIBLE | OCUPADO | EN_MANTENIMIENTO. Sincronizado por TRG_CONT_SYNC_APARTAMENTO.';
COMMENT ON COLUMN APARTAMENTOS.capacidad_maxima IS 'N?mero m?ximo de residentes permitido. Validado en capa Service.';
COMMENT ON COLUMN APARTAMENTOS.activo         IS 'Soft-delete: 1 = activo; 0 = inactivo/retirado. Filtrado en findAll().';
COMMENT ON COLUMN APARTAMENTOS.actualizado_en IS 'Timestamp del ?ltimo UPDATE. Mantenido por TRG_APARTAMENTOS_UPD.';

-- -------------------------------------------------------------------
-- TABLA 3: PARQUEADEROS
-- Puestos de parqueo. Pueden ser fijos (1:1 con apartamento) o
-- rotativos para visitantes (id_apartamento NULL, es_visitante=1).
-- UQ_PARQ_APARTAMENTO garantiza cardinalidad 1:1 expl?cita:
-- m?ximo 1 puesto fijo por apartamento.
-- -------------------------------------------------------------------
CREATE TABLE PARQUEADEROS (
    id_parqueadero  NUMBER              DEFAULT SEC_PARQUEADEROS.NEXTVAL NOT NULL
                                        CONSTRAINT PK_PARQ PRIMARY KEY,
    codigo          VARCHAR2(20  CHAR)  NOT NULL,
    tipo            VARCHAR2(15  CHAR)  NOT NULL,
    estado          VARCHAR2(20  CHAR)  DEFAULT 'DISPONIBLE' NOT NULL,
    es_visitante    NUMBER(1)           DEFAULT 0 NOT NULL,
    id_apartamento  NUMBER,                         -- NULL = puesto de visitantes o sin asignar

    fecha_registro  TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    actualizado_en  TIMESTAMP,

    CONSTRAINT UQ_PARQ_CODIGO
        UNIQUE (codigo),

    CONSTRAINT UQ_PARQ_APARTAMENTO
        UNIQUE (id_apartamento),                    -- garantiza 1:1 con APARTAMENTOS

    CONSTRAINT FK_PARQ_APARTAMENTO
        FOREIGN KEY (id_apartamento)
        REFERENCES APARTAMENTOS(id_apartamento)
        ON DELETE SET NULL,

    CONSTRAINT CHK_PARQ_TIPO
        CHECK (tipo IN ('VEHICULO','MOTO','BICICLETA')),

    CONSTRAINT CHK_PARQ_ESTADO
        CHECK (estado IN ('DISPONIBLE','OCUPADO','EN_MANTENIMIENTO')),

    CONSTRAINT CHK_PARQ_ES_VISITANTE
        CHECK (es_visitante IN (0, 1)),

    CONSTRAINT CHK_PARQ_EXCLUSIVIDAD
        CHECK (NOT (id_apartamento IS NOT NULL AND es_visitante = 1))
        -- Un puesto no puede ser simult?neamente fijo (residente) y rotativo (visitantes)
);

COMMENT ON TABLE  PARQUEADEROS                IS 'Espacios de parqueo del edificio. Fijos (residente, 1:1) o rotativos (visitante).';
COMMENT ON COLUMN PARQUEADEROS.codigo         IS 'C?digo visible: P-01, M-03, B-01. ?nico en el edificio.';
COMMENT ON COLUMN PARQUEADEROS.es_visitante   IS '0 = asignado fijo a un apartamento | 1 = disponible para visitantes en rotaci?n.';
COMMENT ON COLUMN PARQUEADEROS.id_apartamento IS 'FK al apartamento asignado. UNIQUE garantiza 1:1. NULL = rotativo de visitantes.';
COMMENT ON COLUMN PARQUEADEROS.actualizado_en IS 'Timestamp del ?ltimo UPDATE. Mantenido por TRG_PARQUEADEROS_UPD.';

-- ??????????????????????????????????????????????????????????????????
-- ?3  PERSONAS
-- ??????????????????????????????????????????????????????????????????

-- -------------------------------------------------------------------
-- TABLA 4: RESIDENTES
-- Personas que habitan el edificio. NO tiene id_apartamento (la
-- relaci?n va por CONTRATOS ? CONTRATO_RESIDENTE).
-- NO tiene id_tutor_legal (ahora en tabla TUTORES independiente).
-- nombres/apellidos ? VARCHAR2(80 CHAR) para acomodar nombres largos.
-- -------------------------------------------------------------------
CREATE TABLE RESIDENTES (
    id_residente        NUMBER              DEFAULT SEC_RESIDENTES.NEXTVAL NOT NULL
                                            CONSTRAINT PK_RES PRIMARY KEY,
    id_tipo_doc         NUMBER              NOT NULL,
    numero_documento    VARCHAR2(20  CHAR)  NOT NULL,
    nombres             VARCHAR2(25  CHAR)  NOT NULL,
    apellidos           VARCHAR2(25  CHAR)  NOT NULL,
    fecha_nacimiento    DATE                NOT NULL,
    telefono            VARCHAR2(20  CHAR),
    email               VARCHAR2(40  CHAR),             -- m?x 40 chars (uso real, ajustado en v5.4)
    foto_url            VARCHAR2(2000 CHAR),
    doc_pdf_url         VARCHAR2(2000 CHAR),
    fecha_ingreso       DATE                DEFAULT SYSDATE NOT NULL,
    es_menor_edad       NUMBER(1)           DEFAULT 0 NOT NULL,
    activo              NUMBER(1)           DEFAULT 1 NOT NULL,
    fecha_registro      TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    actualizado_en      TIMESTAMP,

    CONSTRAINT UQ_RES_DOCUMENTO
        UNIQUE (id_tipo_doc, numero_documento),

    CONSTRAINT UQ_RES_EMAIL
        UNIQUE (email),                             -- Oracle permite m?ltiples NULLs

    CONSTRAINT FK_RES_TIPO_DOC
        FOREIGN KEY (id_tipo_doc)
        REFERENCES TIPOS_DOCUMENTO(id_tipo_doc),    -- RESTRICT (default Oracle)

    CONSTRAINT CHK_RES_MENOR
        CHECK (es_menor_edad IN (0, 1)),

    CONSTRAINT CHK_RES_ACTIVO
        CHECK (activo IN (0, 1)),

    CONSTRAINT CHK_RES_NOMBRES_NN
        CHECK (TRIM(nombres) IS NOT NULL),

    CONSTRAINT CHK_RES_APELLIDOS_NN
        CHECK (TRIM(apellidos) IS NOT NULL)
);

COMMENT ON TABLE  RESIDENTES                  IS 'Personas que habitan el edificio. La relaci?n residente-apartamento va por CONTRATOS/CONTRATO_RESIDENTE.';
COMMENT ON COLUMN RESIDENTES.id_tipo_doc      IS 'FK a TIPOS_DOCUMENTO. Normaliza el tipo de documento.';
COMMENT ON COLUMN RESIDENTES.nombres          IS 'Primer y segundo nombre. M?x 100 caracteres (CHAR).';
COMMENT ON COLUMN RESIDENTES.apellidos        IS 'Primer y segundo apellido. M?x 100 caracteres (CHAR).';
COMMENT ON COLUMN RESIDENTES.es_menor_edad    IS '1 = menor de 18 a?os. El tutor legal va en la tabla TUTORES.';
COMMENT ON COLUMN RESIDENTES.activo           IS '1 = activo; 0 = inactivo/retirado. Soft-delete.';
COMMENT ON COLUMN RESIDENTES.doc_pdf_url      IS 'Ruta al documento de identidad escaneado (S3, MinIO, local).';
COMMENT ON COLUMN RESIDENTES.actualizado_en   IS 'Timestamp del ?ltimo UPDATE. Mantenido por TRG_RESIDENTES_UPD.';

-- -------------------------------------------------------------------
-- TABLA 5: TUTORES
-- Responsables legales de residentes menores de edad.
-- NO tienen usuario en el sistema (sin FK a USUARIOS).
-- Un tutor puede firmar muchos contratos (FK nullable en CONTRATOS).
-- Comparte TIPOS_DOCUMENTO con RESIDENTES y VISITANTES.
-- -------------------------------------------------------------------
CREATE TABLE TUTORES (
    id_tutor            NUMBER              DEFAULT SEC_TUTORES.NEXTVAL NOT NULL
                                            CONSTRAINT PK_TUT PRIMARY KEY,
    id_residente_menor  NUMBER              NOT NULL,
    id_tipo_doc         NUMBER              NOT NULL,
    numero_documento    VARCHAR2(20  CHAR)  NOT NULL,
    nombres             VARCHAR2(25  CHAR)  NOT NULL,
    apellidos           VARCHAR2(25  CHAR)  NOT NULL,
    telefono            VARCHAR2(20  CHAR),
    email               VARCHAR2(40  CHAR),
    parentesco          VARCHAR2(30  CHAR)  NOT NULL,
    doc_pdf_url         VARCHAR2(2000 CHAR),
    fecha_registro      TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    actualizado_en  TIMESTAMP,

    CONSTRAINT UQ_TUTOR_DOC
        UNIQUE (id_tipo_doc, numero_documento),

    CONSTRAINT FK_TUTOR_RESIDENTE
        FOREIGN KEY (id_residente_menor)
        REFERENCES RESIDENTES(id_residente)
        ON DELETE CASCADE,

    CONSTRAINT FK_TUTOR_TIPO_DOC
        FOREIGN KEY (id_tipo_doc)
        REFERENCES TIPOS_DOCUMENTO(id_tipo_doc),    -- RESTRICT

    CONSTRAINT CHK_TUTOR_PARENTESCO
        CHECK (parentesco IN (
            'PADRE','MADRE','ABUELO','ABUELA','TIO','TIA',
            'HERMANO','HERMANA','TUTOR_LEGAL','OTRO'
        ))
);

COMMENT ON TABLE  TUTORES                       IS 'Responsables legales de residentes menores de edad. Sin acceso al sistema (no son USUARIOS).';
COMMENT ON COLUMN TUTORES.id_residente_menor    IS 'FK al residente menor que representa. CASCADE: si se borra el menor, se borra el tutor.';
COMMENT ON COLUMN TUTORES.parentesco            IS 'PADRE | MADRE | ABUELO | ABUELA | TIO | TIA | HERMANO | HERMANA | TUTOR_LEGAL | OTRO';
COMMENT ON COLUMN TUTORES.doc_pdf_url           IS 'Ruta al documento de identidad del tutor escaneado.';
COMMENT ON COLUMN TUTORES.actualizado_en IS 'Timestamp del ?ltimo UPDATE. Mantenido por TRG_TUTORES_UPD.';

-- -------------------------------------------------------------------
-- TABLA 6: USUARIOS  (autenticaci?n y autorizaci?n)
-- Solo se conecta con: RESIDENTES, CONTRATOS, PAGOS,
-- QR_ACCESOS y REGISTROS_ACCESO. No con VISITAS ni TUTORES.
-- -------------------------------------------------------------------
CREATE TABLE USUARIOS (
    id_usuario      NUMBER              DEFAULT SEC_USUARIOS.NEXTVAL NOT NULL
                                        CONSTRAINT PK_USR PRIMARY KEY,
    id_residente    NUMBER,                         -- nullable: admins/porteros sin apartamento
    username        VARCHAR2(50  CHAR)  NOT NULL,
    password_hash   VARCHAR2(255 CHAR)  NOT NULL,   -- BCrypt factor >= 12
    rol             VARCHAR2(20  CHAR)  NOT NULL,
    activo          NUMBER(1)           DEFAULT 1 NOT NULL,
    ultimo_login    TIMESTAMP,
    fecha_registro  TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    actualizado_en  TIMESTAMP,

    CONSTRAINT UQ_USR_USERNAME
        UNIQUE (username),

    CONSTRAINT UQ_USR_RESIDENTE
        UNIQUE (id_residente),                      -- 1 cuenta por residente; NULLs no compiten

    CONSTRAINT FK_USR_RESIDENTE
        FOREIGN KEY (id_residente)
        REFERENCES RESIDENTES(id_residente)
        ON DELETE SET NULL,

    CONSTRAINT CHK_USR_ROL
        CHECK (rol IN ('ADMINISTRADOR','PORTERO','RESIDENTE')),

    CONSTRAINT CHK_USR_ACTIVO
        CHECK (activo IN (0, 1)),

    CONSTRAINT CHK_USR_USERNAME
        CHECK (username = LOWER(TRIM(username))),   -- siempre en min?sculas

    CONSTRAINT CHK_USR_USERNAME_LEN
        CHECK (LENGTH(TRIM(username)) >= 4)
);

COMMENT ON TABLE  USUARIOS               IS 'Cuentas de acceso. Roles: ADMINISTRADOR, PORTERO, RESIDENTE.';
COMMENT ON COLUMN USUARIOS.id_residente  IS 'NULL para porteros/admins sin apartamento. UNIQUE: 1 cuenta por residente.';
COMMENT ON COLUMN USUARIOS.username      IS 'Siempre en min?sculas. M?nimo 4 caracteres.';
COMMENT ON COLUMN USUARIOS.password_hash IS 'Hash BCrypt (factor >= 12). NUNCA almacenar contrase?as en texto plano.';
COMMENT ON COLUMN USUARIOS.actualizado_en IS 'Timestamp del ?ltimo UPDATE. Mantenido por TRG_USUARIOS_UPD.';

-- ??????????????????????????????????????????????????????????????????
-- ?4  CONTRATOS Y PAGOS  (dependen de ?2 y ?3)
-- ??????????????????????????????????????????????????????????????????

-- -------------------------------------------------------------------
-- TABLA 7: CONTRATOS
-- Contrato de arrendamiento. Los firmantes van en CONTRATO_RESIDENTE
-- (N:M). El tutor firma si hay un menor involucrado (FK nullable).
-- IMPORTANTE: CONTRATOS no se conecta directamente con VISITAS;
-- la conexi?n es CONTRATOS ? CONTRATO_RESIDENTE ? VISITAS.
-- -------------------------------------------------------------------
CREATE TABLE CONTRATOS (
    id_contrato         NUMBER              DEFAULT SEC_CONTRATOS.NEXTVAL NOT NULL
                                            CONSTRAINT PK_CONT PRIMARY KEY,
    id_apartamento      NUMBER              NOT NULL,
    id_tutor            NUMBER,                         -- nullable: solo si hay residente menor
    id_registrado_por   NUMBER              NOT NULL,
    fecha_inicio        DATE                NOT NULL,
    fecha_fin           DATE,                           -- NULL = contrato a t?rmino indefinido
    valor_mensual       NUMBER(12,2)        NOT NULL,
    dia_pago            NUMBER(2)           DEFAULT 5   NOT NULL,   -- d?a del mes (1-28)
    dias_gracia         NUMBER(2)           DEFAULT 5   NOT NULL,
    porcentaje_mora     NUMBER(5,2)         DEFAULT 1.5 NOT NULL,   -- % mensual sobre saldo vencido
    tipo_contrato       VARCHAR2(20  CHAR)  DEFAULT 'INICIAL' NOT NULL,  -- INICIAL | RENOVACION | PERMANENCIA
    contrato_pdf_url    VARCHAR2(2000 CHAR),
    estado              VARCHAR2(20  CHAR)  DEFAULT 'PENDIENTE_FIRMA' NOT NULL,
    observaciones       VARCHAR2(1000 CHAR),
    fecha_registro      TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    actualizado_en      TIMESTAMP,

    CONSTRAINT FK_CONT_APARTAMENTO
        FOREIGN KEY (id_apartamento)
        REFERENCES APARTAMENTOS(id_apartamento),        -- RESTRICT (default Oracle)

    CONSTRAINT FK_CONT_TUTOR
        FOREIGN KEY (id_tutor)
        REFERENCES TUTORES(id_tutor)
        ON DELETE SET NULL,

    CONSTRAINT FK_CONT_REGISTRADO
        FOREIGN KEY (id_registrado_por)
        REFERENCES USUARIOS(id_usuario),                -- RESTRICT

    CONSTRAINT CHK_CONT_FECHAS
        CHECK (fecha_fin IS NULL OR fecha_fin > fecha_inicio),

    CONSTRAINT CHK_CONT_VALOR
        CHECK (valor_mensual > 0),

    CONSTRAINT CHK_CONT_DIA_PAGO
        CHECK (dia_pago BETWEEN 1 AND 28),

    CONSTRAINT CHK_CONT_GRACIA
        CHECK (dias_gracia >= 0),

    CONSTRAINT CHK_CONT_MORA
        CHECK (porcentaje_mora >= 0),

    CONSTRAINT CHK_CONT_MORA_MAX
        CHECK (porcentaje_mora <= 100),

    CONSTRAINT CHK_CONT_TIPO_CONTRATO
        CHECK (tipo_contrato IN ('INICIAL','RENOVACION','PERMANENCIA')),

    CONSTRAINT CHK_CONT_ESTADO
        CHECK (estado IN (
            'PENDIENTE_FIRMA','ACTIVO','VENCIDO','CANCELADO','SUSPENDIDO'
        ))
);

-- ?ndice funcional: garantiza exactamente 1 contrato ACTIVO por apartamento.
-- CASE WHEN convierte a NULL los no-ACTIVOS ? no generan colisi?n UNIQUE.
CREATE UNIQUE INDEX UIX_CONT_APT_ACTIVO ON CONTRATOS (
    CASE WHEN estado = 'ACTIVO' THEN id_apartamento ELSE NULL END
);

COMMENT ON TABLE  CONTRATOS                   IS 'Contratos de arrendamiento. 1 solo ACTIVO por apartamento (UIX_CONT_APT_ACTIVO).';
COMMENT ON COLUMN CONTRATOS.id_apartamento    IS 'FK al apartamento arrendado. RESTRICT: no se puede eliminar un apt. con contrato.';
COMMENT ON COLUMN CONTRATOS.fecha_inicio      IS 'Fecha de inicio de vigencia del contrato.';
COMMENT ON COLUMN CONTRATOS.fecha_fin         IS 'Fecha de vencimiento. NULL = contrato a t?rmino indefinido.';
COMMENT ON COLUMN CONTRATOS.valor_mensual     IS 'Canon mensual de arrendamiento (COP). Debe ser mayor a 0.';
COMMENT ON COLUMN CONTRATOS.contrato_pdf_url  IS 'URL al documento PDF firmado del contrato. NULL si a?n no se ha cargado.';
COMMENT ON COLUMN CONTRATOS.observaciones     IS 'Notas administrativas sobre el contrato (acuerdos especiales, incidencias).';
COMMENT ON COLUMN CONTRATOS.id_tutor          IS 'FK nullable a TUTORES. Se llena cuando uno de los residentes es menor de edad.';
COMMENT ON COLUMN CONTRATOS.dia_pago          IS 'D?a del mes en que vence la cuota (1-28). Usado para generar CUOTAS_ARRIENDO.';
COMMENT ON COLUMN CONTRATOS.dias_gracia       IS 'D?as adicionales sin mora tras la fecha l?mite. 0 = mora inmediata.';
COMMENT ON COLUMN CONTRATOS.porcentaje_mora   IS 'Tasa de mora mensual en %. Ej: 1.5 = 1.5% mensual sobre saldo vencido.';
COMMENT ON COLUMN CONTRATOS.estado            IS 'PENDIENTE_FIRMA ? ACTIVO ? VENCIDO | CANCELADO | SUSPENDIDO';
COMMENT ON COLUMN CONTRATOS.id_registrado_por IS 'Usuario (admin) que cre? el contrato. Auditor?a.';
COMMENT ON COLUMN CONTRATOS.actualizado_en    IS 'Timestamp del ?ltimo UPDATE. Mantenido por TRG_CONTRATOS_UPD.';

-- -------------------------------------------------------------------
-- TABLA 8: CONTRATO_RESIDENTE  (tabla puente N:M)
-- Un contrato puede tener varios residentes (ARRENDATARIO, CODEUDOR,
-- RESIDENTE_MENOR, etc.). Un residente puede aparecer en varios
-- contratos hist?ricos. IMPORTANTE: VISITAS se conecta con esta
-- tabla (id_contrato_res), NO directamente con CONTRATOS.
-- -------------------------------------------------------------------
CREATE TABLE CONTRATO_RESIDENTE (
    id_contrato_res NUMBER              DEFAULT SEC_CONTRATO_RESIDENTE.NEXTVAL NOT NULL
                                        CONSTRAINT PK_CONTRES PRIMARY KEY,
    id_contrato     NUMBER              NOT NULL,
    id_residente    NUMBER              NOT NULL,
    rol_en_contrato VARCHAR2(25  CHAR)  DEFAULT 'ARRENDATARIO' NOT NULL,

    CONSTRAINT UQ_CONTRES
        UNIQUE (id_contrato, id_residente),

    CONSTRAINT FK_CONTRES_CONTRATO
        FOREIGN KEY (id_contrato)
        REFERENCES CONTRATOS(id_contrato)
        ON DELETE CASCADE,

    CONSTRAINT FK_CONTRES_RESIDENTE
        FOREIGN KEY (id_residente)
        REFERENCES RESIDENTES(id_residente),            -- RESTRICT

    CONSTRAINT CHK_CONTRES_ROL
        CHECK (rol_en_contrato IN (
            'ARRENDATARIO','CODEUDOR','RESIDENTE_MENOR','OTRO'
        ))
);

COMMENT ON TABLE  CONTRATO_RESIDENTE                 IS 'N:M entre CONTRATOS y RESIDENTES. Roles en el contrato.';
COMMENT ON COLUMN CONTRATO_RESIDENTE.id_contrato_res IS 'PK surrogate. Referenciada directamente por VISITAS.id_contrato_res.';
COMMENT ON COLUMN CONTRATO_RESIDENTE.id_contrato     IS 'FK al contrato. CASCADE: al borrar el contrato se eliminan sus v?nculos.';
COMMENT ON COLUMN CONTRATO_RESIDENTE.id_residente    IS 'FK al residente. RESTRICT: no se puede borrar un residente con contratos.';
COMMENT ON COLUMN CONTRATO_RESIDENTE.rol_en_contrato IS 'ARRENDATARIO (firmante principal) | CODEUDOR | RESIDENTE_MENOR | OTRO';

-- -------------------------------------------------------------------
-- TABLA 9: CUOTAS_ARRIENDO
-- 1 fila = 1 mes de cobro de 1 contrato.
-- Separar cuota de pago permite abonos parciales, c?lculo preciso
-- de mora y alertas granulares por cuota.
-- -------------------------------------------------------------------
CREATE TABLE CUOTAS_ARRIENDO (
    id_cuota          NUMBER              DEFAULT SEC_CUOTAS_ARRIENDO.NEXTVAL NOT NULL
                                          CONSTRAINT PK_CUOTA PRIMARY KEY,
    id_contrato       NUMBER              NOT NULL,
    anio              NUMBER(4)           NOT NULL,
    mes               NUMBER(2)           NOT NULL,
    tipo_cuota        VARCHAR2(15  CHAR)  DEFAULT 'ARRIENDO' NOT NULL,
    fecha_limite      DATE                NOT NULL,
    valor_base        NUMBER(12,2)        NOT NULL,
    valor_mora        NUMBER(12,2)        DEFAULT 0 NOT NULL,
    valor_total       NUMBER(12,2)        NOT NULL,   -- = valor_base + valor_mora (trigger)
    estado            VARCHAR2(15  CHAR)  DEFAULT 'PENDIENTE' NOT NULL,
    fecha_generacion  TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    actualizado_en    TIMESTAMP,

    CONSTRAINT FK_CUOTA_CONTRATO
        FOREIGN KEY (id_contrato)
        REFERENCES CONTRATOS(id_contrato)
        ON DELETE CASCADE,

    CONSTRAINT UQ_CUOTA_PERIODO
        UNIQUE (id_contrato, anio, mes, tipo_cuota),

    CONSTRAINT CHK_CUOTA_MES
        CHECK (mes BETWEEN 1 AND 12),

    CONSTRAINT CHK_CUOTA_ANIO
        CHECK (anio >= 2000),

    CONSTRAINT CHK_CUOTA_VALOR_BASE
        CHECK (valor_base > 0),

    CONSTRAINT CHK_CUOTA_MORA
        CHECK (valor_mora >= 0),

    CONSTRAINT CHK_CUOTA_TOTAL
        CHECK (valor_total = valor_base + valor_mora),

    CONSTRAINT CHK_CUOTA_ESTADO
        CHECK (estado IN ('PENDIENTE','PAGADA','VENCIDA','EN_MORA','ANULADA')),

    CONSTRAINT CHK_CUOTA_TIPO
        CHECK (tipo_cuota IN ('ARRIENDO','ADMINISTRACION'))
);

COMMENT ON TABLE  CUOTAS_ARRIENDO              IS 'Cada fila = 1 mes de cobro de 1 contrato. Permite abonos y c?lculo preciso de mora.';
COMMENT ON COLUMN CUOTAS_ARRIENDO.anio         IS 'A?o de la cuota (>= 2000). Junto con mes y id_contrato identifica el per?odo ?nico.';
COMMENT ON COLUMN CUOTAS_ARRIENDO.mes          IS 'Mes de la cuota (1-12). UQ(id_contrato, anio, mes) garantiza unicidad mensual.';
COMMENT ON COLUMN CUOTAS_ARRIENDO.fecha_limite IS 'Fecha l?mite de pago. Calculada como dia_pago del contrato en el mes/a?o de la cuota.';
COMMENT ON COLUMN CUOTAS_ARRIENDO.valor_base   IS 'Canon base copiado de CONTRATOS.valor_mensual al generar la cuota. Inmutable.';
COMMENT ON COLUMN CUOTAS_ARRIENDO.valor_mora   IS 'Mora acumulada. Actualizada por job nocturno seg?n CONTRATOS.porcentaje_mora.';
COMMENT ON COLUMN CUOTAS_ARRIENDO.valor_total  IS 'valor_base + valor_mora. Recalculado autom?ticamente por TRG_CUOTAS_UPD.';
COMMENT ON COLUMN CUOTAS_ARRIENDO.estado       IS 'PENDIENTE | PAGADA | VENCIDA | EN_MORA | ANULADA';
COMMENT ON COLUMN CUOTAS_ARRIENDO.tipo_cuota   IS 'ARRIENDO | ADMINISTRACION. Permite registrar la cuota de administraci?n por separado.';
COMMENT ON COLUMN CUOTAS_ARRIENDO.fecha_generacion IS 'Timestamp en que se cre? la cuota (autom?tico o por job nocturno).';
COMMENT ON COLUMN CUOTAS_ARRIENDO.actualizado_en   IS 'Timestamp del ?ltimo UPDATE. Mantenido por TRG_CUOTAS_UPD.';

-- -------------------------------------------------------------------
-- TABLA 10: PAGOS
-- Transacciones de pago asociadas a una cuota.
-- Una cuota puede tener m?ltiples pagos (abonos parciales).
-- -------------------------------------------------------------------
CREATE TABLE PAGOS (
    id_pago           NUMBER              DEFAULT SEC_PAGOS.NEXTVAL NOT NULL
                                          CONSTRAINT PK_PAGO PRIMARY KEY,
    id_cuota          NUMBER              NOT NULL,
    id_registrado_por NUMBER              NOT NULL,
    fecha_pago        DATE                DEFAULT SYSDATE NOT NULL,
    valor_pagado      NUMBER(12,2)        NOT NULL,
    metodo_pago       VARCHAR2(20  CHAR)  NOT NULL,
    comprobante_url   VARCHAR2(2000 CHAR),
    referencia        VARCHAR2(200 CHAR),
    notas             VARCHAR2(500 CHAR),
    fecha_registro    TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT FK_PAGO_CUOTA
        FOREIGN KEY (id_cuota)
        REFERENCES CUOTAS_ARRIENDO(id_cuota),           -- RESTRICT

    CONSTRAINT FK_PAGO_REGISTRADO
        FOREIGN KEY (id_registrado_por)
        REFERENCES USUARIOS(id_usuario),

    CONSTRAINT CHK_PAGO_VALOR
        CHECK (valor_pagado > 0),

    CONSTRAINT CHK_PAGO_METODO
        CHECK (metodo_pago IN (
            'EFECTIVO','TRANSFERENCIA','CHEQUE',
            'TARJETA','CONSIGNACION','OTRO'
        ))
);

COMMENT ON TABLE  PAGOS                    IS 'Transacciones de pago. Una cuota puede tener m?ltiples pagos (abonos parciales).';
COMMENT ON COLUMN PAGOS.id_cuota          IS 'FK a la cuota pagada. RESTRICT: no se puede borrar una cuota con pagos registrados.';
COMMENT ON COLUMN PAGOS.fecha_pago        IS 'Fecha efectiva del pago (puede diferir de fecha_registro si se ingres? tarde).';
COMMENT ON COLUMN PAGOS.valor_pagado      IS 'Monto pagado en esta transacci?n (COP). Puede ser parcial.';
COMMENT ON COLUMN PAGOS.metodo_pago       IS 'EFECTIVO | TRANSFERENCIA | CHEQUE | TARJETA | CONSIGNACION | OTRO';
COMMENT ON COLUMN PAGOS.comprobante_url   IS 'URL al soporte digital del pago (imagen de consignaci?n, captura de transferencia).';
COMMENT ON COLUMN PAGOS.referencia        IS 'N?mero de transacci?n bancaria, recibo o comprobante f?sico.';
COMMENT ON COLUMN PAGOS.notas            IS 'Observaciones del cajero o del residente sobre este pago.';
COMMENT ON COLUMN PAGOS.fecha_registro   IS 'Timestamp de ingreso del pago al sistema. Inmutable.';
COMMENT ON COLUMN PAGOS.id_registrado_por IS 'Usuario que registr? el pago. Auditor?a.';

-- -------------------------------------------------------------------
-- TABLA 11: ALERTAS_PAGO
-- Historial auditable de notificaciones enviadas sobre cuotas.
-- Controla re-env?os y audita qu? alertas ley? el residente.
-- La unicidad diaria (id_cuota, tipo, canal, d?a) se garantiza con
-- el ?ndice funcional UIX_ALERTA_CUOTA_DIA en ?6.
-- -------------------------------------------------------------------
CREATE TABLE ALERTAS_PAGO (
    id_alerta     NUMBER              DEFAULT SEC_ALERTAS_PAGO.NEXTVAL NOT NULL
                                      CONSTRAINT PK_ALERTA PRIMARY KEY,
    id_cuota      NUMBER              NOT NULL,
    tipo_alerta   VARCHAR2(25  CHAR)  NOT NULL,
    canal         VARCHAR2(20  CHAR)  DEFAULT 'SISTEMA' NOT NULL,
    leida         NUMBER(1)           DEFAULT 0 NOT NULL,
    enviada_en    TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    leida_en      TIMESTAMP,

    CONSTRAINT FK_ALERTA_CUOTA
        FOREIGN KEY (id_cuota)
        REFERENCES CUOTAS_ARRIENDO(id_cuota)
        ON DELETE CASCADE,

    CONSTRAINT CHK_ALERTA_TIPO
        CHECK (tipo_alerta IN ('PROXIMO_VENCIMIENTO','VENCIDA','EN_MORA')),

    CONSTRAINT CHK_ALERTA_CANAL
        CHECK (canal IN ('SISTEMA','EMAIL','SMS','WHATSAPP')),

    CONSTRAINT CHK_ALERTA_LEIDA
        CHECK (leida IN (0, 1)),

    CONSTRAINT CHK_ALERTA_LEIDA_EN
        CHECK ((leida = 0 AND leida_en IS NULL) OR (leida = 1 AND leida_en IS NOT NULL))
    -- Unicidad diaria implementada como UIX_ALERTA_CUOTA_DIA en ?6.
    -- Oracle no permite TRUNC() dentro de UNIQUE constraints (ORA-00904).
);

COMMENT ON TABLE  ALERTAS_PAGO             IS 'Historial de notificaciones enviadas sobre cuotas. Evita duplicados diarios (UIX_ALERTA_CUOTA_DIA).';
COMMENT ON COLUMN ALERTAS_PAGO.tipo_alerta IS 'PROXIMO_VENCIMIENTO | VENCIDA | EN_MORA';
COMMENT ON COLUMN ALERTAS_PAGO.canal       IS 'SISTEMA | EMAIL | SMS | WHATSAPP';
COMMENT ON COLUMN ALERTAS_PAGO.leida       IS '0 = no le?da | 1 = le?da por el residente. CHK_ALERTA_LEIDA_EN obliga leida_en IS NOT NULL cuando leida=1.';
COMMENT ON COLUMN ALERTAS_PAGO.enviada_en  IS 'Timestamp en que se gener? y envi? la notificaci?n. Usado por UIX_ALERTA_CUOTA_DIA.';
COMMENT ON COLUMN ALERTAS_PAGO.leida_en    IS 'Timestamp en que el residente marc? la alerta como le?da. NULL si a?n no le?da.';

-- ??????????????????????????????????????????????????????????????????
-- ?4.5  MULTAS ? Sanciones por infracciones
-- ??????????????????????????????????????????????????????????????????

-- NOTA: SEC_MULTAS consolidado en ?0.5

CREATE TABLE MULTAS (
    id_multa           NUMBER              DEFAULT SEC_MULTAS.NEXTVAL NOT NULL
                                           CONSTRAINT PK_MULTA PRIMARY KEY,
    id_apartamento     NUMBER              NOT NULL,
    id_mensaje         NUMBER,                            -- FK opcional a BUZON (aviso de ruido)
    tipo               VARCHAR2(20  CHAR)  NOT NULL,      -- RUIDO | PARQUEADERO
    monto              NUMBER(12,2)        NOT NULL,
    estado             VARCHAR2(20  CHAR)  DEFAULT 'PENDIENTE' NOT NULL,
    descripcion        VARCHAR2(2000 CHAR),
    foto_evidencia     CLOB,                               -- Foto Base64 del veh?culo mal estacionado (PARQUEADERO)
    fecha_creacion     TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    fecha_pago         TIMESTAMP,
    creado_por         NUMBER              NOT NULL,
    registrado_pago_por NUMBER,                                -- FK a USUARIOS: qui?n registr? el pago
    metodo_pago         VARCHAR2(20  CHAR),                    -- m?todo de pago de la multa

    CONSTRAINT CHK_MULTA_TIPO
        CHECK (tipo IN ('RUIDO','PARQUEADERO')),

    CONSTRAINT CHK_MULTA_ESTADO
        CHECK (estado IN ('PENDIENTE','PAGADA','ANULADA')),

    CONSTRAINT CHK_MULTA_MONTO
        CHECK (monto > 0),

    CONSTRAINT FK_MULTA_APARTAMENTO
        FOREIGN KEY (id_apartamento)
        REFERENCES APARTAMENTOS(id_apartamento),

    -- FK_MULTA_MENSAJE (? BUZON) agregada v?a ALTER TABLE en ?5
    -- porque BUZON se crea despu?s de MULTAS (depende de VISITAS ?5).

    CONSTRAINT FK_MULTA_CREADOR
        FOREIGN KEY (creado_por)
        REFERENCES USUARIOS(id_usuario),

    CONSTRAINT FK_MULTA_REGISTRADO_PAGO
        FOREIGN KEY (registrado_pago_por)
        REFERENCES USUARIOS(id_usuario),

    CONSTRAINT CHK_MULTA_METODO_PAGO
        CHECK (metodo_pago IS NULL OR metodo_pago IN (
            'EFECTIVO','TRANSFERENCIA','CHEQUE',
            'TARJETA','CONSIGNACION','OTRO'
        ))
);

COMMENT ON TABLE  MULTAS                   IS 'Multas por ruido o mal uso de parqueadero. Generadas por el portero.';
COMMENT ON COLUMN MULTAS.id_apartamento     IS 'Apartamento sancionado.';
COMMENT ON COLUMN MULTAS.id_mensaje         IS 'FK al aviso de ruido en BUZON que origin? la multa (solo RUIDO).';
COMMENT ON COLUMN MULTAS.tipo               IS 'RUIDO | PARQUEADERO';
COMMENT ON COLUMN MULTAS.monto              IS 'Valor de la multa.';
COMMENT ON COLUMN MULTAS.estado             IS 'PENDIENTE | PAGADA | ANULADA';
COMMENT ON COLUMN MULTAS.fecha_creacion     IS 'Fecha y hora de generaci?n.';
COMMENT ON COLUMN MULTAS.fecha_pago         IS 'Fecha y hora de pago. NULL si PENDIENTE.';
COMMENT ON COLUMN MULTAS.creado_por         IS 'Usuario (portero) que gener? la multa.';
COMMENT ON COLUMN MULTAS.registrado_pago_por IS 'Usuario que registr? el pago de la multa. FK a USUARIOS.';
COMMENT ON COLUMN MULTAS.metodo_pago         IS 'M?todo de pago: EFECTIVO | TRANSFERENCIA | CHEQUE | TARJETA | CONSIGNACION | OTRO.';

-- ?ndice para b?squeda por apartamento
CREATE INDEX IX_MULTAS_APARTAMENTO ON MULTAS(id_apartamento);
CREATE INDEX IX_MULTAS_ESTADO ON MULTAS(estado);

-- ??????????????????????????????????????????????????????????????????
-- ?5  M?DULO DE VISITAS ? CORE DEL SISTEMA
-- ??????????????????????????????????????????????????????????????????

-- -------------------------------------------------------------------
-- TABLA 12: VISITAS
-- Autorizaci?n de visita creada por el residente.
-- CR?TICO: se conecta con CONTRATO_RESIDENTE (no directamente con
-- CONTRATOS). Esto garantiza que el residente tiene una relaci?n
-- activa con un contrato vigente al autorizar la visita.
-- Tambi?n mantiene FK a RESIDENTES para consultas r?pidas.
-- tiempo_validez_min: m?ximo 60 min (antes era 1440 en v3 ?
-- reducido a 1 hora para reforzar la seguridad del acceso).
-- -------------------------------------------------------------------
CREATE TABLE VISITAS (
    id_visita           NUMBER              DEFAULT SEC_VISITAS.NEXTVAL NOT NULL
                                            CONSTRAINT PK_VIS PRIMARY KEY,
    id_contrato_res     NUMBER              NOT NULL,
    id_residente        NUMBER              NOT NULL,   -- desnorm. controlada: agiliza consultas
    tiempo_validez_min  NUMBER(3)           DEFAULT 30 NOT NULL,
    cantidad_personas   NUMBER(3)           DEFAULT 1  NOT NULL,   -- grupo completo (titular incluido)
    notas               VARCHAR2(500 CHAR),
    estado              VARCHAR2(15  CHAR)  DEFAULT 'PENDIENTE' NOT NULL,
    fecha_registro      TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    actualizado_en      TIMESTAMP,

    CONSTRAINT FK_VIS_CONTRATO_RES
        FOREIGN KEY (id_contrato_res)
        REFERENCES CONTRATO_RESIDENTE(id_contrato_res), -- RESTRICT

    CONSTRAINT FK_VIS_RESIDENTE
        FOREIGN KEY (id_residente)
        REFERENCES RESIDENTES(id_residente),            -- RESTRICT

    CONSTRAINT CHK_VIS_TIEMPO
        CHECK (tiempo_validez_min BETWEEN 5 AND 60),

    CONSTRAINT CHK_VIS_CANTIDAD
        CHECK (cantidad_personas BETWEEN 1 AND 99),

    CONSTRAINT CHK_VIS_ESTADO
        CHECK (estado IN ('PENDIENTE','ACTIVA','FINALIZADA','CANCELADA','EXPIRADA'))
);

COMMENT ON TABLE  VISITAS                   IS 'Autorizaci?n de visita registrada por el residente. Base del QR de acceso.';
COMMENT ON COLUMN VISITAS.id_contrato_res   IS 'FK a CONTRATO_RESIDENTE. Garantiza que el residente tiene relaci?n activa con un contrato.';
COMMENT ON COLUMN VISITAS.id_residente      IS 'Desnormalizaci?n controlada de CONTRATO_RESIDENTE.id_residente para consultas r?pidas.';
COMMENT ON COLUMN VISITAS.tiempo_validez_min IS 'Minutos de validez del QR desde su generaci?n. M?nimo 5, m?ximo 60.';
COMMENT ON COLUMN VISITAS.cantidad_personas  IS 'Total de personas del grupo (incluye al visitante titular). Usado en el formulario r?pido de visitas frecuentes.';
COMMENT ON COLUMN VISITAS.estado            IS 'PENDIENTE ? ACTIVA (QR usado) ? FINALIZADA (salida) | CANCELADA | EXPIRADA';
COMMENT ON COLUMN VISITAS.actualizado_en    IS 'Timestamp del ?ltimo UPDATE. Mantenido por TRG_VISITAS_UPD.';

-- -------------------------------------------------------------------
-- TABLA 13: QR_ACCESOS  (relaci?n 1:1 con VISITAS)
-- Token QR de un solo uso. id_vigilante_uso registra qu? portero
-- escane? el QR (trazabilidad del escaneo).
-- -------------------------------------------------------------------
CREATE TABLE QR_ACCESOS (
    id_qr               NUMBER              DEFAULT SEC_QR_ACCESOS.NEXTVAL NOT NULL
                                            CONSTRAINT PK_QR PRIMARY KEY,
    id_visita           NUMBER              NOT NULL,
    id_vigilante_uso    NUMBER,                         -- NULL hasta que se escanee
    codigo_qr           VARCHAR2(255 CHAR)  NOT NULL,   -- UUID v4 generado en Java (ZXing)
    fecha_generacion    TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    fecha_expiracion    TIMESTAMP           NOT NULL,
    usado               NUMBER(1)           DEFAULT 0 NOT NULL,
    fecha_uso           TIMESTAMP,

    CONSTRAINT UQ_QR_VISITA
        UNIQUE (id_visita),                             -- garantiza 1:1 con VISITAS

    CONSTRAINT UQ_QR_CODIGO
        UNIQUE (codigo_qr),                             -- token ?nico global

    CONSTRAINT FK_QR_VISITA
        FOREIGN KEY (id_visita)
        REFERENCES VISITAS(id_visita)
        ON DELETE CASCADE,

    CONSTRAINT FK_QR_VIGILANTE
        FOREIGN KEY (id_vigilante_uso)
        REFERENCES USUARIOS(id_usuario)
        ON DELETE SET NULL,

    CONSTRAINT CHK_QR_EXPIRACION
        CHECK (fecha_expiracion > fecha_generacion),

    CONSTRAINT CHK_QR_USADO
        CHECK (usado IN (0, 1)),

    CONSTRAINT CHK_QR_FECHA_USO
        CHECK (
            (usado = 0 AND fecha_uso IS NULL) OR
            (usado = 1 AND fecha_uso IS NOT NULL)
        )
);

COMMENT ON TABLE  QR_ACCESOS                  IS 'Token QR de un solo uso por visita. Relaci?n 1:1 con VISITAS (UQ_QR_VISITA).';
COMMENT ON COLUMN QR_ACCESOS.codigo_qr        IS 'UUID v4 generado en la capa Service Java. ?nico global.';
COMMENT ON COLUMN QR_ACCESOS.usado            IS '0 = disponible; 1 = consumido. Estado irreversible.';
COMMENT ON COLUMN QR_ACCESOS.id_vigilante_uso IS 'Portero que escane? el QR. NULL hasta el momento del escaneo.';

-- -------------------------------------------------------------------
-- TABLA 14: VISITANTES
-- Personas externas que visitan el edificio.
-- Registro reutilizable: si la misma persona visita varias veces,
-- su registro ya existe y se reutiliza en REGISTRO_VISITA.
-- Reemplaza PERSONAS_VISITA (que duplicaba al visitante por visita).
-- -------------------------------------------------------------------
CREATE TABLE VISITANTES (
    id_visitante      NUMBER              DEFAULT SEC_VISITANTES.NEXTVAL NOT NULL
                                          CONSTRAINT PK_VISIT PRIMARY KEY,
    id_tipo_doc       NUMBER              NOT NULL,
    numero_documento  VARCHAR2(20  CHAR)  NOT NULL,
    nombres           VARCHAR2(25  CHAR)  NOT NULL,
    apellidos         VARCHAR2(25  CHAR)  NOT NULL,
    telefono          VARCHAR2(20  CHAR),
    email             VARCHAR2(40  CHAR),
    activo            NUMBER(1)           DEFAULT 1 NOT NULL,
    foto_url          VARCHAR2(2000 CHAR),              -- Foto de identificaci?n del visitante (opcional)
    doc_pdf_url       VARCHAR2(2000 CHAR),
    foto_doc          CLOB,                            -- Foto del documento de identidad en Base64 (data:image/...;base64,...). Para verificaci?n del portero.
    fecha_registro    TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    actualizado_en    TIMESTAMP,

    CONSTRAINT CHK_VIS_ACTIVO
        CHECK (activo IN (0, 1)),

    CONSTRAINT UQ_VIS_DOCUMENTO
        UNIQUE (id_tipo_doc, numero_documento),

    CONSTRAINT UQ_VIS_EMAIL
        UNIQUE (email),

    CONSTRAINT FK_VIS_TIPO_DOC
        FOREIGN KEY (id_tipo_doc)
        REFERENCES TIPOS_DOCUMENTO(id_tipo_doc)         -- RESTRICT
);

COMMENT ON TABLE  VISITANTES                  IS 'Personas externas reutilizables. Un mismo visitante no se duplica entre visitas.';
COMMENT ON COLUMN VISITANTES.nombres          IS 'Nombre(s) del visitante. M?x 100 caracteres (CHAR).';
COMMENT ON COLUMN VISITANTES.apellidos        IS 'Apellido(s) del visitante. M?x 100 caracteres (CHAR).';
COMMENT ON COLUMN VISITANTES.telefono         IS 'Tel?fono de contacto del visitante. Nullable.';
COMMENT ON COLUMN VISITANTES.email            IS 'Correo electr?nico para env?o del c?digo QR. Nullable. M?x 254 chars (RFC 5321).';
COMMENT ON COLUMN VISITANTES.activo           IS '1=activo | 0=soft-delete. Controlado por la aplicaci?n.';
COMMENT ON COLUMN VISITANTES.foto_url         IS 'URL a la foto de identificaci?n del visitante. NULL si no se ha cargado.';
COMMENT ON COLUMN VISITANTES.doc_pdf_url      IS 'URL al PDF del documento de identidad del visitante. NULL si no se ha cargado.';
COMMENT ON COLUMN VISITANTES.foto_doc         IS 'Foto del documento de identidad en Base64 (data:image/...;base64,...). Mostrada al portero al escanear el QR.';
COMMENT ON COLUMN VISITANTES.actualizado_en   IS 'Timestamp del ?ltimo UPDATE. Mantenido por TRG_VISITANTES_UPD.';

-- -------------------------------------------------------------------
-- TABLA 15: VEHICULOS_VISITA
-- Veh?culos asociados a una visita. El parqueadero se asigna al
-- llegar y se libera al registrar salida (TRG_ACCESO_SALIDA).
-- Una visita puede tener 0 (todos a pie) o varios veh?culos.
-- -------------------------------------------------------------------
CREATE TABLE VEHICULOS_VISITA (
    id_vehiculo_visita NUMBER              DEFAULT SEC_VEHICULOS_VISITA.NEXTVAL NOT NULL
                                           CONSTRAINT PK_VEH PRIMARY KEY,
    id_visita          NUMBER              NOT NULL,
    id_parqueadero     NUMBER,                          -- NULL = no requiere parqueadero
    placa              VARCHAR2(10  CHAR)  NOT NULL,
    tipo               VARCHAR2(15  CHAR)  NOT NULL,
    descripcion_tipo   VARCHAR2(60  CHAR),             -- obligatorio cuando tipo = 'OTRO'
    color              VARCHAR2(15  CHAR),
    marca              VARCHAR2(15  CHAR),
    hora_entrada       TIMESTAMP           DEFAULT SYSTIMESTAMP,
    hora_salida        TIMESTAMP,
    actualizado_en  TIMESTAMP,

    CONSTRAINT UQ_VEH_PLACA_VISITA
        UNIQUE (id_visita, placa),

    CONSTRAINT FK_VEH_VISITA
        FOREIGN KEY (id_visita)
        REFERENCES VISITAS(id_visita)
        ON DELETE CASCADE,

    CONSTRAINT FK_VEH_PARQUEADERO
        FOREIGN KEY (id_parqueadero)
        REFERENCES PARQUEADEROS(id_parqueadero)
        ON DELETE SET NULL,

    CONSTRAINT CHK_VEH_TIPO
        CHECK (tipo IN ('VEHICULO','MOTO','BICICLETA','OTRO')),

    CONSTRAINT CHK_VEH_PLACA_FMT
        CHECK (placa = UPPER(TRIM(placa))),

    CONSTRAINT CHK_VEH_HORAS
        CHECK (hora_salida IS NULL OR hora_salida > hora_entrada),

    CONSTRAINT CHK_VEH_DESC_TIPO
        CHECK (tipo != 'OTRO' OR descripcion_tipo IS NOT NULL)
        -- Si tipo = 'OTRO' (ej: moto el?ctrica), descripcion_tipo es obligatorio
);

COMMENT ON TABLE  VEHICULOS_VISITA                IS 'Veh?culos de visitantes. Pueden ser 0 (a pie) o varios por visita.';
COMMENT ON COLUMN VEHICULOS_VISITA.placa          IS 'Placa en may?sculas sin espacios. Formato colombiano o extranjero.';
COMMENT ON COLUMN VEHICULOS_VISITA.descripcion_tipo IS 'Descripci?n del veh?culo cuando tipo = ''OTRO'' (ej: moto el?ctrica, patineta). CHK_VEH_DESC_TIPO lo exige.';
COMMENT ON COLUMN VEHICULOS_VISITA.id_parqueadero IS 'NULL = visitante lleg? a pie o en transporte p?blico.';
COMMENT ON COLUMN VEHICULOS_VISITA.actualizado_en IS 'Timestamp del ?ltimo UPDATE. Mantenido por TRG_VEHICULOS_UPD.';

-- -------------------------------------------------------------------
-- TABLA 16: REGISTRO_VISITA  (tabla puente N:M)
-- Conecta VISITAS con VISITANTES (N:M reutilizable).
-- id_vehiculo_visita nullable reemplaza a PERSONAS_VEHICULO:
--   ? NULL                    ? el visitante lleg? a pie
--   ? con valor               ? indica en qu? veh?culo viaj?
-- Casos que modela:
--   A: 5 personas en 1 carro ? 5 filas, mismo id_vehiculo_visita
--   B: 6 personas en 3 motos ? 2 filas por cada id_vehiculo_visita
--   C: persona a pie         ? id_vehiculo_visita = NULL
--   D: 1 persona por moto    ? 1 fila con su propio id_vehiculo_visita
-- -------------------------------------------------------------------
CREATE TABLE REGISTRO_VISITA (
    id_registro_visita  NUMBER              DEFAULT SEC_REGISTRO_VISITA.NEXTVAL NOT NULL
                                            CONSTRAINT PK_REGVIS PRIMARY KEY,
    id_visita           NUMBER              NOT NULL,
    id_visitante        NUMBER              NOT NULL,
    id_vehiculo_visita  NUMBER,                         -- NULL = lleg? a pie
    es_titular          NUMBER(1)           DEFAULT 0 NOT NULL,

    CONSTRAINT UQ_REGVIS_VIS_VIT
        UNIQUE (id_visita, id_visitante),               -- un visitante por visita

    CONSTRAINT FK_REGVIS_VISITA
        FOREIGN KEY (id_visita)
        REFERENCES VISITAS(id_visita)
        ON DELETE CASCADE,

    CONSTRAINT FK_REGVIS_VISITANTE
        FOREIGN KEY (id_visitante)
        REFERENCES VISITANTES(id_visitante),            -- RESTRICT

    CONSTRAINT FK_REGVIS_VEHICULO
        FOREIGN KEY (id_vehiculo_visita)
        REFERENCES VEHICULOS_VISITA(id_vehiculo_visita)
        ON DELETE SET NULL,

    CONSTRAINT CHK_REGVIS_TITULAR
        CHECK (es_titular IN (0, 1))
);

COMMENT ON TABLE  REGISTRO_VISITA               IS 'N:M reutilizable entre VISITAS y VISITANTES. id_vehiculo_visita nullable (NULL = a pie).';
COMMENT ON COLUMN REGISTRO_VISITA.es_titular    IS '1 = visitante principal que porta y es responsable del QR del grupo.';
COMMENT ON COLUMN REGISTRO_VISITA.id_vehiculo_visita IS 'NULL = lleg? a pie. Con valor = veh?culo en el que viaj?.';

-- -------------------------------------------------------------------
-- TABLA 17: FRECUENTES_RESIDENTE
-- Registro de visitantes frecuentes por residente.
-- Se popula autom?ticamente via TRG_AUTO_FRECUENTE cuando se crea
-- el primer REGISTRO_VISITA de un par (residente, visitante).
-- El residente puede "ocultar" un frecuente con activo = 0 (soft-
-- delete desde la app). Si el visitante regresa, el trigger lo reactiva.
-- -------------------------------------------------------------------
CREATE TABLE FRECUENTES_RESIDENTE (
    id_frecuente    NUMBER              DEFAULT SEC_FRECUENTES_RESIDENTE.NEXTVAL NOT NULL
                                        CONSTRAINT PK_FREC PRIMARY KEY,
    id_residente    NUMBER              NOT NULL,
    id_visitante    NUMBER              NOT NULL,
    activo          NUMBER(1)           DEFAULT 1 NOT NULL,
    fecha_registro  TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    actualizado_en  TIMESTAMP,

    CONSTRAINT UQ_FREC_RES_VIS
        UNIQUE (id_residente, id_visitante),

    CONSTRAINT FK_FREC_RESIDENTE
        FOREIGN KEY (id_residente)
        REFERENCES RESIDENTES(id_residente)
        ON DELETE CASCADE,

    CONSTRAINT FK_FREC_VISITANTE
        FOREIGN KEY (id_visitante)
        REFERENCES VISITANTES(id_visitante)
        ON DELETE CASCADE,

    CONSTRAINT CHK_FREC_ACTIVO
        CHECK (activo IN (0, 1))
);

COMMENT ON TABLE  FRECUENTES_RESIDENTE              IS 'Visitantes frecuentes por residente. Auto-poblado por TRG_AUTO_FRECUENTE. Soft-delete con activo=0.';
COMMENT ON COLUMN FRECUENTES_RESIDENTE.activo       IS '1 = visible en el panel de frecuentes | 0 = oculto por el residente. TRG_AUTO_FRECUENTE lo reactiva si el visitante regresa.';
COMMENT ON COLUMN FRECUENTES_RESIDENTE.actualizado_en IS 'Timestamp del ?ltimo UPDATE. Mantenido por TRG_FRECUENTES_UPD.';

-- -------------------------------------------------------------------
-- TABLA 18: REGISTROS_ACCESO
-- Entrada/salida f?sica del visitante. Relaci?n 1:1 con VISITAS.
-- Separado de QR_ACCESOS porque tienen ciclos de vida distintos:
--   QR_ACCESOS      = token y su validaci?n (irreversible)
--   REGISTROS_ACCESO = presencia f?sica (hora_salida actualizable)
-- -------------------------------------------------------------------
CREATE TABLE REGISTROS_ACCESO (
    id_acceso       NUMBER              DEFAULT SEC_REGISTROS_ACCESO.NEXTVAL NOT NULL
                                        CONSTRAINT PK_ACCESO PRIMARY KEY,
    id_visita       NUMBER              NOT NULL,
    id_vigilante    NUMBER              NOT NULL,
    hora_entrada    TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    hora_salida     TIMESTAMP,
    observaciones   VARCHAR2(500 CHAR),
    fecha_registro  TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    actualizado_en  TIMESTAMP,

    CONSTRAINT UQ_ACCESO_VISITA
        UNIQUE (id_visita),                             -- garantiza 1:1 con VISITAS

    CONSTRAINT FK_ACCESO_VISITA
        FOREIGN KEY (id_visita)
        REFERENCES VISITAS(id_visita),                  -- RESTRICT

    CONSTRAINT FK_ACCESO_VIGILANTE
        FOREIGN KEY (id_vigilante)
        REFERENCES USUARIOS(id_usuario),

    CONSTRAINT CHK_ACCESO_HORAS
        CHECK (hora_salida IS NULL OR hora_salida > hora_entrada)
);

COMMENT ON TABLE  REGISTROS_ACCESO              IS 'Entrada/salida f?sica del visitante. 1:1 con VISITAS. Separado del QR.';
COMMENT ON COLUMN REGISTROS_ACCESO.id_vigilante IS 'Portero que autoriz? el ingreso. Puede diferir de QR_ACCESOS.id_vigilante_uso.';
COMMENT ON COLUMN REGISTROS_ACCESO.hora_entrada IS 'Timestamp de ingreso f?sico al edificio. Inmutable.';
COMMENT ON COLUMN REGISTROS_ACCESO.hora_salida  IS 'NULL = visitante a?n dentro. Al actualizarse dispara TRG_ACCESO_SALIDA.';
COMMENT ON COLUMN REGISTROS_ACCESO.observaciones IS 'Notas del portero sobre el ingreso (equipaje, incidencias, sin QR f?sico).';
COMMENT ON COLUMN REGISTROS_ACCESO.fecha_registro IS 'Timestamp de creaci?n del registro. Igual a hora_entrada en condiciones normales.';
COMMENT ON COLUMN REGISTROS_ACCESO.actualizado_en IS 'Timestamp del ?ltimo UPDATE. Mantenido por TRG_ACCESO_UPD.';

-- ??????????????????????????????????????????????????????????????????
-- BUZON ? Notificaciones, paquetes y confirmaci?n de visitas
-- ??????????????????????????????????????????????????????????????????

CREATE TABLE BUZON (
    id_mensaje          NUMBER              DEFAULT SEC_BUZON.NEXTVAL NOT NULL
                                            CONSTRAINT PK_BUZON PRIMARY KEY,
    id_apartamento      NUMBER,                          -- NULL = aviso general (admin)
    id_visita           NUMBER,                          -- Solo para CONFIRMAR_VISITA
    tipo                VARCHAR2(20  CHAR)  NOT NULL,
    titulo              VARCHAR2(200 CHAR)  NOT NULL,
    cuerpo              VARCHAR2(2000 CHAR),
    foto_captura        CLOB,                            -- Base64 foto tomada por el portero
    leido               NUMBER(1)           DEFAULT 0 NOT NULL,
    leido_en            TIMESTAMP,
    entregado           NUMBER(1)           DEFAULT 0 NOT NULL,  -- Solo PAQUETE: el residente retir? el paquete
    entregado_en        TIMESTAMP,
    confirmado          NUMBER(1),                       -- Solo CONFIRMAR_VISITA: 1=aceptado, 0=rechazado, NULL=pendiente
    confirmado_en       TIMESTAMP,
    creado_por          NUMBER              NOT NULL,
    fecha_creacion      TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT CHK_BUZON_TIPO
        CHECK (tipo IN ('PAQUETE','AVISO','CONFIRMAR_VISITA','QUEJA_RUIDO')),

    CONSTRAINT CHK_BUZON_LEIDO
        CHECK (leido IN (0, 1)),

    CONSTRAINT CHK_BUZON_ENTREGADO
        CHECK (entregado IN (0, 1)),

    CONSTRAINT CHK_BUZON_CONFIRMADO
        CHECK (confirmado IN (0, 1) OR confirmado IS NULL),

    CONSTRAINT CHK_BUZON_LEIDO_EN
        CHECK ((leido = 0 AND leido_en IS NULL) OR (leido = 1 AND leido_en IS NOT NULL)),

    CONSTRAINT CHK_BUZON_ENTREGADO_EN
        CHECK ((entregado = 0 AND entregado_en IS NULL) OR (entregado = 1 AND entregado_en IS NOT NULL)),

    CONSTRAINT CHK_BUZON_CONFIRMADO_EN
        CHECK ((confirmado IS NULL AND confirmado_en IS NULL) OR (confirmado IS NOT NULL AND confirmado_en IS NOT NULL)),

    CONSTRAINT FK_BUZON_APARTAMENTO
        FOREIGN KEY (id_apartamento)
        REFERENCES APARTAMENTOS(id_apartamento)
        ON DELETE CASCADE,

    CONSTRAINT FK_BUZON_VISITA
        FOREIGN KEY (id_visita)
        REFERENCES VISITAS(id_visita)
        ON DELETE CASCADE,

    CONSTRAINT FK_BUZON_CREADOR
        FOREIGN KEY (creado_por)
        REFERENCES USUARIOS(id_usuario)
);

COMMENT ON TABLE  BUZON                       IS 'Buz?n de notificaciones: avisos generales, paquetes en porter?a y solicitudes de confirmaci?n de visita.';
COMMENT ON COLUMN BUZON.id_apartamento         IS 'Apartamento destino. NULL = aviso general para todos.';
COMMENT ON COLUMN BUZON.id_visita              IS 'FK a VISITAS cuando el mensaje es una solicitud de confirmaci?n.';
COMMENT ON COLUMN BUZON.tipo                   IS 'PAQUETE | AVISO | CONFIRMAR_VISITA | QUEJA_RUIDO';
COMMENT ON COLUMN BUZON.titulo                 IS 'Asunto visible del mensaje.';
COMMENT ON COLUMN BUZON.cuerpo                 IS 'Cuerpo del mensaje. Para PAQUETE: descripci?n del paquete/domicilio.';
COMMENT ON COLUMN BUZON.foto_captura           IS 'Foto capturada por el portero (Base64). Solo para CONFIRMAR_VISITA.';
COMMENT ON COLUMN BUZON.leido                  IS '1 = el residente ley? el mensaje.';
COMMENT ON COLUMN BUZON.entregado              IS '1 = el paquete fue retirado por el residente. Solo PAQUETE.';
COMMENT ON COLUMN BUZON.confirmado             IS '1 = residente confirm? la visita | 0 = rechaz? | NULL = pendiente. Solo CONFIRMAR_VISITA.';
COMMENT ON COLUMN BUZON.creado_por             IS 'Usuario que cre? el mensaje (portero o admin).';

-- ??????????????????????????????????????????????????????????????????
-- ?6  ?NDICES DE RENDIMIENTO
--     Las columnas ya cubiertas por UNIQUE constraints (Oracle crea
--     ?ndice impl?cito) NO se duplican aqu?:
--       TIPOS_DOCUMENTO.codigo
--       APARTAMENTOS.numero
--       PARQUEADEROS.codigo, id_apartamento
--       RESIDENTES(id_tipo_doc, numero_documento), email
--       TUTORES(id_tipo_doc, numero_documento)
--       USUARIOS.username, id_residente
--       CONTRATOS ? UIX_CONT_APT_ACTIVO (funcional)
--       CONTRATO_RESIDENTE(id_contrato, id_residente)
--       CUOTAS_ARRIENDO(id_contrato, anio, mes)
--       QR_ACCESOS.id_visita, codigo_qr
--       VISITANTES(id_tipo_doc, numero_documento)
--       VEHICULOS_VISITA(id_visita, placa)
--       REGISTRO_VISITA(id_visita, id_visitante)
--       REGISTROS_ACCESO.id_visita
-- ??????????????????????????????????????????????????????????????????

-- ?? PARQUEADEROS ??????????????????????????????????????????????????
CREATE INDEX IDX_PARQ_TIPO_ESTADO     ON PARQUEADEROS       (tipo, estado);

-- ?? RESIDENTES ????????????????????????????????????????????????????
CREATE INDEX IDX_RES_TIPO_DOC         ON RESIDENTES         (id_tipo_doc);
CREATE INDEX IDX_RES_ACTIVO           ON RESIDENTES         (activo);

-- ?? TUTORES ???????????????????????????????????????????????????????
CREATE INDEX IDX_TUTOR_RESIDENTE      ON TUTORES            (id_residente_menor);
CREATE INDEX IDX_TUTOR_TIPO_DOC       ON TUTORES            (id_tipo_doc);

-- ?? USUARIOS ??????????????????????????????????????????????????????
CREATE INDEX IDX_USR_ROL              ON USUARIOS           (rol);

-- ?? CONTRATOS ?????????????????????????????????????????????????????
CREATE INDEX IDX_CONT_APARTAMENTO     ON CONTRATOS          (id_apartamento);
CREATE INDEX IDX_CONT_TUTOR           ON CONTRATOS          (id_tutor);
CREATE INDEX IDX_CONT_ESTADO          ON CONTRATOS          (estado);
CREATE INDEX IDX_CONT_FECHA_FIN       ON CONTRATOS          (fecha_fin);
CREATE INDEX IDX_CONTRATOS_REGISTRADO_POR ON CONTRATOS      (id_registrado_por);

-- ?? MULTAS ????????????????????????????????????????????????????????
CREATE INDEX IDX_MULTAS_APARTAMENTO   ON MULTAS             (id_apartamento);
CREATE INDEX IDX_MULTAS_CREADO_POR    ON MULTAS             (creado_por);
CREATE INDEX IDX_MULTAS_MENSAJE       ON MULTAS             (id_mensaje);
CREATE INDEX IDX_MULTAS_PAGO_POR      ON MULTAS             (registrado_pago_por);

-- ?? CONTRATO_RESIDENTE ????????????????????????????????????????????
CREATE INDEX IDX_CONTRES_CONTRATO     ON CONTRATO_RESIDENTE (id_contrato);
CREATE INDEX IDX_CONTRES_RESIDENTE    ON CONTRATO_RESIDENTE (id_residente);

-- ?? CUOTAS_ARRIENDO ???????????????????????????????????????????????
CREATE INDEX IDX_CUOTA_CONTRATO       ON CUOTAS_ARRIENDO    (id_contrato);
CREATE INDEX IDX_CUOTA_ESTADO         ON CUOTAS_ARRIENDO    (estado);
CREATE INDEX IDX_CUOTA_FECHA_LIM      ON CUOTAS_ARRIENDO    (fecha_limite);

-- ?? PAGOS ?????????????????????????????????????????????????????????
CREATE INDEX IDX_PAGO_CUOTA           ON PAGOS              (id_cuota);
CREATE INDEX IDX_PAGO_FECHA           ON PAGOS              (fecha_pago);
CREATE INDEX IDX_PAGO_REGISTRADO      ON PAGOS              (id_registrado_por);

-- ?? ALERTAS_PAGO ??????????????????????????????????????????????????
CREATE INDEX IDX_ALERTA_CUOTA         ON ALERTAS_PAGO       (id_cuota);
CREATE INDEX IDX_ALERTA_LEIDA         ON ALERTAS_PAGO       (leida);
-- Evita enviar la misma alerta m?s de una vez por d?a (TRUNC no v?lido en UNIQUE constraint):
CREATE UNIQUE INDEX UIX_ALERTA_CUOTA_DIA ON ALERTAS_PAGO (
    id_cuota, tipo_alerta, canal, TRUNC(enviada_en)
);

-- ?? VISITAS ???????????????????????????????????????????????????????
CREATE INDEX IDX_VIS_CONTRATO_RES     ON VISITAS            (id_contrato_res);
CREATE INDEX IDX_VIS_RESIDENTE        ON VISITAS            (id_residente);
CREATE INDEX IDX_VIS_ESTADO           ON VISITAS            (estado);

-- ?? QR_ACCESOS ????????????????????????????????????????????????????
-- UQ_QR_VISITA y UQ_QR_CODIGO ya crean ?ndices impl?citos.
CREATE INDEX IDX_QR_EXPIRACION        ON QR_ACCESOS         (fecha_expiracion);
CREATE INDEX IDX_QR_VIGILANTE         ON QR_ACCESOS         (id_vigilante_uso);

-- ?? VISITANTES ????????????????????????????????????????????????????
CREATE INDEX IDX_VISITANTES_TIPO_DOC  ON VISITANTES         (id_tipo_doc);
CREATE INDEX IDX_VISITANTES_DOC       ON VISITANTES         (numero_documento);
CREATE INDEX IDX_VISITANTES_ACTIVO    ON VISITANTES         (activo);

-- ?? VEHICULOS_VISITA ??????????????????????????????????????????????
CREATE INDEX IDX_VEH_VISITA           ON VEHICULOS_VISITA   (id_visita);
CREATE INDEX IDX_VEH_PARQUEADERO      ON VEHICULOS_VISITA   (id_parqueadero);
CREATE INDEX IDX_VEH_PLACA            ON VEHICULOS_VISITA   (placa);

-- ?? REGISTRO_VISITA ???????????????????????????????????????????????
CREATE INDEX IDX_REGVIS_VISITA        ON REGISTRO_VISITA    (id_visita);
CREATE INDEX IDX_REGVIS_VISITANTE     ON REGISTRO_VISITA    (id_visitante);
CREATE INDEX IDX_REGVIS_VEHICULO      ON REGISTRO_VISITA    (id_vehiculo_visita);
-- 1 solo titular por visita (funci?n CASE: NULL cuando no es titular ? no colisiona)
CREATE UNIQUE INDEX UIX_REGVIS_TITULAR ON REGISTRO_VISITA (
    CASE WHEN es_titular = 1 THEN id_visita ELSE NULL END
);

-- ?? FRECUENTES_RESIDENTE ??????????????????????????????????????????
-- UQ_FREC_RES_VIS crea ?ndice impl?cito en (id_residente, id_visitante).
-- IDX_FREC_RESIDENTE eliminado en v4.2: redundante con la columna l?der
-- del ?ndice ?nico de UQ_FREC_RES_VIS.
CREATE INDEX IDX_FREC_VISITANTE       ON FRECUENTES_RESIDENTE (id_visitante);
CREATE INDEX IDX_FREC_ACTIVO          ON FRECUENTES_RESIDENTE (id_residente, activo);

-- ?? REGISTROS_ACCESO ??????????????????????????????????????????????
-- UQ_ACCESO_VISITA ya crea ?ndice impl?cito en id_visita.
CREATE INDEX IDX_ACCESO_VIGILANTE     ON REGISTROS_ACCESO   (id_vigilante);
CREATE INDEX IDX_ACCESO_ENTRADA       ON REGISTROS_ACCESO   (hora_entrada);

-- ?? BUZON ?????????????????????????????????????????????????????????????
CREATE INDEX IDX_BUZON_APARTAMENTO    ON BUZON              (id_apartamento);
CREATE INDEX IDX_BUZON_VISITA         ON BUZON              (id_visita);
CREATE INDEX IDX_BUZON_TIPO           ON BUZON              (tipo);
CREATE INDEX IDX_BUZON_PENDIENTES     ON BUZON              (id_apartamento, leido, fecha_creacion);
CREATE INDEX IDX_BUZON_CONFIRMAR      ON BUZON              (id_visita, confirmado);
CREATE INDEX IDX_BUZON_CREADO_POR     ON BUZON              (creado_por);

-- FK_MULTA_MENSAJE se agrega aqu? porque BUZON (creada en ?5) debe existir antes.
-- MULTAS la declara como comentario; la constraint real vive en este ALTER TABLE.
ALTER TABLE MULTAS ADD CONSTRAINT FK_MULTA_MENSAJE
    FOREIGN KEY (id_mensaje)
    REFERENCES BUZON(id_mensaje)
    ON DELETE SET NULL;

-- ??????????????????????????????????????????????????????????????????
-- ?5.2 TABLA QUEJAS_SUGERENCIAS (v5.0)
-- ??????????????????????????????????????????????????????????????????
-- NOTA: SEQ_QUEJAS_SUGERENCIAS consolidado en ?0.5

CREATE TABLE QUEJAS_SUGERENCIAS (
    id_queja            NUMBER          DEFAULT SEQ_QUEJAS_SUGERENCIAS.NEXTVAL NOT NULL,
    id_apartamento      NUMBER          NOT NULL,
    id_multa            NUMBER,
    tipo                VARCHAR2(15)    NOT NULL,
    categoria           VARCHAR2(30),
    titulo              VARCHAR2(200)   NOT NULL,
    descripcion         VARCHAR2(2000)  NOT NULL,
    foto_evidencia      CLOB,
    estado              VARCHAR2(20)    DEFAULT 'PENDIENTE',
    respuesta_admin     VARCHAR2(2000),
    prioridad           VARCHAR2(10)    DEFAULT 'MEDIA',
    fecha_creacion      TIMESTAMP       DEFAULT SYSTIMESTAMP,
    fecha_respuesta     TIMESTAMP,
    creado_por          NUMBER          NOT NULL,
    respondido_por      NUMBER,

    CONSTRAINT PK_QUEJA PRIMARY KEY (id_queja),
    
    CONSTRAINT CHK_QUEJA_TIPO 
        CHECK (tipo IN ('QUEJA','SUGERENCIA','APELACION')),
    
    CONSTRAINT CHK_QUEJA_ESTADO 
        CHECK (estado IN ('PENDIENTE','EN_REVISION','RESUELTA','CERRADA')),
    
    CONSTRAINT CHK_QUEJA_PRIORIDAD 
        CHECK (prioridad IN ('BAJA','MEDIA','ALTA')),
    
    CONSTRAINT CHK_QUEJA_CATEGORIA
        CHECK (categoria IN ('LIMPIEZA','SEGURIDAD','MANTENIMIENTO','CONVIVENCIA','ZONAS_COMUNES','OTRO')),
    
    CONSTRAINT CHK_QUEJA_RESPUESTA
        CHECK ((respuesta_admin IS NULL AND respondido_por IS NULL AND fecha_respuesta IS NULL) 
            OR (respuesta_admin IS NOT NULL AND respondido_por IS NOT NULL AND fecha_respuesta IS NOT NULL)),
    
    CONSTRAINT FK_QUEJA_APARTAMENTO
        FOREIGN KEY (id_apartamento)
        REFERENCES APARTAMENTOS(id_apartamento)
        ON DELETE CASCADE,
    
    CONSTRAINT FK_QUEJA_MULTA
        FOREIGN KEY (id_multa)
        REFERENCES MULTAS(id_multa)
        ON DELETE CASCADE,
    
    CONSTRAINT FK_QUEJA_CREADOR
        FOREIGN KEY (creado_por)
        REFERENCES USUARIOS(id_usuario),
    
    CONSTRAINT FK_QUEJA_RESPONDEDOR
        FOREIGN KEY (respondido_por)
        REFERENCES USUARIOS(id_usuario)
);

COMMENT ON TABLE  QUEJAS_SUGERENCIAS                    IS 'Sistema de quejas, sugerencias y apelaciones de multas por parte de residentes.';
COMMENT ON COLUMN QUEJAS_SUGERENCIAS.id_apartamento     IS 'Apartamento que genera la queja/sugerencia (siempre identificado para apelaciones).';
COMMENT ON COLUMN QUEJAS_SUGERENCIAS.id_multa           IS 'FK a MULTAS. Solo para tipo=APELACION, vincula la queja con una multa espec?fica.';
COMMENT ON COLUMN QUEJAS_SUGERENCIAS.tipo               IS 'QUEJA | SUGERENCIA | APELACION';
COMMENT ON COLUMN QUEJAS_SUGERENCIAS.categoria          IS 'Clasificaci?n: LIMPIEZA, SEGURIDAD, MANTENIMIENTO, CONVIVENCIA, ZONAS_COMUNES, OTRO';
COMMENT ON COLUMN QUEJAS_SUGERENCIAS.foto_evidencia     IS 'Foto Base64 como evidencia (opcional).';
COMMENT ON COLUMN QUEJAS_SUGERENCIAS.estado             IS 'PENDIENTE | EN_REVISION | RESUELTA | CERRADA';
COMMENT ON COLUMN QUEJAS_SUGERENCIAS.respuesta_admin    IS 'Respuesta escrita por el administrador.';
COMMENT ON COLUMN QUEJAS_SUGERENCIAS.prioridad          IS 'BAJA | MEDIA | ALTA - asignada por el admin.';
COMMENT ON COLUMN QUEJAS_SUGERENCIAS.creado_por         IS 'Usuario (residente) que crea la queja/sugerencia.';
COMMENT ON COLUMN QUEJAS_SUGERENCIAS.respondido_por     IS 'Usuario (admin) que responde la queja/sugerencia.';

-- ?? QUEJAS_SUGERENCIAS ????????????????????????????????????????????
CREATE INDEX IDX_QUEJA_APARTAMENTO    ON QUEJAS_SUGERENCIAS (id_apartamento);
CREATE INDEX IDX_QUEJA_MULTA          ON QUEJAS_SUGERENCIAS (id_multa);
CREATE INDEX IDX_QUEJA_ESTADO         ON QUEJAS_SUGERENCIAS (estado, fecha_creacion DESC);
CREATE INDEX IDX_QUEJA_TIPO           ON QUEJAS_SUGERENCIAS (tipo);
CREATE INDEX IDX_QUEJA_CREADOR        ON QUEJAS_SUGERENCIAS (creado_por);
CREATE INDEX IDX_QUEJA_PENDIENTES     ON QUEJAS_SUGERENCIAS (estado, prioridad, fecha_creacion DESC);
CREATE INDEX IDX_QUEJAS_RESPONDIDO_POR ON QUEJAS_SUGERENCIAS (respondido_por);

-- ??????????????????????????????????????????????????????????????????
-- ?7  TRIGGERS (17 en total)
-- ??????????????????????????????????????????????????????????????????

-- -------------------------------------------------------------------
-- 7.1  TRG_RESIDENTES_UPD ? actualizado_en autom?tico
-- -------------------------------------------------------------------
CREATE OR REPLACE TRIGGER TRG_RESIDENTES_UPD
    BEFORE UPDATE ON RESIDENTES
    FOR EACH ROW
BEGIN
    :NEW.actualizado_en := SYSTIMESTAMP;
END TRG_RESIDENTES_UPD;

-- -------------------------------------------------------------------
-- 7.2  TRG_CONTRATOS_UPD ? actualizado_en autom?tico
-- -------------------------------------------------------------------
CREATE OR REPLACE TRIGGER TRG_CONTRATOS_UPD
    BEFORE UPDATE ON CONTRATOS
    FOR EACH ROW
BEGIN
    :NEW.actualizado_en := SYSTIMESTAMP;
END TRG_CONTRATOS_UPD;

-- -------------------------------------------------------------------
-- 7.3  TRG_CUOTAS_UPD ? actualizado_en + recalcular valor_total
-- Recalcular siempre (INSERT y UPDATE) garantiza consistencia
-- con el CHECK chk_cuota_total.
-- -------------------------------------------------------------------
CREATE OR REPLACE TRIGGER TRG_CUOTAS_UPD
    BEFORE INSERT OR UPDATE ON CUOTAS_ARRIENDO
    FOR EACH ROW
BEGIN
    IF UPDATING THEN
        :NEW.actualizado_en := SYSTIMESTAMP;
    END IF;
    :NEW.valor_total := :NEW.valor_base + :NEW.valor_mora;
END TRG_CUOTAS_UPD;

-- -------------------------------------------------------------------
-- 7.4  TRG_VISITAS_UPD ? actualizado_en autom?tico
-- -------------------------------------------------------------------
CREATE OR REPLACE TRIGGER TRG_VISITAS_UPD
    BEFORE UPDATE ON VISITAS
    FOR EACH ROW
BEGIN
    :NEW.actualizado_en := SYSTIMESTAMP;
END TRG_VISITAS_UPD;

-- -------------------------------------------------------------------
-- 7.5  TRG_ACCESO_UPD ? actualizado_en en REGISTROS_ACCESO
-- -------------------------------------------------------------------
CREATE OR REPLACE TRIGGER TRG_ACCESO_UPD
    BEFORE UPDATE ON REGISTROS_ACCESO
    FOR EACH ROW
BEGIN
    :NEW.actualizado_en := SYSTIMESTAMP;
END TRG_ACCESO_UPD;

-- -------------------------------------------------------------------
-- 7.6  TRG_CONT_SYNC_APARTAMENTO
-- Cuando un contrato pasa a ACTIVO  ? APARTAMENTOS.estado = OCUPADO
-- Cuando vence o se cancela (desde ACTIVO) ? estado = DISPONIBLE
-- -------------------------------------------------------------------
CREATE OR REPLACE TRIGGER TRG_CONT_SYNC_APARTAMENTO
    AFTER UPDATE OF estado ON CONTRATOS
    FOR EACH ROW
BEGIN
    IF :NEW.estado = 'ACTIVO' AND :OLD.estado != 'ACTIVO' THEN
        UPDATE APARTAMENTOS
         WHERE id_apartamento = :NEW.id_apartamento;

    ELSIF :NEW.estado IN ('VENCIDO','CANCELADO')
      AND :OLD.estado = 'ACTIVO' THEN
        UPDATE APARTAMENTOS
         WHERE id_apartamento = :NEW.id_apartamento;
    END IF;
END TRG_CONT_SYNC_APARTAMENTO;

-- -------------------------------------------------------------------
-- 7.7  TRG_QR_USAR
-- Al marcar un QR como usado (0?1), la visita pasa a ACTIVA.
-- WHEN eval?a los valores OLD/NEW sin los dos puntos.
-- -------------------------------------------------------------------
CREATE OR REPLACE TRIGGER TRG_QR_USAR
    AFTER UPDATE OF usado ON QR_ACCESOS
    FOR EACH ROW
    WHEN (NEW.usado = 1 AND OLD.usado = 0)
BEGIN
    UPDATE VISITAS
     WHERE id_visita = :NEW.id_visita;
END TRG_QR_USAR;

-- -------------------------------------------------------------------
-- 7.8  TRG_ACCESO_SALIDA
-- Al registrar la hora de salida en REGISTROS_ACCESO:
--   ? Cierra la visita (FINALIZADA)
--   ? Libera los parqueaderos ocupados por la visita
--   ? Registra hora_salida en VEHICULOS_VISITA
-- -------------------------------------------------------------------
CREATE OR REPLACE TRIGGER TRG_ACCESO_SALIDA
    AFTER UPDATE OF hora_salida ON REGISTROS_ACCESO
    FOR EACH ROW
    WHEN (NEW.hora_salida IS NOT NULL AND OLD.hora_salida IS NULL)
BEGIN
    -- Cerrar la visita
    UPDATE VISITAS
     WHERE id_visita = :NEW.id_visita;

    -- Liberar parqueaderos ocupados por esa visita
    UPDATE PARQUEADEROS
     WHERE id_parqueadero IN (
         SELECT id_parqueadero
           FROM VEHICULOS_VISITA
          WHERE id_visita = :NEW.id_visita
            AND id_parqueadero IS NOT NULL
     );

    -- Registrar hora de salida en los veh?culos de la visita
    UPDATE VEHICULOS_VISITA
     WHERE id_visita = :NEW.id_visita;
END TRG_ACCESO_SALIDA;


-- -------------------------------------------------------------------
-- 7.9  TRG_APARTAMENTOS_UPD -- actualizado_en automatico
-- -------------------------------------------------------------------
CREATE OR REPLACE TRIGGER TRG_APARTAMENTOS_UPD
    BEFORE UPDATE ON APARTAMENTOS
    FOR EACH ROW
BEGIN
    :NEW.actualizado_en := SYSTIMESTAMP;
END TRG_APARTAMENTOS_UPD;

-- -------------------------------------------------------------------
-- 7.10 TRG_PARQUEADEROS_UPD -- actualizado_en automatico
-- -------------------------------------------------------------------
CREATE OR REPLACE TRIGGER TRG_PARQUEADEROS_UPD
    BEFORE UPDATE ON PARQUEADEROS
    FOR EACH ROW
BEGIN
    :NEW.actualizado_en := SYSTIMESTAMP;
END TRG_PARQUEADEROS_UPD;

-- -------------------------------------------------------------------
-- 7.11 TRG_TUTORES_UPD -- actualizado_en automatico
-- -------------------------------------------------------------------
CREATE OR REPLACE TRIGGER TRG_TUTORES_UPD
    BEFORE UPDATE ON TUTORES
    FOR EACH ROW
BEGIN
    :NEW.actualizado_en := SYSTIMESTAMP;
END TRG_TUTORES_UPD;

-- -------------------------------------------------------------------
-- 7.12 TRG_USUARIOS_UPD -- actualizado_en automatico
-- -------------------------------------------------------------------
CREATE OR REPLACE TRIGGER TRG_USUARIOS_UPD
    BEFORE UPDATE ON USUARIOS
    FOR EACH ROW
BEGIN
    :NEW.actualizado_en := SYSTIMESTAMP;
END TRG_USUARIOS_UPD;

-- -------------------------------------------------------------------
-- 7.13 TRG_VISITANTES_UPD -- actualizado_en automatico
-- -------------------------------------------------------------------
CREATE OR REPLACE TRIGGER TRG_VISITANTES_UPD
    BEFORE UPDATE ON VISITANTES
    FOR EACH ROW
BEGIN
    :NEW.actualizado_en := SYSTIMESTAMP;
END TRG_VISITANTES_UPD;

-- -------------------------------------------------------------------
-- 7.14 TRG_VEHICULOS_UPD -- actualizado_en automatico
-- -------------------------------------------------------------------
CREATE OR REPLACE TRIGGER TRG_VEHICULOS_UPD
    BEFORE UPDATE ON VEHICULOS_VISITA
    FOR EACH ROW
BEGIN
    :NEW.actualizado_en := SYSTIMESTAMP;
END TRG_VEHICULOS_UPD;

-- -------------------------------------------------------------------
-- 7.15  TRG_AUTO_FRECUENTE
-- Al insertar en REGISTRO_VISITA, recupera el id_residente de la
-- VISITA asociada y registra el par (residente, visitante) en
-- FRECUENTES_RESIDENTE si no existe.
-- Si exist?a pero con activo=0 (eliminado por el residente), lo
-- reactiva autom?ticamente para que vuelva a aparecer en el panel.
-- -------------------------------------------------------------------
CREATE OR REPLACE TRIGGER TRG_AUTO_FRECUENTE
    AFTER INSERT ON REGISTRO_VISITA
    FOR EACH ROW
DECLARE
    v_id_residente VISITAS.id_residente%TYPE;
BEGIN
    SELECT id_residente
      INTO v_id_residente
      FROM VISITAS
     WHERE id_visita = :NEW.id_visita;

    BEGIN
        INSERT INTO FRECUENTES_RESIDENTE (id_residente, id_visitante)
        VALUES (v_id_residente, :NEW.id_visitante);
    EXCEPTION
        WHEN DUP_VAL_ON_INDEX THEN
            -- Par ya registrado; si estaba oculto, lo reactiva
            UPDATE FRECUENTES_RESIDENTE
             WHERE id_residente = v_id_residente
               AND id_visitante = :NEW.id_visitante
               AND activo       = 0;
    END;
END TRG_AUTO_FRECUENTE;

-- -------------------------------------------------------------------
-- 7.16  TRG_FRECUENTES_UPD ? actualizado_en autom?tico
-- -------------------------------------------------------------------
CREATE OR REPLACE TRIGGER TRG_FRECUENTES_UPD
    BEFORE UPDATE ON FRECUENTES_RESIDENTE
    FOR EACH ROW
BEGIN
    :NEW.actualizado_en := SYSTIMESTAMP;
END TRG_FRECUENTES_UPD;

-- -------------------------------------------------------------------
-- 7.17  TRG_VIS_VALIDAR_RESIDENTE
-- BEFORE INSERT OR UPDATE en VISITAS: garantiza que la columna
-- desnormalizada id_residente es coherente con el id_residente
-- del CONTRATO_RESIDENTE referenciado.
-- Previene inconsistencias entre la desnormalizaci?n controlada y
-- la tabla de verdad CONTRATO_RESIDENTE.
-- Solo se dispara cuando cambia id_residente o id_contrato_res.
-- -------------------------------------------------------------------
CREATE OR REPLACE TRIGGER TRG_VIS_VALIDAR_RESIDENTE
    BEFORE INSERT OR UPDATE OF id_residente, id_contrato_res ON VISITAS
    FOR EACH ROW
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM CONTRATO_RESIDENTE
     WHERE id_contrato_res = :NEW.id_contrato_res
       AND id_residente    = :NEW.id_residente;

    IF v_count = 0 THEN
        RAISE_APPLICATION_ERROR(-20001,
            'VISITAS.id_residente (' || :NEW.id_residente ||
            ') no coincide con CONTRATO_RESIDENTE.id_residente' ||
            ' para id_contrato_res=' || :NEW.id_contrato_res || '.');
    END IF;
END TRG_VIS_VALIDAR_RESIDENTE;

-- ??????????????????????????????????????????????????????????????????
-- ?8  PROCEDIMIENTOS ALMACENADOS
-- ??????????????????????????????????????????????????????????????????

-- -------------------------------------------------------------------
-- 8.1  SP_VALIDAR_QR ? VALIDACI?N AT?MICA
-- Llamado desde Java cuando el portero escanea el QR.
-- FOR UPDATE previene race conditions (dos porteros, mismo QR).
-- Retorna: p_valido (1/0), p_mensaje y cursor con datos de la visita.
-- -------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE SP_VALIDAR_QR (
    p_codigo_qr     IN  VARCHAR2,
    p_id_vigilante  IN  NUMBER,
    p_valido        OUT NUMBER,         -- 1 = v?lido y procesado; 0 = rechazado
    p_mensaje       OUT VARCHAR2,
    p_cursor        OUT SYS_REFCURSOR
)
AS
    v_id_qr         QR_ACCESOS.id_qr%TYPE;
    v_id_visita     QR_ACCESOS.id_visita%TYPE;
    v_usado         QR_ACCESOS.usado%TYPE;
    v_expiracion    QR_ACCESOS.fecha_expiracion%TYPE;
BEGIN
    -- 1. Buscar el QR y bloquearlo (previene race condition)
    BEGIN
        SELECT id_qr, id_visita, usado, fecha_expiracion
          INTO v_id_qr, v_id_visita, v_usado, v_expiracion
          FROM QR_ACCESOS
         WHERE codigo_qr = p_codigo_qr
           FOR UPDATE;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            p_valido  := 0;
            p_mensaje := 'QR no encontrado en el sistema.';
            OPEN p_cursor FOR SELECT NULL FROM DUAL WHERE 1=0;
            RETURN;
    END;

    -- 2. Verificar expiraci?n
    IF SYSTIMESTAMP > v_expiracion THEN
        UPDATE QR_ACCESOS
         WHERE id_qr = v_id_qr;
        UPDATE VISITAS
         WHERE id_visita = v_id_visita;
        COMMIT;
        p_valido  := 0;
        p_mensaje := 'QR expirado. Solicite un nuevo c?digo al residente.';
        OPEN p_cursor FOR SELECT NULL FROM DUAL WHERE 1=0;
        RETURN;
    END IF;

    -- 3. Verificar uso previo
    IF v_usado = 1 THEN
        p_valido  := 0;
        p_mensaje := 'QR ya fue utilizado. Acceso denegado.';
        OPEN p_cursor FOR SELECT NULL FROM DUAL WHERE 1=0;
        RETURN;
    END IF;

    -- 4. QR v?lido: marcar como usado y registrar acceso f?sico
    UPDATE QR_ACCESOS
           fecha_uso        = SYSTIMESTAMP,
           id_vigilante_uso = p_id_vigilante
     WHERE id_qr = v_id_qr;

    INSERT INTO REGISTROS_ACCESO (id_visita, id_vigilante, hora_entrada)
    VALUES (v_id_visita, p_id_vigilante, SYSTIMESTAMP);

    COMMIT;

    -- 5. Retornar datos para la pantalla del portero
    OPEN p_cursor FOR
        SELECT
            r.nombres  || ' ' || r.apellidos               AS residente_nombre,
            td.codigo  || '-' || r.numero_documento        AS residente_documento,
            a.numero                                        AS numero_apartamento,
            q.fecha_expiracion,
            v.tiempo_validez_min,
            v.cantidad_personas,
            v.notas,
            -- Lista de visitantes con indicador de documento PDF
            (SELECT LISTAGG(
                        vi.nombres || ' ' || vi.apellidos
                        || ' (' || td2.codigo || ': ' || vi.numero_documento || ')'
                        || CASE WHEN vi.doc_pdf_url IS NOT NULL THEN ' [CON ID]' ELSE '' END,
                        ' | ')
                        WITHIN GROUP (ORDER BY rv.es_titular DESC, rv.id_registro_visita)
               FROM REGISTRO_VISITA  rv
               JOIN VISITANTES       vi  ON vi.id_visitante  = rv.id_visitante
               JOIN TIPOS_DOCUMENTO  td2 ON td2.id_tipo_doc  = vi.id_tipo_doc
              WHERE rv.id_visita = v.id_visita)             AS personas_lista,
            -- Verificar si el visitante titular tiene documento PDF cargado
            (SELECT CASE WHEN COUNT(*) > 0 THEN 'S?' ELSE 'NO' END
               FROM REGISTRO_VISITA rv
               JOIN VISITANTES vi ON vi.id_visitante = rv.id_visitante
              WHERE rv.id_visita = v.id_visita
                AND rv.es_titular = 1
                AND vi.doc_pdf_url IS NOT NULL)              AS tiene_documento_pdf,
            -- Lista de veh?culos (desde VEHICULOS_VISITA + PARQUEADEROS)
            (SELECT LISTAGG(
                        veh.tipo || ': ' || veh.placa
                        || CASE WHEN p.codigo IS NOT NULL
                                THEN ' -> Parq. ' || p.codigo
                                ELSE ' (sin parqueadero)'
                           END,
                        ' | ')
                        WITHIN GROUP (ORDER BY veh.id_vehiculo_visita)
               FROM VEHICULOS_VISITA veh
               LEFT JOIN PARQUEADEROS p ON p.id_parqueadero = veh.id_parqueadero
              WHERE veh.id_visita = v.id_visita)            AS vehiculos_lista
        FROM VISITAS              v
        JOIN QR_ACCESOS           q   ON q.id_visita        = v.id_visita
        JOIN CONTRATO_RESIDENTE   cr  ON cr.id_contrato_res = v.id_contrato_res
        JOIN CONTRATOS            c   ON c.id_contrato      = cr.id_contrato
        JOIN APARTAMENTOS         a   ON a.id_apartamento   = c.id_apartamento
        JOIN RESIDENTES           r   ON r.id_residente     = v.id_residente
        JOIN TIPOS_DOCUMENTO      td  ON td.id_tipo_doc     = r.id_tipo_doc
        WHERE v.id_visita = v_id_visita;

    p_valido  := 1;
    p_mensaje := 'Acceso autorizado.';

EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        p_valido  := 0;
        p_mensaje := 'Error interno: ' || SQLERRM;
        OPEN p_cursor FOR SELECT NULL FROM DUAL WHERE 1=0;
END SP_VALIDAR_QR;

-- -------------------------------------------------------------------
-- 8.2  SP_LIBERAR_VISITA_FRECUENTE ? REGISTRO R?PIDO
-- Crea una visita completa para un visitante frecuente ya registrado.
-- Solo requiere los datos que cambian d?a a d?a:
--   ? p_cantidad_personas  ? cu?ntas personas vienen en el grupo
--   ? p_tipo_vehiculo      ? en qu? vienen (NULL = a pie)
--   ? p_placa              ? placa del veh?culo (NULL si a pie)
--   ? p_descripcion_tipo   ? obligatorio solo si tipo = 'OTRO'
--   ? p_tiempo_validez     ? minutos de validez del QR (5-60)
-- Crea autom?ticamente: VISITAS + REGISTRO_VISITA + VEHICULOS_VISITA
-- (si aplica) + QR_ACCESOS. Retorna el c?digo QR y su expiraci?n.
-- -------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE SP_LIBERAR_VISITA_FRECUENTE (
    p_id_visitante      IN  NUMBER,        -- visitante frecuente pre-registrado
    p_id_contrato_res   IN  NUMBER,        -- CONTRATO_RESIDENTE del residente autorizante
    p_id_residente      IN  NUMBER,        -- residente que autoriza (validaci?n de seguridad)
    p_cantidad_personas IN  NUMBER,        -- total del grupo (>= 1, titular incluido)
    p_tiempo_validez    IN  NUMBER,        -- minutos de validez del QR (5-60)
    p_tipo_vehiculo     IN  VARCHAR2,      -- NULL=a pie | 'VEHICULO'|'MOTO'|'BICICLETA'|'OTRO'
    p_placa             IN  VARCHAR2,      -- NULL si a pie o sin veh?culo
    p_descripcion_tipo  IN  VARCHAR2,      -- obligatorio si tipo = 'OTRO' (ej: moto el?ctrica)
    p_notas             IN  VARCHAR2,      -- observaciones opcionales de la visita
    p_id_visita         OUT NUMBER,
    p_codigo_qr         OUT VARCHAR2,
    p_fecha_expiracion  OUT TIMESTAMP,
    p_mensaje           OUT VARCHAR2
)
AS
    v_id_visita     VISITAS.id_visita%TYPE;
    v_count         NUMBER;
    v_codigo_qr     VARCHAR2(255);
    v_fecha_exp     TIMESTAMP;
BEGIN
    -- 1. Verificar que el visitante es frecuente activo del residente
    SELECT COUNT(*) INTO v_count
      FROM FRECUENTES_RESIDENTE
     WHERE id_residente = p_id_residente
       AND id_visitante = p_id_visitante
       AND activo       = 1;

    IF v_count = 0 THEN
        p_id_visita := NULL; p_codigo_qr := NULL; p_fecha_expiracion := NULL;
        p_mensaje := 'El visitante no figura como frecuente activo de este residente.';
        RETURN;
    END IF;

    -- 2. Verificar que id_contrato_res pertenece al residente con contrato ACTIVO
    SELECT COUNT(*) INTO v_count
      FROM CONTRATO_RESIDENTE cr
      JOIN CONTRATOS          c  ON c.id_contrato = cr.id_contrato
     WHERE cr.id_contrato_res = p_id_contrato_res
       AND cr.id_residente    = p_id_residente
       AND c.estado           = 'ACTIVO';

    IF v_count = 0 THEN
        p_id_visita := NULL; p_codigo_qr := NULL; p_fecha_expiracion := NULL;
        p_mensaje := 'No se encontr? un contrato ACTIVO para este residente.';
        RETURN;
    END IF;

    -- 3. Validar rango de tiempo de validez del QR
    IF p_tiempo_validez NOT BETWEEN 5 AND 60 THEN
        p_id_visita := NULL; p_codigo_qr := NULL; p_fecha_expiracion := NULL;
        p_mensaje := 'El tiempo de validez del QR debe estar entre 5 y 60 minutos.';
        RETURN;
    END IF;

    -- 4. Validar descripcion_tipo cuando el veh?culo es de tipo OTRO
    IF p_tipo_vehiculo = 'OTRO'
       AND (p_descripcion_tipo IS NULL OR TRIM(p_descripcion_tipo) IS NULL) THEN
        p_id_visita := NULL; p_codigo_qr := NULL; p_fecha_expiracion := NULL;
        p_mensaje := 'Debe especificar la descripci?n del veh?culo cuando el tipo es OTRO (ej: moto el?ctrica).';
        RETURN;
    END IF;

    -- 5. Crear la visita
    INSERT INTO VISITAS (
        id_contrato_res,
        id_residente,
        tiempo_validez_min,
        cantidad_personas,
        notas,
        estado
    ) VALUES (
        p_id_contrato_res,
        p_id_residente,
        p_tiempo_validez,
        p_cantidad_personas,
        p_notas,
        'PENDIENTE'
    ) RETURNING id_visita INTO v_id_visita;

    -- 6. Registrar visitante frecuente como titular del grupo
    --    TRG_AUTO_FRECUENTE se activa aqu?; el par ya existe ? no duplica
    INSERT INTO REGISTRO_VISITA (id_visita, id_visitante, es_titular)
    VALUES (v_id_visita, p_id_visitante, 1);

    -- 7. Registrar el veh?culo si aplica (NULL en p_tipo_vehiculo = a pie)
    IF p_tipo_vehiculo IS NOT NULL AND p_placa IS NOT NULL THEN
        INSERT INTO VEHICULOS_VISITA (
            id_visita,
            placa,
            tipo,
            descripcion_tipo
        ) VALUES (
            v_id_visita,
            UPPER(TRIM(p_placa)),
            p_tipo_vehiculo,
            CASE WHEN p_tipo_vehiculo = 'OTRO' THEN p_descripcion_tipo ELSE NULL END
        );
    END IF;

    -- 8. Generar c?digo QR ?nico (SYS_GUID ? 32 chars hexadecimal en min?sculas)
    v_codigo_qr := LOWER(RAWTOHEX(SYS_GUID()));
    v_fecha_exp := SYSTIMESTAMP + NUMTODSINTERVAL(p_tiempo_validez, 'MINUTE');

    INSERT INTO QR_ACCESOS (id_visita, codigo_qr, fecha_expiracion, usado)
    VALUES (v_id_visita, v_codigo_qr, v_fecha_exp, 0);

    COMMIT;

    p_id_visita        := v_id_visita;
    p_codigo_qr        := v_codigo_qr;
    p_fecha_expiracion := v_fecha_exp;
    p_mensaje := 'Visita frecuente registrada. QR generado correctamente.';

EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        p_id_visita := NULL; p_codigo_qr := NULL; p_fecha_expiracion := NULL;
        p_mensaje := 'Error interno: ' || SQLERRM;
END SP_LIBERAR_VISITA_FRECUENTE;

-- ??????????????????????????????????????????????????????????????????
-- ?8.3  FUNCIONES AUT?NOMAS
-- Usables directamente en sentencias SQL (SELECT, WHERE, etc.)
-- ??????????????????????????????????????????????????????????????????

-- -------------------------------------------------------------------
-- 8.3.1  FN_SALDO_CUOTA
-- Retorna el saldo pendiente de una cuota:
--   valor_total ? SUM(pagos.valor_pagado)
-- Retorna NULL si la cuota no existe; 0 si ya est? completamente pagada.
-- -------------------------------------------------------------------
CREATE OR REPLACE FUNCTION FN_SALDO_CUOTA (
    p_id_cuota IN NUMBER
) RETURN NUMBER
IS
    v_valor_total  CUOTAS_ARRIENDO.valor_total%TYPE;
    v_pagado       NUMBER := 0;
BEGIN
    SELECT valor_total
      INTO v_valor_total
      FROM CUOTAS_ARRIENDO
     WHERE id_cuota = p_id_cuota;

    SELECT NVL(SUM(valor_pagado), 0)
      INTO v_pagado
      FROM PAGOS
     WHERE id_cuota = p_id_cuota;

    RETURN GREATEST(v_valor_total - v_pagado, 0);
EXCEPTION
    WHEN NO_DATA_FOUND THEN RETURN NULL;
END FN_SALDO_CUOTA;

-- -------------------------------------------------------------------
-- 8.3.2  FN_CALCULAR_MORA
-- Calcula la mora aplicable a una cuota en la fecha indicada.
-- La mora es proporcional a los d?as vencidos (porcentaje_mora es mensual).
--   mora = valor_base ? (pct_mora/100) ? (dias_vencidos/30)
-- No aplica mora durante el per?odo de gracia del contrato.
-- Retorna 0 si la cuota est? dentro del plazo o per?odo de gracia.
-- Retorna NULL si la cuota no existe.
-- -------------------------------------------------------------------
CREATE OR REPLACE FUNCTION FN_CALCULAR_MORA (
    p_id_cuota IN NUMBER,
    p_fecha    IN DATE DEFAULT SYSDATE
) RETURN NUMBER
IS
    v_valor_base   CUOTAS_ARRIENDO.valor_base%TYPE;
    v_fecha_limite CUOTAS_ARRIENDO.fecha_limite%TYPE;
    v_id_contrato  CUOTAS_ARRIENDO.id_contrato%TYPE;
    v_dias_gracia  CONTRATOS.dias_gracia%TYPE;
    v_pct_mora     CONTRATOS.porcentaje_mora%TYPE;
    v_dias_mora    NUMBER;
BEGIN
    SELECT ca.valor_base, ca.fecha_limite, ca.id_contrato
      INTO v_valor_base, v_fecha_limite, v_id_contrato
      FROM CUOTAS_ARRIENDO ca
     WHERE ca.id_cuota = p_id_cuota;

    SELECT c.dias_gracia, c.porcentaje_mora
      INTO v_dias_gracia, v_pct_mora
      FROM CONTRATOS c
     WHERE c.id_contrato = v_id_contrato;

    -- D?as vencidos contados desde la fecha l?mite + per?odo de gracia
    v_dias_mora := GREATEST(0, p_fecha - (v_fecha_limite + v_dias_gracia));

    IF v_dias_mora = 0 THEN
        RETURN 0;
    END IF;

    -- Mora diaria proporcional al porcentaje mensual
    RETURN ROUND(v_valor_base * (v_pct_mora / 100) * (v_dias_mora / 30), 2);
EXCEPTION
    WHEN NO_DATA_FOUND THEN RETURN NULL;
END FN_CALCULAR_MORA;

-- -------------------------------------------------------------------
-- 8.3.3  FN_MINUTOS_RESTANTES_QR
-- Retorna los minutos restantes de vigencia de un QR.
-- Valor negativo indica que ya expir?.
-- Retorna NULL si el c?digo QR no existe.
-- -------------------------------------------------------------------
CREATE OR REPLACE FUNCTION FN_MINUTOS_RESTANTES_QR (
    p_codigo_qr IN VARCHAR2
) RETURN NUMBER
IS
    v_expiracion QR_ACCESOS.fecha_expiracion%TYPE;
BEGIN
    SELECT fecha_expiracion
      INTO v_expiracion
      FROM QR_ACCESOS
     WHERE codigo_qr = p_codigo_qr;

    RETURN ROUND((CAST(v_expiracion AS DATE) - CAST(SYSTIMESTAMP AS DATE)) * 1440, 1);
EXCEPTION
    WHEN NO_DATA_FOUND THEN RETURN NULL;
END FN_MINUTOS_RESTANTES_QR;

-- ??????????????????????????????????????????????????????????????????
-- ?8.4  PAQUETES PL/SQL
-- Agrupan l?gica de negocio relacionada y permiten sobrecarga,
-- variables de estado de sesi?n y compilaci?n incremental.
-- ??????????????????????????????????????????????????????????????????

-- ??????????????????????????????????????????????????????????????????
-- 8.4.1  PKG_PAGOS ? Gesti?n de pagos y mora
-- ??????????????????????????????????????????????????????????????????
CREATE OR REPLACE PACKAGE PKG_PAGOS AS

    -- Retorna el saldo pendiente de una cuota (valor_total ? pagos realizados).
    FUNCTION FN_SALDO_CUOTA (p_id_cuota NUMBER) RETURN NUMBER;

    -- Calcula la mora aplicable a una cuota en una fecha determinada.
    FUNCTION FN_CALCULAR_MORA (
        p_id_cuota NUMBER,
        p_fecha    DATE DEFAULT SYSDATE
    ) RETURN NUMBER;

    -- Registra un pago (parcial o total) sobre una cuota y actualiza su estado.
    PROCEDURE SP_REGISTRAR_PAGO (
        p_id_cuota        IN  NUMBER,
        p_id_usuario      IN  NUMBER,
        p_valor_pagado    IN  NUMBER,
        p_metodo_pago     IN  VARCHAR2,
        p_referencia      IN  VARCHAR2 DEFAULT NULL,
        p_notas           IN  VARCHAR2 DEFAULT NULL,
        p_id_pago         OUT NUMBER,
        p_mensaje         OUT VARCHAR2
    );

    -- Recalcula y aplica mora a todas las cuotas vencidas de un contrato.
    -- Retorna el n?mero de cuotas actualizadas.
    PROCEDURE SP_APLICAR_MORA (
        p_id_contrato IN  NUMBER,
        p_cuotas_upd  OUT NUMBER,
        p_mensaje     OUT VARCHAR2
    );

END PKG_PAGOS;

CREATE OR REPLACE PACKAGE BODY PKG_PAGOS AS

    -- ---------------------------------------------------------------
    FUNCTION FN_SALDO_CUOTA (p_id_cuota NUMBER) RETURN NUMBER IS
        v_total  CUOTAS_ARRIENDO.valor_total%TYPE;
        v_pagado NUMBER := 0;
    BEGIN
        SELECT valor_total INTO v_total
          FROM CUOTAS_ARRIENDO WHERE id_cuota = p_id_cuota;
        SELECT NVL(SUM(valor_pagado), 0) INTO v_pagado
          FROM PAGOS WHERE id_cuota = p_id_cuota;
        RETURN GREATEST(v_total - v_pagado, 0);
    EXCEPTION
        WHEN NO_DATA_FOUND THEN RETURN NULL;
    END FN_SALDO_CUOTA;

    -- ---------------------------------------------------------------
    FUNCTION FN_CALCULAR_MORA (
        p_id_cuota NUMBER,
        p_fecha    DATE DEFAULT SYSDATE
    ) RETURN NUMBER IS
        v_base         CUOTAS_ARRIENDO.valor_base%TYPE;
        v_limite       CUOTAS_ARRIENDO.fecha_limite%TYPE;
        v_id_contrato  CUOTAS_ARRIENDO.id_contrato%TYPE;
        v_gracia       CONTRATOS.dias_gracia%TYPE;
        v_pct          CONTRATOS.porcentaje_mora%TYPE;
        v_dias         NUMBER;
    BEGIN
        SELECT ca.valor_base, ca.fecha_limite, ca.id_contrato
          INTO v_base, v_limite, v_id_contrato
          FROM CUOTAS_ARRIENDO ca WHERE ca.id_cuota = p_id_cuota;
        SELECT c.dias_gracia, c.porcentaje_mora
          INTO v_gracia, v_pct
          FROM CONTRATOS c WHERE c.id_contrato = v_id_contrato;
        v_dias := GREATEST(0, p_fecha - (v_limite + v_gracia));
        IF v_dias = 0 THEN RETURN 0; END IF;
        RETURN ROUND(v_base * (v_pct / 100) * (v_dias / 30), 2);
    EXCEPTION
        WHEN NO_DATA_FOUND THEN RETURN NULL;
    END FN_CALCULAR_MORA;

    -- ---------------------------------------------------------------
    PROCEDURE SP_REGISTRAR_PAGO (
        p_id_cuota        IN  NUMBER,
        p_id_usuario      IN  NUMBER,
        p_valor_pagado    IN  NUMBER,
        p_metodo_pago     IN  VARCHAR2,
        p_referencia      IN  VARCHAR2 DEFAULT NULL,
        p_notas           IN  VARCHAR2 DEFAULT NULL,
        p_id_pago         OUT NUMBER,
        p_mensaje         OUT VARCHAR2
    ) IS
        v_estado  CUOTAS_ARRIENDO.estado%TYPE;
        v_saldo   NUMBER;
    BEGIN
        -- Validar existencia y estado de la cuota
        SELECT estado INTO v_estado
          FROM CUOTAS_ARRIENDO WHERE id_cuota = p_id_cuota;

        IF v_estado = 'PAGADA' THEN
            p_id_pago := NULL;
            p_mensaje := 'La cuota ya est? completamente pagada.';
            RETURN;
        END IF;

        IF p_valor_pagado <= 0 THEN
            p_id_pago := NULL;
            p_mensaje := 'El valor a pagar debe ser mayor a cero.';
            RETURN;
        END IF;

        -- Registrar el pago
        INSERT INTO PAGOS (
            id_cuota, id_registrado_por, valor_pagado,
            metodo_pago, referencia, notas
        ) VALUES (
            p_id_cuota, p_id_usuario, p_valor_pagado,
            p_metodo_pago, p_referencia, p_notas
        ) RETURNING id_pago INTO p_id_pago;

        -- Recalcular saldo y actualizar estado si qued? en cero
        v_saldo := FN_SALDO_CUOTA(p_id_cuota);
        IF v_saldo <= 0 THEN
            UPDATE CUOTAS_ARRIENDO
             WHERE id_cuota = p_id_cuota;
        END IF;

        COMMIT;
        p_mensaje := CASE
            WHEN v_saldo <= 0 THEN 'Pago registrado. Cuota cancelada en su totalidad.'
            ELSE 'Abono registrado. Saldo pendiente: $'
                 || TO_CHAR(v_saldo, 'FM9,999,990.00')
        END;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            p_id_pago := NULL;
            p_mensaje := 'Cuota no encontrada (id_cuota=' || p_id_cuota || ').';
        WHEN OTHERS THEN
            ROLLBACK;
            p_id_pago := NULL;
            p_mensaje := 'Error interno: ' || SQLERRM;
    END SP_REGISTRAR_PAGO;

    -- ---------------------------------------------------------------
    PROCEDURE SP_APLICAR_MORA (
        p_id_contrato IN  NUMBER,
        p_cuotas_upd  OUT NUMBER,
        p_mensaje     OUT VARCHAR2
    ) IS
        v_mora NUMBER;
    BEGIN
        p_cuotas_upd := 0;
        FOR rec IN (
            SELECT id_cuota, valor_base
              FROM CUOTAS_ARRIENDO
             WHERE id_contrato = p_id_contrato
               AND estado IN ('PENDIENTE', 'VENCIDA', 'EN_MORA')
               AND fecha_limite < SYSDATE
        ) LOOP
            v_mora := FN_CALCULAR_MORA(rec.id_cuota);
            IF v_mora > 0 THEN
                UPDATE CUOTAS_ARRIENDO
                       valor_total = valor_base + v_mora,
                       estado      = 'EN_MORA'
                 WHERE id_cuota = rec.id_cuota;
                p_cuotas_upd := p_cuotas_upd + 1;
            END IF;
        END LOOP;
        COMMIT;
        p_mensaje := p_cuotas_upd || ' cuota(s) actualizadas con mora.';
    EXCEPTION
        WHEN OTHERS THEN
            ROLLBACK;
            p_cuotas_upd := 0;
            p_mensaje := 'Error interno: ' || SQLERRM;
    END SP_APLICAR_MORA;

END PKG_PAGOS;

-- ??????????????????????????????????????????????????????????????????
-- 8.4.2  PKG_VISITAS ? Gesti?n del ciclo de vida de visitas y QR
-- ??????????????????????????????????????????????????????????????????
CREATE OR REPLACE PACKAGE PKG_VISITAS AS

    -- Retorna 1 si el visitante tiene un QR activo (no usado, no expirado)
    -- con el residente indicado; 0 en caso contrario.
    FUNCTION FN_TIENE_QR_ACTIVO (
        p_id_visitante NUMBER,
        p_id_residente NUMBER
    ) RETURN NUMBER;

    -- Retorna los minutos restantes de vigencia de un QR.
    -- Negativo si ya expir?. NULL si el c?digo no existe.
    FUNCTION FN_MINUTOS_RESTANTES_QR (p_codigo_qr VARCHAR2) RETURN NUMBER;

    -- Cancela una visita PENDIENTE e invalida su QR (si existe).
    PROCEDURE SP_CANCELAR_VISITA (
        p_id_visita IN  NUMBER,
        p_mensaje   OUT VARCHAR2
    );

    -- Genera el QR para una visita PENDIENTE que a?n no tiene QR activo.
    PROCEDURE SP_GENERAR_QR_VISITA (
        p_id_visita  IN  NUMBER,
        p_tiempo_min IN  NUMBER,
        p_codigo_qr  OUT VARCHAR2,
        p_expiracion OUT TIMESTAMP,
        p_mensaje    OUT VARCHAR2
    );

END PKG_VISITAS;

CREATE OR REPLACE PACKAGE BODY PKG_VISITAS AS

    -- ---------------------------------------------------------------
    FUNCTION FN_TIENE_QR_ACTIVO (
        p_id_visitante NUMBER,
        p_id_residente NUMBER
    ) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM QR_ACCESOS      q
          JOIN VISITAS         v  ON q.id_visita    = v.id_visita
          JOIN REGISTRO_VISITA rv ON v.id_visita    = rv.id_visita
         WHERE rv.id_visitante  = p_id_visitante
           AND v.id_residente   = p_id_residente
           AND q.usado          = 0
           AND q.fecha_expiracion > SYSTIMESTAMP;
        RETURN CASE WHEN v_count > 0 THEN 1 ELSE 0 END;
    END FN_TIENE_QR_ACTIVO;

    -- ---------------------------------------------------------------
    FUNCTION FN_MINUTOS_RESTANTES_QR (p_codigo_qr VARCHAR2) RETURN NUMBER IS
        v_exp QR_ACCESOS.fecha_expiracion%TYPE;
    BEGIN
        SELECT fecha_expiracion INTO v_exp
          FROM QR_ACCESOS WHERE codigo_qr = p_codigo_qr;
        RETURN ROUND((CAST(v_exp AS DATE) - CAST(SYSTIMESTAMP AS DATE)) * 1440, 1);
    EXCEPTION
        WHEN NO_DATA_FOUND THEN RETURN NULL;
    END FN_MINUTOS_RESTANTES_QR;

    -- ---------------------------------------------------------------
    PROCEDURE SP_CANCELAR_VISITA (
        p_id_visita IN  NUMBER,
        p_mensaje   OUT VARCHAR2
    ) IS
        v_estado VISITAS.estado%TYPE;
    BEGIN
        SELECT estado INTO v_estado
          FROM VISITAS WHERE id_visita = p_id_visita;

        IF v_estado != 'PENDIENTE' THEN
            p_mensaje := 'Solo se pueden cancelar visitas PENDIENTE. '
                      || 'Estado actual: ' || v_estado || '.';
            RETURN;
        END IF;

        UPDATE VISITAS
         WHERE id_visita = p_id_visita;

        -- Invalidar QR asociado si a?n no fue usado
        UPDATE QR_ACCESOS
         WHERE id_visita = p_id_visita AND usado = 0;

        COMMIT;
        p_mensaje := 'Visita ' || p_id_visita || ' cancelada correctamente.';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            p_mensaje := 'Visita no encontrada (id_visita=' || p_id_visita || ').';
        WHEN OTHERS THEN
            ROLLBACK;
            p_mensaje := 'Error interno: ' || SQLERRM;
    END SP_CANCELAR_VISITA;

    -- ---------------------------------------------------------------
    PROCEDURE SP_GENERAR_QR_VISITA (
        p_id_visita  IN  NUMBER,
        p_tiempo_min IN  NUMBER,
        p_codigo_qr  OUT VARCHAR2,
        p_expiracion OUT TIMESTAMP,
        p_mensaje    OUT VARCHAR2
    ) IS
        v_estado VISITAS.estado%TYPE;
        v_count  NUMBER;
    BEGIN
        SELECT estado INTO v_estado
          FROM VISITAS WHERE id_visita = p_id_visita;

        IF v_estado != 'PENDIENTE' THEN
            p_codigo_qr := NULL; p_expiracion := NULL;
            p_mensaje := 'Solo se puede generar QR para visitas PENDIENTE.';
            RETURN;
        END IF;

        -- Verificar que no exista ya un QR activo
        SELECT COUNT(*) INTO v_count
          FROM QR_ACCESOS
         WHERE id_visita = p_id_visita AND usado = 0
           AND fecha_expiracion > SYSTIMESTAMP;
        IF v_count > 0 THEN
            p_codigo_qr := NULL; p_expiracion := NULL;
            p_mensaje := 'Ya existe un QR activo para esta visita.';
            RETURN;
        END IF;

        IF p_tiempo_min NOT BETWEEN 5 AND 60 THEN
            p_codigo_qr := NULL; p_expiracion := NULL;
            p_mensaje := 'El tiempo de validez debe estar entre 5 y 60 minutos.';
            RETURN;
        END IF;

        p_codigo_qr  := LOWER(RAWTOHEX(SYS_GUID()));
        p_expiracion := SYSTIMESTAMP + NUMTODSINTERVAL(p_tiempo_min, 'MINUTE');

        INSERT INTO QR_ACCESOS (id_visita, codigo_qr, fecha_expiracion, usado)
        VALUES (p_id_visita, p_codigo_qr, p_expiracion, 0);

        COMMIT;
        p_mensaje := 'QR generado. Vigencia: ' || p_tiempo_min || ' minuto(s).';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            p_codigo_qr := NULL; p_expiracion := NULL;
            p_mensaje := 'Visita no encontrada (id_visita=' || p_id_visita || ').';
        WHEN OTHERS THEN
            ROLLBACK;
            p_codigo_qr := NULL; p_expiracion := NULL;
            p_mensaje := 'Error interno: ' || SQLERRM;
    END SP_GENERAR_QR_VISITA;

END PKG_VISITAS;

-- ??????????????????????????????????????????????????????????????????
-- 8.4.3  PKG_RESIDENTES ? Gesti?n de residentes y contratos
-- ??????????????????????????????????????????????????????????????????
CREATE OR REPLACE PACKAGE PKG_RESIDENTES AS

    -- Retorna 1 si el residente tiene al menos un contrato ACTIVO; 0 si no.
    FUNCTION FN_TIENE_CONTRATO_ACTIVO (p_id_residente NUMBER) RETURN NUMBER;

    -- Retorna el n?mero del apartamento del contrato ACTIVO del residente.
    -- NULL si no tiene contrato activo.
    FUNCTION FN_APARTAMENTO_ACTUAL (p_id_residente NUMBER) RETURN VARCHAR2;

    -- Realiza soft-delete del residente (activo = 0).
    -- Rechaza si el residente tiene un contrato ACTIVO.
    PROCEDURE SP_DESACTIVAR_RESIDENTE (
        p_id_residente IN  NUMBER,
        p_mensaje      OUT VARCHAR2
    );

    -- Reactiva un residente previamente desactivado (activo = 1).
    PROCEDURE SP_ACTIVAR_RESIDENTE (
        p_id_residente IN  NUMBER,
        p_mensaje      OUT VARCHAR2
    );

END PKG_RESIDENTES;

CREATE OR REPLACE PACKAGE BODY PKG_RESIDENTES AS

    -- ---------------------------------------------------------------
    FUNCTION FN_TIENE_CONTRATO_ACTIVO (p_id_residente NUMBER) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM CONTRATO_RESIDENTE cr
          JOIN CONTRATOS          c  ON c.id_contrato = cr.id_contrato
         WHERE cr.id_residente = p_id_residente
           AND c.estado        = 'ACTIVO';
        RETURN CASE WHEN v_count > 0 THEN 1 ELSE 0 END;
    END FN_TIENE_CONTRATO_ACTIVO;

    -- ---------------------------------------------------------------
    FUNCTION FN_APARTAMENTO_ACTUAL (p_id_residente NUMBER) RETURN VARCHAR2 IS
        v_numero APARTAMENTOS.numero%TYPE;
    BEGIN
        SELECT a.numero INTO v_numero
          FROM CONTRATO_RESIDENTE cr
          JOIN CONTRATOS          c  ON c.id_contrato   = cr.id_contrato
          JOIN APARTAMENTOS       a  ON a.id_apartamento = c.id_apartamento
         WHERE cr.id_residente = p_id_residente
           AND c.estado        = 'ACTIVO'
           AND ROWNUM          = 1;
        RETURN v_numero;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN RETURN NULL;
    END FN_APARTAMENTO_ACTUAL;

    -- ---------------------------------------------------------------
    PROCEDURE SP_DESACTIVAR_RESIDENTE (
        p_id_residente IN  NUMBER,
        p_mensaje      OUT VARCHAR2
    ) IS
        v_activo RESIDENTES.activo%TYPE;
    BEGIN
        SELECT activo INTO v_activo
          FROM RESIDENTES WHERE id_residente = p_id_residente;

        IF v_activo = 0 THEN
            p_mensaje := 'El residente ya est? inactivo.';
            RETURN;
        END IF;

        IF FN_TIENE_CONTRATO_ACTIVO(p_id_residente) = 1 THEN
            p_mensaje := 'No se puede desactivar un residente con contrato ACTIVO vigente.';
            RETURN;
        END IF;

        UPDATE RESIDENTES SET activo = 0
         WHERE id_residente = p_id_residente;
        COMMIT;
        p_mensaje := 'Residente ' || p_id_residente || ' desactivado correctamente.';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            p_mensaje := 'Residente no encontrado (id=' || p_id_residente || ').';
        WHEN OTHERS THEN
            ROLLBACK;
            p_mensaje := 'Error interno: ' || SQLERRM;
    END SP_DESACTIVAR_RESIDENTE;

    -- ---------------------------------------------------------------
    PROCEDURE SP_ACTIVAR_RESIDENTE (
        p_id_residente IN  NUMBER,
        p_mensaje      OUT VARCHAR2
    ) IS
        v_activo RESIDENTES.activo%TYPE;
    BEGIN
        SELECT activo INTO v_activo
          FROM RESIDENTES WHERE id_residente = p_id_residente;

        IF v_activo = 1 THEN
            p_mensaje := 'El residente ya est? activo.';
            RETURN;
        END IF;

        UPDATE RESIDENTES SET activo = 1
         WHERE id_residente = p_id_residente;
        COMMIT;
        p_mensaje := 'Residente ' || p_id_residente || ' reactivado correctamente.';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            p_mensaje := 'Residente no encontrado (id=' || p_id_residente || ').';
        WHEN OTHERS THEN
            ROLLBACK;
            p_mensaje := 'Error interno: ' || SQLERRM;
    END SP_ACTIVAR_RESIDENTE;

END PKG_RESIDENTES;

-- ??????????????????????????????????????????????????????????????????
-- ?9  VISTAS OPERATIVAS
-- ??????????????????????????????????????????????????????????????????
-- -------------------------------------------------------------------
-- 9.1  VW_RESIDENTE_APARTAMENTO
-- Reemplaza el id_apartamento que no existe en RESIDENTES.
-- Muestra el apartamento actual del residente (contrato ACTIVO).
-- Usa CONTRATO_RESIDENTE como puente entre RESIDENTES y CONTRATOS.
-- Filtro: contratos.estado = 'ACTIVO' AND residentes.activo = 1.
-- -------------------------------------------------------------------
CREATE OR REPLACE VIEW VW_RESIDENTE_APARTAMENTO AS
SELECT
    r.id_residente,
    r.nombres || ' ' || r.apellidos        AS nombre_completo,
    td.codigo || '-' || r.numero_documento AS documento,
    r.telefono,
    r.email,
    r.es_menor_edad,
    cr.rol_en_contrato,
    a.id_apartamento,
    a.numero                               AS numero_apartamento,
    a.piso,
    a.tipo                                 AS tipo_apartamento,
    c.id_contrato,
    c.fecha_inicio,
    c.fecha_fin,
    c.valor_mensual,
    c.estado                               AS estado_contrato
FROM RESIDENTES          r
JOIN TIPOS_DOCUMENTO     td  ON td.id_tipo_doc    = r.id_tipo_doc
JOIN CONTRATO_RESIDENTE  cr  ON cr.id_residente   = r.id_residente
JOIN CONTRATOS           c   ON c.id_contrato     = cr.id_contrato
                             AND c.estado         = 'ACTIVO'
JOIN APARTAMENTOS        a   ON a.id_apartamento  = c.id_apartamento
WHERE r.activo = 1;

COMMENT ON TABLE VW_RESIDENTE_APARTAMENTO IS 'Residentes activos con su apartamento actual (contrato ACTIVO). Usa CONTRATO_RESIDENTE como puente. Un residente puede tener varios roles.';

-- -------------------------------------------------------------------
-- 9.2  VW_VISITAS_ACTIVAS ? dashboard del portero
-- Visitas que est?n dentro del edificio en este momento.
-- Usa REGISTRO_VISITA (no PERSONAS_VISITA) para contar visitantes.
-- La ruta al apartamento va por CONTRATO_RESIDENTE ? CONTRATOS.
-- -------------------------------------------------------------------
CREATE OR REPLACE VIEW VW_VISITAS_ACTIVAS AS
SELECT
    v.id_visita,
    a.numero                                       AS numero_apartamento,
    r.nombres || ' ' || r.apellidos                AS residente,
    ra.hora_entrada,
    v.tiempo_validez_min,
    v.cantidad_personas,
    v.notas,
    COUNT(DISTINCT rv.id_visitante)                AS total_visitantes,
    COUNT(DISTINCT veh.id_vehiculo_visita)         AS total_vehiculos,
    u_vig.username                                 AS portero_entrada
FROM VISITAS              v
JOIN CONTRATO_RESIDENTE   cr  ON cr.id_contrato_res = v.id_contrato_res
JOIN CONTRATOS            c   ON c.id_contrato      = cr.id_contrato
JOIN APARTAMENTOS         a   ON a.id_apartamento   = c.id_apartamento
JOIN RESIDENTES           r   ON r.id_residente     = v.id_residente
LEFT JOIN REGISTROS_ACCESO ra ON ra.id_visita       = v.id_visita
LEFT JOIN USUARIOS        u_vig ON u_vig.id_usuario = ra.id_vigilante
LEFT JOIN REGISTRO_VISITA rv  ON rv.id_visita       = v.id_visita
LEFT JOIN VEHICULOS_VISITA veh ON veh.id_visita     = v.id_visita
WHERE v.estado = 'ACTIVA'
GROUP BY
    v.id_visita, a.numero,
    r.nombres, r.apellidos, ra.hora_entrada,
    v.tiempo_validez_min, v.cantidad_personas, v.notas, u_vig.username;

COMMENT ON TABLE VW_VISITAS_ACTIVAS IS 'Dashboard del portero: visitas con estado=ACTIVA. total_visitantes desde REGISTRO_VISITA (visitantes reutilizables).';

-- -------------------------------------------------------------------
-- 9.3  VW_CARTERA_PENDIENTE ? m?dulo de cartera
-- Cuotas pendientes con saldo real (descontando abonos parciales).
-- El residente mostrado es el ARRENDATARIO del contrato
-- (rol_en_contrato = ARRENDATARIO en CONTRATO_RESIDENTE).
-- -------------------------------------------------------------------
CREATE OR REPLACE VIEW VW_CARTERA_PENDIENTE AS
SELECT
    c.id_contrato,
    a.numero                                   AS numero_apartamento,
    r.nombres || ' ' || r.apellidos            AS residente,
    r.email                                    AS correo_residente,
    r.telefono,
    cq.id_cuota,
    cq.anio,
    cq.mes,
    cq.fecha_limite,
    cq.valor_base,
    cq.valor_mora,
    cq.valor_total,
    NVL(SUM(p.valor_pagado), 0)                AS total_pagado,
    cq.valor_total - NVL(SUM(p.valor_pagado), 0) AS saldo_pendiente,
    cq.fecha_limite - TRUNC(SYSDATE)           AS dias_vencimiento,   -- negativo = ya vencida
    cq.estado
FROM CUOTAS_ARRIENDO     cq
JOIN CONTRATOS           c    ON c.id_contrato      = cq.id_contrato
JOIN APARTAMENTOS        a    ON a.id_apartamento   = c.id_apartamento
JOIN CONTRATO_RESIDENTE  cr   ON cr.id_contrato     = c.id_contrato
                              AND cr.rol_en_contrato = 'ARRENDATARIO'
JOIN RESIDENTES          r    ON r.id_residente     = cr.id_residente
LEFT JOIN PAGOS          p    ON p.id_cuota         = cq.id_cuota
WHERE cq.estado IN ('PENDIENTE','VENCIDA','EN_MORA')
GROUP BY
    c.id_contrato, a.numero,
    r.nombres, r.apellidos, r.email, r.telefono,
    cq.id_cuota, cq.anio, cq.mes, cq.fecha_limite,
    cq.valor_base, cq.valor_mora, cq.valor_total, cq.estado
ORDER BY cq.fecha_limite ASC;

COMMENT ON TABLE VW_CARTERA_PENDIENTE IS 'Cuotas pendientes/vencidas/en mora con saldo real. Muestra el ARRENDATARIO del contrato via CONTRATO_RESIDENTE.';

-- -------------------------------------------------------------------
-- 9.4  VW_PARQUEADEROS_VISITANTES ? disponibilidad en tiempo real
-- Estado actual de parqueaderos rotativos de visitantes.
-- placa_actual NULL = puesto libre.
-- -------------------------------------------------------------------
CREATE OR REPLACE VIEW VW_PARQUEADEROS_VISITANTES AS
SELECT
    p.id_parqueadero,
    p.codigo,
    p.tipo,
    p.estado,
    veh.placa                                  AS placa_actual,
    veh.tipo                                   AS tipo_vehiculo_actual,
    v.id_visita                                AS visita_activa,
    ra.hora_entrada                            AS desde
FROM PARQUEADEROS          p
LEFT JOIN VEHICULOS_VISITA veh ON veh.id_parqueadero = p.id_parqueadero
LEFT JOIN VISITAS          v   ON v.id_visita        = veh.id_visita
                               AND v.estado          = 'ACTIVA'
LEFT JOIN REGISTROS_ACCESO ra  ON ra.id_visita       = v.id_visita
                               AND ra.hora_salida    IS NULL
WHERE p.es_visitante = 1
ORDER BY p.tipo, p.codigo;

COMMENT ON TABLE VW_PARQUEADEROS_VISITANTES IS 'Disponibilidad de parqueaderos rotativos en tiempo real. placa_actual NULL = puesto libre.';

-- -------------------------------------------------------------------
-- 9.5  VW_VISITANTES_FRECUENTES ? panel de visitas frecuentes
-- Lista los visitantes frecuentes de cada residente con estad?sticas
-- de visitas anteriores para pre-poblar el formulario r?pido.
-- Filtrar activo = 1 para el panel principal del residente.
-- Filtrar activo = 0 para la vista de "frecuentes ocultos".
-- -------------------------------------------------------------------
CREATE OR REPLACE VIEW VW_VISITANTES_FRECUENTES AS
SELECT
    fr.id_frecuente,
    fr.id_residente,
    vi.id_visitante,
    vi.nombres || ' ' || vi.apellidos          AS nombre_visitante,
    td.codigo || '-' || vi.numero_documento    AS documento,
    COUNT(DISTINCT rv.id_visita)               AS total_visitas,
    MAX(vis.fecha_registro)                    AS ultima_visita,
    -- ?ltimo veh?culo usado (pre-pobla el formulario r?pido)
    MAX(veh.placa) KEEP (
        DENSE_RANK LAST ORDER BY vis.fecha_registro NULLS FIRST
    )                                          AS ultima_placa,
    MAX(veh.tipo) KEEP (
        DENSE_RANK LAST ORDER BY vis.fecha_registro NULLS FIRST
    )                                          AS ultimo_tipo_vehiculo,
    MAX(veh.descripcion_tipo) KEEP (
        DENSE_RANK LAST ORDER BY vis.fecha_registro NULLS FIRST
    )                                          AS ultima_descripcion_tipo,
    fr.activo
FROM FRECUENTES_RESIDENTE  fr
JOIN VISITANTES            vi  ON vi.id_visitante  = fr.id_visitante
JOIN TIPOS_DOCUMENTO       td  ON td.id_tipo_doc   = vi.id_tipo_doc
LEFT JOIN REGISTRO_VISITA  rv  ON rv.id_visitante  = fr.id_visitante
LEFT JOIN VISITAS          vis ON vis.id_visita     = rv.id_visita
                               AND vis.id_residente = fr.id_residente
LEFT JOIN VEHICULOS_VISITA veh ON veh.id_visita     = vis.id_visita
                               AND rv.id_vehiculo_visita = veh.id_vehiculo_visita
GROUP BY
    fr.id_frecuente, fr.id_residente,
    vi.id_visitante, vi.nombres, vi.apellidos,
    td.codigo, vi.numero_documento,
    fr.activo;

COMMENT ON TABLE VW_VISITANTES_FRECUENTES IS 'Panel de visitantes frecuentes por residente. Filtrar activo=1 para el panel principal. Incluye ?ltimo veh?culo para pre-poblar SP_LIBERAR_VISITA_FRECUENTE.';

-- ??????????????????????????????????????????????????????????????????
-- ?10  DATOS SEMILLA
-- ??????????????????????????????????????????????????????????????????
-- IMPORTANTE: el password_hash es un placeholder.
-- En producci?n debe reemplazarse con un hash generado por
-- BCryptPasswordEncoder de Spring Security:
--   BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
--   String hash = encoder.encode("contrase?a_real");
-- ??????????????????????????????????????????????????????????????????

INSERT INTO USUARIOS (id_residente, username, password_hash, rol)
VALUES (
    NULL,
    'admin',
    '$2a$12$7/e3JZhNQGXv949JtyHHBOojiprheNCb/FuOgdAA0vjKbFbHHsHpC',
    'ADMINISTRADOR'
);

COMMIT;

-- ??????????????????????????????????????????????????????????????????
-- RESUMEN DEL ESQUEMA v5.4
-- ??????????????????????????????????????????????????????????????????
-- Secuencias  : 21 (SEC_TIPOS_DOCUMENTO ... SEC_BUZON, SEC_MULTAS, SEQ_QUEJAS_SUGERENCIAS)
-- Tablas      : 20
--   1.  TIPOS_DOCUMENTO
--   2.  APARTAMENTOS
--   3.  PARQUEADEROS
--   4.  RESIDENTES
--   5.  TUTORES              (nueva en v4.0 - reemplaza autorreferencia)
--   6.  USUARIOS
--   7.  CONTRATOS
--   8.  CONTRATO_RESIDENTE   (nueva en v4.0 - N:M CONTRATOS ? RESIDENTES)
--   9.  CUOTAS_ARRIENDO
--   10. PAGOS
--   11. ALERTAS_PAGO
--   12. MULTAS               (nueva en v4.3 - sanciones ruido/parqueadero)
--   13. VISITAS              (+ cantidad_personas en v4.1)
--   14. QR_ACCESOS
--   15. VISITANTES           (nueva en v4.0 - reemplaza PERSONAS_VISITA)
--   16. VEHICULOS_VISITA     (+ descripcion_tipo en v4.1)
--   17. REGISTRO_VISITA      (nueva en v4.0 - reemplaza PERSONAS_VEHICULO)
--   18. FRECUENTES_RESIDENTE (nueva en v4.1 - m?dulo visitas frecuentes)
--   19. REGISTROS_ACCESO
--   20. QUEJAS_SUGERENCIAS   (nueva en v5.0 - m?dulo quejas, sugerencias y apelaciones)
-- Triggers    : 17
--   TRG_RESIDENTES_UPD ? TRG_CONTRATOS_UPD ? TRG_CUOTAS_UPD
--   TRG_VISITAS_UPD ? TRG_ACCESO_UPD ? TRG_CONT_SYNC_APARTAMENTO
--   TRG_QR_USAR ? TRG_ACCESO_SALIDA ? TRG_APARTAMENTOS_UPD
--   TRG_PARQUEADEROS_UPD ? TRG_TUTORES_UPD ? TRG_USUARIOS_UPD
--   TRG_VISITANTES_UPD ? TRG_VEHICULOS_UPD
--   TRG_AUTO_FRECUENTE (v4.1) ? TRG_FRECUENTES_UPD (v4.1)
--   TRG_VIS_VALIDAR_RESIDENTE (v4.1)
-- Vistas      : 5
--   VW_RESIDENTE_APARTAMENTO ? VW_VISITAS_ACTIVAS
--   VW_CARTERA_PENDIENTE ? VW_PARQUEADEROS_VISITANTES
--   VW_VISITANTES_FRECUENTES (v4.1)
-- Procedimientos: 2
--   SP_VALIDAR_QR               (validaci?n at?mica con FOR UPDATE)
--   SP_LIBERAR_VISITA_FRECUENTE (registro r?pido de visitante frecuente, v4.1)
-- Funciones aut?nomas: 3
--   FN_SALDO_CUOTA              (saldo pendiente de una cuota)
--   FN_CALCULAR_MORA            (mora proporcional por d?as vencidos)
--   FN_MINUTOS_RESTANTES_QR     (vigencia restante de un c?digo QR)
-- Paquetes PL/SQL: 3
--   PKG_PAGOS       (FN_SALDO_CUOTA, FN_CALCULAR_MORA, SP_REGISTRAR_PAGO, SP_APLICAR_MORA)
--   PKG_VISITAS     (FN_TIENE_QR_ACTIVO, FN_MINUTOS_RESTANTES_QR, SP_CANCELAR_VISITA, SP_GENERAR_QR_VISITA)
--   PKG_RESIDENTES  (FN_TIENE_CONTRATO_ACTIVO, FN_APARTAMENTO_ACTUAL, SP_DESACTIVAR_RESIDENTE, SP_ACTIVAR_RESIDENTE)
-- ?ndices     : ~50
--   2 funcionales ?nicos (UIX_CONT_APT_ACTIVO, UIX_ALERTA_CUOTA_DIA)
--   ~18 impl?citos por UNIQUE constraints
--   ~30 expl?citos por FKs y b?squedas frecuentes  (+ 8 nuevos en v5.2)
--   (IDX_FREC_RESIDENTE eliminado en v4.2 por redundancia con UQ_FREC_RES_VIS)
-- ??????????????????????????????????????????????????????????????????
-- FIN DEL SCRIPT ? modelo_relacional_v4.sql  v5.4
-- ??????????????????????????????????????????????????????????????????
