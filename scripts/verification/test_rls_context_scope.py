"""Static check: resident and porter predicates in the newest PKG_SAED_SECURITY_RLS body must scope through the
user's assignments restricted to the ACTIVE CONTEXT property. A user assigned as RESIDENTE in one property and as
administrator of another used to read and write both under the RESIDENTE context because the subquery was the union
of all the user's active assignments. No Docker process, no database connection."""
import re
import unittest

from test_rls_policy_columns import newest_rls_body


def function_text(body: str, function: str) -> str:
    """Text of one function of the package BODY (the specification only declares it with a semicolon)."""
    body = body[re.search(r"CREATE OR REPLACE PACKAGE BODY", body, re.IGNORECASE).start():]
    match = re.search(r"FUNCTION\s+" + function + r"\s*\([^)]*\)\s*RETURN\s+VARCHAR2\s+AS(.*?)END\s+" + function + r"\s*;",
                      body, re.IGNORECASE | re.DOTALL)
    return match.group(0) if match else ""


class ResidentContextScopeTest(unittest.TestCase):
    def setUp(self):
        _, self.body = newest_rls_body()

    def assignment_lines(self, function):
        text = function_text(self.body, function)
        self.assertTrue(text, function)
        return [line.strip() for line in text.splitlines() if "FROM USUARIO_ASIGNACIONES" in line.upper()]

    def test_every_user_assignment_subquery_is_restricted_to_the_context_property(self):
        for function in ("FN_FILTRO_PROPIEDAD", "FN_FILTRO_UNIDAD"):
            lines = self.assignment_lines(function)
            self.assertEqual(len(lines), 2, f"{function}: only the two helper definitions may query the assignments")
            for line in lines:
                self.assertRegex(line, r"^v_my_(props|units) := ", line)
                self.assertTrue(line.endswith("|| v_scope;"), f"{function}: unrestricted assignment subquery: {line}")

    def test_the_context_restriction_is_built_from_the_context_property(self):
        for function in ("FN_FILTRO_PROPIEDAD", "FN_FILTRO_UNIDAD"):
            text = function_text(self.body, function)
            match = re.search(r"IF v_prop IS NOT NULL THEN\s+v_scope := ' AND id_propiedad = ' \|\| v_prop;\s+END IF;",
                              text)
            self.assertIsNotNone(match, function)

    def test_resident_and_porter_branches_use_the_restricted_helpers(self):
        text = function_text(self.body, "FN_FILTRO_PROPIEDAD")
        self.assertGreaterEqual(text.count("|| v_my_props ||"), 10)
        self.assertGreaterEqual(text.count("|| v_my_units ||"), 3)
        votos = text[text.index("p_tab = 'VOTOS'"):]
        votos = votos[:votos.index("ACCIONES_AUTOMATIZACION")]
        self.assertIn("v_my_units", votos)
        self.assertIn("v_my_props", votos)
        children = text[text.index("'ASISTENCIAS_ASAMBLEA', 'PODERES_REPRESENTACION', 'VOTACIONES'"):]
        self.assertIn("v_my_props", children[:children.index("p_tab = 'VOTOS'")])
        unidad = function_text(self.body, "FN_FILTRO_UNIDAD")
        self.assertGreaterEqual(unidad.count("|| v_my_units ||"), 10)

    def test_no_other_function_reads_a_residents_assignments_unrestricted(self):
        for function in ("FN_FILTRO_ORGANIZACION", "FN_FILTRO_USUARIOS", "FN_FILTRO_ASIGNACION"):
            for line in self.assignment_lines(function):
                self.assertIn("id_organizacion = ' || v_org", line, f"{function}: {line}")


if __name__ == "__main__":
    unittest.main()
