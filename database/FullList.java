import java.sql.*;
public class FullList {
    public static void main(String[] a) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:oracle:thin:@localhost:1521/xepdb1","RESIDENCIAL","Residencial2024#")) {
            ResultSet rs = c.createStatement().executeQuery(
                "SELECT u.username, u.rol, r.nombres||' '||r.apellidos nombre, a.numero "+
                "FROM usuarios u "+
                "JOIN residentes r ON u.id_residente=r.id_residente "+
                "JOIN contrato_residente cr ON r.id_residente=cr.id_residente "+
                "JOIN contratos c ON cr.id_contrato=c.id_contrato "+
                "JOIN apartamentos a ON c.id_apartamento=a.id_apartamento "+
                "WHERE u.activo=1 AND c.estado='ACTIVO' "+
                "ORDER BY u.id_usuario");
            while (rs.next()) {
                System.out.println(rs.getString("username")+" | "+rs.getString("rol")+" | "+rs.getString("nombre")+" | Apt "+rs.getString("numero"));
            }
        }
    }
}
