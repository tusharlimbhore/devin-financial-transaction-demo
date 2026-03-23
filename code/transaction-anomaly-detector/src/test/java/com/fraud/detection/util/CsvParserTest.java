package com.fraud.detection.util;

import com.fraud.detection.model.Transaction;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CsvParserTest {

    private static final String HEADER = "step,type,amount,nameOrig,oldbalanceOrg,newbalanceOrig,nameDest,oldbalanceDest,newbalanceDest,isFraud,isFlaggedFraud";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File createCsvFile(String content) throws IOException {
        File file = tempFolder.newFile("test.csv");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
        return file;
    }

    @Test
    public void testParseValidCsv() throws IOException {
        String csv = HEADER + "\n"
                + "1,PAYMENT,9839.64,C1231006815,170136.0,160296.36,M1979787155,0.0,0.0,0,0\n"
                + "2,TRANSFER,181.0,C1305486145,181.0,0.0,C553264065,0.0,0.0,1,0\n";
        File file = createCsvFile(csv);

        List<Transaction> transactions = CsvParser.parseTransactions(file.getAbsolutePath());

        assertEquals(2, transactions.size());

        Transaction t1 = transactions.get(0);
        assertEquals(1, t1.getStep());
        assertEquals("PAYMENT", t1.getType());
        assertEquals(9839.64, t1.getAmount(), 0.001);
        assertEquals("C1231006815", t1.getNameOrig());
        assertEquals(170136.0, t1.getOldBalanceOrig(), 0.001);
        assertEquals(160296.36, t1.getNewBalanceOrig(), 0.001);
        assertEquals("M1979787155", t1.getNameDest());
        assertEquals(0.0, t1.getOldBalanceDest(), 0.001);
        assertEquals(0.0, t1.getNewBalanceDest(), 0.001);
        assertEquals(0, t1.getIsFraud());
        assertEquals(0, t1.getIsFlaggedFraud());

        Transaction t2 = transactions.get(1);
        assertEquals(2, t2.getStep());
        assertEquals("TRANSFER", t2.getType());
        assertEquals(181.0, t2.getAmount(), 0.001);
        assertEquals(1, t2.getIsFraud());
        assertEquals(0, t2.getIsFlaggedFraud());
    }

    @Test
    public void testParseEmptyFile() throws IOException {
        File file = createCsvFile("");
        List<Transaction> transactions = CsvParser.parseTransactions(file.getAbsolutePath());
        assertTrue(transactions.isEmpty());
    }

    @Test
    public void testParseHeaderOnlyFile() throws IOException {
        File file = createCsvFile(HEADER + "\n");
        List<Transaction> transactions = CsvParser.parseTransactions(file.getAbsolutePath());
        assertTrue(transactions.isEmpty());
    }

    @Test
    public void testParseMalformedLine() throws IOException {
        String csv = HEADER + "\n"
                + "1,PAYMENT,9839.64,C1231006815,170136.0,160296.36,M1979787155,0.0,0.0,0,0\n"
                + "not,a,valid,number,line,x,y,z,w,v,u\n"
                + "2,TRANSFER,181.0,C1305486145,181.0,0.0,C553264065,0.0,0.0,1,0\n";
        File file = createCsvFile(csv);

        List<Transaction> transactions = CsvParser.parseTransactions(file.getAbsolutePath());

        // Malformed line should be skipped; only valid lines parsed
        assertEquals(2, transactions.size());
        assertEquals(1, transactions.get(0).getStep());
        assertEquals(2, transactions.get(1).getStep());
    }

    @Test
    public void testParseMissingFields() throws IOException {
        String csv = HEADER + "\n"
                + "1,PAYMENT,9839.64,C1231006815,170136.0,160296.36,M1979787155,0.0,0.0,0,0\n"
                + "2,TRANSFER,181.0\n"  // fewer than 11 columns
                + "3,CASH_OUT,500.0,C999,1000.0,500.0,C888,0.0,500.0,0,0\n";
        File file = createCsvFile(csv);

        List<Transaction> transactions = CsvParser.parseTransactions(file.getAbsolutePath());

        // Line with <11 fields should be skipped
        assertEquals(2, transactions.size());
        assertEquals(1, transactions.get(0).getStep());
        assertEquals(3, transactions.get(1).getStep());
    }

    @Test
    public void testParseBlankLines() throws IOException {
        String csv = HEADER + "\n"
                + "\n"
                + "1,PAYMENT,9839.64,C1231006815,170136.0,160296.36,M1979787155,0.0,0.0,0,0\n"
                + "   \n"
                + "2,TRANSFER,181.0,C1305486145,181.0,0.0,C553264065,0.0,0.0,1,0\n";
        File file = createCsvFile(csv);

        List<Transaction> transactions = CsvParser.parseTransactions(file.getAbsolutePath());

        // Blank lines should be skipped
        assertEquals(2, transactions.size());
    }

    @Test(expected = IOException.class)
    public void testParseNonExistentFile() throws IOException {
        CsvParser.parseTransactions("/nonexistent/path/file.csv");
    }
}
