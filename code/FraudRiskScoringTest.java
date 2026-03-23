import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive JUnit 4 tests for FraudRiskScoring.
 */
public class FraudRiskScoringTest {

    // ---------------------------------------------------------------
    // Helper: build a Transaction from convenient parameters
    // ---------------------------------------------------------------
    private static FraudRiskScoring.Transaction makeTxn(int step, String type, double amount,
                                                         String nameOrig, String nameDest) {
        return makeTxn(step, type, amount, nameOrig, 0.0, 0.0, nameDest, 0.0, 0.0, 0, 0);
    }

    private static FraudRiskScoring.Transaction makeTxn(int step, String type, double amount,
                                                         String nameOrig, double oldBalOrg,
                                                         double newBalOrg, String nameDest,
                                                         double oldBalDest, double newBalDest,
                                                         int isFraud, int isFlaggedFraud) {
        String[] fields = new String[]{
                String.valueOf(step),
                type,
                String.valueOf(amount),
                nameOrig,
                String.valueOf(oldBalOrg),
                String.valueOf(newBalOrg),
                nameDest,
                String.valueOf(oldBalDest),
                String.valueOf(newBalDest),
                String.valueOf(isFraud),
                String.valueOf(isFlaggedFraud)
        };
        return new FraudRiskScoring.Transaction(fields);
    }

    // ---------------------------------------------------------------
    // round2 tests
    // ---------------------------------------------------------------
    @Test
    public void testRound2_basic() {
        assertEquals(1.23, FraudRiskScoring.round2(1.2345), 0.0);
    }

    @Test
    public void testRound2_roundsUp() {
        assertEquals(1.24, FraudRiskScoring.round2(1.235), 0.0);
    }

    @Test
    public void testRound2_zero() {
        assertEquals(0.0, FraudRiskScoring.round2(0.0), 0.0);
    }

    @Test
    public void testRound2_negative() {
        assertEquals(-1.23, FraudRiskScoring.round2(-1.234), 0.0);
    }

    @Test
    public void testRound2_wholeNumber() {
        assertEquals(5.0, FraudRiskScoring.round2(5.0), 0.0);
    }

    @Test
    public void testRound2_manyDecimals() {
        assertEquals(3.14, FraudRiskScoring.round2(3.14159), 0.0);
    }

    // ---------------------------------------------------------------
    // scoreAmount tests
    // ---------------------------------------------------------------
    @Test
    public void testScoreAmount_zero() {
        assertEquals(0.0, FraudRiskScoring.scoreAmount(0.0), 0.001);
    }

    @Test
    public void testScoreAmount_negative() {
        assertEquals(0.0, FraudRiskScoring.scoreAmount(-500.0), 0.001);
    }

    @Test
    public void testScoreAmount_smallAmount() {
        // 1000 / 10000 * 25 = 2.5
        assertEquals(2.5, FraudRiskScoring.scoreAmount(1000.0), 0.001);
    }

    @Test
    public void testScoreAmount_halfThreshold() {
        // 5000 / 10000 * 25 = 12.5
        assertEquals(12.5, FraudRiskScoring.scoreAmount(5000.0), 0.001);
    }

    @Test
    public void testScoreAmount_exactThreshold() {
        // >= 10000 -> full weight 25
        assertEquals(25.0, FraudRiskScoring.scoreAmount(10000.0), 0.001);
    }

    @Test
    public void testScoreAmount_aboveThreshold() {
        assertEquals(25.0, FraudRiskScoring.scoreAmount(50000.0), 0.001);
    }

    @Test
    public void testScoreAmount_verySmall() {
        // 1 / 10000 * 25 = 0.0025 -> rounds to 0.0
        assertEquals(0.0, FraudRiskScoring.scoreAmount(1.0), 0.01);
    }

    @Test
    public void testScoreAmount_justBelowThreshold() {
        // 9999 / 10000 * 25 = 24.9975 -> rounds to 25.0
        assertEquals(25.0, FraudRiskScoring.scoreAmount(9999.0), 0.01);
    }

    // ---------------------------------------------------------------
    // scoreType tests
    // ---------------------------------------------------------------
    @Test
    public void testScoreType_cashOut() {
        assertEquals(20.0, FraudRiskScoring.scoreType("CASH_OUT"), 0.001);
    }

    @Test
    public void testScoreType_transfer() {
        assertEquals(20.0, FraudRiskScoring.scoreType("TRANSFER"), 0.001);
    }

    @Test
    public void testScoreType_payment() {
        assertEquals(0.0, FraudRiskScoring.scoreType("PAYMENT"), 0.001);
    }

