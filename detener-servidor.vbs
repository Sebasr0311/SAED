' Detiene todos los procesos node.exe (server.js)
Dim wmi, processes, process
Set wmi = GetObject("winmgmts:\\.\root\cimv2")
Set processes = wmi.ExecQuery("SELECT * FROM Win32_Process WHERE Name='node.exe'")
For Each process In processes
  process.Terminate()
Next
Set wmi = Nothing
