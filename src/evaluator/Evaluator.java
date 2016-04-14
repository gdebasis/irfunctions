/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package evaluator;

import indexing.TrecDocIndexer;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;


/**
 *
 * @author Debasis
 */

class PerQueryRelDocs {
    String qid;
    HashMap<String, Integer> relMap; // keyed by docid, entry stores the rel value
    int numRel;
    
    public PerQueryRelDocs(String qid) {
        this.qid = qid;
        numRel = 0;
        relMap = new HashMap<>();
    }
    
    void addTuple(String docId, int rel) {
        if (relMap.get(docId) != null)
            return;
        if (rel > 0) {
            numRel++;
            relMap.put(docId, rel);
        }
    }    
}

class AllRelRcds {
    String qrelsFile;
    HashMap<String, PerQueryRelDocs> perQueryRels;
    int totalNumRel;

    public AllRelRcds(String qrelsFile) {
        this.qrelsFile = qrelsFile;
        perQueryRels = new HashMap<>();
        totalNumRel = 0;
    }
    
    int getTotalNumRel() {
        if (totalNumRel > 0)
            return totalNumRel;
        
        for (Map.Entry<String, PerQueryRelDocs> e : perQueryRels.entrySet()) {
            PerQueryRelDocs perQryRelDocs = e.getValue();
            totalNumRel += perQryRelDocs.numRel;
        }
        return totalNumRel;
    }
    
    void load() throws Exception {
        FileReader fr = new FileReader(qrelsFile);
        BufferedReader br = new BufferedReader(fr);
        String line;
        
        while ((line = br.readLine()) != null) {
            storeRelRcd(line);
        }
        br.close();
        fr.close();
    }
    
    void storeRelRcd(String line) {
        String[] tokens = line.split("\\s+");
        String qid = tokens[0];
        PerQueryRelDocs relTuple = perQueryRels.get(qid);
        if (relTuple == null) {
            relTuple = new PerQueryRelDocs(qid);
            perQueryRels.put(qid, relTuple);
        }
        relTuple.addTuple(tokens[2], Integer.parseInt(tokens[3]));
    }
    
    PerQueryRelDocs getRelInfo(String qid) {
        return perQueryRels.get(qid);
    }
}

class ResultTuple implements Comparable<ResultTuple> {
    String docName; // doc name
    int rank;       // rank of retrieved document
    int rel;    // is this relevant? comes from qrel-info

    public ResultTuple(String docName, int rank) {
        this.docName = docName;
        this.rank = rank;
    }

    @Override
    public int compareTo(ResultTuple t) {
        return rank < t.rank? -1 : rank == t.rank? 0 : 1;
    }
}

class RetrievedResults implements Comparable<RetrievedResults> {
    String qid;
    List<ResultTuple> rtuples;
    int numRelRet;
    IndexReader reader;
    
    public RetrievedResults(IndexReader reader, String qid, TopDocs topDocs) throws Exception {
        this.reader = reader;
        this.qid = qid;
        this.rtuples = new ArrayList<>(1000);
        
        for (int i = 0; i < topDocs.scoreDocs.length; i++) {
            ScoreDoc sd = topDocs.scoreDocs[i];            
            Document d = reader.document(sd.doc);
            String docId = d.get(TrecDocIndexer.FIELD_ID);
            rtuples.add(new ResultTuple(docId, i+1));
        }
    }
    
    void fillRelInfo(PerQueryRelDocs relInfo) {
        String qid = relInfo.qid;
        
        for (ResultTuple rt : rtuples) {
            Integer relIntObj = relInfo.relMap.get(rt.docName); 
            rt.rel = relIntObj == null? 0 : relIntObj.intValue();
        }
    }

    float computeAP(PerQueryRelDocs relInfo) {
        
        fillRelInfo(relInfo);
        
        float prec = 0;
        int numRel = relInfo.numRel;
        int numRelSeen = 0;
        for (ResultTuple tuple : this.rtuples) {            
            if (tuple.rel == 0)
                continue;
            numRelSeen++;
            prec += numRelSeen/(float)(tuple.rank);
        }
        numRelRet = numRelSeen;
        prec = numRel==0? 0 : prec/(float)numRel;
        
        return prec;        
    }
        
    @Override
    public int compareTo(RetrievedResults that) {
        return this.qid.compareTo(that.qid);
    }
}

// In-memory evaluator of intermediate results
public class Evaluator {
    AllRelRcds relRcds;
    IndexReader reader;
    
    public Evaluator(Properties prop, IndexReader reader) throws Exception {
        this.reader = reader;
        String trecCode = prop.getProperty("trec.code", "6");
        String qrelsFile = prop.getProperty("trec." + trecCode + ".qrels.file");        
        relRcds = new AllRelRcds(qrelsFile);
        relRcds.load();
    }
    
    public float computeAP(String qid, TopDocs topDocs) throws Exception {
        RetrievedResults res = new RetrievedResults(reader, qid, topDocs);
        PerQueryRelDocs relDocInfo = relRcds.getRelInfo(qid);
        return res.computeAP(relDocInfo);
    }            
}
