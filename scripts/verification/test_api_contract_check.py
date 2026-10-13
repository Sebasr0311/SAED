"""Unit tests for the static API-contract checker parsers. No network, no database."""
import unittest

import api_contract_check as acc

CONTROLLER = '''
package x;
@RestController
@RequestMapping("/api/v1/units/{unitId}")
public class UnitController {
    // @GetMapping("/commented")
    @GetMapping
    public String list() { return ""; }

    @GetMapping({"/a", "/b/{id}"})
    public String multi() { return ""; }

    @PostMapping(value = "/up", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void up() {}

    @DeleteMapping(value = "/{id:\\d+}", params = "force")
    public void del() {}

    @RequestMapping(value = "/both", method = {RequestMethod.POST, RequestMethod.PUT})
    public void both() {}

    @PatchMapping(path = "/p/{x}")
    public void patch() {}
}
'''

NO_PREFIX = '''
@RestController
public class Plain {
    @GetMapping("/health")
    public String h() { return ""; }
}
'''


class JavaParserTest(unittest.TestCase):
    def test_extracts_methods_and_paths(self):
        eps = set(acc.parse_java_endpoints(CONTROLLER))
        self.assertIn(("GET", "/units/{}"), eps)
        self.assertIn(("GET", "/units/{}/a"), eps)
        self.assertIn(("GET", "/units/{}/b/{}"), eps)
        self.assertIn(("POST", "/units/{}/up"), eps)
        self.assertIn(("DELETE", "/units/{}/{}"), eps)
        self.assertIn(("POST", "/units/{}/both"), eps)
        self.assertIn(("PUT", "/units/{}/both"), eps)
        self.assertIn(("PATCH", "/units/{}/p/{}"), eps)

    def test_ignores_comments(self):
        eps = set(acc.parse_java_endpoints(CONTROLLER))
        self.assertNotIn(("GET", "/units/{}/commented"), eps)

    def test_class_without_prefix(self):
        self.assertEqual([("GET", "/health")], acc.parse_java_endpoints(NO_PREFIX))


class NormalizeTest(unittest.TestCase):
    def test_normalize(self):
        self.assertEqual("/a/{}/b", acc.normalize_path("/a/{id}/b/"))
        self.assertEqual("/a/{}", acc.normalize_path("/a/{id:[0-9]+}?x=1"))
        self.assertEqual("/a", acc.normalize_path("a"))


FRONT = '''
const a = await api.get('/mascotas');
await tenantApi.post(`/unidades/${unitId}/mascotas`, body);
await api.patch(`/planes/${id}/status`, { a: 1 });
await api.get(`/x${query}`);
await api.get(`/y?${params.toString()}`);
await api.get(`/z/${id}?a=${b}`);
await apiClient.put(cond ? '/m/1' : `/m/${id}/n`, p);
await api.postFormData('/documentos/upload', fd);
await api.delete(`/q/${id}`);
await api.get(url);
const r = await fetch(`${BASE_URL}/contratos/${id}/pdf`, { headers });
const r2 = await fetch(`${BASE_URL}/paz/${id}`, { method: 'POST', headers });
// api.get('/commented')
'''


class FrontendParserTest(unittest.TestCase):
    def setUp(self):
        self.calls, self.dynamic = acc.parse_frontend_calls(FRONT)
        self.set = {(m, p) for m, p, _ in self.calls}

    def test_literals_and_templates(self):
        self.assertIn(("GET", "/mascotas"), self.set)
        self.assertIn(("POST", "/unidades/{}/mascotas"), self.set)
        self.assertIn(("PATCH", "/planes/{}/status"), self.set)
        self.assertIn(("POST", "/documentos/upload"), self.set)
        self.assertIn(("DELETE", "/q/{}"), self.set)

    def test_query_suffix_handling(self):
        self.assertIn(("GET", "/x"), self.set)
        self.assertIn(("GET", "/y"), self.set)
        self.assertIn(("GET", "/z/{}"), self.set)

    def test_ternary_yields_both_branches(self):
        self.assertIn(("PUT", "/m/1"), self.set)
        self.assertIn(("PUT", "/m/{}/n"), self.set)

    def test_fetch_wrapper_and_method(self):
        self.assertIn(("GET", "/contratos/{}/pdf"), self.set)
        self.assertIn(("POST", "/paz/{}"), self.set)

    def test_dynamic_and_comments(self):
        self.assertEqual(1, len(self.dynamic))
        self.assertNotIn(("GET", "/commented"), self.set)


class MatchTest(unittest.TestCase):
    def test_matching(self):
        eps = [("PATCH", "/platform/plans/{}/status"), ("GET", "/zonas-comunes"), ("GET", "/a/{}")]
        calls = [("PATCH", "/planes/{}/status", "f:1"), ("GET", "/reservas/zonas", "f:2"),
                 ("GET", "/zonas-comunes", "f:3"), ("GET", "/a/7", "f:4"), ("POST", "/a/{}", "f:5"), ("GET", "/a/export", "f:6")]
        unmatched = acc.find_unmatched(calls, eps, allowlist={})
        self.assertEqual({"f:1", "f:2", "f:5", "f:6"}, {c[2] for c in unmatched})

    def test_allowlist(self):
        calls = [("GET", "/ext", "f:1")]
        self.assertEqual([], acc.find_unmatched(calls, [], allowlist={("GET", "/ext"): "reason"}))

    def test_unused_backend(self):
        eps = [("GET", "/a"), ("GET", "/b/{}")]
        calls = [("GET", "/b/{}", "f:1")]
        self.assertEqual([("GET", "/a")], acc.find_unused(calls, eps))


if __name__ == "__main__":
    unittest.main()
