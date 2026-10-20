package com.github.randir7.notificationservice.service;

import com.github.randir7.notificationservice.config.NotificationProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Технический компонент отправки письма через SMTP.
 *
 * <p><b>JavaMailSender</b> создан автоконфигурацией spring-boot-starter-mail
 * на основе свойств spring.mail.* (хост, порт, SMTP-флаги). Ручная работа
 * с jakarta.mail.Session и Properties не требуется.
 *
 * <p><b>MimeMessageHelper с явной кодировкой UTF-8:</b> тексты писем
 * содержат кириллицу. Без явного указания charset кодировка взялась бы
 * из настроек платформы, и на части окружений письма пришли бы
 * с искажёнными символами. Явный UTF-8 устраняет этот класс ошибок.
 *
 * <p><b>SimpleMailMessage против MimeMessage:</b> SimpleMailMessage проще,
 * но поддерживает только plain text без явного управления кодировкой
 * и без вложений. MimeMessageHelper одинаково удобен для plain text
 * и готов к расширению (HTML-тела, вложения) без переписывания кода.
 *
 * <p><b>Синхронность и ошибки:</b> send() блокирует поток до передачи
 * письма SMTP-серверу. Ошибки доставки приходят как MailException
 * (наследник RuntimeException) и НАМЕРЕННО пробрасываются наверх:
 * политику обработки сбоя определяет вызывающий слой
 * (Kafka-потребитель повторит обработку, REST-контроллер вернёт 5xx).
 * Технический компонент не должен решать бизнес-вопросы повторно.
 */
@Slf4j
@Component
public class EmailSender {
    
    private final JavaMailSender mailSender;
    private final String fromAddress;
    
    public EmailSender(JavaMailSender mailSender, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.fromAddress = properties.from();
    }
    
    /**
     * Отправка plain-text письма одному получателю.
     *
     * @param to      адрес получателя (email пользователя)
     * @param subject тема письма
     * @param body    тело письма
     */
    public void send(String to, String subject, String body) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body);
        } catch (MessagingException e) {
            // Заполнение полей простого письма не может сломаться на практике,
            // но API MimeMessageHelper объявляет checked-исключение.
            throw new IllegalStateException("Не удалось сформировать письмо для " + to, e);
        }
        mailSender.send(message);
        log.info("Письмо передано SMTP-серверу: to={}, subject={}", to, subject);
    }
}