package istc.bigdawg.islands.api;

import istc.bigdawg.executor.QueryResult;
import istc.bigdawg.query.ConnectionInfo;

/**
 * Not used any more, from Initial Proof of Concept
 */

public class ApiQueryResult implements QueryResult {
    private String result;

    public ApiQueryResult(String result) {
        this.result = result;
    }

    @Override
    public String toPrettyString() {
        return result;
    }

    @Override
    public ConnectionInfo getConnectionInfo() {
        return null;
    }
}

