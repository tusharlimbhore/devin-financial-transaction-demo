package com.fraud.detection.model;

import com.fraud.detection.model.AnomalyAlert.AnomalyType;
import com.fraud.detection.model.AnomalyAlert.Severity;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AnomalyAlertTest {

    private Transaction createTransaction() {
        return new Transaction(1, "TRANSFER", 50000.0, "C_SRC",
                100000.0, 50000.0, "C_DST", 0.0, 50000.0, 0, 0);
    }

    @Test
    public void testConstructorAndGetters() {
        Transaction tx = createTransaction();
        List<Transaction> txList = Collections.singletonList(tx);

        AnomalyAlert alert = new AnomalyAlert("CUST_001",
                AnomalyType.REPEATED_HIGH_VALUE,
                Severity.HIGH,
                "Multiple high-value transactions detected",
                txList);

        assertEquals("CUST_001", alert.getCustomerId());
        assertEquals(AnomalyType.REPEATED_HIGH_VALUE, alert.getAnomalyType());
        assertEquals(Severity.HIGH, alert.getSeverity());
        assertEquals("Multiple high-value transactions detected", alert.getDetails());
        assertEquals(1, alert.getInvolvedTransactions().size());
        assertEquals(tx, alert.getInvolvedTransactions().get(0));
    }

    @Test
    public void testConstructorWithMultipleTransactions() {
        Transaction tx1 = new Transaction(1, "TRANSFER", 50000.0, "C1",
                100000.0, 50000.0, "C2", 0.0, 50000.0, 0, 0);
        Transaction tx2 = new Transaction(2, "CASH_OUT", 49000.0, "C1",
                50000.0, 1000.0, "C3", 0.0, 49000.0, 0, 0);
        List<Transaction> txList = Arrays.asList(tx1, tx2);

        AnomalyAlert alert = new AnomalyAlert("C1",
                AnomalyType.TRANSFER_THEN_CASHOUT,
                Severity.CRITICAL,
                "Transfer followed by cash-out",
                txList);

        assertEquals(2, alert.getInvolvedTransactions().size());
    }

    // --- AnomalyType enum tests ---

    @Test
    public void testAllAnomalyTypesExist() {
        AnomalyType[] types = AnomalyType.values();
        assertEquals(6, types.length);
    }

    @Test
    public void testAnomalyTypeDescriptions() {
        assertEquals("Repeated High-Value Transactions",
                AnomalyType.REPEATED_HIGH_VALUE.getDescription());
        assertEquals("Transfer Followed by Cash-Out",
                AnomalyType.TRANSFER_THEN_CASHOUT.getDescription());
        assertEquals("Transfer → Transfer → Cash-Out Sequence",
                AnomalyType.TRANSFER_TRANSFER_CASHOUT.getDescription());
        assertEquals("Sudden Increase in Transaction Amount",
                AnomalyType.SUDDEN_AMOUNT_SPIKE.getDescription());
        assertEquals("Sudden Change in Transaction Type",
                AnomalyType.SUDDEN_TYPE_CHANGE.getDescription());
        assertEquals("Multiple High-Value Transactions in Short Time",
                AnomalyType.MULTIPLE_HIGH_VALUE_SHORT_TIME.getDescription());
    }

    @Test
    public void testAnomalyTypeValueOf() {
        assertEquals(AnomalyType.REPEATED_HIGH_VALUE, AnomalyType.valueOf("REPEATED_HIGH_VALUE"));
        assertEquals(AnomalyType.TRANSFER_THEN_CASHOUT, AnomalyType.valueOf("TRANSFER_THEN_CASHOUT"));
        assertEquals(AnomalyType.TRANSFER_TRANSFER_CASHOUT, AnomalyType.valueOf("TRANSFER_TRANSFER_CASHOUT"));
        assertEquals(AnomalyType.SUDDEN_AMOUNT_SPIKE, AnomalyType.valueOf("SUDDEN_AMOUNT_SPIKE"));
        assertEquals(AnomalyType.SUDDEN_TYPE_CHANGE, AnomalyType.valueOf("SUDDEN_TYPE_CHANGE"));
        assertEquals(AnomalyType.MULTIPLE_HIGH_VALUE_SHORT_TIME, AnomalyType.valueOf("MULTIPLE_HIGH_VALUE_SHORT_TIME"));
    }

    // --- Severity enum tests ---

    @Test
    public void testAllSeverityLevelsExist() {
        Severity[] levels = Severity.values();
        assertEquals(4, levels.length);
    }

    @Test
    public void testSeverityValues() {
        assertEquals(Severity.LOW, Severity.valueOf("LOW"));
        assertEquals(Severity.MEDIUM, Severity.valueOf("MEDIUM"));
        assertEquals(Severity.HIGH, Severity.valueOf("HIGH"));
        assertEquals(Severity.CRITICAL, Severity.valueOf("CRITICAL"));
    }

    @Test
    public void testSeverityOrdinalOrder() {
        assertTrue(Severity.LOW.ordinal() < Severity.MEDIUM.ordinal());
        assertTrue(Severity.MEDIUM.ordinal() < Severity.HIGH.ordinal());
        assertTrue(Severity.HIGH.ordinal() < Severity.CRITICAL.ordinal());
    }

    // --- toString tests ---

    @Test
    public void testToStringContainsSeverity() {
        AnomalyAlert alert = new AnomalyAlert("C1",
                AnomalyType.SUDDEN_AMOUNT_SPIKE,
                Severity.CRITICAL,
                "Spike detected",
                Collections.singletonList(createTransaction()));

        String result = alert.toString();
        assertTrue(result.contains("CRITICAL"));
    }

    @Test
    public void testToStringContainsCustomerId() {
        AnomalyAlert alert = new AnomalyAlert("CUST_XYZ",
                AnomalyType.SUDDEN_TYPE_CHANGE,
                Severity.MEDIUM,
                "Type change detected",
                Collections.singletonList(createTransaction()));

        String result = alert.toString();
        assertTrue(result.contains("CUST_XYZ"));
    }

    @Test
    public void testToStringContainsAnomalyTypeDescription() {
        AnomalyAlert alert = new AnomalyAlert("C1",
                AnomalyType.TRANSFER_THEN_CASHOUT,
                Severity.HIGH,
                "Suspicious pattern",
                Collections.singletonList(createTransaction()));

        String result = alert.toString();
        assertTrue(result.contains("Transfer Followed by Cash-Out"));
    }

    @Test
    public void testToStringContainsDetails() {
        String details = "3 high-value transactions within same time step";
        AnomalyAlert alert = new AnomalyAlert("C1",
                AnomalyType.MULTIPLE_HIGH_VALUE_SHORT_TIME,
                Severity.HIGH,
                details,
                Collections.singletonList(createTransaction()));

        String result = alert.toString();
        assertTrue(result.contains(details));
    }

    @Test
    public void testToStringContainsBorderDecoration() {
        AnomalyAlert alert = new AnomalyAlert("C1",
                AnomalyType.REPEATED_HIGH_VALUE,
                Severity.LOW,
                "test",
                Collections.singletonList(createTransaction()));

        String result = alert.toString();
        assertTrue(result.contains("══════════════════════════════════════════════════"));
        assertTrue(result.contains("ANOMALY ALERT"));
    }

    @Test
    public void testToStringContainsInvolvedTransactions() {
        Transaction tx = new Transaction(5, "PAYMENT", 99999.0, "C_FROM",
                200000.0, 100001.0, "C_TO", 0.0, 99999.0, 0, 0);

        AnomalyAlert alert = new AnomalyAlert("C_FROM",
                AnomalyType.SUDDEN_AMOUNT_SPIKE,
                Severity.HIGH,
                "Spike",
                Collections.singletonList(tx));

        String result = alert.toString();
        assertTrue(result.contains("Involved Transactions"));
        assertTrue(result.contains("C_FROM"));
        assertTrue(result.contains("C_TO"));
    }
}
