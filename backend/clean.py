import re

with open('../database_final_release/datos_prueba.sql', 'r') as f:
    data = f.read()

# Remove all inserts into PERSONAS, USUARIOS, ADMINISTRADORES_SAED, USUARIO_ASIGNACIONES
data = re.sub(r'INSERT INTO PERSONAS.*?;', '', data, flags=re.DOTALL)
data = re.sub(r'INSERT INTO USUARIOS.*?;', '', data, flags=re.DOTALL)
data = re.sub(r'INSERT INTO ADMINISTRADORES_SAED.*?;', '', data, flags=re.DOTALL)
data = re.sub(r'INSERT INTO USUARIO_ASIGNACIONES.*?;', '', data, flags=re.DOTALL)

with open('../database_final_release/datos_prueba.sql', 'w') as f:
    f.write(data)
