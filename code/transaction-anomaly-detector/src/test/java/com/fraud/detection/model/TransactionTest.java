package com.fraud.detection.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TransactionTest {

    @Test
    public void testConstructorAndGetters() {
        Transaction t = new Transaction(5, "TRANSFER", 12345.67, "C_SRC",
                50000.0, 37654.33, "C_DST", 0.0, 12345.67, 1, 0);

        assertEquals(5, t.getStep());
        assertEquals("TRANSFER", t.getType());
        assertEquals(12345.67, t.getAmount(), 0.001);
        assertEquals("C_SRC", t.getNameOrig());
        assertEquals(50000.0, t.getOldBalanceOrig(), 0.001);
        assertEquals(37654.33, t.getNewBalanceOrig(), 0.001);
        assertEquals("C_DST", t.getNameDest());
        assertEquals(0.0, t.getOldBalanceDest(), 0.001);
        assertEquals(12345.67, t.getNewBalanceDest(), 0.001);
        assertEquals(1, t.getIsFraud());
        assertEquals(0, t.getIsFlaggedFraud());
    }

    @Test
    public void testConstructorWithZeroValues() {
        Transaction t = new Transaction(0, "PAYMENT", 0.0, "C0",
                0.0, 0.0, "C1", 0.0, 0.0, 0, 0);

        assertEquals(0, t.getStep());
        assertEquals("PAYMENT", t.getType());
        assertEquals(0.0, t.getAmount(), 0.001);
        assertEquals("C0", t.getNameOrig());
        assertEquals(0.0, t.getOldBalanceOrig(), 0.001);
        assertEquals(0.0, t.getNewBalanceOrig(), 0.001);
        assertEquals("C1", t.getNameDest());
        assertEquals(0.0, t.getOldBalanceDest(), 0.001);
        assertEquals(0.0, t.getNewBalanceDest(), 0.001);
        assertEquals(0, t.getIsFraud());
        assertEquals(0, t.getIsFlaggedFraud());
    }

    @Test
    public void testToString() {
        Transaction t = new Transaction(3, "CASH_OUT", 9999.99, "C_ORIG",
                20000.0, 10000.01, "C_DEST", 0.0, 9999.99, 0, 0);

        String result = t.toString();

        assertTrue(result.contains("step=3"));
        assertTrue(result.contains("type='CASH_OUT'"));
        assertTrue(result.contains("amount=9999.99"));
        assertTrue(result.contains("from='C_ORIG'"));
        assertTrue(result.contains("to='C_DEST'"));
    }

    @Test
    public void testToStringFormat() {
        Transaction t = new Transaction(1, "PAYMENT", 100.50, "SRC", 1000.0, 899.50,
                "DST", 0.0, 100.50, 0, 0);

        String result = t.toString();

        // Should match the format: Transaction{step=%d, type='%s', amount=%.2f, from='%s', to='%s'}
        assertTrue(result.startsWith("Transaction{"));
        assertTrue(result.endsWith("}"));
    }

    @Test
    public void testFlaggedFraudTransaction() {
        Transaction t = new Transaction(10, "TRANSFER", 500000.0, "C_SRC",
                1000000.0, 500000.0, "C_DST", 0.0, 500000.0, 1, 1);

        assertEquals(1, t.getIsFraud());
        assertEquals(1, t.getIsFlaggedFraud());
    }
}