    @Test
    public void testScoreType_debit() {
        assertEquals(0.0, FraudRiskScoring.scoreType("DEBIT"), 0.001);
    }

    @Test
    public void testScoreType_cashIn() {
        assertEquals(0.0, FraudRiskScoring.scoreType("CASH_IN"), 0.001);
    }

    @Test
    public void testScoreType_emptyString() {
        assertEquals(0.0, FraudRiskScoring.scoreType(""), 0.001);
    }

    @Test
    public void testScoreType_caseSensitive() {
        // lowercase should NOT match
        assertEquals(0.0, FraudRiskScoring.scoreType("cash_out"), 0.001);
    }

    // ---------------------------------------------------------------
    // scoreNewDestination tests
    // ---------------------------------------------------------------
    @Test
    public void testScoreNewDestination_firstSeen() {
        Map<String, Integer> firstSeen = new HashMap<>();
        firstSeen.put("D1", 0);
        // txnIndex matches firstSeen index -> new destination
        assertEquals(15.0, FraudRiskScoring.scoreNewDestination(0, "D1", firstSeen), 0.001);
    }

    @Test
    public void testScoreNewDestination_seenBefore() {
        Map<String, Integer> firstSeen = new HashMap<>();
        firstSeen.put("D1", 0);
        // txnIndex 5 != firstSeen 0 -> not new
        assertEquals(0.0, FraudRiskScoring.scoreNewDestination(5, "D1", firstSeen), 0.001);
    }

    @Test
    public void testScoreNewDestination_unknownDest() {
        Map<String, Integer> firstSeen = new HashMap<>();
        // dest not in map at all
        assertEquals(0.0, FraudRiskScoring.scoreNewDestination(0, "UNKNOWN", firstSeen), 0.001);
    }

    @Test
    public void testScoreNewDestination_emptyMap() {
        Map<String, Integer> firstSeen = new HashMap<>();
        assertEquals(0.0, FraudRiskScoring.scoreNewDestination(0, "D1", firstSeen), 0.001);
    }

    @Test
    public void testScoreNewDestination_multipleDestinations() {
        Map<String, Integer> firstSeen = new HashMap<>();
        firstSeen.put("D1", 0);
        firstSeen.put("D2", 3);
        firstSeen.put("D3", 7);
        assertEquals(15.0, FraudRiskScoring.scoreNewDestination(3, "D2", firstSeen), 0.001);
        assertEquals(0.0, FraudRiskScoring.scoreNewDestination(5, "D2", firstSeen), 0.001);
    }

    // ---------------------------------------------------------------
    // scoreRapidSequence tests
    // ---------------------------------------------------------------
    @Test
    public void testScoreRapidSequence_noHistory() {
        Map<String, List<int[]>> histories = new HashMap<>();
        assertEquals(0.0, FraudRiskScoring.scoreRapidSequence(0, "A1", 1, histories), 0.001);
    }

    @Test
    public void testScoreRapidSequence_singleEntry() {
        Map<String, List<int[]>> histories = new HashMap<>();
        List<int[]> list = new ArrayList<>();
        list.add(new int[]{0, 1});
        histories.put("A1", list);
        // Only one entry in history -> 0
        assertEquals(0.0, FraudRiskScoring.scoreRapidSequence(0, "A1", 1, histories), 0.001);
    }

    @Test
    public void testScoreRapidSequence_oneNearby() {
        // 1 nearby txn -> 50% of 20 = 10
        Map<String, List<int[]>> histories = new HashMap<>();
        List<int[]> list = new ArrayList<>();
        list.add(new int[]{0, 1});
        list.add(new int[]{1, 1}); // same step
        histories.put("A1", list);
        assertEquals(10.0, FraudRiskScoring.scoreRapidSequence(0, "A1", 1, histories), 0.001);
    }

    @Test
    public void testScoreRapidSequence_twoNearby() {
        // 2 nearby txns -> 75% of 20 = 15
        Map<String, List<int[]>> histories = new HashMap<>();
        List<int[]> list = new ArrayList<>();
        list.add(new int[]{0, 5});
        list.add(new int[]{1, 5}); // same step
        list.add(new int[]{2, 6}); // step diff = 1
        histories.put("A1", list);
        assertEquals(15.0, FraudRiskScoring.scoreRapidSequence(0, "A1", 5, histories), 0.001);
    }

    @Test
    public void testScoreRapidSequence_threeOrMoreNearby() {
        // 3+ nearby txns -> 100% of 20 = 20
        Map<String, List<int[]>> histories = new HashMap<>();
        List<int[]> list = new ArrayList<>();
        list.add(new int[]{0, 5});
        list.add(new int[]{1, 5});
        list.add(new int[]{2, 5});
        list.add(new int[]{3, 6});
        histories.put("A1", list);
        assertEquals(20.0, FraudRiskScoring.scoreRapidSequence(0, "A1", 5, histories), 0.001);
    }

