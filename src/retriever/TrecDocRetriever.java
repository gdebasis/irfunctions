/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package retriever;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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
    HashMap<String, List<TRECQuery>> trecQriesMap;
    File workDirF;
    int numDocsInCollection;
    
    static final int NUMDOCS_TO_REPORT_FOR_CUSTOM_SIM = 20;
    static final public String FIELD_ID = "id";
    static final public String FIELD_ANALYZED_CONTENT = "words";  // Standard analyzer w/o stopwords.    
    
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
        workDirF = new File(prop.getProperty("workdir"));
        
        reader = DirectoryReader.open(FSDirectory.open(new File(indexDir)));
        searcher = new IndexSearcher(reader);
        
        analyzer = new EnglishAnalyzer(
            Version.LUCENE_4_9,
            StopFilter.makeStopSet(
                Version.LUCENE_4_9, buildStopwordList("stopfile"))); // default analyzer
        
        trecQriesMap = new HashMap<>();
        
        numDocsInCollection = reader.numDocs();
    }

    public List<TRECQuery> constructQueries(int trecCode) throws Exception {
        String key = "trec." + trecCode;
        List<TRECQuery> queries = trecQriesMap.get(key);
        if (queries != null)
            return queries;
        
        String queryFilePropName = key + ".query.file";
        String queryFile = prop.getProperty(queryFilePropName);
        TRECQueryParser parser = new TRECQueryParser(queryFile, analyzer);
        parser.parse();
        queries = parser.getQueries();
        
        // cache for later use from in-memory
        trecQriesMap.put(key, queries);
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
    
    // trecCode is either 6, 7 or 8
    public String batchRetrieveTREC(int trecCode, String retFunc, float k, float b) throws Exception {
        setSimilarity(retFunc, k, b);
        String runName = retFunc + "." + k + "." + b;
        StringBuffer trecBatchRes = new StringBuffer();
        List<TRECQuery> queries = constructQueries(trecCode);
        
        for (TRECQuery query : queries) {
            TopScoreDocCollector collector = TopScoreDocCollector.create(1000, true);
            Query luceneQuery = buildQuery(query.title);
            searcher.search(luceneQuery, collector);
            trecBatchRes.append(getTuples(query, collector.topDocs(), runName));
        }        
        return trecBatchRes.toString();
    }
    
    private TopDocs randomize(TopDocs topDocs) {
        // Randomize a given ranked list
        // Simply sorting by the docids will be good enough to randomize
        // a list...
        ScoreDoc[] rerankedScoreDocs = Arrays.copyOf(topDocs.scoreDocs, topDocs.scoreDocs.length);
        Arrays.sort(rerankedScoreDocs, new ScoreDocComparator_DocId());
        return new TopDocs(topDocs.scoreDocs.length, rerankedScoreDocs, topDocs.getMaxScore());
    }
    
    public TopDocs retrieveWithCustomReranker(String query, String clsName) throws Exception {
        
        DoclistReranker reranker;        
        searcher.setSimilarity(new BM25Similarity());
        
        // We retrieve 2000 documents so as to give the reranker a chance
        // to retrieve docs beyond 1000 (the standard for TREC)
        TopScoreDocCollector collector = TopScoreDocCollector.create(
                NUMDOCS_TO_REPORT_FOR_CUSTOM_SIM, true);
        Query luceneQuery = buildQuery(query);

        searcher.search(luceneQuery, collector);
        TopDocs initialList = collector.topDocs();
        
        TopDocs randomizedList = randomize(initialList);
        reranker = new DoclistReranker(workDirF, reader, luceneQuery, randomizedList);
        TopDocs rerankedDocs = reranker.rerank(clsName);

        return rerankedDocs;
    }

    void setSimilarity(String retFunc, float k, float b) {
        // Create sim object
        Similarity sim = new DefaultSimilarity();
        if (retFunc.equals("BM25"))
            sim = new BM25Similarity(k, b);
        
        searcher.setSimilarity(sim);        
    }
    
    public void retrieve(String query, String retFunc, int nwanted, float k, float b) throws Exception {
        setSimilarity(retFunc, k, b);
        
        TopScoreDocCollector collector;
        collector = TopScoreDocCollector.create(nwanted, true);
                
        Query luceneQuery = buildQuery(query);
        searcher.search(luceneQuery, collector);        
    }
    
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
}
