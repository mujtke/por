package org.sosy_lab.cpachecker.util.obsgraph;

import java.util.Map;

public interface Copier<T> {

    /**
     * @param memo Store the objects that have been copied.
     * @return Deep copy of this object.
     */
    T deepCopy(Map<Object, Object> memo);
}
