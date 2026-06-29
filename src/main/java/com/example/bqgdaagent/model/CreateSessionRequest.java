package com.example.bqgdaagent.model;

public class CreateSessionRequest {

    private String userId;
    private String displayName;

    public CreateSessionRequest() {
    }

    public CreateSessionRequest(String userId, String displayName) {
        this.userId = userId;
        this.displayName = displayName;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
