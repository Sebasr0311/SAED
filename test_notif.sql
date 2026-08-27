BEGIN
  PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(2);
  PKG_SAED_SESSION.SET_CONTEXT(p_id_usuario => 2, p_id_organizacion => 1, p_id_propiedad => 1, p_rol_codigo => 'ADMIN_PROPIEDAD');
END;
/
SELECT count(*) FROM NOTIFICACIONES;
EXIT;
