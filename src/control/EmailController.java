package control;

import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;

public class EmailController {

    public static void sendEmail(String recipient, String subject, String body) {
        try {
            SimpleEmail email = new SimpleEmail();
            email.setHostName("smtp.zoho.com"); // Zoho SMTP server
            email.setSmtpPort(587); // TLS port
            email.setAuthentication("blibsys@zohomail.com", "Blib2358"); // Replace with your credentials
            email.setStartTLSRequired(true); // Enable TLS
            email.setFrom("blibsys@zohomail.com", "Blib"); // Sender details
            email.addTo(recipient); // Recipient email
            email.setSubject(subject); // Email subject
            email.setMsg(body); // Email body

            System.out.println("Starting email send...");
            email.send();
            System.out.println("Email sent successfully to " + recipient);
        } catch (EmailException e) {
            System.err.println("Failed to send email: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Test the email sending
        sendEmail("test@example.com", "Test Email", "This is a test email sent using Zoho Mail SMTP.");
    }
}
