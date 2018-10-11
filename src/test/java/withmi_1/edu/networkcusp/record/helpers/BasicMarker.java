/**
 * Copyright (c) 2004-2011 QOS.ch
 * All rights reserved.
 *
 * Permission is hereby granted, free  of charge, to any person obtaining
 * a  copy  of this  software  and  associated  documentation files  (the
 * "Software"), to  deal in  the Software without  restriction, including
 * without limitation  the rights to  use, copy, modify,  merge, publish,
 * distribute,  sublicense, and/or sell  copies of  the Software,  and to
 * permit persons to whom the Software  is furnished to do so, subject to
 * the following conditions:
 *
 * The  above  copyright  notice  and  this permission  notice  shall  be
 * included in all copies or substantial portions of the Software.
 *
 * THE  SOFTWARE IS  PROVIDED  "AS  IS", WITHOUT  WARRANTY  OF ANY  KIND,
 * EXPRESS OR  IMPLIED, INCLUDING  BUT NOT LIMITED  TO THE  WARRANTIES OF
 * MERCHANTABILITY,    FITNESS    FOR    A   PARTICULAR    PURPOSE    AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE,  ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */
package withmi_1.edu.networkcusp.record.helpers;

import plv.colorado.edu.quantmchecker.qual.*;
import withmi_1.edu.networkcusp.record.Marker;

import java.util.*;

/**
 * A simple implementation of the {@link Marker} interface.
 * 
 * @author Ceki G&uuml;lc&uuml;
 * @author Joern Huxhorn
 */
public class BasicMarker implements Marker {

    private static final long serialVersionUID = 1803952589649545191L;

    private final String name;
    private List<Marker> referenceList;

    BasicMarker(String name) {
        if (name == null) {
            BasicMarkerGuide();
        }
        this.name = name;
    }

    private void BasicMarkerGuide() {
        throw new IllegalArgumentException("A marker name cannot be null");
    }

    public String getName() {
        return name;
    }

    public synchronized void add(Marker reference) {
        if (reference == null) {
            throw new IllegalArgumentException("A null value cannot be added to a Marker as reference.");
        }

        // no point in adding the reference multiple times
        if (this.contains(reference)) {
            return;

        } else if (reference.contains(this)) { // avoid recursion
            // a potential reference should not its future "parent" as a reference
            return;
        } else {
            // let's add the reference
            if (referenceList == null) {
                addGateKeeper();
            }
            referenceList.add(reference);
        }

    }

    private void addGateKeeper() {
        referenceList = new Vector<Marker>();
    }

    public synchronized boolean hasReferences() {
        return ((referenceList != null) && (referenceList.size() > 0));
    }

    public boolean hasChildren() {
        return hasReferences();
    }

    public synchronized Iterator<Marker> iterator() {
        if (referenceList != null) {
            return referenceList.iterator();
        } else {
            return new ArrayList<Marker>().iterator();
        }
    }

    public synchronized boolean remove(Marker referenceToRemove) {
        if (referenceList == null) {
            return false;
        }

        int size = referenceList.size();
        for (int q = 0; q < size; q++) {
            Marker m = referenceList.get(q);
            if (referenceToRemove.equals(m)) {
                referenceList.remove(q);
                return true;
            }
        }
        return false;
    }

    public boolean contains(Marker other) {
        if (other == null) {
            return new BasicMarkerAid().invoke();
        }

        if (this.equals(other)) {
            return true;
        }

        if (hasReferences()) {
            for (int p = 0; p < referenceList.size(); p++) {
                if (containsService(other, p)) return true;
            }
        }
        return false;
    }

    private boolean containsService(Marker other, int q) {
        Marker ref = referenceList.get(q);
        if (ref.contains(other)) {
            return true;
        }
        return false;
    }

    /**
     * This method is mainly used with Expression Evaluators.
     */
    public boolean contains(String name) {
        if (name == null) {
            return containsFunction();
        }

        if (this.name.equals(name)) {
            return true;
        }

        if (hasReferences()) {
            for (int q = 0; q < referenceList.size(); q++) {
                Marker ref = referenceList.get(q);
                if (ref.contains(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsFunction() {
        throw new IllegalArgumentException("Other cannot be null");
    }

    private static String OPEN = "[ ";
    private static String CLOSE = " ]";
    private static String SEP = ", ";

    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof Marker))
            return false;

        final Marker other = (Marker) obj;
        return name.equals(other.getName());
    }

    public int hashCode() {
        return name.hashCode();
    }

    public String toString() {
        if (!this.hasReferences()) {
            return this.getName();
        }
        @Bound("+ (* 2 this) 3") int i;
        @Iter("<= it this") Iterator<Marker> it = this.iterator();
        Marker reference;
        @Inv("= (- sb it it) (- (+ c209 c210 c213 c215 c218) c212 c212)") StringBuilder sb = new StringBuilder(this.getName());
        c209: sb.append(' ');
        c210: sb.append(OPEN);
        while (it.hasNext()) {
            c212: reference = it.next();
            c213: sb.append(reference.getName());
            if (it.hasNext()) {
                c215: sb.append(SEP);
            }
        }
        c218: sb.append(CLOSE);

        return sb.toString();
    }

    private class BasicMarkerAid {
        public boolean invoke() {
            throw new IllegalArgumentException("Other cannot be null");
        }
    }
}