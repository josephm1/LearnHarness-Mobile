package ai.learnharness.android;
import org.junit.Test;
import static org.junit.Assert.*;
public class EinkReadingTest {
    @Test public void retainsThreeLinesBetweenScreenfuls(){assertEquals(828,EinkReading.step(900,24));}
    @Test public void shortViewportsStillAdvance(){assertEquals(20,EinkReading.step(40,24));}
    @Test public void handlesZeroAndLargeMeasurements(){assertEquals(0,EinkReading.step(-5,24));assertEquals(900,EinkReading.step(900,-1));assertEquals(450,EinkReading.step(900,Integer.MAX_VALUE));}
}
