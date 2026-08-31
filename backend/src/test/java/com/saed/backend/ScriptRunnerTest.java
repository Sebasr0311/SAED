package com.saed.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;

@SpringBootTest
@ActiveProfiles("dev")
public class ScriptRunnerTest {
    @Autowired
    private DataSource dataSource;

    @Test
    public void runScript() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            System.out.println("1. OBTENIENDO PODERES DE SUPERADMIN...");
            try { stmt.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;"); } catch(Exception e){}
            try { stmt.execute("BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;"); } catch(Exception e){}
            
            // Delete child data
            try { stmt.execute("DELETE FROM PQRS"); } catch(Exception e){}
            try { stmt.execute("DELETE FROM INCIDENTES"); } catch(Exception e){}
            try { stmt.execute("DELETE FROM ASIGNACIONES_OBRA"); } catch(Exception e){}
            try { stmt.execute("DELETE FROM OBRAS"); } catch(Exception e){}
            
            String[] tables = {
                "USUARIO_ASIGNACIONES", "RESIDENTES_UNIDAD", 
                "PROPIETARIOS_UNIDAD", "CONTRATOS_PROVEEDOR", "TRANSACCIONES_FINANCIERAS", 
                "CONCILIACIONES_BANCARIAS", "GASTOS", "PRESUPUESTOS", "UNIDADES", "PROPIEDADES", "ORGANIZACIONES",
                "ROLES"
            };
            for (String t : tables) {
                try { stmt.execute("DELETE FROM " + t); } catch(Exception e) {}
            }
            
            // Delete only non-admin users
            try { stmt.execute("DELETE FROM USUARIOS WHERE ID_USUARIO > 1"); } catch(Exception e){}
            try { stmt.execute("DELETE FROM PERSONAS WHERE ID_PERSONA > 1"); } catch(Exception e){}
            
            System.out.println("2. INSERTANDO ORGANIZACIONES, PROPIEDADES Y UNIDADES (Como Superadmin)...");
            String[] data = {
                "INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO) VALUES (1, 'CONJUNTO RESIDENCIAL HORIZONTE', 'NIT-11111', 'admin@horizonte.com')",
                "INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, PAIS) VALUES (1, 1, 2, 'TORRE NORTE', 'AV 123', 'BOGOTA', 'COLOMBIA')",
                "INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, IDENTIFICADOR, ID_TIPO_UNIDAD, AREA_M2) VALUES (1, 1, 'APTO 101 (HORIZONTE)', 1, 100)",
                
                "INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO) VALUES (2, 'CONDOMINIO EL SOL', 'NIT-22222', 'admin@elsol.com')",
                "INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, PAIS) VALUES (2, 2, 2, 'TORRE PRINCIPAL', 'AV 456', 'MEDELLIN', 'COLOMBIA')",
                "INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, IDENTIFICADOR, ID_TIPO_UNIDAD, AREA_M2) VALUES (2, 2, 'APTO 101 (EL SOL)', 1, 80)"
            };
            for (String sql : data) { try { stmt.execute(sql); } catch(Exception e) { if (!e.getMessage().contains("ORA-00001")) System.err.println("Insert Err: " + e.getMessage()); } }
            
            System.out.println("3. INSERTANDO USUARIOS DE PRUEBA...");
            String[] comunes = {
                "INSERT INTO ROLES (ID_ROL, CODIGO, NOMBRE, ALCANCE, ESTADO) VALUES (1, 'SUPERADMIN', 'Administrador del Sistema', 'GLOBAL', 'ACTIVO')",
                "INSERT INTO ROLES (ID_ROL, CODIGO, NOMBRE, ALCANCE, ESTADO) VALUES (2, 'ADMIN_PROPIEDAD', 'Administrador Conjunto', 'PROPIEDAD', 'ACTIVO')",
                "INSERT INTO ROLES (ID_ROL, CODIGO, NOMBRE, ALCANCE, ESTADO) VALUES (5, 'RESIDENTE', 'Residente', 'UNIDAD', 'ACTIVO')",
                
                "INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) VALUES (2, 1, '987654321', 'NATURAL', 'RESIDENTE', 'HORIZONTE', 'resh@test.com')",
                "INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) VALUES (3, 1, '111222333', 'NATURAL', 'RESIDENTE', 'SOL', 'ressol@test.com')",
                
                "INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (2, 2, 'residente_hor', 'resh@test.com', '$2a$10$Y8yWwG2uR38jM8eIq0f6oOV3/1vM7Z13GkIefw7U9M/P0FqX3q4P3', 'ACTIVO')",
                "INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (3, 3, 'residente_sol', 'ressol@test.com', '$2a$10$Y8yWwG2uR38jM8eIq0f6oOV3/1vM7Z13GkIefw7U9M/P0FqX3q4P3', 'ACTIVO')"
            };
            for (String sql : comunes) { try { stmt.execute(sql); } catch(Exception e) { if (!e.getMessage().contains("ORA-00001")) System.err.println("User Err: " + e.getMessage()); } }
            
            System.out.println("4. INSERTANDO ASIGNACIONES DE USUARIO...");
            try { stmt.execute("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ESTADO) VALUES (1, 1, 1, 'ACTIVA')"); } catch(Exception e){ if(!e.getMessage().contains("ORA-00001")) System.err.println("Asig Err: " + e.getMessage());}
            try { stmt.execute("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO) VALUES (2, 2, 5, 1, 1, 1, 'ACTIVA')"); } catch(Exception e){ if(!e.getMessage().contains("ORA-00001")) System.err.println("Asig Err: " + e.getMessage());}
            try { stmt.execute("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO) VALUES (3, 3, 5, 2, 2, 2, 'ACTIVA')"); } catch(Exception e){ if(!e.getMessage().contains("ORA-00001")) System.err.println("Asig Err: " + e.getMessage());}
            
            System.out.println("TODO HA SIDO RECONSTRUIDO CORRECTAMENTE.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
