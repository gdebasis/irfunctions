/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package retriever;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.BytesRef;
import static retriever.TrecDocRetriever.FIELD_ANALYZED_CONTENT;

/**
 *
 * @author Debasis
 */

class TermWt implements Comparable<TermWt> {
    String term;
    float wt;

    TermWt(String term, float wt) {
        this.term = term;
        this.wt = wt;
    }
    
    TermWt(TermWt that) {
        this.term = that.term;
        this.wt = that.wt;
    }
    
    @Override
    public int compareTo(TermWt t) {
        return this.term.compareTo(t.term);
    }    
}

class DocVector {
    TermWt[] vec;
    IndexReader reader;
    
    DocVector(DocVector that, float[] rewts) {
        vec = new TermWt[that.vec.length];
        for (int i = 0; i < that.vec.length; i++) {
            this.vec[i] = new TermWt(that.vec[i]);
        }
    }
    
    DocVector(IndexReader reader, Query q) {
        this.reader = reader;
        
        Set<Term> terms = new HashSet<Term>();
        q.extractTerms(terms);
        vec = new TermWt[terms.size()];
        
        int i = 0;
        for (Term t : terms) {
            vec[i] = new TermWt(t.text(), 1.0f);
            i++;
        }
        
        Arrays.sort(vec);
    }
    
    DocVector(IndexReader reader, int docId) throws Exception {
        this.reader = reader;
        Terms terms = reader.getTermVector(docId, FIELD_ANALYZED_CONTENT);

        TermsEnum termsEnum;
        BytesRef term;
        List<TermWt> tfvec = new ArrayList<>();
        
        // Construct the normalized tf vector
        termsEnum = terms.iterator(null); // access the terms for this field
        while ((term = termsEnum.next()) != null) { // explore the terms for this field
            String termStr = term.utf8ToString();
            DocsEnum docsEnum = termsEnum.docs(null, null); // enumerate through documents, in this case only one
            while (docsEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                //get the term frequency in the document
                int tf = docsEnum.freq();
                tfvec.add(new TermWt(termStr, tf));
            }
        }
        
        Collections.sort(tfvec);
        
        vec = new TermWt[tfvec.size()];
        vec = tfvec.toArray(vec);
    }
    
    float cosineSim(DocVector that) { // cosinesim(this, that)
        int i = 0, j = 0;
        float sim = 0;
        
        while (i < this.vec.length && j < that.vec.length) {
            int cmp = this.vec[i].term.compareTo(that.vec[j].term);
            if (cmp == 0) {
                sim += this.vec[i].wt * that.vec[j].wt;
                i++;
                j++;
            }
            else if (cmp < 0)
                i++;
            else
                j++;            
        }
        return sim;
    }

    float[] getTfs() {
        float[] tfwts = new float[this.vec.length];
        for (int i = 0; i < tfwts.length; i++) {
            tfwts[i] = this.vec[i].wt;
        }
        return tfwts;
    }
    
    float[] getIdfs() {        
        float[] idfs = new float[this.vec.length];
        int numDocs = reader.numDocs();
        for (int i = 0; i < idfs.length; i++) {
            Term t = new Term(TrecDocRetriever.FIELD_ANALYZED_CONTENT, this.vec[i].term);
            try {
                int docFreq = reader.docFreq(t);            
                idfs[i] = numDocs/(float)docFreq;
            }
            catch (Exception ex) { ex.printStackTrace(); }
        }
        return idfs;        
    }
}

class ScoreDocComparator implements Comparator<ScoreDoc> {
    @Override
    public int compare(ScoreDoc s1, ScoreDoc s2) { // descending
        return s1.score < s2.score? 1 : s1.score == s2.score? 0 : -1;
    }
}

public class DoclistReranker {
    IndexReader reader;
    Query query;
    TopDocs topDocs;
    DocVector qvec;
    File workDir;
    
    final static String PackageName = "simfuns";
    final static String DocReWtMethodName = "rewtDoc";
    final static String QryReWtMethodName = "rewtQry";
    
    public DoclistReranker(File workDir, IndexReader reader, Query query, TopDocs topDocs) {        
        this.workDir = workDir;        
        this.reader = reader;
        this.query = query;
        this.topDocs = topDocs;
        qvec = new DocVector(reader, query);
    }
    
    Class<?> getReWtClass(File workDirF, String className) throws Exception {
        URL[] cp = {workDirF.toURI().toURL()};
        URLClassLoader urlcl = new URLClassLoader(cp);
        Class<?> clazz = urlcl.loadClass(PackageName + "." + className);
        return clazz;
    }
    
    Object getRewtObj(Class<?> clazz) throws Exception {
        Object rewtObj = null;
        Constructor ctor = clazz.getConstructor();
        rewtObj = ctor.newInstance();
        return rewtObj;
    }
    
    DocVector getReWeightedVec(DocVector vec, Class<?> rewtClass, Object rewtObject, String methodName) throws Exception {
        DocVector rewtVec;        
        float[] tfs = vec.getTfs();
        float[] idfs = vec.getIdfs();                
        float[] rewts = reweight(rewtClass, rewtObject, methodName, tfs, idfs);        
        rewtVec = new DocVector(vec, rewts);
        return rewtVec;        
    }

    float[] reweight(Class<?> rewtClass, Object rewtObj, String methodName,
                    float[] tfs, float[] idfs) throws Exception {
        float[] rewts = (float[])(rewtClass
            .getDeclaredMethod(methodName, float[].class, float[].class)
            .invoke(rewtObj, tfs, idfs));
        return rewts;
    }    
    
    public TopDocs rerank(String clsName) throws Exception {
        TopDocs reranked = null;
        ScoreDoc[] newScoreDocs = new ScoreDoc[topDocs.scoreDocs.length];
    
        Class<?> rewtClass = getReWtClass(workDir, clsName);
        Object rewtObj = getRewtObj(rewtClass);
        
        // Re-weight the document and the query vectors
        DocVector wtqvec = getReWeightedVec(this.qvec, rewtClass, rewtObj, QryReWtMethodName);
        
        try {
            int i = 0;
            for (ScoreDoc sd : topDocs.scoreDocs) {
                int docid = sd.doc;
                DocVector dvec = new DocVector(reader, docid);
                dvec = getReWeightedVec(dvec, rewtClass, rewtObj, DocReWtMethodName);
                newScoreDocs[i++] = new ScoreDoc(docid, dvec.cosineSim(wtqvec));
            }
            // sort by similarity scores
            Arrays.sort(newScoreDocs, new ScoreDocComparator());
            
            reranked = new TopDocs(newScoreDocs.length, newScoreDocs, newScoreDocs[0].score);
        }
        catch (Exception ex) {
            return topDocs;
        }
        return reranked;
    }
}
