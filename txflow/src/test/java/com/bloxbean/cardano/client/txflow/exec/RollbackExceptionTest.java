package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.FlowStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RollbackExceptionTest {

    @Test
    void testBasicConstructor() {
        FlowStep step = mock(FlowStep.class);
        when(step.getId()).thenReturn("step1");

        RollbackException exception = new RollbackException(
                "Rollback detected",
                "txhash123",
                step,
                12345L,
                false
        );

        assertEquals("Rollback detected", exception.getMessage());
        assertEquals("txhash123", exception.getTxHash());
        assertEquals(step, exception.getStep());
        assertEquals(12345L, exception.getPreviousBlockHeight());
        assertFalse(exception.isRequiresFlowRestart());
    }

    @Test
    void testConstructorWithCause() {
        FlowStep step = mock(FlowStep.class);
        RuntimeException cause = new RuntimeException("underlying error");

        RollbackException exception = new RollbackException(
                "Rollback detected",
                cause,
                "txhash456",
                step,
                67890L,
                true
        );

        assertEquals("Rollback detected", exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals("txhash456", exception.getTxHash());
        assertEquals(step, exception.getStep());
        assertEquals(67890L, exception.getPreviousBlockHeight());
        assertTrue(exception.isRequiresFlowRestart());
    }

    @Test
    void testForStepRebuild() {
        FlowStep step = mock(FlowStep.class);
        when(step.getId()).thenReturn("deposit-step");

        RollbackException exception = RollbackException.forStepRebuild(
                "abc123",
                step,
                100L
        );

        assertEquals("abc123", exception.getTxHash());
        assertEquals(step, exception.getStep());
        assertEquals(100L, exception.getPreviousBlockHeight());
        assertFalse(exception.isRequiresFlowRestart());
        assertTrue(exception.getMessage().contains("abc123"));
        assertTrue(exception.getMessage().contains("deposit-step"));
        assertTrue(exception.getMessage().contains("100"));
    }

    @Test
    void testForFlowRestart() {
        FlowStep step = mock(FlowStep.class);
        when(step.getId()).thenReturn("withdraw-step");

        RollbackException exception = RollbackException.forFlowRestart(
                "def456",
                step,
                200L
        );

        assertEquals("def456", exception.getTxHash());
        assertEquals(step, exception.getStep());
        assertEquals(200L, exception.getPreviousBlockHeight());
        assertTrue(exception.isRequiresFlowRestart());
        assertTrue(exception.getMessage().contains("def456"));
        assertTrue(exception.getMessage().contains("withdraw-step"));
        assertTrue(exception.getMessage().contains("200"));
        assertTrue(exception.getMessage().contains("restart"));
    }

    @Test
    void testExtendsFlowExecutionException() {
        FlowStep step = mock(FlowStep.class);
        RollbackException exception = RollbackException.forStepRebuild("tx", step, 1L);

        assertTrue(exception instanceof FlowExecutionException);
        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    void testDifferentSteps() {
        FlowStep step1 = mock(FlowStep.class);
        when(step1.getId()).thenReturn("step1");

        FlowStep step2 = mock(FlowStep.class);
        when(step2.getId()).thenReturn("step2");

        RollbackException ex1 = RollbackException.forStepRebuild("tx1", step1, 100L);
        RollbackException ex2 = RollbackException.forFlowRestart("tx2", step2, 200L);

        assertEquals("step1", ex1.getStep().getId());
        assertEquals("step2", ex2.getStep().getId());
        assertFalse(ex1.isRequiresFlowRestart());
        assertTrue(ex2.isRequiresFlowRestart());
    }
}
