package com.edificio.admin.service;

import com.edificio.admin.dao.UsuarioDAO;
import com.edificio.admin.exception.DatosInvalidosException;
import com.edificio.admin.exception.RegistroNoEncontradoException;
import com.edificio.admin.model.Usuario;
import com.edificio.admin.model.enums.TipoRol;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.SQLException;
import java.util.List;

public class UsuarioService {

    private final UsuarioDAO usuarioDAO;

    public UsuarioService() {
        this.usuarioDAO = new UsuarioDAO();
    }

    public Usuario autenticar(String username, String contrasenaPlana) throws SQLException {
        if (username == null || username.isBlank())
            throw new DatosInvalidosException("El nombre de usuario es obligatorio.");
        if (contrasenaPlana == null || contrasenaPlana.isBlank())
            throw new DatosInvalidosException("La contrasena es obligatoria.");

        Usuario usuario = usuarioDAO.findByUsername(username.trim());

        if (usuario == null || !usuario.isActivo())
            throw new RegistroNoEncontradoException("Usuario no encontrado o inactivo.");

        if (!BCrypt.checkpw(contrasenaPlana, usuario.getPasswordHash()))
            throw new DatosInvalidosException("Contrasena incorrecta.");

        usuarioDAO.registrarLogin(usuario.getIdUsuario());
        return usuario;
    }

    public List<Usuario> listarTodos() throws SQLException {
        return usuarioDAO.findAll();
    }

    public Usuario buscarPorId(Integer id) throws SQLException {
        validarId(id);
        Usuario u = usuarioDAO.findById(id);
        if (u == null) throw new RegistroNoEncontradoException("Usuario no encontrado: " + id);
        return u;
    }

    public Integer registrar(Usuario usuario) throws SQLException {
        usuario.setUsername(usuario.getUsername().toLowerCase().trim());
        validar(usuario);
        if (usuarioDAO.findByUsername(usuario.getUsername()) != null)
            throw new DatosInvalidosException("El username '" + usuario.getUsername() + "' ya existe.");
        if (usuario.getIdResidente() != null && usuarioDAO.existsByResidente(usuario.getIdResidente()))
            throw new DatosInvalidosException("El residente ya tiene un usuario activo.");
        usuario.setPasswordHash(hashear(usuario.getPasswordHash()));
        return usuarioDAO.insert(usuario);
    }

    public void actualizar(Usuario usuario) throws SQLException {
        validarId(usuario.getIdUsuario());
        if (usuario.getUsername() != null)
            usuario.setUsername(usuario.getUsername().toLowerCase().trim());
        validar(usuario);
        Usuario existente = usuarioDAO.findById(usuario.getIdUsuario());
        // Verificar idResidente no duplicado (excluyendose a si mismo)
        if (usuario.getIdResidente() != null && usuarioDAO.existsByResidente(usuario.getIdResidente())) {
            if (existente.getIdResidente() == null || !existente.getIdResidente().equals(usuario.getIdResidente()))
                throw new DatosInvalidosException("El residente ya tiene un usuario activo.");
        }
        usuario.setPasswordHash(hashear(usuario.getPasswordHash()));
        usuarioDAO.update(usuario);
    }

    public void toggleActivo(Integer id) throws SQLException {
        validarId(id);
        Usuario u = usuarioDAO.findById(id);
        if (u == null) throw new RegistroNoEncontradoException("Usuario no encontrado: " + id);
        if (u.isActivo()) {
            usuarioDAO.delete(id); // soft-delete (activo=0)
        } else {
            usuarioDAO.reactivar(id); // activo=1
        }
    }

    public void eliminar(Integer idEliminar, Integer idAdmin, String adminPassword) throws SQLException {
        validarId(idEliminar);
        validarId(idAdmin);
        if (adminPassword == null || adminPassword.isBlank())
            throw new DatosInvalidosException("La contrasena del administrador es obligatoria.");
        Usuario admin = usuarioDAO.findById(idAdmin);
        if (admin == null) throw new RegistroNoEncontradoException("Administrador no encontrado.");
        if (!BCrypt.checkpw(adminPassword, admin.getPasswordHash()))
            throw new DatosInvalidosException("Contrasena de administrador incorrecta.");
        Usuario u = usuarioDAO.findById(idEliminar);
        if (u == null) throw new RegistroNoEncontradoException("Usuario no encontrado: " + idEliminar);
        usuarioDAO.hardDelete(idEliminar);
    }

    public void desactivar(Integer id) throws SQLException {
        validarId(id);
        usuarioDAO.delete(id);
    }

    // ---- helpers ----

    private String hashear(String password) {
        if (password.startsWith("$2a$") || password.startsWith("$2b$")) {
            return password;
        }
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    // ---- validaciones ----

    private void validar(Usuario u) {
        if (u.getUsername() == null || u.getUsername().isBlank())
            throw new DatosInvalidosException("El username es obligatorio.");
        if (u.getPasswordHash() == null || u.getPasswordHash().isBlank())
            throw new DatosInvalidosException("El password es obligatorio.");
        if (u.getRol() == null)
            throw new DatosInvalidosException("El rol es obligatorio.");
        if (u.getIdResidente() != null && u.getRol() != TipoRol.RESIDENTE)
            throw new DatosInvalidosException("Si se asigna un residente, el rol debe ser RESIDENTE.");
    }

    private void validarId(Integer id) {
        if (id == null || id <= 0)
            throw new DatosInvalidosException("ID de usuario invalido.");
    }
}
