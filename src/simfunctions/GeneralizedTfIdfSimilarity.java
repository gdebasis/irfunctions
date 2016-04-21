/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package simfunctions;

import java.util.Locale;
import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.search.similarities.BasicStats;
import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.search.similarities.LMSimilarity;
import org.apache.lucene.util.BytesRef;

/**
 *
 * @author Debasis
 */
public class GeneralizedTfIdfSimilarity extends LMSimilarity {

    CubicBezierTF tfFunc;

    public GeneralizedTfIdfSimilarity(CubicBezierTF tfFunc) {
        this.tfFunc = tfFunc;
    }
        
    @Override
    public String getName() {
        return "Generalized-TFIDF " + tfFunc.toString();
    }

    @Override
    protected float score(BasicStats stats, float freq, float docLen) {
        // Return the tf-idf score (the tf being a generalized one obtained
        // from the Bezier curve)
        //float tf = tfFunc.getTFScore(freq/stats.getAvgFieldLength()); // docLen
        float tf = tfFunc.getTFScore(freq); // docLen
        float idf = (float)Math.log(stats.getNumberOfDocuments()/(double)stats.getDocFreq());
        return stats.getTotalBoost() * tf * idf;
    }
}
