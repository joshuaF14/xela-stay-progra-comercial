package com.xelastay.ai;

@FunctionalInterface
public interface AiToolExecutor {

    String CONSULTAR_ALOJAMIENTOS_DISPONIBLES = "consultar_alojamientos_disponibles";

    String execute(String name);
}
