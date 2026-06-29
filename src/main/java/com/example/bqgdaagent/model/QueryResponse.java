package com.example.bqgdaagent.model;

public class QueryResponse {

    private String sessionId;
    private String answer;
    private Object rawResponse;

    public QueryResponse() {
    }

    public QueryResponse(String sessionId, String answer, Object rawResponse) {
        this.sessionId = sessionId;
        this.answer = answer;
        this.rawResponse = rawResponse;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public Object getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(Object rawResponse) {
        this.rawResponse = rawResponse;
    }
}
