/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package simfunctions;

import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.util.BytesRef;

/**
 *
 * @author Debasis
 */
public class GeneralizedTfIdfSimilarity extends DefaultSimilarity {

    CubicBezierTF tfFunc;

    public GeneralizedTfIdfSimilarity(CubicBezierTF tfFunc) {
        this.tfFunc = tfFunc;
    }
        
    @Override
    public float tf(float freq) {
        return tfFunc.getTFScore(freq);
    }
}
