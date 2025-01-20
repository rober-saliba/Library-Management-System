package control;

import java.sql.SQLException;
import java.text.ParseException;

public class ManualTest {
    public static void main(String[] args) {
    	
    	System.setProperty("mail.debug", "true");

        // Database connection details
        String username = "root";
        String password = "Aa123456";
        String host = "localhost"; // Change to your database host if needed
        String dbName = "blibdb";

        // Initialize the DBController instance
		DBController dbController = new DBController();

		// Connect to the database
		boolean isConnected = dbController.connectToDB(username, password, host, dbName);

		if (isConnected) {
		    System.out.println("Connected to the database successfully.");

		    // Call the method to check late returns and freeze/unfreeze logic
		    //dbController.checkLateReturn();
		  //  dbController.checkPenalty();
	        testEmailSending();


		    System.out.println("checkLateReturnAndFreeze executed successfully. Check the database for updates.");
		} else {
		    System.out.println("Failed to connect to the database. Exiting...");
		}
    }
    
    private static void testEmailSending() {
        String recipient = "waelswaid@gmail.com"; // Replace with the recipient email
        String subject = "Test Email";
        String body = "This is a test email sent from the Blib system.";

        System.out.println("Testing email functionality...");
        EmailController.sendEmail(recipient, subject, body);
        System.out.println("Email test completed. Check the recipient inbox.");
    }
    
    
}
