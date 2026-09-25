-- V5.29: Permitir id_propiedad NULL en USUARIO_ASIGNACIONES cuando la asignacion es INACTIVA
-- Permite que administradores de propiedad sin propiedades asignadas permanezcan registrados como inactivos.

CREATE OR REPLACE TRIGGER TRG_ASIGNACION_VALIDA_SCOPE
    BEFORE INSERT OR UPDATE ON USUARIO_ASIGNACIONES
    FOR EACH ROW
DECLARE
    v_alcance ROLES.alcance%TYPE;
BEGIN
    SELECT alcance INTO v_alcance FROM ROLES WHERE id_rol = :NEW.id_rol;

    IF v_alcance = 'GLOBAL' THEN
        IF :NEW.id_organizacion IS NOT NULL OR :NEW.id_propiedad IS NOT NULL OR :NEW.id_unidad IS NOT NULL THEN
            RAISE_APPLICATION_ERROR(-20010, 'Un rol de alcance GLOBAL no debe tener organización/propiedad/unidad asociada.');
        END IF;
    ELSIF v_alcance IN ('ORGANIZACION', 'PROPIEDADES_SELECCIONADAS') THEN
        IF :NEW.id_organizacion IS NULL THEN
            RAISE_APPLICATION_ERROR(-20011, 'Un rol de alcance ORGANIZACION requiere id_organizacion.');
        END IF;
    ELSIF v_alcance = 'PROPIEDAD' THEN
        IF :NEW.id_organizacion IS NULL THEN
            RAISE_APPLICATION_ERROR(-20011, 'Un rol de alcance PROPIEDAD requiere id_organizacion.');
        END IF;
        -- Si la asignación está ACTIVA, requiere obligatoriamente id_propiedad.
        -- Si está INACTIVA, se permite id_propiedad NULL para mantener administradores inactivos sin propiedad asignada.
        IF :NEW.estado IN ('ACTIVA', 'ACTIVO') AND :NEW.id_propiedad IS NULL THEN
            RAISE_APPLICATION_ERROR(-20012, 'Un rol de alcance PROPIEDAD requiere id_propiedad cuando la asignación está activa.');
        END IF;
    ELSIF v_alcance = 'UNIDAD' THEN
        IF :NEW.id_organizacion IS NULL OR :NEW.id_propiedad IS NULL OR :NEW.id_unidad IS NULL THEN
            RAISE_APPLICATION_ERROR(-20013, 'Un rol de alcance UNIDAD requiere id_organizacion, id_propiedad e id_unidad.');
        END IF;
    END IF;
END;
/
