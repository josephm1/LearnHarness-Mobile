package ai.learnharness.android;

/** Agora's three-line continuity rule, capped at half of a short viewport. */
final class EinkReading {
    static int step(int viewport,int line){
        int available=Math.max(0,viewport);
        int overlap=(int)Math.min(available/2L,Math.max(0,line)*3L);
        return available-overlap;
    }
}
