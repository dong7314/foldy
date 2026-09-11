package dev.poldy.lab;

import org.junit.Test;
import static org.junit.Assert.*;
import static dev.poldy.lab.FoldSignals.Action.*;

public class FoldSignalsTest {
    @Test public void opensOnceAndRestsAtEndpoint() {
        FoldSignals s=new FoldSignals();assertEquals(REST,s.accept(0));
        assertEquals(OPEN,s.accept(90));assertEquals(NONE,s.accept(90));assertEquals(REST,s.accept(180));
    }
    @Test public void closesFromFullyOpen() {
        FoldSignals s=new FoldSignals();s.accept(180);assertEquals(CLOSE,s.accept(90));assertEquals(REST,s.accept(0));
    }
    @Test public void neverGuessesDirectionWhenStartingHalfOpen() {
        FoldSignals s=new FoldSignals();assertEquals(NONE,s.accept(90));assertEquals(NONE,s.accept(90));
        assertEquals(REST,s.accept(0));assertEquals(OPEN,s.accept(90));
    }
    @Test public void returningToSameEndpointReleasesAndRearms() {
        FoldSignals s=new FoldSignals();s.accept(0);s.accept(90);assertEquals(REST,s.accept(0));assertEquals(OPEN,s.accept(90));
    }
    @Test public void clearedSessionDoesNotReuseDirection() {
        FoldSignals s=new FoldSignals();s.accept(180);s.clear();assertEquals(NONE,s.accept(90));
    }
    @Test public void baseOpeningPrecedesPublicHingeWithoutStartingTwice() {
        FoldSignals s=new FoldSignals();s.accept(0);s.acceptBaseState(0);
        assertEquals(OPEN,s.acceptBaseState(1));assertEquals(NONE,s.accept(90));
        assertEquals(REST,s.accept(180));
    }
    @Test public void baseClosingRestoresEndpointAfterPartialReversal() {
        FoldSignals s=new FoldSignals();s.accept(0);s.acceptBaseState(0);s.acceptBaseState(1);
        assertEquals(REST,s.acceptBaseState(0));assertEquals(NONE,s.accept(0));
        assertEquals(OPEN,s.acceptBaseState(1));
    }
    @Test public void baseOpenedIsNotProofOfFullyFlatHinge() {
        FoldSignals s=new FoldSignals();s.accept(0);s.acceptBaseState(0);s.acceptBaseState(1);
        assertEquals(NONE,s.acceptBaseState(3));assertEquals(REST,s.accept(180));
    }
}
