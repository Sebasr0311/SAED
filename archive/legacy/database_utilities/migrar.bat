@echo OFF
java -cp "database\classes;C:\Users\JUAN\.m2\repository\com\oracle\database\jdbc\ojdbc11\23.3.0.23.09\ojdbc11-23.3.0.23.09.jar;C:\Users\JUAN\.m2\repository\com\oracle\database\security\oraclepki\23.3.0.23.09\oraclepki-23.3.0.23.09.jar" MigrarDatosLocalToATP --execute
pause
