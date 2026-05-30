package com.edificio.admin.service;

import com.edificio.admin.dao.UsuarioDAO;
import com.edificio.admin.exception.DatosInvalidosException;
import com.edificio.admin.exception.RegistroNoEncontradoException;
import com.edificio.admin.model.Usuario;
import com.edificio.admin.model.enums.TipoRol;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.SQLException;
import java.util.List;

/**
 * Logica de negocio para USUARIOS.
 *
 * Autenticacion: se recupera el usuario por username y se verifica que
 * el passwordHash en BD coincida con la contrasena ingresada usando BCrypt.
 *
 * Registro / actualizar: el password_hash se almacena como hash BCrypt.
 * Si el valor recibido ya tiene el prefijo BCrypt ($2a$ / $2b$) se asume
 * que viene de la BD y no se re-hashea (util en actualizar cuando no se
 * cambia la contrasena).
 */
public class UsuarioService {

    private final UsuarioDAO usuarioDAO;

    public UsuarioService() {
        this.usuarioDAO = new UsuarioDAO();
    }

    /**
     * Autentica un usuario.
     * @return el Usuario autenticado
     * @throws DatosInvalidosException si los campos estan vacios
     * @throws RegistroNoEncontradoException si el username no existe o esta inactivo
     */
    public Usuario autenticar(String username, String contrasenaPlana) throws SQLException {
        if (username == null || username.isBlank())
            throw new DatosInvalidosException("El nombre de usuario es obligatorio.");
        if (contrasenaPlana == null || contrasenaPlana.isBlank())
            throw new DatosInvalidosException("La contrasena es obligatoria.");

        Usuario usuario = usuarioDAO.findByUsername(username.trim());

        if (usuario == null || !usuario.isActivo())
            throw new RegistroNoEncontradoException("Usuario no encontrado o inactivo.");

        // Verificar contrasena con BCrypt
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
        usuario.setPasswordHash(hashear(usuario.getPasswordHash()));
        return usuarioDAO.insert(usuario);
    }

    public void actualizar(Usuario usuario) throws SQLException {
        validarId(usuario.getIdUsuario());
        if (usuario.getUsername() != null)
            usuario.setUsername(usuario.getUsername().toLowerCase().trim());
        validar(usuario);
        usuario.setPasswordHash(hashear(usuario.getPasswordHash()));
        usuarioDAO.update(usuario);
    }

    public void desactivar(Integer id) throws SQLException {
        validarId(id);
        usuarioDAO.delete(id);
    }

    // ---- helpers ----

    /**
     * Hashea el password con BCrypt solo si aun no tiene el prefijo BCrypt.
     * Permite llamar a actualizar() sin re-hashear un hash que ya viene de la BD.
     */
    private String hashear(String password) {
        if (password.startsWith("$2a$") || password.startsWith("$2b$")) {
            return password; // ya es un hash BCrypt
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
