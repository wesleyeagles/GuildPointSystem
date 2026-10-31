package com.guild.app.dll.dto;

/**
 * O que a DLL instalada consulta para se atualizar (rota publica). Os nomes dos campos sao
 * lidos pela DLL (CerberusBags/src/updater.cpp): nao renomear.
 */
public record DllUpdateManifest(String version, String sha256, String signature, int size) {}
