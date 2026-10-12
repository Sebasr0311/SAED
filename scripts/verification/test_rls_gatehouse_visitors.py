"""Static checks of the gatehouse/visitor RLS work (V5.63 columns, V5.64 package body). No database.

* The porter is gatehouse staff of the ACTIVE CONTEXT property: it must not share the resident, unit-bound VISITANTES
  predicate (porters only hold property-level assignments, so that predicate hid every visitor from them).
* A visitor registered by a resident has no visit yet, so VISITANTES carries its own ID_PROPIEDAD and REGISTRADO_POR.
* The new body is the V5.58 body verbatim except for the VISITANTES/PORTERO lines."""
import difflib
from pathlib import Path
import re
import unittest

from test_rls_context_scope import function_text
from test_rls_policy_columns import MIGRATIONS, newest_rls_body, read

PREVIOUS = MIGRATIONS / "V5.58__rls_resident_context_property_scope.sql"
COLUMNS_MIGRATION = next(MIGRATIONS.glob("V5.63__*.sql"), None)
ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "backend" / "src" / "main" / "java"
ALLOWED_CHANGE = re.compile(r"VISITANTE|PORTERO|registrado_por|^\s*RETURN 'id_propiedad IN \(' \|\| v_my_props|^\s*END IF;\s*$|^\s*--|^\s*$", re.IGNORECASE)
RESIDENT_ROLES = "'RESIDENTE', 'PROPIETARIO_UNIDAD', 'RESIDENTE_CONVIVENCIA'"
FUNCTIONS = ("FN_FILTRO_ORGANIZACION", "FN_FILTRO_PROPIEDAD", "FN_FILTRO_UNIDAD", "FN_FILTRO_USUARIOS",
             "FN_FILTRO_ASIGNACION", "FN_FILTRO_GLOBAL_READONLY", "FN_FILTRO_GLOBAL_MUTATE")


def porter_branch(text: str) -> str:
    start = text.index("IF v_rol = 'PORTERO' THEN")
    return text[start:text.index("END IF;", start)]


class GatehousePackageBodyTest(unittest.TestCase):
    def setUp(self):
        self.path, self.body = newest_rls_body()
        self.propiedad = function_text(self.body, "FN_FILTRO_PROPIEDAD")

    def test_the_newest_body_is_the_gatehouse_migration(self):
        self.assertTrue(self.path.name.startswith("V5.64__"), self.path.name)

    def test_only_visitor_and_porter_lines_changed_from_the_previous_body(self):
        old = function_text(read(PREVIOUS), "FN_FILTRO_PROPIEDAD").splitlines()
        new = self.propiedad.splitlines()
        changed = [line[1:] for line in difflib.ndiff(old, new) if line[:1] in "+-"]
        self.assertTrue(changed, "FN_FILTRO_PROPIEDAD must change")
        offenders = [line for line in changed if not ALLOWED_CHANGE.search(line)]
        self.assertEqual(offenders, [])

    def test_every_other_function_is_the_previous_body_verbatim(self):
        previous = read(PREVIOUS)
        for function in FUNCTIONS:
            if function == "FN_FILTRO_PROPIEDAD":
                continue
            self.assertEqual(function_text(self.body, function) or function, function_text(previous, function), function)
            self.assertTrue(function_text(self.body, function), function)

    def test_the_porter_has_its_own_property_scoped_visitor_predicate(self):
        branch = porter_branch(self.propiedad)
        self.assertIn("p_tab = 'VISITANTES'", branch)
        self.assertIn("id_propiedad IN (' || v_my_props || ')", branch)
        self.assertNotIn("v_my_units", branch)
        self.assertNotIn("v_my_units", re.sub(r"--.*", "", branch))

    def test_the_porter_branch_comes_before_the_resident_branch_that_shares_it(self):
        generic = self.propiedad.index("IF v_rol IN ('RESIDENTE', 'PORTERO', 'PROPIETARIO_UNIDAD', 'RESIDENTE_CONVIVENCIA') THEN\n"
                                       "            IF p_tab IN ('REPORTES_CONFIGURADOS'")
        self.assertLess(self.propiedad.index("IF v_rol = 'PORTERO' THEN"), generic)

    def test_the_resident_sees_the_visitors_of_its_unit_or_registered_by_itself_inside_the_property(self):
        start = self.propiedad.index("IF v_rol IN ('RESIDENTE', 'PORTERO', 'PROPIETARIO_UNIDAD', 'RESIDENTE_CONVIVENCIA') THEN\n"
                                     "            IF p_tab IN ('REPORTES_CONFIGURADOS'")
        resident = self.propiedad[start:self.propiedad.index("IF v_prop IS NOT NULL THEN\n            IF p_tab = 'PROPIEDADES'")]
        block = resident[resident.index("IF p_tab = 'VISITANTES' THEN"):]
        block = block[:block.index("END IF;")]
        self.assertIn("v_my_units", block)
        self.assertIn("registrado_por = ' || v_usr", block)
        self.assertIn("id_propiedad IN (' || v_my_props || ')", block)

    def test_admin_visitors_are_the_ones_of_the_context_property(self):
        prop_branch = self.propiedad[self.propiedad.index("IF v_prop IS NOT NULL THEN\n            IF p_tab = 'PROPIEDADES'"):]
        line = next(l for l in prop_branch.splitlines() if "p_tab = 'VISITANTES'" in l)
        self.assertIn("id_propiedad = ' || v_prop", line)
        org_branch = prop_branch[prop_branch.index("ELSE\n            IF p_tab = 'PROPIEDADES'"):]
        line = next(l for l in org_branch.splitlines() if "p_tab = 'VISITANTES'" in l)
        self.assertIn("id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org", line)

    def test_porter_is_not_in_the_unit_function_resident_branch(self):
        unidad = function_text(self.body, "FN_FILTRO_UNIDAD")
        self.assertIn("IF v_rol IN (" + RESIDENT_ROLES + ") THEN", unidad)
        self.assertNotIn("'PORTERO'", unidad)

    def test_the_header_documents_the_porter_table_by_table_decision(self):
        header = read(self.path).split("CREATE OR REPLACE PACKAGE", 1)[0]
        for table in ("VISITANTES", "VISITAS", "QR_ACCESOS", "VEHICULOS_VISITA", "PAQUETES", "DOMICILIOS",
                      "PQRS_TRAZABILIDAD", "VOTOS"):
            self.assertIn(table, header, table)

    def test_ends_with_a_compilation_check_and_swallows_nothing(self):
        text = read(self.path)
        self.assertNotRegex(text.split("END PKG_SAED_SECURITY_RLS;", 1)[1].upper(), r"WHEN OTHERS THEN\s+NULL")
        self.assertIn("did not compile with status VALID", text)


class VisitantesScopeColumnsTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(COLUMNS_MIGRATION, "V5.63 migration is missing")
        self.text = re.sub(r"--.*", "", read(COLUMNS_MIGRATION))

    def test_starts_with_the_bootstrap_context_block(self):
        self.assertTrue(self.text.split("BEGIN", 1)[1].lstrip().startswith("PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);"))

    def test_adds_both_columns_without_default_guarded_by_the_dictionary(self):
        for column in ("ID_PROPIEDAD", "REGISTRADO_POR"):
            self.assertRegex(self.text, r"USER_TAB_COLS[^;]*TABLE_NAME = 'VISITANTES'[^;]*COLUMN_NAME = '" + column + "'")
            self.assertRegex(self.text, r"ALTER TABLE VISITANTES ADD \(" + column + r" NUMBER\)")
        self.assertNotRegex(self.text, r"ALTER TABLE VISITANTES ADD[^;]*DEFAULT")

    def test_backfills_the_property_from_the_existing_visits_without_merge(self):
        self.assertNotRegex(self.text.upper(), r"\bMERGE\b")
        update = re.search(r"UPDATE VISITANTES.*?;", self.text, re.DOTALL | re.IGNORECASE).group(0)
        for part in ("ID_PROPIEDAD", "FROM VISITAS", "UNIDADES", "ID_PROPIEDAD IS NULL"):
            self.assertIn(part, update)

    def test_adds_foreign_keys_and_an_index_guarded_by_the_dictionary(self):
        for name in ("FK_VISITANTES_PROPIEDAD", "FK_VISITANTES_REGISTRADO_POR"):
            self.assertIn("CONSTRAINT_NAME = '" + name + "'", self.text)
        self.assertIn("ON DELETE SET NULL", self.text.upper())
        self.assertIn("IX_VISITANTES_PROPIEDAD", self.text)
        self.assertRegex(self.text, r"USER_INDEXES WHERE INDEX_NAME = 'IX_VISITANTES_PROPIEDAD'")

    def test_swallows_nothing(self):
        self.assertNotRegex(self.text.upper(), r"WHEN OTHERS")


class VisitorInsertsGoThroughTheResolverTest(unittest.TestCase):
    """UQ_VISITANTES_PERSONA is global, so a caller that cannot see an existing visitor row (other resident, other
    property, unclaimed legacy row) must not insert again (ORA-00001 -> 409). Visitor identity is therefore resolved
    only by SP_RESOLVER_VISITANTE (V5.67), never by an application INSERT."""

    def test_no_application_code_inserts_into_visitantes(self):
        offenders = [path.name for path in JAVA.rglob("*.java")
                     if re.search(r"INSERT INTO VISITANTES", path.read_text(encoding="utf-8"), re.IGNORECASE)]
        self.assertEqual(offenders, [])

    def test_the_three_registration_paths_use_the_resolver(self):
        for relative in ("person/repository/impl/DependentRepositoryImpl.java",
                         "porteria/controller/PorteriaController.java",
                         "dashboard/controller/DashboardController.java"):
            text = (JAVA / "com" / "saed" / "backend" / relative).read_text(encoding="utf-8")
            self.assertIn("VisitorIdentityRegistry", text, relative)


if __name__ == "__main__":
    unittest.main()
