package com.tranquility.SpeakSmart.model;

public enum Roles {
    ADMIN,
    Moderator,
    USER;

    public String getAuthority() {
        return this.name();
    }
}