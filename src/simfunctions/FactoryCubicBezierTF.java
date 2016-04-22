/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package simfunctions;

import java.util.List;
import java.util.PriorityQueue;
import java.util.Properties;
import java.util.TreeSet;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.similarities.Similarity;
import retriever.TrecDocRetriever;
import trec.TRECQuery;

/**
 *
 * @author Debasis
 */
public class FactoryCubicBezierTF {
    float delta;
    int numTopRes;
    TreeSet<CubicBezierTF> topFuncs;
    TrecDocRetriever retriever;
    
    public FactoryCubicBezierTF(TrecDocRetriever retriever) {
        Properties prop = retriever.getProperties();
        this.delta = Float.parseFloat(prop.getProperty("delta", "0.1"));
        numTopRes = Integer.parseInt(prop.getProperty("topfunctions.numwanted", "2"));
        topFuncs = new TreeSet<>();
        this.retriever = retriever;
    }
    
    public void showTopFunctions() {
        for (CubicBezierTF tfFunction : topFuncs) {
            System.out.println(tfFunction);
        }
    }
    
    public void exploreFunctionSpace() throws Exception {
        
        float oneMinusDelta = 1.0f - delta;
        float oneMinusTwiceDelta = 1.0f - 2*delta;
        float thisMAP;
        int count = 0;
        
        for (float x1 = delta; x1 <= oneMinusTwiceDelta; x1 += delta) {
            for (float y1 = delta; y1 <= oneMinusTwiceDelta; y1 += delta) {
                for (float x2 = x1+delta; x2 <= oneMinusDelta; x2 += delta) {
                    for (float y2 = y1+delta; y2 <= oneMinusDelta; y2 += delta) {
                        for (float endY = y2+delta; endY <= 1; endY += delta) {
    
							/*						
                            if (x2 - 2*x1 > 0)
                                continue;
                            if (y2 - 2*y1 > 0)
                                continue;
                            if (1+x1 - 2*x2 > 0)
                                continue;
                            if (endY+y1 - 2*y2 > 0)
                                continue;
                            */
                            count++;

                            CubicBezierTF tfFunc = new CubicBezierTF(x1, y1, x2, y2, endY);
                            System.out.println(count + ": Running retrieval with: " + tfFunc);
                                                        
                            retriever.batchRetrieveTREC(tfFunc);
                            
                            if (topFuncs.size() < numTopRes)
                                topFuncs.add(tfFunc);
                            else {
                                CubicBezierTF worstConf = topFuncs.first();
                                if (tfFunc.compareTo(worstConf) > 0) {
                                    // update the sorted set... found better than the worst seen so far...
                                    // remove the worst and insert this new one
                                    topFuncs.remove(worstConf);
                                    topFuncs.add(tfFunc);
                                }
                            }
                            
                        }
                    }
                }    
            }    
        }
    }
}
