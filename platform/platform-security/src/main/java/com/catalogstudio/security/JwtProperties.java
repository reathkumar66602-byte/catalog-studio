package com.catalogstudio.security;

public record JwtProperties(String secret, long accessTokenMinutes, long refreshTokenDays) {}
