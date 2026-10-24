package ai.learnharness.android;
import org.junit.Test;import static org.junit.Assert.*;
public class MarkdownMathTest {
 @Test public void adaptsInlineAndKeepsOriginalDisplayDelimiters(){assertEquals("Let $$x^2$$ be positive.\n$$\n\\frac{1}{2}\n$$",MarkdownMath.normalize("Let $x^2$ be positive.\n$$\n\\frac{1}{2}\n$$"));}
 @Test public void preservesCodeFencesInlineCodeAndEscapedDollars(){String code="`$x$`\n```tex\n$x$\n```\n~~~\n$y$\n~~~\n\\$5 and unclosed $x";assertEquals(code,MarkdownMath.normalize(code));}
 @Test public void handlesTwoFormulasAndBackslashEscapes(){assertEquals("$$x$$ and $$\\alpha$$",MarkdownMath.normalize("$x$ and $\\alpha$"));assertEquals("$$x\\\\$$",MarkdownMath.normalize("$x\\\\$"));}
}
