package ai.learnharness.android;
import org.junit.Test;
import static org.junit.Assert.*;
public class PagesTest {
    @Test public void emptyPageIsReadable() { assertEquals(1, Pages.split("", 100).size()); }
    @Test public void longUnbrokenTextNeverDisappears() {
        String text = "x".repeat(405);
        var pages = Pages.split(text, 100); assertEquals(5, pages.size()); assertEquals(text, String.join("", pages));
        for (String p : pages) assertTrue(p.length() <= 100);
    }
    @Test public void paragraphBoundaryIsPreferred() {
        var pages = Pages.split("a".repeat(60) + "\n\n" + "b".repeat(60), 100);
        assertEquals("a".repeat(60), pages.get(0)); assertEquals("b".repeat(60), pages.get(1));
    }
    @Test public void unicodePairsStayTogether() {
        String text = "a".repeat(99) + "😀" + "b".repeat(99);
        var pages = Pages.split(text, 100);
        assertEquals(text, String.join("", pages));
        for (String p : pages) assertFalse(Character.isHighSurrogate(p.charAt(p.length()-1)));
    }
}