    @Test
    public void testScoreRapidSequence_noNearbySteps() {
        // Entries exist but steps are far apart (diff > 1)
        Map<String, List<int[]>> histories = new HashMap<>();
        List<int[]> list = new ArrayList<>();
        list.add(new int[]{0, 1});
        list.add(new int[]{1, 10}); // step diff = 9
        histories.put("A1", list);
        assertEquals(0.0, FraudRiskScoring.scoreRapidSequence(0, "A1", 1, histories), 0.001);
    }

    @Test
    public void testScoreRapidSequence_adjacentStep() {
        // Step difference of exactly 1 counts as nearby
        Map<String, List<int[]>> histories = new HashMap<>();
        List<int[]> list = new ArrayList<>();
        list.add(new int[]{0, 5});
        list.add(new int[]{1, 4}); // step 4 vs 5 -> diff = 1
        histories.put("A1", list);
        assertEquals(10.0, FraudRiskScoring.scoreRapidSequence(0, "A1", 5, histories), 0.001);
    }

    @Test
    public void testScoreRapidSequence_stepDiffExactlyTwo() {
        // Step difference of 2 is NOT nearby
        Map<String, List<int[]>> histories = new HashMap<>();
        List<int[]> list = new ArrayList<>();
        list.add(new int[]{0, 5});
        list.add(new int[]{1, 3}); // step diff = 2
        histories.put("A1", list);
        assertEquals(0.0, FraudRiskScoring.scoreRapidSequence(0, "A1", 5, histories), 0.001);
    }

    // ---------------------------------------------------------------
    // scoreHighAmountCashoutPattern tests
    // ---------------------------------------------------------------
    @Test
    public void testScoreHighAmountCashout_notCashOut() {
        FraudRiskScoring.Transaction txn = makeTxn(1, "TRANSFER", 50000.0, "A1", "D1");
        List<FraudRiskScoring.Transaction> all = new ArrayList<>();
        all.add(txn);
        Map<String, List<int[]>> histories = new HashMap<>();
        List<int[]> list = new ArrayList<>();
        list.add(new int[]{0, 1});
        histories.put("A1", list);
        assertEquals(0.0, FraudRiskScoring.scoreHighAmountCashoutPattern(txn, 0, "A1", all, histories), 0.001);
    }

    @Test
    public void testScoreHighAmountCashout_bothHighAmount() {
        // Prior txn has high amount AND current CASH_OUT has high amount -> full weight 20
        FraudRiskScoring.Transaction priorTxn = makeTxn(1, "TRANSFER", 20000.0, "A1", "D1");
        FraudRiskScoring.Transaction cashoutTxn = makeTxn(2, "CASH_OUT", 15000.0, "A1", "D2");
        List<FraudRiskScoring.Transaction> all = new ArrayList<>();
        all.add(priorTxn);
        all.add(cashoutTxn);
        Map<String, List<int[]>> histories = new HashMap<>();
        List<int[]> list = new ArrayList<>();
        list.add(new int[]{0, 1});
        list.add(new int[]{1, 2});
        histories.put("A1", list);
        assertEquals(20.0, FraudRiskScoring.scoreHighAmountCashoutPattern(cashoutTxn, 1, "A1", all, histories), 0.001);
    }

    @Test
    public void testScoreHighAmountCashout_onlyPriorHigh() {
        // Prior txn has high amount, current cash_out is low amount -> 60% of 20 = 12
        FraudRiskScoring.Transaction priorTxn = makeTxn(1, "TRANSFER", 20000.0, "A1", "D1");
        FraudRiskScoring.Transaction cashoutTxn = makeTxn(2, "CASH_OUT", 500.0, "A1", "D2");
        List<FraudRiskScoring.Transaction> all = new ArrayList<>();
        all.add(priorTxn);
        all.add(cashoutTxn);
        Map<String, List<int[]>> histories = new HashMap<>();
        List<int[]> list = new ArrayList<>();
        list.add(new int[]{0, 1});
        list.add(new int[]{1, 2});
        histories.put("A1", list);
        assertEquals(12.0, FraudRiskScoring.scoreHighAmountCashoutPattern(cashoutTxn, 1, "A1", all, histories), 0.001);
    }

