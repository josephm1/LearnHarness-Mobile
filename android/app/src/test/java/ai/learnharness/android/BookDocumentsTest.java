package ai.learnharness.android;
import org.junit.Test;
import static org.junit.Assert.*;
public class BookDocumentsTest {
    @Test public void literalGrepDoesNotTreatPunctuationAsRegex(){assertTrue(BookDocuments.pattern("a+b",false).matcher("A+B").find());assertFalse(BookDocuments.pattern("a+b",false).matcher("aaab").find());}
    @Test public void regexGrepSupportsAlternation(){assertTrue(BookDocuments.pattern("neuron|synapse",true).matcher("A synapse").find());}
    @Test public void validatesOneBasedScope()throws Exception{assertArrayEquals(new int[]{1,Integer.MAX_VALUE},BookDocuments.scope(""));assertArrayEquals(new int[]{10,20},BookDocuments.scope("10-20"));assertArrayEquals(new int[]{2,2},BookDocuments.scope("2"));}
    @Test(expected=java.io.IOException.class)public void rejectsInvalidScope()throws Exception{BookDocuments.scope("20-10");}
    @Test(expected=java.io.IOException.class)public void rejectsZeroPage()throws Exception{BookDocuments.scope("0");}
    @Test(expected=com.google.re2j.PatternSyntaxException.class)public void rejectsUnsupportedBacktrackingRegex(){BookDocuments.pattern("(a)\\1",true);}
}
