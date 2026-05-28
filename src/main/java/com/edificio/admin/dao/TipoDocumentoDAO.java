package com.edificio.admin.dao;

import com.edificio.admin.model.TipoDocumento;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO de solo lectura para la tabla TIPOS_DOCUMENTO.
 * Los tipos de documento son un catálogo mantenido en BD; no se crean desde la app.
 */
public class TipoDocumentoDAO {

    private Connection conn() {
        return ConexionBD.getInstancia().getConexion();
    }

    /**
     * Devuelve todos los tipos de documento activos, ordenados por id_tipo_doc.
     * Usar para poblar ComboBoxes en la UI.
     */
    public List<TipoDocumento> findActivos() throws SQLException {
        List<TipoDocumento> lista = new ArrayList<>();
        String sql = "SELECT id_tipo_doc, codigo, descripcion, activo "
                   + "FROM   TIPOS_DOCUMENTO "
                   + "WHERE  activo = 1 "
                   + "ORDER  BY id_tipo_doc";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new TipoDocumento(
                    rs.getInt("id_tipo_doc"),
                    rs.getString("codigo"),
                    rs.getString("descripcion"),
                    rs.getInt("activo") == 1
                ));
            }
        }
        return lista;
    }
}
