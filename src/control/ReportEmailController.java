package control;

import org.apache.commons.mail.EmailAttachment;
import org.apache.commons.mail.MultiPartEmail;
import java.io.File;

public class ReportEmailController {

    public static void sendReportEmail(String recipient, String subject, String body, File attachment) {
        try {
            // Create the email
            MultiPartEmail email = new MultiPartEmail();
            email.setHostName("smtp.zoho.com"); // Replace with your SMTP server
            email.setSmtpPort(587); // TLS port
            email.setAuthentication("blibsys@zohomail.com", "Blib2358"); // Replace with your credentials
            email.setStartTLSRequired(true); // Enable TLS
            email.setFrom("blibsys@zohomail.com", "Blib"); // Replace with sender details
            email.addTo(recipient); // Add recipient email
            email.setSubject(subject); // Email subject
            email.setMsg(body); // Email body

            // Attach the report file
            if (attachment != null) {
                EmailAttachment emailAttachment = new EmailAttachment();
                emailAttachment.setPath(attachment.getAbsolutePath());
                emailAttachment.setDisposition(EmailAttachment.ATTACHMENT);
                emailAttachment.setDescription("Report Attachment");
                emailAttachment.setName(attachment.getName());
                email.attach(emailAttachment);
            }

            email.send();
            System.out.println("Report email sent successfully to: " + recipient);
        } catch (Exception e) {
            System.err.println("Failed to send report email: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