    @Test
    public void testScoreHighAmountCashout_onlyCurrentHigh() {
        // No prior high amount, but current cash_out is high -> 60% of 20 = 12
        FraudRiskScoring.Transaction priorTxn = makeTxn(1, "TRANSFER", 500.0, "A1", "D1");
        FraudRiskScoring.Transaction cashoutTxn = makeTxn(2, "CASH_OUT", 15000.0, "A1", "D2");
        List<FraudRiskScoring.Transaction> all = new ArrayList<>();
        all.add(priorTxn);
        all.add(cashoutTxn);
        Map<String, List<int[]>> histories = new HashMap<>();
        List<int[]> list = new ArrayList<>();
        list.add(new int[]{0, 1});
        list.add(new int[]{1, 2});
        histories.put("A1", list);
        assertEquals(12.0, FraudRiskScoring.scoreHighAmountCashoutPattern(cashoutTxn, 1, "A1", all, histories), 0.001);
    }

    @Test
    public void testScoreHighAmountCashout_neitherHigh() {
        // No prior high amount, current cash_out is low -> 20% of 20 = 4
        FraudRiskScoring.Transaction priorTxn = makeTxn(1, "TRANSFER", 500.0, "A1", "D1");
        FraudRiskScoring.Transaction cashoutTxn = makeTxn(2, "CASH_OUT", 500.0, "A1", "D2");
        List<FraudRiskScoring.Transaction> all = new ArrayList<>();
        all.add(priorTxn);
        all.add(cashoutTxn);
        Map<String, List<int[]>> histories = new HashMap<>();
        List<int[]> list = new ArrayList<>();
        list.add(new int[]{0, 1});
        list.add(new int[]{1, 2});
        histories.put("A1", list);
        assertEquals(4.0, FraudRiskScoring.scoreHighAmountCashoutPattern(cashoutTxn, 1, "A1", all, histories), 0.001);
    }

    @Test
    public void testScoreHighAmountCashout_noPriorHistory() {
        // CASH_OUT with high amount but no prior txns -> only isHighAmount = true -> 60%
        FraudRiskScoring.Transaction cashoutTxn = makeTxn(1, "CASH_OUT", 15000.0, "A1", "D1");
        List<FraudRiskScoring.Transaction> all = new ArrayList<>();
        all.add(cashoutTxn);
        Map<String, List<int[]>> histories = new HashMap<>();
        List<int[]> list = new ArrayList<>();
        list.add(new int[]{0, 1});
        histories.put("A1", list);
        assertEquals(12.0, FraudRiskScoring.scoreHighAmountCashoutPattern(cashoutTxn, 0, "A1", all, histories), 0.001);
    }

    @Test
    public void testScoreHighAmountCashout_nullHistory() {
        // origin not in histories map at all
        FraudRiskScoring.Transaction cashoutTxn = makeTxn(1, "CASH_OUT", 15000.0, "A1", "D1");
        List<FraudRiskScoring.Transaction> all = new ArrayList<>();
        all.add(cashoutTxn);
        Map<String, List<int[]>> histories = new HashMap<>();
        // A1 not in map -> hasPriorHighAmount = false, isHighAmount = true -> 60%
        assertEquals(12.0, FraudRiskScoring.scoreHighAmountCashoutPattern(cashoutTxn, 0, "A1", all, histories), 0.001);
    }

    @Test
    public void testScoreHighAmountCashout_priorTxnNotBefore() {
        // Prior entry in history has index >= current, so should not count as "prior"
        FraudRiskScoring.Transaction txn0 = makeTxn(1, "CASH_OUT", 500.0, "A1", "D1");
        FraudRiskScoring.Transaction txn1 = makeTxn(2, "TRANSFER", 20000.0, "A1", "D2");
        List<FraudRiskScoring.Transaction> all = new ArrayList<>();
        all.add(txn0);
        all.add(txn1);
        Map<String, List<int[]>> histories = new HashMap<>();
        List<int[]> list = new ArrayList<>();
        list.add(new int[]{0, 1});
        list.add(new int[]{1, 2});
        histories.put("A1", list);
        // txnIndex=0, so idx<0 never true -> hasPriorHighAmount=false, amount=500 -> neither high -> 20% = 4
        assertEquals(4.0, FraudRiskScoring.scoreHighAmountCashoutPattern(txn0, 0, "A1", all, histories), 0.001);
    }

    // ---------------------------------------------------------------
    // classifyRisk tests
    // ---------------------------------------------------------------
    @Test
    public void testClassifyRisk_low() {
        assertEquals("LOW", FraudRiskScoring.classifyRisk(0.0));
        assertEquals("LOW", FraudRiskScoring.classifyRisk(20.0));
        assertEquals("LOW", FraudRiskScoring.classifyRisk(39.99));
    }

