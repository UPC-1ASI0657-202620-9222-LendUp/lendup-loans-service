package com.lendup.prestamos.config.security;
public interface TokenVerifier { VerifiedToken verify(String idToken) throws Exception; }
