"""Static check: a table protected by a tenant policy function must either have the column that function filters on
or be special-cased (filtered through its parent) in the newest PKG_SAED_SECURITY_RLS body. VOTOS,
ACCIONES_/EJECUCIONES_AUTOMATIZACION and MEMBRESIAS_HISTORIAL raised ORA-28113 for every profile because they had
neither. No Docker process, no database connection."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
MIGRATIONS = ROOT / "database" / "migrations"
PREDICATE_COLUMN = {
    "FN_FILTRO_PROPIEDAD": "ID_PROPIEDAD",
    "FN_FILTRO_ORGANIZACION": "ID_ORGANIZACION",
    "FN_FILTRO_UNIDAD": "ID_UNIDAD",
}
TYPES = r"(?:NUMBER|VARCHAR2|VARCHAR|NVARCHAR2|CHAR|DATE|TIMESTAMP|CLOB|BLOB|RAW|FLOAT|INTEGER)"


def version_key(path: Path):
    return tuple(int(part) for part in re.match(r"V(\d+(?:\.\d+)*)__", path.name).group(1).split("."))


def migrations():
    return sorted(MIGRATIONS.glob("V*__*.sql"), key=version_key)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def policy_tables(texts):
    found = {}
    for text in texts:
        for table, fn in re.findall(r"add_rls\('([A-Z0-9_]+)',\s*'[A-Z0-9_]+',\s*'(FN_FILTRO_[A-Z_]+)'", text):
            found[table] = fn
        for table, fn in re.findall(
                r"ADD_(?:GROUPED_)?POLICY\(\s*(?:NULL|USER)\s*,\s*'\"?([A-Z0-9_]+)\"?'.*?\"?PKG_SAED_SECURITY_RLS\"?\.\"?(FN_FILTRO_[A-Z_]+)\"?",
                text, re.IGNORECASE):
            found[table.upper()] = fn.upper()
    return found


def table_columns(texts):
    columns = {}
    for text in texts:
        for table, body in re.findall(r'CREATE TABLE\s+"?([A-Z0-9_]+)"?\s*\((.*?)\)\s*(?:PCTFREE|TABLESPACE|;|\n/)',
                                      text, re.IGNORECASE | re.DOTALL):
            cols = re.findall(r'(?m)^\s*\(?\s*"?([A-Z0-9_]+)"?\s+' + TYPES, body, re.IGNORECASE)
            columns.setdefault(table.upper(), set()).update(c.upper() for c in cols)
        for table, body in re.findall(r'ALTER TABLE\s+"?([A-Z0-9_]+)"?\s+ADD\s*\((.*?)\)\s*;', text,
                                      re.IGNORECASE | re.DOTALL):
            cols = re.findall(r'(?:^|,)\s*"?([A-Z0-9_]+)"?\s+' + TYPES, body, re.IGNORECASE)
            columns.setdefault(table.upper(), set()).update(c.upper() for c in cols)
        for table, column in re.findall(r"add_column\('([A-Z0-9_]+)',\s*'([A-Z0-9_]+)'", text, re.IGNORECASE):
            columns.setdefault(table.upper(), set()).add(column.upper())
    return columns


def newest_rls_body():
    holders = [p for p in migrations() if "PACKAGE BODY PKG_SAED_SECURITY_RLS" in read(p).upper()
               and "FUNCTION FN_FILTRO_PROPIEDAD" in read(p).upper() and "FN_FILTRO_GLOBAL_MUTATE" in read(p).upper()]
    return holders[-1], read(holders[-1])


def function_text(body: str, function: str) -> str:
    body = body[re.search(r"PACKAGE BODY", body, re.IGNORECASE).start():]
    match = re.search(r"FUNCTION\s+" + function + r"\s*\(.*?END\s+" + function + r"\s*;", body,
                      re.IGNORECASE | re.DOTALL)
    return match.group(0) if match else ""


class PolicyPredicateColumnsTest(unittest.TestCase):
    def test_every_policy_table_has_the_predicate_column_or_a_parent_branch(self):
        texts = [read(p) for p in migrations()]
        policies = policy_tables(texts)
        columns = table_columns(texts)
        _, body = newest_rls_body()
        self.assertGreater(len(policies), 40)
        offenders = []
        for table, fn in sorted(policies.items()):
            needed = PREDICATE_COLUMN.get(fn)
            if needed is None or table not in columns:
                continue  # global/user/assignment functions or tables created outside the migrations
            if needed in columns[table]:
                continue
            if re.search(r"'" + table + r"'", function_text(body, fn)):
                continue
            offenders.append(f"{table} ({fn} needs {needed})")
        self.assertEqual(offenders, [])

    def test_the_four_tables_that_raised_ora_28113_are_special_cased(self):
        _, body = newest_rls_body()
        for table, fn in (("VOTOS", "FN_FILTRO_PROPIEDAD"),
                          ("ACCIONES_AUTOMATIZACION", "FN_FILTRO_PROPIEDAD"),
                          ("EJECUCIONES_AUTOMATIZACION", "FN_FILTRO_PROPIEDAD"),
                          ("MEMBRESIAS_HISTORIAL", "FN_FILTRO_ORGANIZACION")):
            self.assertIn("'" + table + "'", function_text(body, fn), table)

    def test_votes_stay_readable_by_the_unit_that_cast_them(self):
        _, body = newest_rls_body()
        votos = function_text(body, "FN_FILTRO_PROPIEDAD")
        branch = votos[votos.index("p_tab = 'VOTOS'"):]
        branch = branch[:branch.index("ACCIONES_AUTOMATIZACION")]
        self.assertIn("id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES", branch)
        self.assertIn("id_persona_votante IN (SELECT id_persona FROM USUARIOS", branch)

    def test_the_package_replacement_does_not_swallow_errors_after_the_body(self):
        path, _ = newest_rls_body()
        self.assertNotRegex(read(path).split("END PKG_SAED_SECURITY_RLS;", 1)[1].upper(), r"WHEN OTHERS THEN\s+NULL")


if __name__ == "__main__":
    unittest.main()
