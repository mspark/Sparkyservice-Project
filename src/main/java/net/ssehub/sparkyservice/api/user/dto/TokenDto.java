package net.ssehub.sparkyservice.api.user.dto;

import java.io.Serializable;

public class TokenDto implements Serializable {
    private static final long serialVersionUID = 6425173137954338569L;
    public String token;
    public String expiration;
}