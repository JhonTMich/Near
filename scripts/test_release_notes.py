import unittest
from release_notes import notes, MAX_CHARS

class ReleaseNotesTest(unittest.TestCase):
    def test_tag_message_wins_without_its_title(self):
        self.assertEqual(notes("1.8.5", "Near 1.8.5\n\n- Arreglo del login\n", "## Otra cosa"), "- Arreglo del login\n")
    def test_tag_signature_is_not_published(self):
        tag = "Near 1.8.5\n\n- Cambio\n-----BEGIN PGP SIGNATURE-----\nabc\n-----END PGP SIGNATURE-----\n"
        self.assertEqual(notes("1.8.5", tag), "- Cambio\n")
    def test_title_only_tag_falls_back_to_the_pull_request(self):
        body = "## Near 1.8.4\r\n\r\n- Entrar ya no se desplaza.\r\n\r\nTras integrar, se etiqueta `v1.8.4`.\r\n\r\n🤖 Generated with [Claude Code](https://claude.com/claude-code)"
        self.assertEqual(notes("1.8.4", "Near 1.8.4", body), "## Near 1.8.4\n\n- Entrar ya no se desplaza.\n")
    def test_generic_line_when_nothing_describes_the_release(self):
        self.assertEqual(notes("1.8.5", "Near 1.8.5", ""), "Actualización de Near 1.8.5.\n")
    def test_long_notes_are_cut_at_a_line(self):
        text = notes("1.8.5", pr_body="\n".join(["- " + "x" * 98] * 100))
        self.assertLessEqual(len(text), MAX_CHARS + 4)
        self.assertTrue(text.endswith("\n\n…\n"))

if __name__ == "__main__":
    unittest.main()
