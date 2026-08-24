@echo off
cd /d "C:\Users\JUAN\IdeaProjects\sistema-administracion-edificios"
"C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd" exec:java -Dexec.mainClass=com.edificio.admin.Main
pause
