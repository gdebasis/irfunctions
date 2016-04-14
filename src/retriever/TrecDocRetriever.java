/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package retriever;
import evaluator.Evaluator;
import indexing.TrecDocIndexer;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.store.*;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;
import simfunctions.CubicBezierTF;
import trec.TRECQuery;
import trec.TRECQueryParser;

/**
 *
 * @author Debasis
 */

class TermFreq implements Comparable<TermFreq> {
    String term;
    int tf;  // document component
    float idf;

    public TermFreq(String term, int tf, float idf) {
        this.term = term;
        this.tf = tf;
        this.idf = idf;
    }

    @Override
    public int compareTo(TermFreq t) {
        return -1 * new Integer(tf).compareTo(t.tf); // descending
    }
}

class ScoreDocComparator_DocId implements Comparator<ScoreDoc> {

    @Override
    public int compare(ScoreDoc t, ScoreDoc t1) {
        return new Integer(t.doc).compareTo(t1.doc);
    }    
}

public class TrecDocRetriever {
    IndexReader reader;
    IndexSearcher searcher;    
    Analyzer analyzer;
    Properties prop;
    int numDocsInCollection;
    String runName;
    int numWanted;
    int trecCode;
    Evaluator evaluator;
    
    static final public String FIELD_ID = "id";
    static final public String FIELD_ANALYZED_CONTENT = "words";  // Standard analyzer w/o stopwords.    
    
    public int getTrecCode() { return trecCode; }
    
