import re

with open('frontend/src/pages/ContratosPage.jsx', 'r', encoding='utf-8') as f:
    content = f.read()

# Replace API endpoints
content = content.replace("api.get('/apartamentos')", "api.get('/units')")
content = content.replace("api.get('/residentes')", "api.get('/personas')")

# Fix apartamentos.items to just apartamentos, since /units returns an array
content = content.replace("(apartamentos?.items || [])", "(apartamentos || [])")

# Fix residentes.items to residentes.content, since /personas returns paginated data
content = content.replace("(residentes?.items || [])", "(residentes?.content || [])")

# Map idApartamento to id for options
content = content.replace("key={a.idApartamento} value={a.idApartamento}", "key={a.id} value={a.id}")

# Map idResidente to id for options
content = content.replace("key={r.idResidente} value={r.idResidente}", "key={r.id} value={r.id}")
content = content.replace("r.nombres + ' ' + r.apellidos", "r.primerNombre + ' ' + r.primerApellido")

# Find the apto
content = content.replace("String(a.idApartamento) === String(idApartamento)", "String(a.id) === String(idApartamento)")

with open('frontend/src/pages/ContratosPage.jsx', 'w', encoding='utf-8') as f:
    f.write(content)
