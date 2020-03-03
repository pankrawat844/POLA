package com.example.pola.models;

public class User {
    public User(String id, String name,String token) {
        this.id = id;
        this.name = name;
        this.token = token;
        type = "POLA";
        isHandled=true;
    }

    public String type;
    public String id;
    public String token;
    public String name;
    public double latitude;
    public double longitude;
    public boolean isHandled;
}