    protected List<String> buildStopwordList(String stopwordFileName) {
        List<String> stopwords = new ArrayList<>();
        String stopFile = prop.getProperty(stopwordFileName);        
        String line;

        try {
            FileReader fr = new FileReader(stopFile);
            BufferedReader br = new BufferedReader(fr);
            
            while ( (line = br.readLine()) != null ) {
                stopwords.add(line.trim());
            }
            br.close();
            fr.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return stopwords;
    }
    
    public IndexReader getReader() { return reader; }
    public Analyzer getAnalyzer() { return analyzer; }
    
    public TrecDocRetriever(String propFile) throws Exception {
        this.prop = new Properties();
        prop.load(new FileReader(propFile));
        
        String indexDir = prop.getProperty("index");
        
        reader = DirectoryReader.open(FSDirectory.open(new File(indexDir)));
        searcher = new IndexSearcher(reader);
        
        analyzer = new EnglishAnalyzer(
            Version.LUCENE_4_9,
            StopFilter.makeStopSet(
                Version.LUCENE_4_9, buildStopwordList("stopfile"))); // default analyzer
        
        numDocsInCollection = reader.numDocs();
        runName = prop.getProperty("retrieve.runname", "noname");
        numWanted = Integer.parseInt(prop.getProperty("retrieve.num_wanted", "1000"));
        trecCode = Integer.parseInt(prop.getProperty("trec.code", "6"));
        
        evaluator = new Evaluator(this.prop, reader);
    }
    
    public List<TRECQuery> constructQueries(int trecCode) throws Exception {
        String key = "trec." + trecCode;
        List<TRECQuery> queries;
        
        String queryFilePropName = key + ".query.file";  // trec.6.query.file
        String queryFile = prop.getProperty(queryFilePropName);
        TRECQueryParser parser = new TRECQueryParser(queryFile, analyzer);
        parser.parse();
        queries = parser.getQueries();
        
        return queries;
    }    

    public String getTuples(TRECQuery query, TopDocs topDocs, String runName) throws Exception {
        StringBuffer buff = new StringBuffer();
        ScoreDoc[] hits = topDocs.scoreDocs;
        for (int i = 0; i < hits.length; ++i) {
            int docId = hits[i].doc;
            Document d = searcher.doc(docId);
            buff.append(query.id.trim()).append("\tQ0\t").
                    append(d.get(FIELD_ID)).append("\t").
                    append((i+1)).append("\t").
                    append(hits[i].score).append("\t").
                    append(runName).append("\n");                
        }
        return buff.toString();
    }
    
    // Batch retrieve with a particular setting of the tf function.
    // Note that there is one for the document and one for the query.
    // trecCode is either 6, 7 or 8
    public float batchRetrieveTREC(CubicBezierTF dtfFunc) throws Exception {
        System.out.println("Batch retrieving for TREC " + trecCode);
        
        List<TRECQuery> queries = constructQueries(trecCode);
        float map = 0f;
        
        for (TRECQuery query : queries) {
            System.out.println("Retrieving for query: " + query);
            TopScoreDocCollector collector = TopScoreDocCollector.create(1000, true);
            Query luceneQuery = buildQuery(query.title);
            searcher.search(luceneQuery, collector);
            
            // Re-rank based on the custom tf functions for doc and qry
            TopDocs initialList = collector.topDocs();
            DoclistReranker reranker = new DoclistReranker(reader,
                    dtfFunc, luceneQuery, initialList);
            TopDocs rerankedDocs = reranker.rerank();
        
            map += evaluator.computeAP(query.id, rerankedDocs);
        }        
        
        // Evaluate
        // TODO: Write code here to evaluate and keep track of the
        // function settings which yields the highest MAP till now.
        return map;
    }

    void exploreAndEvalFunctionSpace() {
        
    }
    
    private TopDocs randomize(TopDocs topDocs) {
        // Randomize a given ranked list
        // Simply sorting by the docids will be good enough to randomize
        // a list...
        ScoreDoc[] rerankedScoreDocs = Arrays.copyOf(topDocs.scoreDocs, topDocs.scoreDocs.length);
        Arrays.sort(rerankedScoreDocs, new ScoreDocComparator_DocId());
        return new TopDocs(topDocs.scoreDocs.length, rerankedScoreDocs, topDocs.getMaxScore());
    }

    /*
    public TopDocs retrieveWithCustomReranker(String query) throws Exception {
        
        DoclistReranker reranker;        
        searcher.setSimilarity(new BM25Similarity());
        
        // We retrieve 2000 documents so as to give the reranker a chance
        // to retrieve docs beyond 1000 (the standard for TREC)
        TopScoreDocCollector collector = TopScoreDocCollector.create(
                numWanted<<1, true);
        Query luceneQuery = buildQuery(query);

        searcher.search(luceneQuery, collector);
        TopDocs initialList = collector.topDocs();
        
        TopDocs randomizedList = randomize(initialList);
        reranker = new DoclistReranker(reader, luceneQuery, randomizedList);
        TopDocs rerankedDocs = reranker.rerank();

        return rerankedDocs;
    }
    */
    
    String analyze(String query) throws Exception {
        StringBuffer buff = new StringBuffer();
        TokenStream stream = analyzer.tokenStream("dummy", new StringReader(query));
        CharTermAttribute termAtt = stream.addAttribute(CharTermAttribute.class);
        stream.reset();
        while (stream.incrementToken()) {
            String term = termAtt.toString();
            term = term.toLowerCase();
            buff.append(term).append(" ");
        }
        stream.end();
        stream.close();
        return buff.toString();
    }
    
    Query buildQuery(String queryStr) throws Exception {
        BooleanQuery q = new BooleanQuery();
        Term thisTerm = null;
        Query tq = null;
        String[] terms = analyze(queryStr).split("\\s+");

        for (String term : terms) {
            thisTerm = new Term(TrecDocRetriever.FIELD_ANALYZED_CONTENT, term);
            tq = new TermQuery(thisTerm);
            q.add(tq, BooleanClause.Occur.SHOULD);
        }
        return q;
    }
        
    boolean isNumber(String term) {
        int len = term.length();
        for (int i = 0; i < len; i++) {
            char ch = term.charAt(i);
            if (Character.isDigit(ch))
                return true;
        }
        return false;
    }
    
    public String getTfVectorString(int docId) throws Exception {
        Terms terms = reader.getTermVector(docId, FIELD_ANALYZED_CONTENT);
        if (terms == null || terms.size() == 0)
            return "";

        TermsEnum termsEnum;
        BytesRef term;
        List<TermFreq> tfvec = new ArrayList<>();
        
        // Construct the normalized tf vector
        termsEnum = terms.iterator(null); // access the terms for this field
        while ((term = termsEnum.next()) != null) { // explore the terms for this field
            String termStr = term.utf8ToString();
            if (isNumber(termStr))
                continue;
            DocsEnum docsEnum = termsEnum.docs(null, null); // enumerate through documents, in this case only one
            while (docsEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                //get the term frequency in the document
                int tf = docsEnum.freq();
                float idf = numDocsInCollection/(float)reader.docFreq(new Term(FIELD_ANALYZED_CONTENT, term));
                tfvec.add(new TermFreq(termStr, tf, idf));
            }
        }
        
        Collections.sort(tfvec);
        StringBuffer buff = new StringBuffer();
        for (TermFreq tf : tfvec)
            buff.append(tf.term).append(":").append(tf.tf).append(", ").append(tf.idf).append(" ");
        
        if (buff.length()>2) {
            buff.deleteCharAt(buff.length()-1);
            buff.deleteCharAt(buff.length()-1);
        }
        
        return buff.toString();
    }        
    
    public void saveRetrievedTuples(FileWriter fw, TRECQuery query, TopDocs topDocs) throws Exception {
        StringBuffer buff = new StringBuffer();
        ScoreDoc[] hits = topDocs.scoreDocs;
        int len = Math.min(numWanted, hits.length);
        for (int i = 0; i < len; ++i) {
            int docId = hits[i].doc;
            Document d = searcher.doc(docId);
            buff.append(query.id.trim()).append("\tQ0\t").
                    append(d.get(TrecDocIndexer.FIELD_ID)).append("\t").
                    append((i+1)).append("\t").
                    append(hits[i].score).append("\t").
                    append(runName).append("\n");                
        }
        fw.write(buff.toString());        
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            args = new String[1];
            args[0] = "init.properties";
        }
        try {
            TrecDocRetriever searcher = new TrecDocRetriever(args[0]);
            CubicBezierTF tfFunc = new CubicBezierTF(.06f, .23f, .58f, .81f, 1.0f);
            float map = searcher.batchRetrieveTREC(tfFunc);
            System.out.println("MAP (" + tfFunc + ") :" + map);
            
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }        
}
