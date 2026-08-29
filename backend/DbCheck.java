import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

public class DbCheck {
    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        props.put("user", "SAED_APP");
        props.put("password", "SaedApp2026!");
        String url = "jdbc:oracle:thin:@(description=(retry_count=20)(retry_delay=3)(address=(protocol=tcps)(port=1522)(host=adb.sa-bogota-1.oraclecloud.com))(connect_data=(service_name=geadcc8b471d081_saed2_high.adb.oraclecloud.com))(security=(ssl_server_dn_match=yes)))";
        try (Connection conn = DriverManager.getConnection(url, props);
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
            
            try (ResultSet rs = stmt.executeQuery("SELECT SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD') FROM DUAL")) {
                if (rs.next()) {
                    System.out.println("CTX_PROP: " + rs.getString(1));
                }
            }
        }
    }
}
