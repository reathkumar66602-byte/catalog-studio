package com.catalogstudio.email.service;

import com.catalogstudio.email.dto.OutboundMail;

public interface MailTransport {
    boolean send(OutboundMail mail);
}
