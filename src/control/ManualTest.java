package control;

import entity.ConstantsAndGlobalVars;
import entity.MsgParser;
import enums.UserStatus;

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
            dbController.checkLateReturn();
            // Test the getUserStatusTask
            try {
                testGetUserStatus(dbController);
            } catch (Exception e) {
                e.printStackTrace();
            }

            System.out.println("Testing completed.");
        } else {
            System.out.println("Failed to connect to the database. Exiting...");
        }
    }

    private static void testGetUserStatus(DBController dbController) {
        // Simulate a MsgParser to send to the DBController
        MsgParser<String> msg = new MsgParser<>();
        String testUserID = "319008264"; // Replace with a valid userID in your database
        msg.addToCommPipe(testUserID);

        // Call the method and print the result
        MsgParser response = dbController.getUserStatus(msg);
        if (!response.getCommPipe().isEmpty()) {
            String userStatus = (String) response.getCommPipe().get(0);
            System.out.println("User ID: " + testUserID + ", Status: " + userStatus);

            // Validate the status
            if (userStatus.equals(UserStatus.Active.name())) {
                System.out.println("The user is Active and can borrow/reserve.");
            } else if (userStatus.equals(UserStatus.Frozen.name())) {
                System.out.println("The user is Frozen and cannot borrow/reserve.");
            } else {
                System.out.println("Unexpected status: " + userStatus);
            }
        } else {
            System.out.println("No status returned for user ID: " + testUserID);
        }
    }
}
