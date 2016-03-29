/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package trec;

/**
 *
 * @author Debasis
 */
import java.io.FileReader;
import java.io.StringReader;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import javax.xml.parsers.*;
import java.util.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.Version;
import retriever.TrecDocRetriever;

public class TRECQueryParser extends DefaultHandler {
    StringBuffer        buff;      // Accumulation buffer for storing the current topic
    String              fileName;
    TRECQuery           query;
    Analyzer            analyzer;
    
    public List<TRECQuery>  queries;
    final static String[] tags = {"id", "title", "desc", "narr"};

    public TRECQueryParser(String fileName, Analyzer analyzer) throws SAXException {
        this.fileName = fileName;
        this.analyzer = analyzer;
        buff = new StringBuffer();
        queries = new LinkedList<>();
    }

    public void parse() throws Exception {
        SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
        saxParserFactory.setValidating(false);
        SAXParser saxParser = saxParserFactory.newSAXParser();
        saxParser.parse(fileName, this);
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

    public List<TRECQuery> getQueries() { return queries; }
    
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        try {
            if (qName.equalsIgnoreCase("top")) {
                query = new TRECQuery();
                queries.add(query);
            }
            else
                buff = new StringBuffer();
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }
    
    public Query constructLuceneQueryObj(TRECQuery trecQuery) throws Exception {        
        Query luceneQuery = buildQuery(trecQuery.title);
        return luceneQuery;
    }
    
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        try {
            if (qName.equalsIgnoreCase("title"))
                query.title = buff.toString();            
            else if (qName.equalsIgnoreCase("desc"))
                query.desc = buff.toString();
            else if (qName.equalsIgnoreCase("narr"))
                query.narr = buff.toString();
            else if (qName.equalsIgnoreCase("num"))
                query.id = buff.toString();
            else if (qName.equalsIgnoreCase("top"))
                query.luceneQuery = constructLuceneQueryObj(query);            
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }
    
    @Override
    public void characters(char ch[], int start, int length) throws SAXException {
        buff.append(new String(ch, start, length));
    }
}    
