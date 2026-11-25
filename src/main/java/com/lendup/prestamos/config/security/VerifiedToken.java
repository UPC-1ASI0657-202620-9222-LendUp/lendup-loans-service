package com.lendup.prestamos.config.security;
public record VerifiedToken(String uid,boolean emailVerified,boolean administrator){}
