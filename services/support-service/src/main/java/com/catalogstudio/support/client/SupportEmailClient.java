package com.catalogstudio.support.client;

import com.catalogstudio.events.support.SupportEmailNotificationEvent;

public interface SupportEmailClient {

    /** @return true when platform accepted/sent the email */
    boolean send(SupportEmailNotificationEvent event);
}
