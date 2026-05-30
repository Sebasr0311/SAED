import java.sql.*;
public class ListFull {
    public static void main(String[] a) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:oracle:thin:@localhost:1521/xepdb1","RESIDENCIAL","Residencial2024#")) {
            ResultSet rs = c.createStatement().executeQuery(
                "SELECT u.username, u.rol, r.primer_nombre||' '||r.primer_apellido nombre, a.numero "+
                "FROM usuarios u "+
                "LEFT JOIN residentes r ON u.id_residente=r.id_residente "+
                "LEFT JOIN apartamentos a ON r.id_apartamento=a.id_apartamento "+
                "WHERE u.activo=1 ORDER BY u.id_usuario");
            while (rs.next()) {
                String apt = rs.getString("numero");
                if (apt == null) apt = "-";
                System.out.println(rs.getString("username")+" | "+rs.getString("rol")+" | "+rs.getString("nombre")+" | Apt "+apt);
            }
        }
    }
}
