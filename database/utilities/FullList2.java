import java.sql.*;
public class FullList2 {
    public static void main(String[] a) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:oracle:thin:@localhost:1521/xepdb1","RESIDENCIAL","Residencial2024#")) {
            ResultSet rs = c.createStatement().executeQuery(
                "SELECT u.username, u.rol, r.nombres||' '||r.apellidos nombre, a.numero "+
                "FROM usuarios u "+
                "JOIN residentes r ON u.id_residente=r.id_residente "+
                "LEFT JOIN contrato_residente cr ON r.id_residente=cr.id_residente "+
                "LEFT JOIN contratos c ON cr.id_contrato=c.id_contrato "+
                "LEFT JOIN apartamentos a ON c.id_apartamento=a.id_apartamento "+
                "WHERE u.activo=1 "+
                "ORDER BY u.id_usuario");
            while (rs.next()) {
                String apt = rs.getString("numero");
                if (apt == null) apt = "-";
                System.out.println(rs.getString("username")+" | "+rs.getString("rol")+" | "+rs.getString("nombre")+" | Apt "+apt);
            }
        }
    }
}
