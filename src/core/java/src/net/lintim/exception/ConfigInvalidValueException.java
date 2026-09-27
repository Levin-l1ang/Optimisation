package net.lintim.exception;
import java.util.*;

public class ConfigInvalidValueException extends LinTimException {

    public ConfigInvalidValueException(List<String> parameterNames) {
        super("Error C5: Value(s) of config parameter(s)" + parameterNames + "are invalid or incompatible in this context");
    }
}
