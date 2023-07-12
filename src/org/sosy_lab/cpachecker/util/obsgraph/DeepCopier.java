package org.sosy_lab.cpachecker.util.obsgraph;

import java.util.HashMap;
import java.util.Map;

public class DeepCopier {

    public <T> T deepCopy(Copier<T> obj) {
        if (obj == null) {
            return null;
        }

        Map<Object, Object> memo = new HashMap<>();
        return obj.deepCopy(memo);
    }
}
