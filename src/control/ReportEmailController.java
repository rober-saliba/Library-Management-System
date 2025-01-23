package control;
import java.io.File;

import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.EmailAttachment;
import org.apache.commons.mail.MultiPartEmail;

public class ReportEmailController {

    public static void sendReportEmail(String recipient, String subject, String body, File[] attachments) {
        try {
            // Ensure the email body is not null or empty
            if (body == null || body.trim().isEmpty()) {
                body = "Please find the attached reports."; // Default message
            }

            // Create the email
            MultiPartEmail email = new MultiPartEmail();
            email.setHostName("smtp.zoho.com");
            email.setSmtpPort(587);
            email.setAuthenticator(new DefaultAuthenticator("blibsys@zohomail.com", "Blib2358")); // Replace with your credentials
            email.setStartTLSEnabled(true);
            email.setFrom("blibsys@zohomail.com", "Blib System"); // Replace with sender details
            email.addTo(recipient);
            email.setSubject(subject);
            email.setMsg(body); // Set the email body

            // Attach all the files
            if (attachments != null) {
                for (File attachment : attachments) {
                    if (attachment != null && attachment.exists()) {
                        EmailAttachment emailAttachment = new EmailAttachment();
                        emailAttachment.setPath(attachment.getAbsolutePath());
                        emailAttachment.setDisposition(EmailAttachment.ATTACHMENT);
                        emailAttachment.setName(attachment.getName());
                        email.attach(emailAttachment);
                    }
                }
            }

            // Send the email
            email.send();
            System.out.println("Email sent successfully to: " + recipient);

        } catch (Exception e) {
            System.err.println("Failed to send report email to " + recipient + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}