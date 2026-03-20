package com.fraud.detection;

import com.fraud.detection.detector.MultipleHighValueShortTimeDetector;
import com.fraud.detection.detector.RepeatedHighValueDetector;
import com.fraud.detection.detector.SuddenAmountSpikeDetector;
import com.fraud.detection.detector.SuddenTypeChangeDetector;
import com.fraud.detection.detector.TransferCashOutDetector;
import com.fraud.detection.model.AnomalyAlert;
import com.fraud.detection.model.AnomalyAlert.AnomalyType;
import com.fraud.detection.model.Transaction;
import com.fraud.detection.service.TransactionAnalysisService;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TransactionAnomalyDetectorTest {

    private Transaction createTx(int step, String type, double amount, String nameOrig) {
        return new Transaction(step, type, amount, nameOrig, 10000, 10000 - amount,
                "C_DEST", 0, amount, 0, 0);
    }

    @Test
    public void testRepeatedHighValueDetector() {
        RepeatedHighValueDetector detector = new RepeatedHighValueDetector();
        List<Transaction> txns = Arrays.asList(
                createTx(1, "PAYMENT", 15000, "C1"),
                createTx(1, "PAYMENT", 20000, "C1"),
                createTx(1, "PAYMENT", 12000, "C1")
        );

        List<AnomalyAlert> alerts = detector.detect("C1", txns);
        assertEquals(1, alerts.size());
        assertEquals(AnomalyType.REPEATED_HIGH_VALUE, alerts.get(0).getAnomalyType());
    }

    @Test
    public void testRepeatedHighValueDetector_noAlert() {
        RepeatedHighValueDetector detector = new RepeatedHighValueDetector();
        List<Transaction> txns = Arrays.asList(
                createTx(1, "PAYMENT", 15000, "C1"),
                createTx(1, "PAYMENT", 500, "C1"),
                createTx(1, "PAYMENT", 12000, "C1")
        );

        List<AnomalyAlert> alerts = detector.detect("C1", txns);
        assertTrue(alerts.isEmpty());
    }

    @Test
    public void testTransferCashOutDetector() {
        TransferCashOutDetector detector = new TransferCashOutDetector();
        List<Transaction> txns = Arrays.asList(
                createTx(1, "PAYMENT", 1000, "C1"),
                createTx(1, "TRANSFER", 50000, "C1"),
                createTx(1, "CASH_OUT", 49000, "C1")
        );

        List<AnomalyAlert> alerts = detector.detect("C1", txns);
        assertEquals(1, alerts.size());
        assertEquals(AnomalyType.TRANSFER_THEN_CASHOUT, alerts.get(0).getAnomalyType());
    }

    @Test
    public void testTransferTransferCashOutDetector() {
        TransferCashOutDetector detector = new TransferCashOutDetector();
        List<Transaction> txns = Arrays.asList(
                createTx(1, "TRANSFER", 50000, "C1"),
                createTx(1, "TRANSFER", 30000, "C1"),
                createTx(1, "CASH_OUT", 49000, "C1")
        );

        List<AnomalyAlert> alerts = detector.detect("C1", txns);
        assertFalse(alerts.isEmpty());
        boolean hasTTCO = alerts.stream()
                .anyMatch(a -> a.getAnomalyType() == AnomalyType.TRANSFER_TRANSFER_CASHOUT);
        assertTrue(hasTTCO);
    }

    @Test
    public void testSuddenAmountSpikeDetector() {
        SuddenAmountSpikeDetector detector = new SuddenAmountSpikeDetector();
        List<Transaction> txns = Arrays.asList(
                createTx(1, "PAYMENT", 500, "C1"),
                createTx(1, "PAYMENT", 600, "C1"),
                createTx(1, "PAYMENT", 550, "C1"),
                createTx(1, "PAYMENT", 50000, "C1")
        );

        List<AnomalyAlert> alerts = detector.detect("C1", txns);
        assertFalse(alerts.isEmpty());
        assertEquals(AnomalyType.SUDDEN_AMOUNT_SPIKE, alerts.get(0).getAnomalyType());
    }

    @Test
    public void testSuddenAmountSpikeDetector_noSpike() {
        SuddenAmountSpikeDetector detector = new SuddenAmountSpikeDetector();
        List<Transaction> txns = Arrays.asList(
                createTx(1, "PAYMENT", 500, "C1"),
                createTx(1, "PAYMENT", 600, "C1"),
                createTx(1, "PAYMENT", 550, "C1")
        );

        List<AnomalyAlert> alerts = detector.detect("C1", txns);
        assertTrue(alerts.isEmpty());
    }

    @Test
    public void testSuddenTypeChangeDetector() {
        SuddenTypeChangeDetector detector = new SuddenTypeChangeDetector();
        List<Transaction> txns = Arrays.asList(
                createTx(1, "PAYMENT", 1000, "C1"),
                createTx(1, "PAYMENT", 2000, "C1"),
                createTx(1, "PAYMENT", 1500, "C1"),
                createTx(1, "TRANSFER", 50000, "C1")
        );

        List<AnomalyAlert> alerts = detector.detect("C1", txns);
        assertFalse(alerts.isEmpty());
        assertEquals(AnomalyType.SUDDEN_TYPE_CHANGE, alerts.get(0).getAnomalyType());
    }

    @Test
    public void testMultipleHighValueShortTimeDetector() {
        MultipleHighValueShortTimeDetector detector = new MultipleHighValueShortTimeDetector();
        List<Transaction> txns = Arrays.asList(
                createTx(1, "PAYMENT", 15000, "C1"),
                createTx(1, "TRANSFER", 20000, "C1"),
                createTx(1, "CASH_OUT", 12000, "C1")
        );

        List<AnomalyAlert> alerts = detector.detect("C1", txns);
        assertEquals(1, alerts.size());
        assertEquals(AnomalyType.MULTIPLE_HIGH_VALUE_SHORT_TIME, alerts.get(0).getAnomalyType());
    }

    @Test
    public void testAnalysisServiceIntegration() {
        TransactionAnalysisService service = new TransactionAnalysisService();

        List<Transaction> txns = Arrays.asList(
                createTx(1, "TRANSFER", 50000, "C1"),
                createTx(1, "TRANSFER", 30000, "C1"),
                createTx(1, "CASH_OUT", 49000, "C1"),
                createTx(1, "PAYMENT", 500, "C2"),
                createTx(1, "PAYMENT", 600, "C2")
        );

        Map<String, List<AnomalyAlert>> alerts = service.analyzeTransactions(txns);
        assertTrue(alerts.containsKey("C1"));
        assertFalse(alerts.containsKey("C2")); // C2 has no anomalies
    }

    @Test
    public void testEmptyTransactions() {
        TransactionAnalysisService service = new TransactionAnalysisService();
        Map<String, List<AnomalyAlert>> alerts = service.analyzeTransactions(Collections.emptyList());
        assertTrue(alerts.isEmpty());
    }
}
