package com.xelastay.events;

/** Envía un evento del outbox al broker. Lanza una excepción si el broker no lo confirma. */
public interface EventSender {

    void send(OutboxEvent event);
}
