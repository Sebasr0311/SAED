# SAED 2.0 — V4.1 SECURITY ANALYSIS

## Principios Cumplidos
1. **Zero-Trust (Mínimo Privilegio)**: El estado `BOOTSTRAP` reduce el acceso a la lectura de asignaciones propias. No otorga acceso a módulos de negocio (Propiedades, Residentes, etc., siguen retornando `1=0` al no tener la organización seteada).
2. **Protección contra Spoofing**: `SET_CONTEXT` comprueba los parámetros (`id_organizacion`, `id_propiedad`, `rol_codigo`) cruzándolos *físicamente* contra `USUARIO_ASIGNACIONES` antes de transicionar al estado `BUSINESS`. Un cliente que envíe un `id_organizacion` falso verá su solicitud rechazada por validación interna o por RLS interno.
3. **Mantenimiento del RLS**: No se desactiva `DBMS_RLS`. La función de predicado se hace *state-aware*, lo que es una práctica recomendada en arquitecturas Oracle VPD avanzadas (Oracle Context-driven VPD).

## Superficie de Ataque
- **Vulnerabilidad de transición**: ¿Puede un atacante quedarse en estado `BOOTSTRAP` para causar daño?
  - *Mitigación*: En `BOOTSTRAP`, las políticas de modificación (`POL_RLS_MUT_...`) de tablas core siguen requiriendo la organización, bloqueando inserciones y actualizaciones.
- **Spoofing de identidad**: ¿Puede establecer `SET_BOOTSTRAP_CONTEXT` con otro `id_usuario`?
  - *Mitigación*: Spring solo obtiene el `id_usuario` firmando criptográficamente desde el token JWT generado tras la validación BCrypt. El cliente no puede inyectarlo.

## Nivel de Privilegios
- La aplicación (`SAED_APP`) **NO recibe** el privilegio `EXEMPT ACCESS POLICY`.
- Las consultas se resuelven nativamente gracias a que el RLS entrega una regla dinámica adaptada al ciclo de vida de la sesión (Auth → Context → Business).
