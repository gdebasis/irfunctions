/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package simfunctions;

import java.util.Comparator;

/**
 * For representing TF, we need two dimensional Bezier functions of the form
 * (x(t), y(t)), where x is normalized TF and y=f(x) is the value of the TF function
 * for that value, e.g. f can be 'close' to the log function.
 * @author Debasis
 */

class TwoDPoint {
    float x;
    float y;

    public TwoDPoint(float x, float y) {
        this.x = x;
        this.y = y;
    }
    
    public String toString() {
        StringBuffer buff = new StringBuffer();
        buff.append("(").append(String.format("%.4f", x)).append(", ")
                .append(String.format("%.4f", y)).append(")");
        return buff.toString();
    }
    
    static TwoDPoint scalarProduct(float a, TwoDPoint p) {
        TwoDPoint scaledPoint = new TwoDPoint(a*p.x, a*p.y);
        return scaledPoint;
    }
    
    static TwoDPoint sum(TwoDPoint a, TwoDPoint b) {
        return new TwoDPoint(a.x + b.x, a.y +  b.y);
    }
}

public class CubicBezierTF implements Comparable<CubicBezierTF> {

    static final float MAX_FREQ = 20f;  // Max frequency of a word
    static final float EPSILON = 0.0001f;
    
    // start is always (0, 0)
    TwoDPoint end; // end is (1, endY)
    TwoDPoint a, b;
    float map;
    
    public CubicBezierTF(float ax, float ay, float bx, float by, float endY) {
        a = new TwoDPoint(ax, ay);
        b = new TwoDPoint(bx, by);
        end = new TwoDPoint(1, endY);
    }
    
    public void setMAP(float map) { this.map = map; }
    
    // Check the constraints
    boolean isValid() {
        // Ensures that the function increases monotonically
        if (a.x >= b.x)
            return false;
        if (a.y >= b.y)
            return false;
        if (a.y >= end.y)
            return false;
        if (b.y >= end.y)
            return false;
        
        // Ensures that the rate of change of the function decreases
        // P2-2P1+P0 < 0
        if (b.x >= 2*a.x)
            return false;
        if (b.y >= 2*a.y)
            return false;
        //P3-2P2+P1 < 0
        if (1+a.x >= 2*b.x)
            return false;
        if (end.y+a.y >= 2*b.y)
            return false;
        
        return true;
    }
    
    TwoDPoint getBezierCurvePoint(float t) {
        float a1 = 3*(float)Math.pow((1-t), 2)*t;
        float a2 = 3*(1-t)*(float)Math.pow(t, 2);
        float a3 = (float)Math.pow(t, 3);
        
        return TwoDPoint.sum(
                TwoDPoint.sum(
                    TwoDPoint.scalarProduct(a1, this.a),
                    TwoDPoint.scalarProduct(a2, this.b)
                ),
                TwoDPoint.scalarProduct(a3, this.end));
    }
    
    // Get the function value, i.e. f(x) given a x value.
    public float getTFScore(float absX) {
        assert(absX <= MAX_FREQ);
        
        float x = absX/MAX_FREQ;
        float start = 0, end = 1, t;
        TwoDPoint mid = null;
        // Find the value of t for which x(t) \approx xPrime 
        // We do this by binary search...
        while (start < end) {
            t = (start + end)/2;
            
            mid = getBezierCurvePoint(t);
            // compare mid.x with x
            if (Math.abs(mid.x - x) < EPSILON) {
                // Got the value of t... terminate search
                break;
            }
            else if (mid.x < x) {
                // continue search on the right half
                start = t;
            }
            else {
                // continue search on the left half
                end = t;                
            }
        }
        return mid.y;
    }
    
    @Override
    public String toString() {
        StringBuffer buff = new StringBuffer();
        buff
            .append("[")
            .append(this.a)
            .append(" ")
            .append(this.b)
            .append(" ")
            .append(String.format("%.4f", this.end.y))
            .append("]")
            .append(" MAP = ")
            .append(map);
        return buff.toString();
    }
    
    public static void main(String[] args) {
        // Unit testing
        CubicBezierTF tf = new CubicBezierTF(.06f, .23f, .58f, .81f, 1.0f);
        System.out.println("f(0.0) = " + tf.getTFScore(0.0f));
        System.out.println("f(0.25) = " + tf.getTFScore(0.25f));
        System.out.println("f(0.5) = " + tf.getTFScore(0.5f));
        System.out.println("f(0.75) = " + tf.getTFScore(0.75f));
        System.out.println("f(1) = " + tf.getTFScore(1.0f));
    }

    @Override
    public int compareTo(CubicBezierTF that) {
        return Float.compare(map, that.map);
    }
}
