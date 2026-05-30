import java.sql.*;
public class Cols {
    public static void main(String[] a) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:oracle:thin:@localhost:1521/xepdb1","RESIDENCIAL","Residencial2024#")) {
            ResultSet rs = c.createStatement().executeQuery("SELECT column_name FROM user_tab_cols WHERE table_name='RESIDENTES' ORDER BY column_id");
            while (rs.next()) System.out.println(rs.getString(1));
        }
    }
}
