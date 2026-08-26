package com.edificio.admin.view.controladores;

import com.edificio.admin.model.Usuario;
import com.edificio.admin.model.enums.TipoRol;
import com.edificio.admin.service.UsuarioService;
import com.edificio.admin.util.ValidadorCampos;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CRUD de usuarios del sistema.
 * Solo accesible para ADMIN (la visibilidad en el Dashboard se controla por rol).
 *
 * Contraseña al editar:
 *   - Si el campo queda vacío se mantiene el hash actual (no se re-hashea).
 *   - Si se ingresa una nueva contraseña se hashea con BCrypt en UsuarioService.
 */
public class UsuarioController implements ControladorVista {

    // ── Tabla ──
    @FXML private TableView<Usuario>               tablaUsuarios;
    @FXML private TableColumn<Usuario, Integer>    colUsrId;
    @FXML private TableColumn<Usuario, String>     colUsrUsername;
    @FXML private TableColumn<Usuario, String>     colUsrRol;
    @FXML private TableColumn<Usuario, String>     colUsrActivo;
    @FXML private TableColumn<Usuario, LocalDateTime> colUsrLogin;

    // ── Formulario ──
    @FXML private TextField     txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private ComboBox<TipoRol> cmbRol;
    @FXML private TextField     txtIdResidente;
    @FXML private CheckBox      chkActivo;

    private final UsuarioService service = new UsuarioService();
    private final ObservableList<Usuario> lista = FXCollections.observableArrayList();
    private Usuario seleccionado;

    @FXML
    private void initialize() {
        cmbRol.getItems().setAll(TipoRol.values());

        // ── Restricciones de entrada ──────────────────────────────────────
        ValidadorCampos.limitar(txtUsername,    50);   // USUARIOS.username  VARCHAR2(50)
        ValidadorCampos.soloDigitos(txtIdResidente, 10);  // id_residente es NUMBER

        configurarTabla();
        cargarDatos();
    }

    @Override
    public void inicializar() {
        cargarDatos();
        limpiarFormulario();
    }

    // ── Tabla ─────────────────────────────────────────────────────────────────

    private void configurarTabla() {
        colUsrId.setCellValueFactory(new PropertyValueFactory<>("idUsuario"));
        colUsrUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colUsrRol.setCellValueFactory(new PropertyValueFactory<>("rol"));
        colUsrLogin.setCellValueFactory(new PropertyValueFactory<>("ultimoLogin"));

        // Columna Activo — Sí / No
        colUsrActivo.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                } else {
                    setText(((Usuario) getTableRow().getItem()).isActivo() ? "Sí" : "No");
                }
            }
        });

        tablaUsuarios.setItems(lista);
        tablaUsuarios.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, nuevo) -> { if (nuevo != null) cargarEnFormulario(nuevo); });
    }

    private void cargarDatos() {
        try {
            List<Usuario> todos = service.listarTodos();
            lista.setAll(todos);
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error", "Error al cargar usuarios: " + e.getMessage());
        }
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @FXML
    private void guardarUsuario() {
        try {
            if (cmbRol.getValue() == null) {
                mostrarAlerta(Alert.AlertType.WARNING, "Validación", "Seleccione un rol.");
                return;
            }
            if (txtUsername.getText().isBlank()) {
                mostrarAlerta(Alert.AlertType.WARNING, "Validación", "El username es obligatorio.");
                return;
            }

            Usuario u = (seleccionado != null) ? seleccionado : new Usuario();
            u.setUsername(txtUsername.getText().trim());
            u.setRol(cmbRol.getValue());
            u.setActivo(chkActivo.isSelected());

            // ID Residente (opcional)
            String idResStr = txtIdResidente.getText().trim();
            if (idResStr.isEmpty()) {
                u.setIdResidente(null);
            } else {
                try {
                    u.setIdResidente(Integer.parseInt(idResStr));
                } catch (NumberFormatException ex) {
                    mostrarAlerta(Alert.AlertType.WARNING, "Validación", "ID Residente debe ser un número entero.");
                    return;
                }
            }

            // Contraseña
            String pwd = txtPassword.getText();
            if (seleccionado == null) {
                // Registro nuevo — contraseña obligatoria
                if (pwd.isBlank()) {
                    mostrarAlerta(Alert.AlertType.WARNING, "Validación", "La contraseña es obligatoria para un nuevo usuario.");
                    return;
                }
                u.setPasswordHash(pwd);
                Integer id = service.registrar(u);
                mostrarAlerta(Alert.AlertType.INFORMATION, "Éxito", "Usuario registrado con ID: " + id);
            } else {
                // Edición — si la contraseña está vacía, mantener la hash actual
                if (!pwd.isBlank()) {
                    u.setPasswordHash(pwd);   // UsuarioService.hashear() hará el BCrypt
                }
                // Si pwd está vacío, u.getPasswordHash() aún tiene el hash anterior (cargado en cargarEnFormulario)
                service.actualizar(u);
                mostrarAlerta(Alert.AlertType.INFORMATION, "Éxito", "Usuario actualizado correctamente.");
            }

            cargarDatos();
            limpiarFormulario();

        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error", e.getMessage());
        }
    }

    @FXML
    private void desactivarUsuario() {
        Usuario sel = tablaUsuarios.getSelectionModel().getSelectedItem();
        if (sel == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Advertencia", "Seleccione un usuario de la tabla.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar desactivación");
        confirm.setHeaderText(null);
        confirm.setContentText("¿Desactivar al usuario '" + sel.getUsername() + "'?\n"
                + "No podrá iniciar sesión hasta ser reactivado.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                service.desactivar(sel.getIdUsuario());
                cargarDatos();
                limpiarFormulario();
            } catch (Exception e) {
                mostrarAlerta(Alert.AlertType.ERROR, "Error", e.getMessage());
            }
        }
    }

    @FXML
    private void limpiarFormulario() {
        seleccionado = null;
        txtUsername.clear();
        txtPassword.clear();
        cmbRol.setValue(null);
        txtIdResidente.clear();
        chkActivo.setSelected(true);
        tablaUsuarios.getSelectionModel().clearSelection();
    }

    private void cargarEnFormulario(Usuario u) {
        seleccionado = u;
        txtUsername.setText(u.getUsername());
        txtPassword.clear();          // nunca mostramos el hash — campo vacío = no cambiar
        cmbRol.setValue(u.getRol());
        txtIdResidente.setText(u.getIdResidente() != null ? String.valueOf(u.getIdResidente()) : "");
        chkActivo.setSelected(u.isActivo());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String contenido) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(contenido);
        alert.showAndWait();
    }
}
