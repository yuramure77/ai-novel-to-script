package com.scripttool.model.dto;

public class AuthResponse {

    private String token;
    private String username;
    private String nickname;

    public AuthResponse(String token, String username, String nickname) {
        this.token = token;
        this.username = username;
        this.nickname = nickname;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}
