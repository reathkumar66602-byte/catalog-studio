package com.catalogstudio.config;

import java.util.Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class ZohoMailSenderConfig {

    @Bean
    JavaMailSender javaMailSender(CatalogStudioProperties properties) {
        CatalogStudioProperties.Mail mail = properties.mail();
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mail.resolvedHost());
        sender.setPort(mail.port() <= 0 ? 587 : mail.port());
        sender.setUsername(mail.username());
        sender.setPassword(mail.password());
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        props.put("mail.smtp.ssl.trust", sender.getHost());
        if (sender.getPort() == 465) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.required", "true");
            props.put("mail.smtp.starttls.enable", "false");
        } else {
            props.put("mail.smtp.starttls.enable", Boolean.toString(mail.startTls()));
            props.put("mail.smtp.starttls.required", Boolean.toString(mail.startTls()));
        }
        return sender;
    }
}