    @Test
    public void testClassifyRisk_medium() {
        assertEquals("MEDIUM", FraudRiskScoring.classifyRisk(40.0));
        assertEquals("MEDIUM", FraudRiskScoring.classifyRisk(55.0));
        assertEquals("MEDIUM", FraudRiskScoring.classifyRisk(70.0));
    }

    @Test
    public void testClassifyRisk_high() {
        assertEquals("HIGH", FraudRiskScoring.classifyRisk(70.01));
        assertEquals("HIGH", FraudRiskScoring.classifyRisk(85.0));
        assertEquals("HIGH", FraudRiskScoring.classifyRisk(100.0));
    }

    @Test
    public void testClassifyRisk_exactBoundaryLow() {
        // Exactly 40.0 should be MEDIUM (score >= LOW_THRESHOLD)
        assertEquals("MEDIUM", FraudRiskScoring.classifyRisk(40.0));
    }

    @Test
    public void testClassifyRisk_exactBoundaryHigh() {
        // Exactly 70.0 should be MEDIUM (score <= HIGH_THRESHOLD)
        assertEquals("MEDIUM", FraudRiskScoring.classifyRisk(70.0));
    }

    @Test
    public void testClassifyRisk_justBelowLow() {
        assertEquals("LOW", FraudRiskScoring.classifyRisk(39.999));
    }

    @Test
    public void testClassifyRisk_justAboveHigh() {
        assertEquals("HIGH", FraudRiskScoring.classifyRisk(70.001));
    }

    // ---------------------------------------------------------------
    // buildAccountHistories tests
    // ---------------------------------------------------------------
    @Test
    public void testBuildAccountHistories_empty() {
        List<FraudRiskScoring.Transaction> txns = new ArrayList<>();
        Map<String, List<int[]>> result = FraudRiskScoring.buildAccountHistories(txns);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testBuildAccountHistories_singleTxn() {
        List<FraudRiskScoring.Transaction> txns = new ArrayList<>();
        txns.add(makeTxn(1, "PAYMENT", 100.0, "A1", "D1"));
        Map<String, List<int[]>> result = FraudRiskScoring.buildAccountHistories(txns);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("A1"));
        assertEquals(1, result.get("A1").size());
        assertArrayEquals(new int[]{0, 1}, result.get("A1").get(0));
    }

    @Test
    public void testBuildAccountHistories_multipleOrigins() {
        List<FraudRiskScoring.Transaction> txns = new ArrayList<>();
        txns.add(makeTxn(1, "PAYMENT", 100.0, "A1", "D1"));
        txns.add(makeTxn(2, "TRANSFER", 200.0, "A2", "D2"));
        txns.add(makeTxn(3, "CASH_OUT", 300.0, "A1", "D3"));
        Map<String, List<int[]>> result = FraudRiskScoring.buildAccountHistories(txns);
        assertEquals(2, result.size());
        assertEquals(2, result.get("A1").size());
        assertEquals(1, result.get("A2").size());
        assertArrayEquals(new int[]{0, 1}, result.get("A1").get(0));
        assertArrayEquals(new int[]{2, 3}, result.get("A1").get(1));
        assertArrayEquals(new int[]{1, 2}, result.get("A2").get(0));
    }

