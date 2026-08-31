import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class Parche {
    public static void main(String[] args) {
        try {
            Connection conn = DriverManager.getConnection(
                "jdbc:oracle:thin:@localhost:1521/XEPDB1", "SAED_V39_FINAL_TEST", "saed2026"
            );
            Statement stmt = conn.createStatement();
            stmt.executeUpdate("INSERT INTO ADMINISTRADORES_SAED (ID_ADMINISTRADOR, ID_USUARIO, NIVEL_ACCESO, ESTADO) VALUES (1, 1, 'GLOBAL', 'ACTIVO')");
            System.out.println("Parche aplicado exitosamente.");
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
