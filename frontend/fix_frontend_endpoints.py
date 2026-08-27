import os

files_to_fix = {
    "src/pages/MultasPage.jsx": [
        ("api.get('/multas/todas')", "api.get('/api/v1/multas/todas')"),
        ("api.get(/multas/)", "api.get(/api/v1/multas/)"),
        ("api.put(/multas//pagar", "api.put(/api/v1/multas//pagar"),
        ("api.put(/multas//anular", "api.put(/api/v1/multas//anular")
    ],
    "src/pages/QuejasAdminPage.jsx": [
        ("api.get('/quejas/todas')", "api.get('/api/v1/quejas/todas')"),
        ("api.put(/quejas//responder", "api.put(/api/v1/quejas//responder"),
        ("api.put(/quejas//estado", "api.put(/api/v1/quejas//estado"),
        ("api.put(/quejas//prioridad", "api.put(/api/v1/quejas//prioridad")
    ],
    "src/pages/ResQuejasPage.jsx": [
        ("api.get(/quejas)", "api.get(/api/v1/quejas)"),
        ("api.post('/quejas'", "api.post('/api/v1/quejas'")
    ],
    "src/pages/ResBuzonPage.jsx": [
        ("api.get('/buzon')", "api.get('/api/v1/buzon')"),
        ("api.put(/buzon//leido)", "api.put(/api/v1/buzon//leido)"),
        ("api.put('/buzon/vaciar')", "api.put('/api/v1/buzon/vaciar')"),
        ("api.put('/buzon/vaciar-multi'", "api.put('/api/v1/buzon/vaciar-multi'")
    ]
}

for filepath, replacements in files_to_fix.items():
    if os.path.exists(filepath):
        with open(filepath, "r", encoding="utf-8") as f:
            content = f.read()
        
        for old, new in replacements:
            content = content.replace(old, new)
            
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(content)

print("Frontend endpoints fixed")