    // ---------------------------------------------------------------
    // buildFirstSeenDestinations tests
    // ---------------------------------------------------------------
    @Test
    public void testBuildFirstSeenDestinations_empty() {
        List<FraudRiskScoring.Transaction> txns = new ArrayList<>();
        Map<String, Integer> result = FraudRiskScoring.buildFirstSeenDestinations(txns);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testBuildFirstSeenDestinations_uniqueDests() {
        List<FraudRiskScoring.Transaction> txns = new ArrayList<>();
        txns.add(makeTxn(1, "PAYMENT", 100.0, "A1", "D1"));
        txns.add(makeTxn(2, "PAYMENT", 200.0, "A2", "D2"));
        Map<String, Integer> result = FraudRiskScoring.buildFirstSeenDestinations(txns);
        assertEquals(2, result.size());
        assertEquals(Integer.valueOf(0), result.get("D1"));
        assertEquals(Integer.valueOf(1), result.get("D2"));
    }

    @Test
    public void testBuildFirstSeenDestinations_duplicateDests() {
        List<FraudRiskScoring.Transaction> txns = new ArrayList<>();
        txns.add(makeTxn(1, "PAYMENT", 100.0, "A1", "D1"));
        txns.add(makeTxn(2, "PAYMENT", 200.0, "A2", "D1"));
        txns.add(makeTxn(3, "PAYMENT", 300.0, "A3", "D1"));
        Map<String, Integer> result = FraudRiskScoring.buildFirstSeenDestinations(txns);
        assertEquals(1, result.size());
        // First occurrence is at index 0
        assertEquals(Integer.valueOf(0), result.get("D1"));
    }

    @Test
    public void testBuildFirstSeenDestinations_mixed() {
        List<FraudRiskScoring.Transaction> txns = new ArrayList<>();
        txns.add(makeTxn(1, "PAYMENT", 100.0, "A1", "D1"));
        txns.add(makeTxn(2, "PAYMENT", 200.0, "A2", "D2"));
        txns.add(makeTxn(3, "PAYMENT", 300.0, "A3", "D1"));
        txns.add(makeTxn(4, "PAYMENT", 400.0, "A4", "D3"));
        Map<String, Integer> result = FraudRiskScoring.buildFirstSeenDestinations(txns);
        assertEquals(3, result.size());
        assertEquals(Integer.valueOf(0), result.get("D1"));
        assertEquals(Integer.valueOf(1), result.get("D2"));
        assertEquals(Integer.valueOf(3), result.get("D3"));
    }

    // ---------------------------------------------------------------
    // computeRiskScores tests
    // ---------------------------------------------------------------
    @Test
    public void testComputeRiskScores_emptyList() {
        List<FraudRiskScoring.Transaction> txns = new ArrayList<>();
        List<FraudRiskScoring.ScoredTransaction> results = FraudRiskScoring.computeRiskScores(txns);
        assertTrue(results.isEmpty());
    }

    @Test
    public void testComputeRiskScores_singleLowRisk() {
        // PAYMENT, small amount, new dest, single txn
        List<FraudRiskScoring.Transaction> txns = new ArrayList<>();
        txns.add(makeTxn(1, "PAYMENT", 100.0, "A1", "D1"));
        List<FraudRiskScoring.ScoredTransaction> results = FraudRiskScoring.computeRiskScores(txns);
        assertEquals(1, results.size());
        FraudRiskScoring.ScoredTransaction scored = results.get(0);
        // amount: 100/10000*25 = 0.25, type: 0, newDest: 15, rapid: 0, cashout: 0
        assertEquals(0.25, scored.amountRisk, 0.01);
        assertEquals(0.0, scored.typeRisk, 0.001);
        assertEquals(15.0, scored.newDestinationRisk, 0.001);
        assertEquals(0.0, scored.rapidSequenceRisk, 0.001);
        assertEquals(0.0, scored.highAmountCashoutRisk, 0.001);
        assertEquals("LOW", scored.riskCategory);
    }

    @Test
    public void testComputeRiskScores_singleHighRiskCashOut() {
        // CASH_OUT, high amount, new dest, single txn -> high amount cashout pattern applies
        List<FraudRiskScoring.Transaction> txns = new ArrayList<>();
        txns.add(makeTxn(1, "CASH_OUT", 50000.0, "A1", "D1"));
        List<FraudRiskScoring.ScoredTransaction> results = FraudRiskScoring.computeRiskScores(txns);
        assertEquals(1, results.size());
        FraudRiskScoring.ScoredTransaction scored = results.get(0);
        // amount: 25, type: 20, newDest: 15, rapid: 0
        // cashout: no prior high (only self), isHighAmount=true -> 60% = 12
        assertEquals(25.0, scored.amountRisk, 0.001);
        assertEquals(20.0, scored.typeRisk, 0.001);
        assertEquals(15.0, scored.newDestinationRisk, 0.001);
        assertEquals(0.0, scored.rapidSequenceRisk, 0.001);
        assertEquals(12.0, scored.highAmountCashoutRisk, 0.001);
        // Total: 25 + 20 + 15 + 0 + 12 = 72
        assertEquals(72.0, scored.riskScore, 0.01);
        assertEquals("HIGH", scored.riskCategory);
    }

    @Test
    public void testComputeRiskScores_multiTxnRapidSequence() {
        // Two transactions from same origin at same step
        List<FraudRiskScoring.Transaction> txns = new ArrayList<>();
        txns.add(makeTxn(1, "TRANSFER", 5000.0, "A1", "D1"));
        txns.add(makeTxn(1, "TRANSFER", 5000.0, "A1", "D2"));
        List<FraudRiskScoring.ScoredTransaction> results = FraudRiskScoring.computeRiskScores(txns);
        assertEquals(2, results.size());
        // Both should have rapid sequence score (1 nearby -> 50% of 20 = 10)
        assertEquals(10.0, results.get(0).rapidSequenceRisk, 0.001);
        assertEquals(10.0, results.get(1).rapidSequenceRisk, 0.001);
    }

    @Test
    public void testComputeRiskScores_highAmountFollowedByCashOut() {
        // Classic fraud pattern: high TRANSFER then CASH_OUT
        List<FraudRiskScoring.Transaction> txns = new ArrayList<>();
        txns.add(makeTxn(1, "TRANSFER", 50000.0, "A1", "D1"));
        txns.add(makeTxn(2, "CASH_OUT", 50000.0, "A1", "D2"));
        List<FraudRiskScoring.ScoredTransaction> results = FraudRiskScoring.computeRiskScores(txns);
        assertEquals(2, results.size());
        // Second txn: both prior high AND current high -> full weight 20
        assertEquals(20.0, results.get(1).highAmountCashoutRisk, 0.001);
    }

    @Test
    public void testComputeRiskScores_scoresCappedAt100() {
        // Create a scenario where raw total exceeds 100
        // CASH_OUT, very high amount, new dest, rapid sequence, prior high amount
        List<FraudRiskScoring.Transaction> txns = new ArrayList<>();
        txns.add(makeTxn(1, "TRANSFER", 50000.0, "A1", "D1"));
        txns.add(makeTxn(1, "TRANSFER", 50000.0, "A1", "D2"));
        txns.add(makeTxn(1, "TRANSFER", 50000.0, "A1", "D3"));
        txns.add(makeTxn(2, "CASH_OUT", 50000.0, "A1", "D4"));
        List<FraudRiskScoring.ScoredTransaction> results = FraudRiskScoring.computeRiskScores(txns);
        FraudRiskScoring.ScoredTransaction lastScored = results.get(3);
        // amount:25 + type:20 + newDest:15 + rapid(3 nearby):20 + cashout(both high):20 = 100
        assertTrue(lastScored.riskScore <= 100.0);
    }

    @Test
    public void testComputeRiskScores_newDestOnlyFirstOccurrence() {
        // D1 appears at index 0 and 2 — only index 0 should get new dest score
        List<FraudRiskScoring.Transaction> txns = new ArrayList<>();
        txns.add(makeTxn(1, "PAYMENT", 100.0, "A1", "D1"));
        txns.add(makeTxn(2, "PAYMENT", 100.0, "A2", "D2"));
        txns.add(makeTxn(3, "PAYMENT", 100.0, "A3", "D1"));
        List<FraudRiskScoring.ScoredTransaction> results = FraudRiskScoring.computeRiskScores(txns);
        assertEquals(15.0, results.get(0).newDestinationRisk, 0.001);
        assertEquals(15.0, results.get(1).newDestinationRisk, 0.001);
        assertEquals(0.0, results.get(2).newDestinationRisk, 0.001);
    }

    // ---------------------------------------------------------------
    // Transaction constructor tests
    // ---------------------------------------------------------------
    @Test
    public void testTransactionConstructor() {
        String[] fields = {"1", "CASH_OUT", "1000.50", "C100", "5000.0", "4000.0",
                "M200", "0.0", "1000.50", "1", "0"};
        FraudRiskScoring.Transaction txn = new FraudRiskScoring.Transaction(fields);
        assertEquals(1, txn.step);
        assertEquals("CASH_OUT", txn.type);
        assertEquals(1000.50, txn.amount, 0.001);
        assertEquals("C100", txn.nameOrig);
        assertEquals(5000.0, txn.oldbalanceOrg, 0.001);
        assertEquals(4000.0, txn.newbalanceOrig, 0.001);
        assertEquals("M200", txn.nameDest);
        assertEquals(0.0, txn.oldbalanceDest, 0.001);
        assertEquals(1000.50, txn.newbalanceDest, 0.001);
        assertEquals(1, txn.isFraud);
        assertEquals(0, txn.isFlaggedFraud);
    }

    @Test
    public void testTransactionConstructor_withWhitespace() {
        String[] fields = {" 2 ", " TRANSFER ", " 500.0 ", " C200 ", " 1000.0 ",
                " 500.0 ", " M300 ", " 0.0 ", " 500.0 ", " 0 ", " 1 "};
        FraudRiskScoring.Transaction txn = new FraudRiskScoring.Transaction(fields);
        assertEquals(2, txn.step);
        assertEquals("TRANSFER", txn.type);
        assertEquals(500.0, txn.amount, 0.001);
        assertEquals("C200", txn.nameOrig);
        assertEquals(0, txn.isFraud);
        assertEquals(1, txn.isFlaggedFraud);
    }

    // ---------------------------------------------------------------
    // ScoredTransaction tests
    // ---------------------------------------------------------------
    @Test
    public void testScoredTransactionDefaults() {
        FraudRiskScoring.Transaction txn = makeTxn(1, "PAYMENT", 100.0, "A1", "D1");
        FraudRiskScoring.ScoredTransaction scored = new FraudRiskScoring.ScoredTransaction(txn);
        assertSame(txn, scored.txn);
        assertEquals(0.0, scored.riskScore, 0.001);
        assertNull(scored.riskCategory);
        assertEquals(0.0, scored.amountRisk, 0.001);
        assertEquals(0.0, scored.typeRisk, 0.001);
        assertEquals(0.0, scored.newDestinationRisk, 0.001);
        assertEquals(0.0, scored.rapidSequenceRisk, 0.001);
        assertEquals(0.0, scored.highAmountCashoutRisk, 0.001);
    }

    // ---------------------------------------------------------------
    // End-to-end integration test
    // ---------------------------------------------------------------
    @Test
    public void testEndToEnd_mixedTransactions() {
        List<FraudRiskScoring.Transaction> txns = new ArrayList<>();
        // Low risk: small PAYMENT
        txns.add(makeTxn(1, "PAYMENT", 50.0, "C1", "M1"));
        // Medium risk: TRANSFER with moderate amount
        txns.add(makeTxn(2, "TRANSFER", 8000.0, "C2", "M2"));
        // High risk: large CASH_OUT to new dest
        txns.add(makeTxn(3, "CASH_OUT", 50000.0, "C3", "M3"));
        // Rapid sequence from C2
        txns.add(makeTxn(2, "TRANSFER", 8000.0, "C2", "M4"));
        // High amount followed by cash_out from C3
        txns.add(makeTxn(4, "CASH_OUT", 30000.0, "C3", "M5"));

        List<FraudRiskScoring.ScoredTransaction> results = FraudRiskScoring.computeRiskScores(txns);
        assertEquals(5, results.size());

        // Verify first txn is low risk
        assertEquals("LOW", results.get(0).riskCategory);

        // Verify all scored transactions have valid risk categories
        for (FraudRiskScoring.ScoredTransaction scored : results) {
            assertTrue(scored.riskScore >= 0.0);
            assertTrue(scored.riskScore <= 100.0);
            assertTrue("LOW".equals(scored.riskCategory)
                    || "MEDIUM".equals(scored.riskCategory)
                    || "HIGH".equals(scored.riskCategory));
        }

        // The last CASH_OUT from C3 should have high amount cashout pattern
        // C3 had a prior high amount txn (index 2, amount 50000) and current is also high
        assertEquals(20.0, results.get(4).highAmountCashoutRisk, 0.001);
    }

    // ---------------------------------------------------------------
    // Edge case: exact boundary for HIGH_AMOUNT_THRESHOLD in cashout
    // ---------------------------------------------------------------
    @Test
    public void testScoreHighAmountCashout_exactThreshold() {
        // amount == 10000 is NOT > HIGH_AMOUNT_THRESHOLD (it uses > not >=)
        FraudRiskScoring.Transaction priorTxn = makeTxn(1, "TRANSFER", 10000.0, "A1", "D1");
        FraudRiskScoring.Transaction cashoutTxn = makeTxn(2, "CASH_OUT", 10000.0, "A1", "D2");
        List<FraudRiskScoring.Transaction> all = new ArrayList<>();
        all.add(priorTxn);
        all.add(cashoutTxn);
        Map<String, List<int[]>> histories = new HashMap<>();
        List<int[]> list = new ArrayList<>();
        list.add(new int[]{0, 1});
        list.add(new int[]{1, 2});
        histories.put("A1", list);
        // 10000 is NOT > 10000, so neither hasPriorHighAmount nor isHighAmount -> 20% = 4
        assertEquals(4.0, FraudRiskScoring.scoreHighAmountCashoutPattern(cashoutTxn, 1, "A1", all, histories), 0.001);
    }

    @Test
    public void testScoreHighAmountCashout_justAboveThreshold() {
        FraudRiskScoring.Transaction priorTxn = makeTxn(1, "TRANSFER", 10001.0, "A1", "D1");
        FraudRiskScoring.Transaction cashoutTxn = makeTxn(2, "CASH_OUT", 10001.0, "A1", "D2");
        List<FraudRiskScoring.Transaction> all = new ArrayList<>();
        all.add(priorTxn);
        all.add(cashoutTxn);
        Map<String, List<int[]>> histories = new HashMap<>();
        List<int[]> list = new ArrayList<>();
        list.add(new int[]{0, 1});
        list.add(new int[]{1, 2});
        histories.put("A1", list);
        // Both > 10000 -> full weight 20
        assertEquals(20.0, FraudRiskScoring.scoreHighAmountCashoutPattern(cashoutTxn, 1, "A1", all, histories), 0.001);
    }
}
